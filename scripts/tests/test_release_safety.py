"""Offline release regression tests. Every Git/network operation is mocked."""
from __future__ import annotations

from copy import deepcopy
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlparse
import zipfile

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import release_package as package
import release_publish as publisher

SHA = "a" * 40
OTHER_SHA = "b" * 40
REPOSITORY = "UpperMoon0/SpiceOfLife-Integrations"
PREFIX = f"/repos/{REPOSITORY}"


def zip_bytes(entries):
    result = io.BytesIO()
    with zipfile.ZipFile(result, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in entries.items():
            archive.writestr(name, data)
    return result.getvalue()


class Fixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.output = self.root / "build/ci-package"
        (self.root / "gradle.properties").write_text("mod_id=solintegrations\nmod_version=0.1.0\n")
        mixins = json.loads((SCRIPTS.parent / "src/main/resources/solintegrations.mixins.json").read_text())
        resource = self.root / "src/main/resources/solintegrations.mixins.json"
        resource.parent.mkdir(parents=True)
        resource.write_text(json.dumps(mixins))
        self.entries = {
            "META-INF/mods.toml": '[[mods]]\nmodId="solintegrations"\nversion="0.1.0"\n',
            "META-INF/MANIFEST.MF": "Manifest-Version: 1.0\r\nImplementation-Version: 0.1.0\r\nMixinConfigs: solintegrations.mixins.json\r\n\r\n",
            "solintegrations.mixins.json": json.dumps(mixins),
            "solintegrations.refmap.json": "",
            "META-INF/jarjar/metadata.json": json.dumps({"jars": [{
                "identifier": {"group": "io.github.llamalad7", "artifact": "mixinextras-forge"},
                "version": {"range": "[0.5.0,0.6)", "artifactVersion": "0.5.0"},
                "path": package.JIJ_PATH, "isObfuscated": False}]}),
            package.JIJ_PATH: zip_bytes({
                "com/llamalad7/mixinextras/platform/forge/MixinExtrasConfigPlugin.class": b"runtime",
                "META-INF/jars/MixinExtras-0.5.0.jar": zip_bytes({"com/llamalad7/mixinextras/MixinExtrasBootstrap.class": b"runtime"}),
            }),
        }
        mappings = {package.PACKAGE + "mixin/integration/tfc/" + name: {"method": "m_1234_"}
                    for name in ("WickerBasketTfcMixin", "PlayerConsumptionMixin", "TfcFoodDataRestorationMixin")}
        self.entries["solintegrations.refmap.json"] = json.dumps({"mappings": mappings, "data": {"searge": mappings}})
        for source in (SCRIPTS.parent / "src/main/java").rglob("*.java"):
            relative = source.relative_to(SCRIPTS.parent / "src/main/java")
            fixture_source = self.root / "src/main/java" / relative
            fixture_source.parent.mkdir(parents=True, exist_ok=True)
            fixture_source.write_text("// A synthetic class fixture, never executed.\n")
            self.entries[relative.with_suffix(".class").as_posix()] = b"\xca\xfe\xba\xbe\0m_36324_\0"
        self.env = {
            "GITHUB_REPOSITORY": REPOSITORY, "GITHUB_SHA": SHA,
            "GITHUB_RUN_ID": "12345", "GITHUB_RUN_ATTEMPT": "1",
            "GITHUB_EVENT_NAME": "push", "GITHUB_REF": "refs/tags/v0.1.0",
            "GITHUB_TOKEN": "ephemeral-test-token", "GITHUB_SERVER_URL": "https://github.com",
        }
        self.context = package.Context(REPOSITORY, SHA, "12345", "1", "0.1.0")
        self.jar = self.root / "build/libs" / self.context.jar_name
        self.jar.parent.mkdir(parents=True)
        self.write_jar()
        self.git_head = SHA
        self.git_tag = SHA
        self.git_tag_object = SHA
        self.on_main = True
        self.git_patch = patch.object(package, "git", side_effect=self.git)
        self.git_patch.start()
        self.addCleanup(self.git_patch.stop)
        self.publish_git_patch = patch.object(publisher, "git", side_effect=self.git)
        self.publish_git_patch.start()
        self.addCleanup(self.publish_git_patch.stop)

    def git(self, root, *args):
        if args == ("rev-parse", "HEAD"):
            return self.git_head
        if args == ("rev-parse", "refs/tags/v0.1.0^{commit}"):
            return self.git_tag
        if args == ("rev-parse", "refs/tags/v0.1.0"):
            return self.git_tag_object
        if args == ("merge-base", "--is-ancestor", SHA, "refs/remotes/origin/main"):
            if not self.on_main:
                raise package.ReleaseError("not on main")
            return ""
        raise AssertionError(f"Unexpected git call: {args}")

    def write_jar(self):
        self.jar.write_bytes(zip_bytes(self.entries))

    def prepare(self):
        return package.prepare(self.root, self.output, self.context)


class PackageTests(Fixture):
    def test_prepare_exact_three_assets_and_verify(self):
        files = self.prepare()
        self.assertEqual(set(files), {self.context.jar_name, package.PROVENANCE, package.CHECKSUMS})
        self.assertEqual(files, package.verify_package(self.output, self.context, self.root))
        self.assertEqual(files, self.prepare())
        provenance = json.loads(files[package.PROVENANCE])
        self.assertEqual(provenance["artifact"]["sha256"], hashlib.sha256(self.jar.read_bytes()).hexdigest())
        self.assertEqual("1", provenance["run_attempt"])

    def test_slim_sibling_is_not_selected(self):
        (self.jar.parent / "solintegrations-0.1.0-slim.jar").write_bytes(b"wrong")
        self.assertEqual(self.prepare()[self.jar.name], self.jar.read_bytes())

    def test_missing_and_wrong_name(self):
        self.jar.rename(self.jar.with_name("solintegrations-0.1.0-slim.jar"))
        with self.assertRaises(package.ReleaseError):
            self.prepare()

    def test_symlink_file_and_parent_rejected(self):
        target = self.root / "original.jar"
        self.jar.rename(target)
        self.jar.symlink_to(target)
        with self.assertRaises(package.ReleaseError):
            self.prepare()
        self.jar.unlink()
        self.jar.parent.rmdir()
        other = self.root / "other"
        other.mkdir()
        (other / self.jar.name).write_bytes(target.read_bytes())
        self.jar.parent.symlink_to(other, target_is_directory=True)
        with self.assertRaises(package.ReleaseError):
            self.prepare()

    def test_wrong_embedded_version_and_manifest(self):
        for name in ("META-INF/mods.toml", "META-INF/MANIFEST.MF"):
            with self.subTest(name=name):
                original = self.entries[name]
                self.entries[name] = original.replace("0.1.0", "0.2.0")
                self.write_jar()
                with self.assertRaises(package.ReleaseError):
                    self.prepare()
                self.entries[name] = original

    def test_missing_jij_refmap_and_dev_artifact_rejected(self):
        for name in (package.JIJ_PATH, "solintegrations.refmap.json", "META-INF/jarjar/metadata.json"):
            with self.subTest(name=name):
                old = self.entries.pop(name)
                self.write_jar()
                with self.assertRaises(package.ReleaseError):
                    self.prepare()
                self.entries[name] = old
        bridge = package.PACKAGE + "integration/tfc/TfcConsumptionBridge.class"
        self.entries[bridge] = b"\xca\xfe\xba\xbe\0getFoodData\0"
        self.write_jar()
        with self.assertRaisesRegex(package.ReleaseError, "not reobfuscated"):
            self.prepare()

    def test_tests_and_upstream_implementations_rejected(self):
        for name in (package.PACKAGE + "integration/tfc/FoodIntegrationGameTests.class",
                     package.PACKAGE + "integration/tfc/ConfigSnapshotScope.class",
                     "com/github/leopoko/solclassic/FoodHelper.class",
                     "net/dries007/tfc/Food.class", "net/p3pp3rf1y/sophisticatedcore/Feeding.class",
                     "org/junit/Test.class"):
            with self.subTest(name=name):
                self.entries[name] = b"unwanted"
                self.write_jar()
                with self.assertRaisesRegex(package.ReleaseError, "Test or upstream"):
                    self.prepare()
                del self.entries[name]

    def test_extra_nested_jar_rejected(self):
        self.entries["META-INF/jarjar/unexpected.jar"] = b"extra"
        self.write_jar()
        with self.assertRaisesRegex(package.ReleaseError, "Unexpected embedded"):
            self.prepare()

    def test_hidden_upstream_class_in_runtime_rejected(self):
        self.entries[package.JIJ_PATH] = zip_bytes({
            "com/llamalad7/mixinextras/platform/forge/MixinExtrasConfigPlugin.class": b"runtime",
            "META-INF/jars/MixinExtras-0.5.0.jar": zip_bytes({
                "com/llamalad7/mixinextras/MixinExtrasBootstrap.class": b"runtime",
                "net/dries007/tfc/UpstreamImplementation.class": b"unwanted",
            }),
        })
        self.write_jar()
        with self.assertRaisesRegex(package.ReleaseError, "hidden inside"):
            self.prepare()

    def test_malformed_or_incomplete_refmap_rejected(self):
        original = json.loads(self.entries["solintegrations.refmap.json"])
        for refmap in ({}, {"mappings": {}}, {"mappings": original["mappings"], "data": {"searge": {}}},
                       {"mappings": {"method": "getFoodData"}, "data": {"searge": {"method": "getFoodData"}}}):
            self.entries["solintegrations.refmap.json"] = json.dumps(refmap)
            self.write_jar()
            with self.assertRaises(package.ReleaseError):
                self.prepare()

    def test_bad_jar_rejected(self):
        self.jar.write_bytes(b"not a jar")
        with self.assertRaises(package.ReleaseError):
            self.prepare()

    def test_package_tampering_and_extra_files(self):
        files = self.prepare()
        for name in files:
            with self.subTest(name=name):
                (self.output / name).write_bytes(files[name] + b"changed")
                with self.assertRaises(package.ReleaseError):
                    package.verify_package(self.output, self.context, self.root)
                (self.output / name).write_bytes(files[name])
        (self.output / "extra.jar").write_bytes(b"extra")
        with self.assertRaises(package.ReleaseError):
            package.verify_package(self.output, self.context, self.root)

    def test_provenance_fields_cannot_be_relabelled(self):
        files = self.prepare()
        for field, value in (("repository", "attacker/repo"), ("commit", OTHER_SHA),
                             ("run_id", "54321"), ("run_attempt", "2"), ("mod_version", "0.2.0")):
            with self.subTest(field=field):
                provenance = json.loads(files[package.PROVENANCE])
                provenance[field] = value
                changed = package.canonical_json(provenance)
                (self.output / package.PROVENANCE).write_bytes(changed)
                sums = f"{package.digest(files[self.jar.name])}  {self.jar.name}\n{package.digest(changed)}  {package.PROVENANCE}\n"
                (self.output / package.CHECKSUMS).write_text(sums)
                with self.assertRaisesRegex(package.ReleaseError, "provenance mismatch"):
                    package.verify_package(self.output, self.context, self.root)

    def test_invalid_versions_rejected(self):
        for value in ("", "v0.1.0", "01.1.0", "1.2", "../0.1.0", "1.2.3-01", "1.2.3 bad"):
            with self.subTest(version=value):
                (self.root / "gradle.properties").write_text(f"mod_id=solintegrations\nmod_version={value}\n")
                with self.assertRaises(package.ReleaseError):
                    package.read_version(self.root)

    def test_valid_versions(self):
        for value in ("0.1.0", "1.2.3-rc.1", "1.2.3+build.5", "1.2.3-beta+build.5"):
            (self.root / "gradle.properties").write_text(f"mod_id=solintegrations\nmod_version={value}\n")
            self.assertEqual(value, package.read_version(self.root))


class FakeGitHub:
    def __init__(self, context):
        self.context = context
        self.release = None
        self.assets = {}
        self.calls = []
        self.ref = {"type": "commit", "sha": SHA}
        self.tag_target = {"type": "commit", "sha": SHA}
        self.main_status = "ahead"
        self.fail_upload = None
        self.extra_release = False
        self.change_after_upload = False

    def list_all(self, path):
        self.calls.append(("GET", path))
        if path == PREFIX + "/releases":
            return ([deepcopy(self.release)] * (2 if self.extra_release else 1)) if self.release else []
        if path == PREFIX + "/releases/7/assets":
            return deepcopy(list(self.assets.values()))
        raise AssertionError(path)

    def request(self, method, path, payload=None, *, upload=None):
        self.calls.append((method, path))
        if method == "GET" and path.startswith(PREFIX + "/git/ref/tags/"):
            return {"object": deepcopy(self.ref)}
        if method == "GET" and path.startswith(PREFIX + "/git/tags/"):
            return {"object": deepcopy(self.tag_target)}
        if method == "GET" and path == PREFIX + f"/compare/{SHA}...main":
            return {"status": self.main_status}
        if method == "POST" and path == PREFIX + "/releases":
            assert self.release is None
            self.release = {**deepcopy(payload), "id": 7}
            return deepcopy(self.release)
        if method == "POST" and path.startswith(PREFIX + "/releases/7/assets?"):
            name = parse_qs(urlparse(path).query)["name"][0]
            if name == self.fail_upload:
                raise package.ReleaseError("Simulated upload failure")
            assert name not in self.assets, "A release asset must never be overwritten"
            self.add_asset(name, upload)
            if self.change_after_upload:
                self.ref = {"type": "commit", "sha": OTHER_SHA}
            return deepcopy(self.assets[name])
        if method == "GET" and path == PREFIX + "/releases/7":
            return deepcopy(self.release)
        if method == "PATCH" and path == PREFIX + "/releases/7":
            assert payload == {"draft": False}
            self.release.update(payload)
            return deepcopy(self.release)
        raise AssertionError((method, path))

    def add_asset(self, name, data):
        self.assets[name] = {"name": name, "size": len(data), "digest": f"sha256:{package.digest(data)}", "state": "uploaded"}

    def existing(self, files, draft=True):
        self.release = {**publisher.release_spec(self.context), "id": 7, "draft": draft}
        for name, data in files.items():
            self.add_asset(name, data)

    def writes(self):
        return [call for call in self.calls if call[0] != "GET"]


class PublishTests(Fixture):
    def setUp(self):
        super().setUp()
        self.files = self.prepare()
        self.api = FakeGitHub(self.context)

    def publish(self, tested_sha=SHA):
        return publisher.publish(self.root, self.output, tested_sha, self.env, self.api)

    def assert_rejected_without_write(self):
        with self.assertRaises(package.ReleaseError):
            self.publish()
        self.assertEqual([], self.api.writes())

    def test_new_release_stages_verifies_and_publishes(self):
        self.assertIn("Published v0.1.0", self.publish())
        self.assertFalse(self.api.release["draft"])
        self.assertEqual(set(self.files), set(self.api.assets))
        writes = self.api.writes()
        self.assertEqual(("POST", PREFIX + "/releases"), writes[0])
        self.assertIn("name=release-provenance.json", writes[1][1])
        self.assertEqual(("PATCH", PREFIX + "/releases/7"), writes[-1])

    def test_pr_manual_and_branch_events_rejected(self):
        for event, ref in (("pull_request", "refs/tags/v0.1.0"), ("pull_request_target", "refs/tags/v0.1.0"),
                           ("workflow_dispatch", "refs/tags/v0.1.0"), ("push", "refs/heads/main")):
            with self.subTest(event=event, ref=ref):
                self.env.update(GITHUB_EVENT_NAME=event, GITHUB_REF=ref)
                self.assert_rejected_without_write()
                self.assertEqual([], self.api.calls)

    def test_wrong_malformed_version_tags_rejected(self):
        for tag in ("v0.2.0", "0.1.0", "v0.1.0-rc.1", "v../0.1.0", "v0.1.0/extra"):
            self.env["GITHUB_REF"] = f"refs/tags/{tag}"
            self.assert_rejected_without_write()

    def test_tested_sha_head_and_tag_must_match(self):
        with self.assertRaises(package.ReleaseError):
            self.publish(OTHER_SHA)
        self.git_head = OTHER_SHA
        self.assert_rejected_without_write()
        self.git_head = SHA
        self.git_tag = OTHER_SHA
        self.assert_rejected_without_write()

    def test_local_main_ancestry_required(self):
        self.on_main = False
        self.assert_rejected_without_write()

    def test_remote_tag_and_main_required(self):
        self.api.ref["sha"] = OTHER_SHA
        self.assert_rejected_without_write()
        self.api.ref["sha"] = SHA
        for status in ("behind", "diverged", None):
            self.api.main_status = status
            self.assert_rejected_without_write()

    def test_annotated_tag_resolves_to_tested_commit(self):
        self.api.ref = {"type": "tag", "sha": OTHER_SHA}
        self.git_tag_object = OTHER_SHA
        self.assertIn("Published", self.publish())

    def test_annotated_retag_to_same_commit_rejected(self):
        self.api.ref = {"type": "tag", "sha": OTHER_SHA}
        self.git_tag_object = "c" * 40
        self.assert_rejected_without_write()

    def test_annotated_tag_to_wrong_commit_rejected(self):
        self.api.ref = {"type": "tag", "sha": OTHER_SHA}
        self.api.tag_target["sha"] = OTHER_SHA
        self.assert_rejected_without_write()

    def test_moved_tag_during_upload_leaves_draft(self):
        self.api.change_after_upload = True
        with self.assertRaises(package.ReleaseError):
            self.publish()
        self.assertTrue(self.api.release["draft"])
        self.assertNotIn(("PATCH", PREFIX + "/releases/7"), self.api.writes())

    def test_wrong_provenance_current_run_or_attempt_rejected(self):
        for field, value in (("GITHUB_RUN_ID", "2"), ("GITHUB_RUN_ATTEMPT", "2")):
            old = self.env[field]
            self.env[field] = value
            self.assert_rejected_without_write()
            self.env[field] = old

    def test_existing_identical_published_release_is_read_only(self):
        self.api.existing(self.files, draft=False)
        self.assertIn("Already published", self.publish())
        self.assertEqual([], self.api.writes())

    def test_same_attempt_partial_draft_resumes_without_overwrite(self):
        self.api.existing({package.PROVENANCE: self.files[package.PROVENANCE]})
        self.assertIn("Published", self.publish())
        self.assertEqual(3, len(self.api.writes()))  # Two missing assets, then publish.
        self.assertFalse(any("name=release-provenance.json" in path for _, path in self.api.writes()))

    def test_same_attempt_empty_draft_resumes(self):
        self.api.existing({})
        self.assertIn("Published", self.publish())

    def current_rerun(self):
        self.env["GITHUB_RUN_ATTEMPT"] = "2"
        self.context = package.Context(REPOSITORY, SHA, "12345", "2", "0.1.0")
        for name, content in package.package_bytes(self.jar.read_bytes(), self.context).items():
            (self.output / name).write_bytes(content)

    def test_real_rerun_preserves_published_earlier_attempt(self):
        self.api.existing(self.files, draft=False)
        old_release = deepcopy(self.api.release)
        old_assets = deepcopy(self.api.assets)
        self.current_rerun()
        self.assertIn("Already published", self.publish())
        self.assertEqual(old_release, self.api.release)
        self.assertEqual(old_assets, self.api.assets)
        self.assertEqual([], self.api.writes())

    def test_real_rerun_resumes_original_provenance_draft(self):
        self.api.existing({package.PROVENANCE: self.files[package.PROVENANCE]})
        self.current_rerun()
        self.assertIn("Published", self.publish())
        self.assertIn("run_attempt=1", self.api.release["body"])
        self.assertEqual(self.api.assets[package.CHECKSUMS]["digest"], "sha256:" + package.digest(self.files[package.CHECKSUMS]))

    def test_real_rerun_without_original_provenance_fails_closed(self):
        self.api.existing({})
        self.current_rerun()
        self.assert_rejected_without_write()

    def test_real_rerun_changed_jar_fails_closed(self):
        self.api.existing(self.files, draft=False)
        self.entries["extra-resource.txt"] = "changed jar"
        self.write_jar()
        self.current_rerun()
        self.assert_rejected_without_write()

    def test_other_run_and_future_attempt_fail_closed(self):
        self.api.existing(self.files)
        original_body = self.api.release["body"]
        for replacement in (original_body.replace("run_id=12345", "run_id=99999"),
                            original_body.replace("run_attempt=1", "run_attempt=2")):
            self.api.release["body"] = replacement
            self.assert_rejected_without_write()

    def test_unrelated_release_and_commit_collision_rejected(self):
        self.api.existing(self.files)
        for field, value in (("body", "unrelated"), ("target_commitish", OTHER_SHA), ("name", "unrelated")):
            old = self.api.release[field]
            self.api.release[field] = value
            self.assert_rejected_without_write()
            self.api.release[field] = old
        self.api.extra_release = True
        self.assert_rejected_without_write()

    def test_extra_conflicting_incomplete_asset_rejected(self):
        self.api.existing(self.files)
        self.api.add_asset("surprise.txt", b"extra")
        self.assert_rejected_without_write()
        del self.api.assets["surprise.txt"]
        for field, value in (("digest", "sha256:" + "0" * 64), ("digest", None), ("size", 0), ("state", "starter")):
            old = self.api.assets[self.jar.name][field]
            self.api.assets[self.jar.name][field] = value
            self.assert_rejected_without_write()
            self.api.assets[self.jar.name][field] = old

    def test_published_release_missing_assets_not_mutated(self):
        self.api.existing({package.PROVENANCE: self.files[package.PROVENANCE]}, draft=False)
        self.assert_rejected_without_write()

    def test_upload_failure_leaves_draft_and_resumes(self):
        self.api.fail_upload = self.jar.name
        with self.assertRaisesRegex(package.ReleaseError, "upload failure"):
            self.publish()
        self.assertTrue(self.api.release["draft"])
        self.assertNotIn(("PATCH", PREFIX + "/releases/7"), self.api.writes())
        self.assertIn(package.PROVENANCE, self.api.assets)
        self.api.fail_upload = None
        self.assertIn("Published", self.publish())

    def test_upload_failure_real_rerun_resumes(self):
        self.api.fail_upload = self.jar.name
        with self.assertRaises(package.ReleaseError):
            self.publish()
        self.current_rerun()
        self.api.fail_upload = None
        self.assertIn("Published", self.publish())


class TransportTests(unittest.TestCase):
    def test_requires_ephemeral_token(self):
        with self.assertRaises(package.ReleaseError):
            publisher.GitHub(REPOSITORY, "")

    def test_disallows_external_endpoint(self):
        api = publisher.GitHub(REPOSITORY, "secret")
        with self.assertRaises(package.ReleaseError):
            api.request("GET", "/repos/other/repo/releases")

    def test_http_error_does_not_retry_or_leak_token(self):
        api = publisher.GitHub(REPOSITORY, "secret-token")
        failure = HTTPError("https://api.github.com", 422, "secret-token", {}, None)
        with patch.object(api.opener, "open", side_effect=failure) as request:
            with self.assertRaises(package.ReleaseError) as error:
                api.request("POST", PREFIX + "/releases", {"draft": True})
            self.assertNotIn("secret-token", str(error.exception))
            self.assertEqual(1, request.call_count)

    def test_cross_origin_redirects_are_not_followed(self):
        self.assertIsNone(publisher.NoRedirects().redirect_request(None, None, 302, "", {}, "https://example.com"))

    def test_cli_help_has_no_side_effect(self):
        for name in ("release_package.py", "release_publish.py"):
            result = subprocess.run([sys.executable, str(SCRIPTS / name), "--help"], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
