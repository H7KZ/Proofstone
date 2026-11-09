#!/usr/bin/env bash
# Mutation check for releases (T-M6-07, specs/self-tests.md §5). Local only: no CI, no workflow.
# For each example: one baseline run with no injected bug (must PASS), then one run per bug named in that example's
# config.yml `bugs:` block (must FAIL). Runs are sequential, one real-server cell each, under scripts/dockerlock.sh.
# Blocking: a survived mutation (suite green with the bug on), a red baseline, an inconclusive run (skip, error, no
# tests) or an example that cannot run (NOT RUN) unless it is named in --allow-missing.
# Writes build/release/mutation-check.env (read by `scripts/release-maven.sh --publish`) and prints a table.
# Usage: bash scripts/release-mutation-check.sh [--allow-missing=<example>[,<example>...]] [--cell=<cell>]
#   --allow-missing  names examples that may be NOT RUN without making the check INCOMPLETE. Never a default.
#   --cell           real-server cell for every run (default paper@26.3).
# Exit: 0 only when status=PASS.
set -euo pipefail
cd "$(dirname "$0")/.."

# The examples under test. A listed example whose project is missing is NOT RUN, never a skip or a pass.
EXAMPLES=(gradle-paper-plugin maven-paper-plugin)
CELL=paper@26.3
ALLOW_MISSING=","
for arg in "$@"; do
  case "$arg" in
    --allow-missing=*) ALLOW_MISSING="$ALLOW_MISSING,${arg#--allow-missing=},"  ;;
    --cell=*) CELL=${arg#--cell=} ;;
    -h | --help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "usage: bash scripts/release-mutation-check.sh [--allow-missing=<example>[,...]] [--cell=<cell>]" >&2; exit 2 ;;
  esac
done
for allowed in ${ALLOW_MISSING//,/ }; do
  known=false
  for name in "${EXAMPLES[@]}"; do [[ "$name" == "$allowed" ]] && known=true; done
  [[ "$known" == true ]] || { echo "release-mutation-check: --allow-missing names unknown example '$allowed'" >&2; exit 2; }
done

RESULT=build/release/mutation-check.env
mkdir -p build/release
rm -f "$RESULT" # a stale PASS must never survive a run that did not finish

HEAD_SHA=$(git rev-parse HEAD)
DIRTY=false
if [[ -n "$(git status --porcelain)" ]]; then
  DIRTY=true
  echo "WARNING: the git tree is dirty. The result is recorded with dirty=true, and --publish refuses it." >&2
fi

ROWS=()
FAILS=()
NOT_RUN_BLOCKING=()
NOT_RUN_ALLOWED=()
KILLED=0

row() { # example run bug expect result detail
  ROWS+=("$(printf '%-20s %-9s %-16s %-7s %-14s %s' "$1" "$2" "$3" "$4" "$5" "$6")")
}

not_run() { # example reason
  if [[ "$ALLOW_MISSING" == *",$1,"* ]]; then
    NOT_RUN_ALLOWED+=("$1")
    row "$1" "-" "-" "-" "NOT RUN (ok)" "$2"
  else
    NOT_RUN_BLOCKING+=("$1: $2")
    row "$1" "-" "-" "-" "NOT RUN" "$2"
  fi
}

# Prints "<bug> <default value>" for each entry of the `bugs:` block in the example's config.yml. That file is the one
# place the toggle list lives: the check reads it, nothing here repeats it.
toggles() {
  tr -d '\r' < "$1/src/main/resources/config.yml" | awk '
    /^bugs:[[:space:]]*$/ { inbugs = 1; next }
    inbugs && /^[^[:space:]#]/ { inbugs = 0 }
    inbugs && /^[[:space:]]+[A-Za-z0-9-]+:[[:space:]]*[A-Za-z]+/ {
      entry = $0
      sub(/^[[:space:]]+/, "", entry)
      name = entry; sub(/:.*/, "", name)
      value = entry; sub(/^[^:]*:[[:space:]]*/, "", value); sub(/[[:space:]]+$/, "", value)
      print name, value
    }
  '
}

# Sums the JUnit <testsuite> counters of the last e2eTest run of one example: "tests skipped failures errors".
xml_counts() {
  local dir="examples/$1/build/test-results/e2eTest" t=0 s=0 f=0 e=0 tag
  if [[ -d "$dir" ]]; then
    while IFS= read -r tag; do
      t=$((t + $(attr_of "$tag" tests)))
      s=$((s + $(attr_of "$tag" skipped)))
      f=$((f + $(attr_of "$tag" failures)))
      e=$((e + $(attr_of "$tag" errors)))
    done < <(cat "$dir"/*.xml 2> /dev/null | grep -o '<testsuite [^>]*' || true)
  fi
  echo "$t $s $f $e"
}

attr_of() { # "<tag ...>" name -> numeric value, 0 when absent
  local value
  value=$(sed -n "s/.* $2=\"\([0-9]*\)\".*/\1/p" <<< "$1" | head -1)
  echo "${value:-0}"
}

first_failure() { # example -> first JUnit failure message, shortened
  cat "examples/$1/build/test-results/e2eTest"/*.xml 2> /dev/null \
    | grep -o '<failure message="[^"]*' | head -1 | sed 's/^<failure message="//' | cut -c1-110 || true
}

# One real-server e2e run. Its exit code goes to RC, its output to the log; the JUnit XML decides the verdict.
run_suite() { # example bug(or empty) log
  local -a props=("-Dproofstone.cells=$CELL")
  if [[ -n "$2" ]]; then props+=("-Dproofstone.bug=$2"); fi
  rm -rf "examples/$1/build/test-results/e2eTest" # stale XML from an earlier run must never be read
  RC=0
  PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew ":examples:$1:e2eTest" "${props[@]}" \
    --no-parallel --max-workers=1 < /dev/null > "$3" 2>&1 || RC=$?
}

gradle_example() { # example
  local name=$1 entry bug value
  local -a entries
  mapfile -t entries < <(toggles "examples/$name")
  if [[ ${#entries[@]} -eq 0 ]]; then
    FAILS+=("$name: no bugs: block with toggles in src/main/resources/config.yml")
    row "$name" "-" "-" "-" "NO TOGGLES" "config.yml has no bugs: block"
    return
  fi

  run_suite "$name" "" "build/release/mutation-$name-baseline.log"
  judge "$name" "" "PASS" "$RC" "build/release/mutation-$name-baseline.log"

  for entry in "${entries[@]}"; do
    bug=${entry%% *}
    value=${entry#* }
    if [[ "$value" != "false" ]]; then
      FAILS+=("$name: bug $bug is not off by default ($value)")
      row "$name" "mutation" "$bug" "FAIL" "BAD DEFAULT" "config.yml default must be false"
      continue
    fi
    run_suite "$name" "$bug" "build/release/mutation-$name-$bug.log"
    judge "$name" "$bug" "FAIL" "$RC" "build/release/mutation-$name-$bug.log"
  done
}

# Verdict for one run. Baseline must PASS. A mutation must be KILLED: the suite red by assertion failures, with no
# errors, skips or missing tests (a Docker or harness crash is not a kill). A green mutation run is SURVIVED.
judge() { # example bug expect rc log
  local name=$1 bug=$2 expect=$3 rc=$4 log=$5 tests skipped failures errors detail verdict
  read -r tests skipped failures errors <<< "$(xml_counts "$name")"
  detail=$(first_failure "$name")
  local label=${bug:-baseline}
  if [[ -z "$bug" ]]; then
    if [[ "$rc" -eq 0 && "$tests" -gt 0 && "$failures" -eq 0 && "$errors" -eq 0 && "$skipped" -eq 0 ]]; then
      verdict="PASS"
    else
      verdict="RED"
      FAILS+=("$name: baseline is not green (rc=$rc tests=$tests skipped=$skipped failures=$failures errors=$errors)")
    fi
  elif grep -q 'unknown proofstone.bug' "$log"; then
    verdict="BAD TOGGLE"
    FAILS+=("$name: $bug is not known to the suite (unknown proofstone.bug)")
  elif [[ "$rc" -eq 0 && "$tests" -gt 0 && "$failures" -eq 0 && "$errors" -eq 0 && "$skipped" -eq 0 ]]; then
    verdict="SURVIVED"
    FAILS+=("$name: mutation $bug survived (suite green with the bug on)")
  elif [[ "$rc" -ne 0 && "$tests" -gt 0 && "$failures" -ge 1 && "$errors" -eq 0 && "$skipped" -eq 0 ]]; then
    verdict="KILLED"
    KILLED=$((KILLED + 1))
  else
    verdict="INCONCLUSIVE"
    FAILS+=("$name: mutation $bug inconclusive (rc=$rc tests=$tests skipped=$skipped failures=$failures errors=$errors)")
  fi
  row "$name" "$([[ -z "$bug" ]] && echo baseline || echo mutation)" "$label" "$expect" "$verdict" "${detail:-}"
  echo "== $name $label: $verdict (log: $log)"
}

for name in "${EXAMPLES[@]}"; do
  dir="examples/$name"
  if [[ -f "$dir/build.gradle.kts" ]]; then
    gradle_example "$name"
  elif [[ -d "$dir" ]]; then
    not_run "$name" "$dir has no build.gradle.kts, and this script has no Maven runner (mvn is not installed here, so it is untested)"
  else
    reason="$dir is missing"
    command -v mvn > /dev/null 2>&1 || reason="$reason, and mvn is not on PATH (T-M6-04 Maven half is not merged)"
    not_run "$name" "$reason"
  fi
done

STATUS=PASS
if [[ ${#FAILS[@]} -gt 0 ]]; then
  STATUS=FAIL
elif [[ ${#NOT_RUN_BLOCKING[@]} -gt 0 ]]; then
  STATUS=INCOMPLETE
fi

{
  echo "status=$STATUS"
  echo "head=$HEAD_SHA"
  echo "dirty=$DIRTY"
  echo "cell=$CELL"
  echo "generated_epoch=$(date +%s)"
  echo "killed=$KILLED"
  echo "allowed_missing=$(IFS=,; echo "${NOT_RUN_ALLOWED[*]:-}")"
  echo "problems=$(IFS=';'; echo "${FAILS[*]:-}${NOT_RUN_BLOCKING[*]:-}")"
} > "$RESULT"

echo
printf '%-20s %-9s %-16s %-7s %-14s %s\n' EXAMPLE RUN BUG EXPECT RESULT DETAIL
for line in "${ROWS[@]}"; do echo "$line"; done
echo
echo "mutation-check: $STATUS (head ${HEAD_SHA:0:12}, cell $CELL, killed $KILLED, dirty $DIRTY)"
for p in "${FAILS[@]:-}"; do [[ -n "$p" ]] && echo "  blocking: $p"; done
for p in "${NOT_RUN_BLOCKING[@]:-}"; do [[ -n "$p" ]] && echo "  blocking: NOT RUN $p"; done
echo "result file: $RESULT"

[[ "$STATUS" == PASS ]]
