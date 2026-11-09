#!/usr/bin/env bash
# Plans the cells with printMatrix, then runs the consumer's suite once per cell, one Gradle or Maven run at a time.
# Called by integrations/github-action/action.yml and by scripts/action-selfcheck.sh, so both run the same shell steps.
# Inputs come from the environment (see the env block of action.yml). Spec: integrations/github-action/README.md.
# Fails loudly: a missing EULA, an unknown cell, a cell with no JUnit XML, a cell that ran no test, a skip, or a failure.
set -euo pipefail

fail() {
  echo "run-cells: ERROR: $*" >&2
  exit 1
}

need() {
  [ -n "${!1:-}" ] || fail "$1 is not set"
}

for v in PROOFSTONE_ROOT PROOFSTONE_SCOPE PROOFSTONE_CELLS PROOFSTONE_BUILD_TOOL PROOFSTONE_PROJECT_DIR \
  PROOFSTONE_TEST_TASK PROOFSTONE_RESULTS_DIR; do
  need "$v"
done
case "$PROOFSTONE_SCOPE" in pr|nightly) ;; *) fail "scope must be pr or nightly, got '$PROOFSTONE_SCOPE'" ;; esac
case "$PROOFSTONE_BUILD_TOOL" in gradle|maven) ;; *) fail "build-tool must be gradle or maven, got '$PROOFSTONE_BUILD_TOOL'" ;; esac
[ -f "$PROOFSTONE_ROOT/versions.yml" ] || fail "no versions.yml in $PROOFSTONE_ROOT"
[ -d "$PROOFSTONE_PROJECT_DIR" ] || fail "project-dir '$PROOFSTONE_PROJECT_DIR' does not exist"

# 1. Plan: the cell list comes from printMatrix, never from this file (docs/versioning.md section 4).
(cd "$PROOFSTONE_ROOT" && ./gradlew printMatrix --scope="$PROOFSTONE_SCOPE" --no-parallel --max-workers=1 --console=plain)
MATRIX="$PROOFSTONE_ROOT/build/ci/matrix-$PROOFSTONE_SCOPE.json"
[ -f "$MATRIX" ] || fail "printMatrix wrote no $MATRIX"
planned="$(node -e '
  const m = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
  if (!Array.isArray(m.include)) { throw new Error("no include array"); }
  for (const e of m.include) { console.log(e.cell); }
' "$MATRIX")" || fail "cannot read $MATRIX"
[ -n "$planned" ] || fail "printMatrix --scope=$PROOFSTONE_SCOPE planned no cell"

# 2. Select: "all" runs the whole scope. Otherwise every named cell must be in the plan (unknown names fail).
if [ "$PROOFSTONE_CELLS" = "all" ]; then
  selected="$planned"
else
  selected=""
  IFS=',' read -r -a wanted <<< "$PROOFSTONE_CELLS"
  for cell in "${wanted[@]}"; do
    cell="$(echo "$cell" | tr -d '[:space:]')"
    [ -n "$cell" ] || continue
    grep -qxF "$cell" <<< "$planned" || fail "cell '$cell' is not in printMatrix --scope=$PROOFSTONE_SCOPE ($(echo $planned | tr '\n' ' '))"
    selected="$selected$cell"$'\n'
  done
  selected="$(printf '%s' "$selected")"
fi
[ -n "$selected" ] || fail "no cell selected from '$PROOFSTONE_CELLS'"
echo "run-cells: scope=$PROOFSTONE_SCOPE build-tool=$PROOFSTONE_BUILD_TOOL cells: $(echo $selected | tr '\n' ' ')"
if [ "${PROOFSTONE_PLAN_ONLY:-false}" = "true" ]; then
  echo "run-cells: plan only, no cell run"
  exit 0
fi
# Checked here, not at the top, so a plan-only run needs no EULA. The action checks it again before any run.
[ "${PROOFSTONE_ACCEPT_EULA:-}" = "true" ] || fail "PROOFSTONE_ACCEPT_EULA must be 'true' in the environment. Never defaulted."

# 3. Run. Optional lock prefix (the local selfcheck uses scripts/dockerlock.sh; GitHub runs one job, so none).
LOCK_CMD=()
if [ -n "${PROOFSTONE_LOCK:-}" ]; then
  read -r -a LOCK_CMD <<< "$PROOFSTONE_LOCK"
