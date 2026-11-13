#!/usr/bin/env bash
# Gradle-level test for the bad-test cell pins (T-M6-09, examples/bad-tests/README.md). A pinned bad test given a selector
# that names another cell must be refused in its own doFirst, with its task name and the supported cell, before any
# server starts. No Docker is needed: the refusal happens before the test JVM and before any container. Run by gates.sh.
# Usage: bash scripts/bad-tests-pin-check.sh
set -euo pipefail
cd "$(dirname "$0")/.."

OUT=$(mktemp)
trap 'rm -f "$OUT"' EXIT
RC=0
./gradlew :examples:bad-tests:metaInvalidWindow :examples:bad-tests:metaDisagree :examples:bad-tests:metaLeak \
  :examples:bad-tests:metaTopoVelocityWrongSecret :examples:bad-tests:metaTopoBungeeUnreachable \
  -Dproofstone.cells=paper@26.3 --continue --max-workers=1 > "$OUT" 2>&1 || RC=$?

FAILS=0
check() { # label, expected text
  if grep -qF -- "$2" "$OUT"; then
    echo "ok   $1"
  else
    echo "FAIL $1: missing '$2'"
    FAILS=$((FAILS + 1))
  fi
}

[[ "$RC" -ne 0 ]] || { echo "FAIL build with a mismatched selector exited 0"; FAILS=$((FAILS + 1)); }
check "metaInvalidWindow refused" "metaInvalidWindow cannot run on -Dproofstone.cells=paper@26.3"
check "metaDisagree refused" "metaDisagree cannot run on -Dproofstone.cells=paper@26.3"
check "metaLeak refused" "metaLeak cannot run on -Dproofstone.cells=paper@26.3"
check "refusal names the supported cell" "Supported cells: paper@26.1.2"
# T-M7-13: the topology fault runs are pinned to their topology cell too; the refusal names it.
check "metaTopoVelocityWrongSecret refused" "metaTopoVelocityWrongSecret cannot run on -Dproofstone.cells=paper@26.3"
check "metaTopoBungeeUnreachable refused" "metaTopoBungeeUnreachable cannot run on -Dproofstone.cells=paper@26.3"
check "topology refusal names its cell" "Supported cells: velocity@4.3.1+paper@26.3"

# No test ran: a server would have produced a test report or a container log before the refusal text is final.
if grep -qE "PROOFSTONE|Testcontainers|itzg/minecraft-server" "$OUT"; then
  echo "FAIL a server or test started before the refusal"
  FAILS=$((FAILS + 1))
else
  echo "ok   no server started"
fi

echo "bad-tests-pin-check: $FAILS failed"
((FAILS == 0))
