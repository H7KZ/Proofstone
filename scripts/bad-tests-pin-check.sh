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

# T-M8-11: the M8 negative controls. The loader agents and the example mods are not built for this check (-x): the refusal is
# in the task's own doFirst and needs none of them, and building them is a Java 25 toolchain plus a download. metaModCrash is
# pinned to fabric@26.3, so paper@26.3 is refused; the Paper-pinned runs are refused on another Paper row.
RC2=0
OUT2=$(mktemp)
trap 'rm -f "$OUT" "$OUT2"' EXIT
LOADER_BUILD=(-x :platforms:fabric:agent:shadowJar -x :platforms:fabric:agent:exampleModJar
  -x :platforms:neoforge:agent:shadowJar -x :platforms:neoforge:agent:exampleModJar)
./gradlew :examples:bad-tests:metaModCrash :examples:bad-tests:metaModCrashNeoForge "${LOADER_BUILD[@]}" \
  -Dproofstone.cells=paper@26.3 --continue --max-workers=1 > "$OUT2" 2>&1 || RC2=$?
cat "$OUT2" >> "$OUT"
RC3=0
OUT3=$(mktemp)
trap 'rm -f "$OUT" "$OUT2" "$OUT3"' EXIT
./gradlew :examples:bad-tests:metaModDataPack :examples:bad-tests:metaModPackHash "${LOADER_BUILD[@]}" \
  -Dproofstone.cells=paper@26.1.2 --continue --max-workers=1 > "$OUT3" 2>&1 || RC3=$?
cat "$OUT3" >> "$OUT"

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
# T-M8-11: the mod, data-pack and resource-pack faults are pinned too.
[[ "$RC2" -ne 0 && "$RC3" -ne 0 ]] || { echo "FAIL a mismatched selector on an M8 fault exited 0"; FAILS=$((FAILS + 1)); }
check "metaModCrash refused" "metaModCrash cannot run on -Dproofstone.cells=paper@26.3"
check "metaModCrashNeoForge refused" "metaModCrashNeoForge cannot run on -Dproofstone.cells=paper@26.3"
check "loader refusal names its cell" "Supported cells: fabric@26.3"
check "metaModDataPack refused" "metaModDataPack cannot run on -Dproofstone.cells=paper@26.1.2"
check "metaModPackHash refused" "metaModPackHash cannot run on -Dproofstone.cells=paper@26.1.2"
check "pack refusal names its cell" "Supported cells: paper@26.3"

# No test ran: a server would have produced a test report or a container log before the refusal text is final.
if grep -qE "PROOFSTONE|Testcontainers|itzg/minecraft-server" "$OUT"; then
  echo "FAIL a server or test started before the refusal"
  FAILS=$((FAILS + 1))
else
  echo "ok   no server started"
fi

echo "bad-tests-pin-check: $FAILS failed"
((FAILS == 0))
