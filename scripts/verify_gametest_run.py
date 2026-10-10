#!/usr/bin/env python3
"""Require fresh, count-matched GameTest completion, not merely Gradle exit 0.

Optional-mod discovery tests intentionally succeed without exercising an absent
integration. A verified result covers only the selected profile's active cases;
it is not client, TFG, or production validation.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import re
import sys


PROFILES = ("none", "withTfc", "withCore", "withBackpacks", "withTfc+withBackpacks")
ANSI = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")
JAVA_NON_CODE = re.compile(
    r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/'
)
ANNOTATION = re.compile(r"@(?:net\.minecraft\.gametest\.framework\.)?GameTest\b")
START = re.compile(r"^.*?\b(\d+) tests are now running!\s*$", re.MULTILINE)
COMPLETE = re.compile(r"^.*?={3,}\s+(\d+) GAME TESTS COMPLETE\s+={3,}\s*$", re.MULTILINE)
PASSED = re.compile(r"^.*?\bAll (\d+) required tests passed :\)\s*$", re.MULTILINE)
FAILURE = re.compile(
    r"\b[1-9]\d*\s+(?:(?:required|optional)\s+)?tests?\s+failed\b"
    r"|\b(?:GameTestAssertException|GameTestTimeoutException|AssertionError)\b"
    r"|\b(?:Exception in server tick loop|Encountered an unexpected exception)\b"
    r"|\bFailed to start (?:the )?(?:minecraft )?server\b"
    r"|\b(?:ModLoadingException|MixinTransformerError|InvalidMixinException|InjectionError)\b"
    r"|\b(?:BUILD FAILED|A fatal error has been detected)\b",
    re.IGNORECASE,
)


class VerificationError(ValueError):
    """The captured invocation does not prove successful GameTest completion."""


@dataclass(frozen=True)
class TestCounts:
    total: int
    required: int


def count_source_tests(source_root: Path) -> TestCounts:
    """Count direct @GameTest annotations, ignoring comments and Java literals."""
    if not source_root.is_dir():
        raise VerificationError(f"GameTest source directory does not exist: {source_root}")
    total = required = 0
    for path in sorted(source_root.rglob("*.java")):
        source = JAVA_NON_CODE.sub(lambda match: "\n" * match.group().count("\n") + " ", path.read_text())
        for match in ANNOTATION.finditer(source):
            total += 1
            suffix = source[match.end():].lstrip()
            arguments = ""
            if suffix.startswith("("):
                depth = 0
                for index, character in enumerate(suffix):
                    depth += (character == "(") - (character == ")")
                    if depth == 0:
                        arguments = suffix[1:index]
                        break
                else:
                    raise VerificationError(f"Unclosed GameTest annotation in {path}")
            if not re.search(r"\brequired\s*=\s*false\b", arguments):
                required += 1
    if total == 0 or required == 0:
        raise VerificationError("Expected at least one registered and required @GameTest")
    return TestCounts(total, required)


def verify_text(text: str, counts: TestCounts, run_id: str, profile: str) -> dict:
    """Validate an invocation envelope and one ordered, successful test batch."""
    if profile not in PROFILES or not re.fullmatch(r"[A-Za-z0-9_-]+", run_id):
        raise VerificationError("Invalid expected profile or run ID")
    text = ANSI.sub("", text).replace("\r\n", "\n")
    lines = text.splitlines()
    begin = f"SOLINTEGRATIONS_GAMETEST_BEGIN run_id={run_id} profile={profile}"
    end = f"SOLINTEGRATIONS_GAMETEST_END run_id={run_id} profile={profile} exit_code=0 tee_exit_code=0"
    if not lines or lines[0] != begin or lines[-1] != end:
        raise VerificationError("Missing/mismatched fresh-run envelope or nonzero Gradle/tee exit code")
    if sum(line.startswith("SOLINTEGRATIONS_GAMETEST_BEGIN ") for line in lines) != 1:
        raise VerificationError("Multiple invocation beginnings; log may contain stale runs")
    if sum(line.startswith("SOLINTEGRATIONS_GAMETEST_END ") for line in lines) != 1:
        raise VerificationError("Multiple invocation endings; log may contain stale runs")
    failure = FAILURE.search(text)
    if failure:
        raise VerificationError(f"Failure signature in invocation: {failure.group(0)}")
    positions = []
    for label, pattern, expected in (
        ("running", START, counts.total),
        ("complete", COMPLETE, counts.total),
        ("all required passed", PASSED, counts.required),
    ):
        matches = list(pattern.finditer(text))
        if len(matches) != 1:
            raise VerificationError(f"Expected exactly one {label} marker, found {len(matches)}")
        match = matches[0]
        actual = int(match.group(1))
        if actual != expected:
            raise VerificationError(f"{label} count is {actual}; current source requires {expected}")
        positions.append(match.start())
    if positions != sorted(positions):
        raise VerificationError("GameTest running/completion/pass markers are out of order")
    return {
        "status": "passed",
        "profile": profile,
        "run_id": run_id,
        "registered_tests": counts.total,
        "required_tests": counts.required,
        "coverage_limit": "Only this profile's active cases; absent optional integrations use no-op cases. No client, TFG, or production validation.",
    }


def verify_file(log: Path, source_root: Path, run_id: str, profile: str, started_at_ns: int) -> dict:
    if not log.is_file():
        raise VerificationError(f"Invocation log does not exist: {log}")
    if started_at_ns <= 0 or log.stat().st_mtime_ns < started_at_ns:
        raise VerificationError("Invocation log is stale: modification predates this run")
    result = verify_text(log.read_text(encoding="utf-8", errors="replace"), count_source_tests(source_root), run_id, profile)
    result.update({"log": str(log), "started_at_ns": started_at_ns, "log_mtime_ns": log.stat().st_mtime_ns})
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--source-root", type=Path, default=Path("src/gametest/java"))
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--profile", choices=PROFILES, required=True)
    parser.add_argument("--started-at-ns", type=int, required=True)
    parser.add_argument("--result", type=Path, help="Write machine-readable validation evidence")
    args = parser.parse_args()
    try:
        result = verify_file(args.log, args.source_root, args.run_id, args.profile, args.started_at_ns)
    except (VerificationError, OSError) as error:
        result = {"status": "failed", "profile": args.profile, "run_id": args.run_id, "error": str(error)}
    if args.result:
        args.result.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
