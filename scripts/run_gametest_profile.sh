#!/usr/bin/env bash
# Capture a unique invocation transcript; never reuse run/logs/latest.log as proof.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
profile="${1:?Usage: bash scripts/run_gametest_profile.sh PROFILE}"
case "$profile" in
  none) profile_args=() ;;
  withTfc) profile_args=(-PwithTfc) ;;
  withCore) profile_args=(-PwithCore) ;;
  withBackpacks) profile_args=(-PwithBackpacks) ;;
  withTfc+withBackpacks) profile_args=(-PwithTfc -PwithBackpacks) ;;
  *) printf 'Unknown GameTest profile: %s\n' "$profile" >&2; exit 2 ;;
esac

mkdir -p build/gametest-ci
run_id="$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
run_dir="$(mktemp -d "build/gametest-ci/${profile}.${run_id}.XXXXXXXX")"
# Isolate the world, serverconfig, logs, and exports as well as the transcript.
# A previous invocation's changed blacklist/config must never become a fixture.
mkdir "$run_dir/server"
server_dir="$(cd "$run_dir/server" && pwd -P)"
started_at_ns="$(python3 -c 'import time; print(time.time_ns())')"
log="$run_dir/invocation.log"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  printf 'run-directory=%s\n' "$run_dir" >> "$GITHUB_OUTPUT"
fi
printf 'SOLINTEGRATIONS_GAMETEST_BEGIN run_id=%s profile=%s\n' "$run_id" "$profile" | tee "$log"

# Skipping optional client icons keeps this a headless server check, not a client test.
# Forge can exit 0 after a startup crash. Both pipefail and explicit log proof matter.
set +e
./gradlew --no-daemon --console=plain --stacktrace runGameTestServer -x downloadAssets \
  "-PgameTestRunDir=$server_dir" "${profile_args[@]}" 2>&1 | tee -a "$log"
pipeline_status=("${PIPESTATUS[@]}")
set -e
printf 'SOLINTEGRATIONS_GAMETEST_END run_id=%s profile=%s exit_code=%s tee_exit_code=%s\n' \
  "$run_id" "$profile" "${pipeline_status[0]}" "${pipeline_status[1]}" | tee -a "$log"

python3 scripts/verify_gametest_run.py \
  --log "$log" --source-root src/gametest/java \
  --run-id "$run_id" --profile "$profile" --started-at-ns "$started_at_ns" \
  --result "$run_dir/verification.json"
