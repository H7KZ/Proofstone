#!/usr/bin/env bash
# Local dry run of integrations/github-action (T-M6-03). Emulates the action's shell steps for the Gradle example:
#   1. the input checks (scope, build-tool, EULA, root);
#   2. printMatrix --scope=pr, and the cell selection (paper@26.3);
#   3. one real-server run of examples/gradle-paper-plugin on paper@26.3, under scripts/dockerlock.sh;
#   4. the JUnit XML check, and the XML printed.
# Step 3 needs Docker and runs the real server. Pass --plan-only to stop after step 2 (no Docker).
# The EULA is read from the environment only: PROOFSTONE_ACCEPT_EULA=true bash scripts/action-selfcheck.sh
# Usage: bash scripts/action-selfcheck.sh [--plan-only]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

plan_only=false
case "${1:-}" in
  "") ;;
  --plan-only) plan_only=true ;;
  *) echo "action-selfcheck: unknown argument '$1'" >&2; exit 2 ;;
esac

if [ "$plan_only" = false ] && [ "${PROOFSTONE_ACCEPT_EULA:-}" != "true" ]; then
  echo "action-selfcheck: PROOFSTONE_ACCEPT_EULA=true must be set in the command environment (https://aka.ms/MinecraftEULA)" >&2
  exit 1
fi
[ -f "$ROOT/versions.yml" ] && [ -f "$ROOT/gradlew" ] || { echo "action-selfcheck: not a Proofstone checkout: $ROOT" >&2; exit 1; }

export PROOFSTONE_ROOT="$ROOT"
export PROOFSTONE_SCOPE=pr
export PROOFSTONE_CELLS=paper@26.3
export PROOFSTONE_BUILD_TOOL=gradle
export PROOFSTONE_PROJECT_DIR="$ROOT/examples/gradle-paper-plugin"
export PROOFSTONE_TEST_TASK=e2eTest
export PROOFSTONE_GRADLE="$ROOT/gradlew"
export PROOFSTONE_RESULTS_DIR="$ROOT/build/action-selfcheck/junit"
export PROOFSTONE_LOCK="bash $ROOT/scripts/dockerlock.sh"
export PROOFSTONE_PLAN_ONLY="$plan_only"
export PROOFSTONE_ACCEPT_EULA="${PROOFSTONE_ACCEPT_EULA:-}"

set +e
bash "$ROOT/integrations/github-action/run-cells.sh"
status=$?
set -e

echo "action-selfcheck: JUnit XML under $PROOFSTONE_RESULTS_DIR"
if [ "$plan_only" = false ]; then
  for f in "$PROOFSTONE_RESULTS_DIR"/paper-26.3/*.xml; do
    [ -e "$f" ] || { echo "action-selfcheck: no XML for paper@26.3" >&2; status=1; break; }
    echo "--- $f"
    cat "$f"
  done
fi
if [ "$status" -eq 0 ]; then
  echo "action-selfcheck: OK (plan-only=$plan_only)"
else
  echo "action-selfcheck: FAILED (exit $status)" >&2
fi
exit "$status"
