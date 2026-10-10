"""Regression checks for false-positive headless Forge run results."""

import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "verify_gametest_run.py"
SPEC = importlib.util.spec_from_file_location("verify_gametest_run", SCRIPT)
checker = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = checker
SPEC.loader.exec_module(checker)

RUN_ID = "fresh_run_123"
PROFILE = "withTfc+withBackpacks"
BEGIN = f"SOLINTEGRATIONS_GAMETEST_BEGIN run_id={RUN_ID} profile={PROFILE}"
END = f"SOLINTEGRATIONS_GAMETEST_END run_id={RUN_ID} profile={PROFILE} exit_code=0 tee_exit_code=0"
RUNNING = "[12:34:56] [Server thread/INFO]: 21 tests are now running!"
COMPLETE = "[12:34:57] [Server thread/INFO]: ========= 21 GAME TESTS COMPLETE ======================"
PASSED = "[12:34:57] [Server thread/INFO]: All 21 required tests passed :)"


def transcript(*body):
    return "\n".join((BEGIN, *(body or (RUNNING, COMPLETE, PASSED, "BUILD SUCCESSFUL in 10s")), END)) + "\n"


class CompletionTests(unittest.TestCase):
    def verify(self, text):
        return checker.verify_text(text, checker.TestCounts(21, 21), RUN_ID, PROFILE)

    def reject(self, text):
        with self.assertRaises(checker.VerificationError):
            self.verify(text)

    def test_accepts_current_complete_success(self):
        result = self.verify(transcript())
        self.assertEqual("passed", result["status"])
        self.assertEqual(21, result["registered_tests"])
        self.assertIn("no-op", result["coverage_limit"])

    def test_accepts_ansi_and_crlf(self):
        self.verify(transcript().replace(RUNNING, "\x1b[32m" + RUNNING + "\x1b[0m").replace("\n", "\r\n"))

    def test_rejects_startup_crash_even_with_gradle_success(self):
        self.reject(transcript("Failed to start the minecraft server", "BUILD SUCCESSFUL in 10s"))

    def test_rejects_startup_only_success(self):
        self.reject(transcript("Starting minecraft server", "BUILD SUCCESSFUL in 10s"))

    def test_rejects_missing_markers(self):
        for marker in (RUNNING, COMPLETE, PASSED):
            with self.subTest(marker=marker):
                self.reject(transcript().replace(marker, ""))

    def test_rejects_wrong_count_in_each_marker(self):
        for marker in (RUNNING, COMPLETE, PASSED):
            with self.subTest(marker=marker):
                self.reject(transcript().replace(marker, marker.replace("21", "20")))

    def test_rejects_duplicate_or_stale_batch(self):
        self.reject(transcript(RUNNING, COMPLETE, PASSED, RUNNING, COMPLETE, PASSED))

    def test_rejects_out_of_order_markers(self):
        self.reject(transcript(COMPLETE, RUNNING, PASSED))

    def test_rejects_previous_run_identifier(self):
        self.reject(transcript().replace(RUN_ID, "old_run_456"))

    def test_rejects_wrong_profile(self):
        self.reject(transcript().replace(PROFILE, "none"))

    def test_rejects_missing_envelope(self):
        self.reject(transcript().replace(BEGIN, ""))
        self.reject(transcript().replace(END, ""))

    def test_rejects_appended_previous_run(self):
        self.reject(transcript() + transcript())

    def test_rejects_nonzero_process_exit(self):
        self.reject(transcript().replace("exit_code=0 tee", "exit_code=1 tee"))

    def test_rejects_tee_failure(self):
        self.reject(transcript().replace("tee_exit_code=0", "tee_exit_code=1"))

    def test_rejects_failure_or_crash_despite_success_markers(self):
        failures = (
            "1 required test failed", "2 optional tests failed", "1 tests failed",
            "GameTestAssertException: wrong count", "GameTestTimeoutException",
            "java.lang.AssertionError", "Exception in server tick loop",
            "Encountered an unexpected exception", "Failed to start server",
            "ModLoadingException", "MixinTransformerError", "InvalidMixinException",
            "InjectionError", "BUILD FAILED", "A fatal error has been detected",
        )
        for failure in failures:
            with self.subTest(failure=failure):
                self.reject(transcript(RUNNING, COMPLETE, PASSED, failure))


class SourceAndFileTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "java"
        self.source.mkdir()
        (self.source / "Tests.java").write_text("\n".join("@GameTest void case%d() {}" % n for n in range(21)))

    def test_counts_current_source_annotations_without_hardcoded_expected_count(self):
        self.assertEqual(checker.TestCounts(21, 21), checker.count_source_tests(self.source))
        (self.source / "NewTest.java").write_text("@GameTest void extra() {}")
        self.assertEqual(checker.TestCounts(22, 22), checker.count_source_tests(self.source))

    def test_ignores_comments_literals_and_holder_annotations(self):
        (self.source / "Misleading.java").write_text('''
            // @GameTest
            /* @GameTest */
            String example = "@GameTest";
            String textBlock = """ @GameTest """;
            @GameTestHolder("solintegrations") class Cases {}
            @net.minecraft.gametest.framework.GameTest(required = false)
            void optional() {}
        ''')
        self.assertEqual(checker.TestCounts(22, 21), checker.count_source_tests(self.source))

    def test_rejects_empty_or_missing_source_directory(self):
        (self.source / "Tests.java").unlink()
        with self.assertRaises(checker.VerificationError):
            checker.count_source_tests(self.source)
        with self.assertRaises(checker.VerificationError):
            checker.count_source_tests(self.root / "missing")

    def test_rejects_stale_log_timestamp(self):
        log = self.root / "invocation.log"
        log.write_text(transcript())
        start = time.time_ns()
        os.utime(log, ns=(start - 10_000_000_000, start - 10_000_000_000))
        with self.assertRaisesRegex(checker.VerificationError, "stale"):
            checker.verify_file(log, self.source, RUN_ID, PROFILE, start)

    def test_accepts_newly_captured_log(self):
        start = time.time_ns() - 1_000_000_000
        log = self.root / "invocation.log"
        log.write_text(transcript())
        self.assertEqual("passed", checker.verify_file(log, self.source, RUN_ID, PROFILE, start)["status"])

    def test_cli_returns_nonzero_for_startup_only(self):
        log = self.root / "invocation.log"
        log.write_text(transcript("BUILD SUCCESSFUL in 1s"))
        result = self.root / "result.json"
        completed = subprocess.run([
            sys.executable, str(SCRIPT), "--log", str(log), "--source-root", str(self.source),
            "--run-id", RUN_ID, "--profile", PROFILE,
            "--started-at-ns", str(time.time_ns() - 1_000_000_000), "--result", str(result),
        ], capture_output=True, text=True)
        self.assertEqual(1, completed.returncode)
        self.assertIn('"status": "failed"', result.read_text())


