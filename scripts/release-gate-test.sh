#!/usr/bin/env bash
# Tests the mutation-check gate (scripts/lib/mutation-gate.sh, T-M6-09) with fixture result files. It never publishes,
# never runs a check and never touches build/. Run by scripts/gates.sh.
# Usage: bash scripts/release-gate-test.sh
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=lib/mutation-gate.sh
source scripts/lib/mutation-gate.sh

HEAD_SHA=0123456789abcdef0123456789abcdef01234567
NOW=1700000000
FIXTURES=$(mktemp -d)
trap 'rm -rf "$FIXTURES"' EXIT

PASSED=0
FAILED=0

# fixture <name> <status> <allowed_missing> <dirty> <head> <age_seconds>: writes a result file, prints its path.
fixture() {
  local path="$FIXTURES/$1.env"
  cat > "$path" << EOF
status=$2
head=$5
dirty=$4
cell=paper@26.3
generated_epoch=$((NOW - $6))
killed=2
allowed_missing=$3
problems=
EOF
  echo "$path"
}

# expect <accept|refuse> <label> <result-file> <head> <now>: asserts the gate's decision and prints the reason.
expect() {
  local want=$1 label=$2 rc=0 reason
  shift 2
  reason=$(mutation_gate_check "$@" 2>&1) || rc=$?
  if [[ "$want" == accept && "$rc" -eq 0 ]] || [[ "$want" == refuse && "$rc" -ne 0 ]]; then
    echo "ok   $label"
    PASSED=$((PASSED + 1))
  else
    echo "FAIL $label: expected $want, gate returned rc=$rc ${reason:+($reason)}"
    FAILED=$((FAILED + 1))
  fi
}

expect accept "complete PASS for HEAD" \
  "$(fixture complete PASS "" false "$HEAD_SHA" 60)" "$HEAD_SHA" "$NOW"

expect refuse "PASS with allowed_missing (--allow-missing run)" \
  "$(fixture allowed PASS maven-paper-plugin false "$HEAD_SHA" 60)" "$HEAD_SHA" "$NOW"

expect refuse "PASS from a dirty tree" \
  "$(fixture dirty PASS "" true "$HEAD_SHA" 60)" "$HEAD_SHA" "$NOW"

expect refuse "PASS for another HEAD" \
  "$(fixture wronghead PASS "" false ffffffffffffffffffffffffffffffffffffffff 60)" "$HEAD_SHA" "$NOW"

expect refuse "INCOMPLETE result" \
  "$(fixture incomplete INCOMPLETE "" false "$HEAD_SHA" 60)" "$HEAD_SHA" "$NOW"

expect refuse "FAIL result" \
  "$(fixture failed FAIL "" false "$HEAD_SHA" 60)" "$HEAD_SHA" "$NOW"

expect refuse "PASS older than 24 hours" \
  "$(fixture stale PASS "" false "$HEAD_SHA" 90000)" "$HEAD_SHA" "$NOW"

expect refuse "no result file" \
  "$FIXTURES/missing.env" "$HEAD_SHA" "$NOW"

malformed="$FIXTURES/malformed.env"
grep -v '^allowed_missing=' "$(fixture source PASS "" false "$HEAD_SHA" 60)" > "$malformed"
expect refuse "PASS without an allowed_missing line" \
  "$malformed" "$HEAD_SHA" "$NOW"

echo "release-gate-test: $PASSED passed, $FAILED failed"
((FAILED == 0))
