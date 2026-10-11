#!/usr/bin/env python3
"""Collect and validate the one production Forge jar; no network or publication.

Python 3.11+. CI must run Gradle build/reobfJarJar before prepare. The checks below
are packaging guards, not a substitute for the build, unit tests or GameTests.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import tomllib
import zipfile


MOD_ID = "solintegrations"
PACKAGE = "dev/uppermoon/solintegrations/"
PROVENANCE = "release-provenance.json"
CHECKSUMS = "SHA256SUMS"
JIJ_PATH = "META-INF/jarjar/mixinextras-forge-0.5.0.jar"
VERSION_RE = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?\Z")
SHA_RE = re.compile(r"[0-9a-f]{40}\Z")


class ReleaseError(Exception):
    """A failed release safety invariant."""


def require(condition, message):
    if not condition:
        raise ReleaseError(message)


def regular_file(path: Path) -> bytes:
    require(not any(p.is_symlink() for p in (path, *path.parents)), f"Symlink is not allowed: {path}")
    require(path.is_file(), f"Missing regular file: {path}")
    require(stat.S_ISREG(path.stat().st_mode), f"Not a regular file: {path}")
    return path.read_bytes()


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_json(value) -> bytes:
    return (json.dumps(value, sort_keys=True, indent=2) + "\n").encode()


def git(root: Path, *args: str) -> str:
    result = subprocess.run(["git", *args], cwd=root, text=True, capture_output=True, check=False)
    require(result.returncode == 0, f"Git check failed: {' '.join(args)}")
    return result.stdout.strip()


def read_version(root: Path) -> str:
    props = {}
    for line in regular_file(root / "gradle.properties").decode().splitlines():
        if not line.strip() or line.lstrip().startswith(("#", "!")):
            continue
        key, sep, value = line.partition("=")
        if key.strip() in ("mod_id", "mod_version"):
            require(sep and key.strip() not in props, f"Ambiguous property: {key}")
            props[key.strip()] = value.strip()
    require(props.get("mod_id") == MOD_ID, "Unexpected mod_id")
    version = props.get("mod_version", "")
    match = VERSION_RE.fullmatch(version)
    require(len(version) <= 100 and match is not None, "mod_version must be a valid SemVer")
    if match.group(4):
        require(all(not (part.isdigit() and len(part) > 1 and part[0] == "0")
                    for part in match.group(4).split(".")), "Invalid SemVer prerelease")
    return version


@dataclass(frozen=True)
class Context:
    repository: str
    commit: str
    run_id: str
    run_attempt: str
    version: str

    @property
    def jar_name(self):
        return f"{MOD_ID}-{self.version}.jar"

    @property
    def tag(self):
        return f"v{self.version}"

    @classmethod
    def load(cls, root: Path, tested_sha: str, env=None):
        env = os.environ if env is None else env
        repository = env.get("GITHUB_REPOSITORY", "")
        sha = env.get("GITHUB_SHA", "")
        require(re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) is not None,
                "GITHUB_REPOSITORY must identify one repository")
        require(SHA_RE.fullmatch(sha) is not None and tested_sha == sha,
                "Tested SHA does not match GITHUB_SHA")
        require(git(root, "rev-parse", "HEAD") == sha, "Checked-out HEAD is not the tested SHA")
        ids = [env.get("GITHUB_RUN_ID", ""), env.get("GITHUB_RUN_ATTEMPT", "")]
        require(all(re.fullmatch(r"[1-9][0-9]*", value) for value in ids), "Missing or invalid CI run/attempt")
        return cls(repository, sha, *ids, read_version(root))

    def provenance(self, jar_bytes):
        return {
            "schema_version": 1,
            "repository": self.repository,
            "commit": self.commit,
            "run_id": self.run_id,
            "run_attempt": self.run_attempt,
            "mod_version": self.version,
            "artifact": {"filename": self.jar_name, "sha256": digest(jar_bytes), "size": len(jar_bytes)},
        }


def archive_names(archive):
    infos = archive.infolist()
    names = [entry.filename for entry in infos]
    require(len(names) == len(set(names)), "Duplicate jar entries")
    for entry in infos:
        path = PurePosixPath(entry.filename)
        require(not path.is_absolute() and ".." not in path.parts and "\\" not in entry.filename,
                "Unsafe jar entry")
        require(not stat.S_ISLNK(entry.external_attr >> 16), "Symlink inside jar")
    require(archive.testzip() is None, "Corrupt jar entry")
    return names


def validate_jar(data: bytes, context: Context, root: Path):
    """Reject a renamed slim/dev jar, omitted runtime, test classes or wrong build."""
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            names = archive_names(jar)
            mods = tomllib.loads(jar.read("META-INF/mods.toml").decode()).get("mods", [])
            require(len(mods) == 1 and mods[0].get("modId") == MOD_ID
                    and mods[0].get("version") == context.version, "Embedded mods.toml version/mod mismatch")
            manifest = jar.read("META-INF/MANIFEST.MF").decode().replace("\r\n", "\n").replace("\n ", "")
            fields = dict(line.split(": ", 1) for line in manifest.splitlines() if ": " in line)
            require(fields.get("Implementation-Version") == context.version, "Manifest version mismatch")
            require(fields.get("MixinConfigs") == f"{MOD_ID}.mixins.json", "Manifest mixin configuration mismatch")
            mixins = json.loads(jar.read(f"{MOD_ID}.mixins.json"))
            source_mixins = json.loads(regular_file(root / "src/main/resources" / f"{MOD_ID}.mixins.json"))
            require(mixins == source_mixins and mixins.get("refmap") == f"{MOD_ID}.refmap.json",
                    "Mixin configuration/refmap mismatch")
            refmap = json.loads(jar.read(f"{MOD_ID}.refmap.json"))
            mappings = refmap.get("mappings")
            require(isinstance(mappings, dict) and mappings
                    and mappings == refmap.get("data", {}).get("searge"), "Missing production searge refmap")
            mapped_mixins = {PACKAGE + "mixin/integration/tfc/" + name for name in
                             ("WickerBasketTfcMixin", "PlayerConsumptionMixin", "TfcFoodDataRestorationMixin")}
            require(mapped_mixins <= set(mappings), "Refmap is missing a production mixin")
            require(all(isinstance(values, dict) and values and
                        all(isinstance(value, str) and re.search(r"[mf]_[0-9]+_", value)
                            for value in values.values()) for values in mappings.values()),
                    "Refmap has missing or non-production mappings")
            # Java source ownership is deliberately closed: only main-source classes,
            # including their inner classes, may appear outside the intended JIJ.
            sources = {p.relative_to(root / "src/main/java").with_suffix("").as_posix()
                       for p in (root / "src/main/java").rglob("*.java")}
            classes = {name[:-6] for name in names if name.endswith(".class")}
            require(sources and sources <= classes, "Missing main-source classes")
            require(all(name.split("$", 1)[0] in sources and name.startswith(PACKAGE) for name in classes),
                    "Test or upstream implementation classes were packaged")
            bridge = jar.read(PACKAGE + "integration/tfc/TfcConsumptionBridge.class")
            require(bridge.startswith(b"\xca\xfe\xba\xbe") and b"m_36324_" in bridge,
                    "Jar is not reobfuscated (Player.getFoodData production reference missing)")
            metadata = json.loads(jar.read("META-INF/jarjar/metadata.json"))
            expected_jij = {
                "identifier": {"group": "io.github.llamalad7", "artifact": "mixinextras-forge"},
                "version": {"range": "[0.5.0,0.6)", "artifactVersion": "0.5.0"},
                "path": JIJ_PATH, "isObfuscated": False,
            }
            require(metadata == {"jars": [expected_jij]}, "Unexpected or missing MixinExtras JIJ metadata")
            require([name for name in names if name.endswith(".jar")] == [JIJ_PATH], "Unexpected embedded jars")
            with zipfile.ZipFile(io.BytesIO(jar.read(JIJ_PATH))) as nested:
                nested_names = archive_names(nested)
                core_path = "META-INF/jars/MixinExtras-0.5.0.jar"
                require("com/llamalad7/mixinextras/platform/forge/MixinExtrasConfigPlugin.class" in nested_names
                        and [name for name in nested_names if name.endswith(".jar")] == [core_path],
                        "Invalid MixinExtras Forge runtime")
                with zipfile.ZipFile(io.BytesIO(nested.read(core_path))) as core:
                    core_names = archive_names(core)
                    require("com/llamalad7/mixinextras/MixinExtrasBootstrap.class" in core_names
                            and not any(name.endswith(".jar") for name in core_names), "Invalid MixinExtras core runtime")
                    require(all(name.startswith("com/llamalad7/mixinextras/") for name in nested_names + core_names
                                if name.endswith(".class")), "Unexpected classes hidden inside MixinExtras runtime")
    except (KeyError, ValueError, TypeError, zipfile.BadZipFile, UnicodeError) as error:
        raise ReleaseError(f"Invalid production jar: {error}") from error


def package_bytes(jar_bytes: bytes, context: Context):
    provenance = canonical_json(context.provenance(jar_bytes))
    checksums = f"{digest(jar_bytes)}  {context.jar_name}\n{digest(provenance)}  {PROVENANCE}\n".encode()
    return {context.jar_name: jar_bytes, PROVENANCE: provenance, CHECKSUMS: checksums}


def verify_package(directory: Path, context: Context, root: Path):
    require(directory.is_dir() and not directory.is_symlink(), "Missing package directory or symlink")
    expected_names = {context.jar_name, PROVENANCE, CHECKSUMS}
    require({p.name for p in directory.iterdir()} == expected_names, "Package must contain exactly the three release assets")
    files = {name: regular_file(directory / name) for name in expected_names}
    validate_jar(files[context.jar_name], context, root)
    require(files == package_bytes(files[context.jar_name], context),
            "Checksum or provenance mismatch (repository, commit, version, run and attempt must match)")
    return files


def prepare(root: Path, output: Path, context: Context):
    jar_path = root / "build/libs" / context.jar_name
    jar_bytes = regular_file(jar_path)
    validate_jar(jar_bytes, context, root)
    files = package_bytes(jar_bytes, context)
    if output.exists() or output.is_symlink():
        require(verify_package(output, context, root) == files, "Existing package differs; refusing to overwrite")
        return files
    require(not any(p.is_symlink() for p in output.parents), "Symlink package parent")
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix=".release-package-", dir=output.parent))
    try:
        for name, content in files.items():
            (temporary / name).write_bytes(content)
        temporary.rename(output)
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)
    return verify_package(output, context, root)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["prepare", "verify"])
    parser.add_argument("--tested-sha", required=True)
    parser.add_argument("--output", type=Path, default=Path("build/ci-package"))
    args = parser.parse_args()
    root = Path.cwd()
    try:
        context = Context.load(root, args.tested_sha)
        if args.command == "prepare":
            prepare(root, args.output, context)
        else:
            verify_package(args.output, context, root)
        print(f"Verified {context.jar_name}: commit {context.commit}, run {context.run_id}/{context.run_attempt}")
    except (ReleaseError, OSError) as error:
        print(f"Release package rejected: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
