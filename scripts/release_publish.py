#!/usr/bin/env python3
"""Publish a verified, tag-triggered CI package to GitHub Releases only.

Uses only the ephemeral GITHUB_TOKEN. Never creates/moves a tag, replaces an
asset, deletes a release or broadens permissions. A failure leaves a draft for
inspection and an exact same-package retry. Workflow must gate this job on every
required build/test job and serialize publishers by tag.
"""
from __future__ import annotations

import argparse
from dataclasses import replace
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, build_opener, HTTPRedirectHandler

from release_package import Context, PROVENANCE, ReleaseError, digest, git, package_bytes, require, verify_package


class NoRedirects(HTTPRedirectHandler):
    # Never send a release token through an unexpected redirect.
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class GitHub:
    def __init__(self, repository: str, token: str):
        require(bool(token), "GITHUB_TOKEN is required")
        self.repository = repository
        self.token = token
        self.opener = build_opener(NoRedirects())

    def request(self, method: str, path: str, payload=None, *, upload=None):
        require(path.startswith(f"/repos/{self.repository}/"), "Unexpected API destination")
        base = "https://uploads.github.com" if upload is not None else "https://api.github.com"
        data = upload if upload is not None else (json.dumps(payload).encode() if payload is not None else None)
        headers = {"Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
                   "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "solintegrations-release-ci"}
        if data is not None:
            headers["Content-Type"] = "application/octet-stream" if upload is not None else "application/json"
        try:
            with self.opener.open(Request(base + path, data=data, method=method, headers=headers), timeout=60) as response:
                return json.load(response)
        except HTTPError as error:
            # Do not log response bodies, request headers or token-bearing diagnostics.
            raise ReleaseError(f"GitHub {method} {path} returned HTTP {error.code}; no automatic write retry") from None
        except (URLError, ValueError, TimeoutError) as error:
            raise ReleaseError(f"GitHub {method} {path} failed ({type(error).__name__}); inspect before retrying") from None

    def list_all(self, path: str):
        result = []
        page = 1
        while True:
            values = self.request("GET", f"{path}?per_page=100&page={page}")
            require(isinstance(values, list), "Unexpected paginated GitHub response")
            result.extend(values)
            if len(values) < 100:
                return result
            page += 1


def validate_event(root: Path, tested_sha: str, env):
    require(env.get("GITHUB_EVENT_NAME") == "push", "Only tag push events can publish")
    require(env.get("GITHUB_SERVER_URL", "https://github.com") == "https://github.com", "Only github.com is supported")
    context = Context.load(root, tested_sha, env)
    require(env.get("GITHUB_REF") == f"refs/tags/{context.tag}", "Tag must be exactly v<mod_version>")
    require(git(root, "rev-parse", f"refs/tags/{context.tag}^{{commit}}") == context.commit,
            "Local tag no longer points to the tested commit")
    git(root, "merge-base", "--is-ancestor", context.commit, "refs/remotes/origin/main")
    return context


def remote_identity(api, context):
    prefix = f"/repos/{context.repository}"
    ref = api.request("GET", f"{prefix}/git/ref/tags/{quote(context.tag, safe='')}")
    obj = ref.get("object", {})
    original = (obj.get("type"), obj.get("sha"))
    for _ in range(8):
        if obj.get("type") == "commit":
            break
        require(obj.get("type") == "tag" and isinstance(obj.get("sha"), str), "Unsupported Git tag object")
        obj = api.request("GET", f"{prefix}/git/tags/{quote(obj['sha'], safe='')}").get("object", {})
    require(obj.get("type") == "commit" and obj.get("sha") == context.commit,
            "Remote tag moved or does not identify the tested commit")
    comparison = api.request("GET", f"{prefix}/compare/{context.commit}...main")
    require(comparison.get("status") in ("ahead", "identical"), "Tested commit is not reachable from current main")
    return original


def release_spec(context):
    marker = (f"<!-- solintegrations-release-v1 repository={context.repository} commit={context.commit} "
              f"run_id={context.run_id} run_attempt={context.run_attempt} -->")
    return {
        "tag_name": context.tag,
        "target_commitish": context.commit,
        "name": context.tag,
        "body": (f"Spice of Life Integrations {context.version}\n\n"
                 f"Built from {context.commit}; validated by the complete CI test matrix.\n"
                 "SHA256SUMS and release-provenance.json accompany the production jar.\n\n" + marker),
        "prerelease": "-" in context.version.split("+", 1)[0],
    }


def validate_release(release, expected):
    require(all(release.get(key) == value for key, value in expected.items()),
            "Existing release belongs to different content/run or has conflicting metadata")
    require(type(release.get("draft")) is bool and type(release.get("id")) is int,
            "Invalid release identity/state")


def verify_assets(api, prefix, release_id, files, *, complete=False):
    assets = api.list_all(f"{prefix}/releases/{release_id}/assets")
    names = set()
    for asset in assets:
        name = asset.get("name")
        require(name in files and name not in names, "Unexpected or duplicate release asset; refusing collision")
        names.add(name)
        require(asset.get("state") == "uploaded" and asset.get("size") == len(files[name])
                and asset.get("digest") == f"sha256:{digest(files[name])}",
                f"Existing asset has different bytes or incomplete upload: {name}")
    if complete:
        require(names == set(files), "Release assets are incomplete")
    return names


def publish(root: Path, package: Path, tested_sha: str, env=None, api=None):
    env = os.environ if env is None else env
    context = validate_event(root, tested_sha, env)
    files = verify_package(package, context, root)
    # Construct the network client only after every local/event check succeeds.
    api = api if api is not None else GitHub(context.repository, env.get("GITHUB_TOKEN", ""))
    prefix = f"/repos/{context.repository}"
    identity = remote_identity(api, context)
    require(identity[1] == git(root, "rev-parse", f"refs/tags/{context.tag}"),
            "Remote tag object changed since checkout")
    expected = release_spec(context)
    releases = [release for release in api.list_all(f"{prefix}/releases") if release.get("tag_name") == context.tag]
    require(len(releases) <= 1, "Multiple releases use this tag; refusing collision")
    if releases:
        release = releases[0]
        # A full GitHub Actions rerun increments run_attempt. Preserve an earlier
        # attempt's already-uploaded provenance; never relabel or overwrite it.
        attempt = re.search(r" run_attempt=([1-9][0-9]*) -->$", release.get("body") or "")
        require(attempt is not None and int(attempt[1]) <= int(context.run_attempt),
                "Existing release has invalid/future attempt metadata")
        original_context = replace(context, run_attempt=attempt[1])
        expected = release_spec(original_context)
        validate_release(release, expected)
        if original_context != context:
            original_files = package_bytes(files[context.jar_name], original_context)
            names = verify_assets(api, prefix, release["id"], original_files)
            require(PROVENANCE in names,
                    "Earlier-attempt draft has no verified provenance; inspect it before retrying")
            files = original_files
    else:
        require(remote_identity(api, context) == identity, "Remote tag changed during release preparation")
        release = api.request("POST", f"{prefix}/releases", {**expected, "draft": True})
        validate_release(release, expected)
        require(release["draft"], "New release was unexpectedly published")
    release_id = release["id"]
    names = verify_assets(api, prefix, release_id, files, complete=not release["draft"])
    if not release["draft"]:
        require(remote_identity(api, context) == identity, "Remote tag changed during verification")
        return f"Already published with identical assets: {context.tag}"
    for name in sorted(files, key=lambda name: (name != PROVENANCE, name)):
        content = files[name]
        if name not in names:
            require(remote_identity(api, context) == identity, "Remote tag changed before upload")
            api.request("POST", f"{prefix}/releases/{release_id}/assets?name={quote(name, safe='')}", upload=content)
    verify_assets(api, prefix, release_id, files, complete=True)
    current = api.request("GET", f"{prefix}/releases/{release_id}")
    validate_release(current, expected)
    require(current["draft"], "Release state changed concurrently; inspect before retrying")
    require(remote_identity(api, context) == identity, "Remote tag changed before publication")
    published = api.request("PATCH", f"{prefix}/releases/{release_id}", {"draft": False})
    validate_release(published, expected)
    require(not published["draft"], "GitHub did not publish the draft")
    verify_assets(api, prefix, release_id, files, complete=True)
    require(remote_identity(api, context) == identity, "Remote tag changed during publication; inspect the release")
    return f"Published {context.tag} with all three verified assets"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tested-sha", required=True)
    parser.add_argument("--package", type=Path, default=Path("build/ci-package"))
    args = parser.parse_args()
    try:
        print(publish(Path.cwd(), args.package, args.tested_sha))
    except (ReleaseError, OSError) as error:
        print(f"GitHub release rejected: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