fi
mkdir -p "$PROOFSTONE_RESULTS_DIR"
failures=()
summary=()

# Test task results: Gradle writes build/test-results/<task>, Maven writes target/failsafe-reports.
if [ "$PROOFSTONE_BUILD_TOOL" = "gradle" ]; then
  read -r -a TASK_WORDS <<< "$PROOFSTONE_TEST_TASK"
  RESULTS_REL="build/test-results/${TASK_WORDS[0]##*:}"
else
  RESULTS_REL="target/failsafe-reports"
fi

# An array, not a pipe or here-string: Gradle must not read the list through stdin.
mapfile -t cells <<< "$selected"
for cell in "${cells[@]}"; do
  [ -n "$cell" ] || continue
  slug="${cell//@/-}"
  out="$PROOFSTONE_RESULTS_DIR/$slug"
  echo "run-cells: === $cell ==="
  # A stale XML from the previous cell must not pass for this one.
  rm -rf "$PROOFSTONE_PROJECT_DIR/$RESULTS_REL"

  set +e
  if [ "$PROOFSTONE_BUILD_TOOL" = "gradle" ]; then
    scope_arg=()
    [ "$PROOFSTONE_SCOPE" = "nightly" ] && scope_arg=("-Dproofstone.scope=nightly")
    GRADLE="${PROOFSTONE_GRADLE:-$PROOFSTONE_PROJECT_DIR/gradlew}"
    # shellcheck disable=SC2086  # the task string holds several words on purpose
    (cd "$PROOFSTONE_PROJECT_DIR" && ${LOCK_CMD[@]+"${LOCK_CMD[@]}"} "$GRADLE" $PROOFSTONE_TEST_TASK \
      "-Dproofstone.cells=$cell" ${scope_arg[@]+"${scope_arg[@]}"} --no-parallel --max-workers=1 --console=plain)
  else
    # shellcheck disable=SC2086
    (cd "$PROOFSTONE_PROJECT_DIR" && ${LOCK_CMD[@]+"${LOCK_CMD[@]}"} mvn -B "-Dproofstone.cells=$cell" $PROOFSTONE_TEST_TASK)
  fi
  run_status=$?
  set -e

  # Copy the XML out of the project, so the next cell cannot overwrite it.
  mkdir -p "$out"
  xml_count=0
  if [ -d "$PROOFSTONE_PROJECT_DIR/$RESULTS_REL" ]; then
    for f in "$PROOFSTONE_PROJECT_DIR/$RESULTS_REL"/*.xml; do
      [ -e "$f" ] || continue
      cp "$f" "$out/"
      xml_count=$((xml_count + 1))
    done
  fi

  problems=""
  [ "$run_status" -eq 0 ] || problems="$problems exit=$run_status;"
  if [ "$xml_count" -eq 0 ]; then
    problems="$problems no JUnit XML in $RESULTS_REL;"
  else
    # grep exits 1 on zero matches, which pipefail would turn into a silent exit: count with `|| true` instead.
    tests=$(cat "$out"/*.xml | grep -o '<testcase' | wc -l | tr -d ' ' || true)
    skipped=$(cat "$out"/*.xml | grep -o '<skipped' | wc -l | tr -d ' ' || true)
    bad=$(cat "$out"/*.xml | grep -o '<failure\|<error' | wc -l | tr -d ' ' || true)
    [ "$tests" -gt 0 ] || problems="$problems no test ran (the suite did not select this cell);"
    [ "$skipped" -eq 0 ] || problems="$problems $skipped skipped test(s), a skip is not a pass;"
    [ "$bad" -eq 0 ] || problems="$problems $bad failure/error element(s);"
  fi

  if [ -z "$problems" ]; then
    summary+=("PASS  $cell  tests=$tests skipped=0")
  else
    summary+=("FAIL  $cell ${problems%;}")
    failures+=("$cell")
  fi
done

echo "run-cells: summary"
printf '%s\n' "${summary[@]}"

if [ "${#failures[@]}" -gt 0 ]; then
  fail "${#failures[@]} cell(s) failed: ${failures[*]}"
fi
echo "run-cells: all selected cells passed"