class RunnerTests(unittest.TestCase):
    """Exercise the real shell wrapper against a controlled fake Gradle process."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "repository with spaces"
        self.root.mkdir()
        (self.root / "scripts").mkdir()
        for name in ("verify_gametest_run.py", "run_gametest_profile.sh"):
            shutil.copy2(SCRIPT.parent / name, self.root / "scripts" / name)
        source = self.root / "src/gametest/java"
        source.mkdir(parents=True)
        (source / "Tests.java").write_text("@GameTest void one() {}\n@GameTest void two() {}\n")

    def fake_gradle(self, text, exit_code=0):
        wrapper = self.root / "gradlew"
        wrapper.write_text("#!/usr/bin/env bash\nprintf '%s\\n' \"$@\" > captured-args.txt\n"
                           + "cat <<'TRANSCRIPT'\n" + text + "\nTRANSCRIPT\n"
                           + f"exit {exit_code}\n")
        wrapper.chmod(0o755)

    def run_profile(self, profile="withTfc+withBackpacks"):
        return subprocess.run(["bash", "scripts/run_gametest_profile.sh", profile],
                              cwd=self.root, capture_output=True, text=True)

    def successful_output(self):
        return "\n".join((RUNNING, COMPLETE, PASSED)).replace("21", "2")

    def captured_server_directory(self):
        args = (self.root / "captured-args.txt").read_text().splitlines()
        values = [argument.removeprefix("-PgameTestRunDir=") for argument in args
                  if argument.startswith("-PgameTestRunDir=")]
        self.assertEqual(1, len(values))
        return Path(values[0])

    def test_runner_accepts_success_and_passes_exact_profile_flags(self):
        self.fake_gradle(self.successful_output())
        run = self.run_profile()
        self.assertEqual(0, run.returncode, run.stdout + run.stderr)
        args = (self.root / "captured-args.txt").read_text().splitlines()
        self.assertIn("-PwithTfc", args)
        self.assertIn("-PwithBackpacks", args)
        self.assertIn("runGameTestServer", args)
        self.assertEqual("downloadAssets", args[args.index("-x") + 1])
        result = next((self.root / "build/gametest-ci").glob("*/verification.json"))
        self.assertEqual(2, json.loads(result.read_text())["registered_tests"])
        server = self.captured_server_directory()
        self.assertTrue(server.is_absolute())
        self.assertTrue(server.is_dir())
        self.assertEqual(result.parent.resolve() / "server", server)
        self.assertIn(json.loads(result.read_text())["run_id"], server.parent.name)

    def test_runner_rejects_zero_exit_startup_only(self):
        self.fake_gradle("Starting minecraft server\nBUILD SUCCESSFUL in 1s")
        self.assertNotEqual(0, self.run_profile().returncode)

    def test_runner_rejects_zero_exit_startup_crash(self):
        self.fake_gradle("Failed to start the minecraft server\nBUILD SUCCESSFUL in 1s")
        self.assertNotEqual(0, self.run_profile().returncode)

    def test_runner_rejects_nonzero_exit_even_after_success_summary(self):
        self.fake_gradle(self.successful_output(), exit_code=17)
        self.assertNotEqual(0, self.run_profile().returncode)
        log = next((self.root / "build/gametest-ci").glob("*/invocation.log"))
        self.assertIn("exit_code=17 tee_exit_code=0", log.read_text())

    def test_repeated_invocations_have_distinct_fresh_logs(self):
        self.fake_gradle(self.successful_output())
        self.assertEqual(0, self.run_profile().returncode)
        self.assertEqual(0, self.run_profile().returncode)
        results = [json.loads(p.read_text()) for p in (self.root / "build/gametest-ci").glob("*/verification.json")]
        self.assertEqual(2, len(results))
        self.assertEqual(2, len({r["run_id"] for r in results}))

    def test_every_profile_receives_a_fresh_absolute_server_directory(self):
        self.fake_gradle(self.successful_output())
        directories = set()
        for profile in checker.PROFILES:
            with self.subTest(profile=profile):
                run = self.run_profile(profile)
                self.assertEqual(0, run.returncode, run.stdout + run.stderr)
                server = self.captured_server_directory()
                self.assertTrue(server.is_absolute())
                self.assertTrue(server.is_dir())
                self.assertEqual([], list(server.iterdir()))
                directories.add(server)
        self.assertEqual(len(checker.PROFILES), len(directories))

    def test_rerun_cannot_reuse_previous_world_or_server_config(self):
        self.fake_gradle(self.successful_output())
        self.assertEqual(0, self.run_profile().returncode)
        previous = self.captured_server_directory()
        config = previous / "world/serverconfig/solclassic-server.toml"
        config.parent.mkdir(parents=True)
        config.write_text('foodBlacklist = ["minecraft:apple"]\n')
        self.assertEqual(0, self.run_profile().returncode)
        current = self.captured_server_directory()
        self.assertNotEqual(previous, current)
        self.assertTrue(config.exists())
        self.assertEqual([], list(current.iterdir()))

    def test_rejects_unknown_profile_before_invoking_gradle(self):
        self.fake_gradle(self.successful_output())
        self.assertEqual(2, self.run_profile("unrecognized").returncode)
        self.assertFalse((self.root / "captured-args.txt").exists())


if __name__ == "__main__":
    unittest.main()
