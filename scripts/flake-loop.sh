#!/usr/bin/env bash
# Flake loop for the Gradle example (T-M6-10, docs/plan/ROADMAP.md "M6 pre-release findings" item 3). Local only.
# Runs `:examples:gradle-paper-plugin:e2eTest` on paper@26.3 <n> times, one run at a time. Every run takes its own
# dockerlock (this script is NOT inside the lock, so no lock is nested). A red stays red: there is no retry.
# Before the next run starts, each run's evidence is copied to <outdir>/run-NN/: the JUnit XML, the repro bundle
# (only a red run writes one), the Gradle log, the container logs of the Paper server (sampled while it runs, the
# last sample before the harness removes the container is kept), host free memory and CPU load before and after,
# `docker ps` names, and `docker stats` samples of the harness containers. Boot and join times come from the Docker
# timestamps of the container log (first line to the `Done (` line and to the first `joined the game` line).
#
# --stress starts ONE bounded CPU and memory stressor, named proofstone-stress, for the whole loop. Its limits are
# half of what Docker reports (NCPU, MemTotal). It is removed on exit. No other container is ever touched: the
# script only reads other containers (ps, inspect, logs, stats) and removes proofstone-stress by name.
#
# Usage: bash scripts/flake-loop.sh <n> <outdir> [--stress]
#   <outdir> must be under build/ (gitignored). Logs are never committed.
# Output: <outdir>/summary.tsv, one line per run: run, verdict, tests, failures, errors, skipped, wall_s, boot_s, join_s,
#   free_gb_before, cpu_pct_before, detail. Verdict types: pass, join-timeout, INVALID, DISAGREE, LEAK, VACUOUS, FLAKY,
#   ERRLOG (ErrorLogGuard "moved too quickly"), SKIPPED, NORUN (no XML: build or Docker failure), OTHER:<text>.
# Exit: 0 when the loop ran to the end (reds do not change this), 2 on bad usage, 1 when the loop itself could not run.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

usage() { echo "usage: bash scripts/flake-loop.sh <n> <outdir> [--stress]" >&2; exit 2; }
[[ $# -ge 2 && $# -le 3 ]] || usage
N=$1
OUT=$2
STRESS=false
if [[ $# -eq 3 ]]; then
  [[ "$3" == "--stress" ]] || usage
  STRESS=true
fi
[[ "$N" =~ ^[1-9][0-9]*$ ]] || { echo "flake-loop: <n> must be a positive integer, got '$N'" >&2; exit 2; }

mkdir -p "$OUT" || exit 1
OUT=$(cd "$OUT" && pwd)
case "$OUT" in
  */build/*) ;;
  *) echo "flake-loop: <outdir> must be under build/ (gitignored), got $OUT" >&2; exit 2 ;;
esac

EX=examples/gradle-paper-plugin
IMAGE_MATCH=itzg/minecraft-server # the Paper host image (versions.yml platforms.paper.host.image)
ALPINE=alpine@sha256:3f4d6b32e26703c6082839234f43769d5046b3ac7c910033911d1e71d8cd630a # alpine:3, pinned
STRESS_NAME=proofstone-stress
WATCHER_PID=""

# Host free memory (GiB) and CPU load (%), from Windows. Read-only.
host_sample() {
  powershell.exe -NoProfile -NonInteractive -Command '
    $os = Get-CimInstance Win32_OperatingSystem
    $cpu = (Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average
    "free_gb=" + [math]::Round($os.FreePhysicalMemory / 1MB, 2) + " cpu_pct=" + $cpu
  ' 2>/dev/null | tr -d '\r'
}

stress_start() {
  local ncpu memb half_bytes half_mb cpus
  ncpu=$(docker info --format '{{.NCPU}}') || return 1
  memb=$(docker info --format '{{.MemTotal}}') || return 1
  cpus=$((ncpu / 2)); [[ $cpus -ge 1 ]] || cpus=1
  half_bytes=$((memb / 2))
  half_mb=$((half_bytes / 1048576 - 128)) # ballast in /dev/shm, which the container's memory limit also counts
  # Only our own leftover is removed, by name.
  docker rm -f "$STRESS_NAME" > /dev/null 2>&1 || true
  # Busy loops on every CPU share, plus a memory ballast. The ballast must fill /dev/shm, so shm-size = limit.
  bash scripts/dockerlock.sh docker run -d --name "$STRESS_NAME" --cpus "$cpus" --memory "${half_bytes}b" \
    --shm-size "${half_bytes}b" "$ALPINE" sh -c "
      i=0; while [ \$i -lt $cpus ]; do (while :; do :; done) & i=\$((i+1)); done
      dd if=/dev/zero of=/dev/shm/ballast bs=1M count=$half_mb 2>/dev/null
      wait" > "$OUT/stress-start.log" 2>&1 || return 1
  echo "stress: $STRESS_NAME cpus=$cpus memory=${half_bytes}b (half of NCPU=$ncpu, MemTotal=$memb)" >&2
  [[ "$(docker inspect -f '{{.State.Running}}' "$STRESS_NAME" 2>/dev/null)" == "true" ]] || {
    echo "flake-loop: $STRESS_NAME is not running" >&2
    return 1
  }
}

cleanup() {
  [[ -n "$WATCHER_PID" ]] && kill "$WATCHER_PID" 2>/dev/null
  if [[ "$STRESS" == true ]]; then docker rm -f "$STRESS_NAME" > /dev/null 2>&1; fi
}
trap cleanup EXIT INT TERM

# Samples the Paper server containers while a run is going. Reads only: logs and stats of containers that the
# harness (Testcontainers) started with the Paper image. The log file is overwritten each cycle, so the last cycle
# before the harness removes the container is what is kept.
watch_containers() { # rundir
  local rdir=$1 id img tick=0
  while :; do
    for id in $(docker ps -q --filter label=org.testcontainers=true 2> /dev/null); do
      img=$(docker inspect -f '{{.Config.Image}}' "$id" 2> /dev/null)
      case "$img" in *"$IMAGE_MATCH"*) ;; *) continue ;; esac
      # A container the harness removed between `ps` and `logs` keeps its last good snapshot: the error text is never kept.
      docker logs --timestamps "$id" > "$rdir/.tmp-$id.log" 2>&1 \
        && mv -f "$rdir/.tmp-$id.log" "$rdir/container-$id.log"
      rm -f "$rdir/.tmp-$id.log"
      if ((tick % 5 == 0)); then
        docker stats --no-stream "$id" >> "$rdir/docker-stats.txt" 2>&1
        echo "--- $(date -u +%FT%TZ)" >> "$rdir/docker-stats.txt"
      fi
    done
    tick=$((tick + 1))
    sleep 2
  done
}

# Seconds (with fraction) of a docker timestamp such as 2026-11-09T10:00:00.123456789Z.
epoch() {
  local ts=$1 base frac
  base=${ts%%.*}
  frac=${ts#*.}; frac=${frac%Z}; frac=${frac:0:9}
  printf '%s.%s\n' "$(date -u -d "${base}Z" +%s)" "$frac"
}

# Boot and join seconds for every container log of one run: "boot1,boot2 join1,join2" (n/a when a line is missing).
times_of() { # rundir
  local f first done_ts join_ts boots="" joins="" b j
  for f in "$1"/container-*.log; do
    [[ -f "$f" ]] || continue
    first=$(head -1 "$f" | cut -d' ' -f1)
    done_ts=$(grep -m1 'Done (' "$f" | cut -d' ' -f1)
    join_ts=$(grep -m1 'joined the game' "$f" | cut -d' ' -f1)
    b=n/a; j=n/a
    if [[ -n "$first" && -n "$done_ts" ]]; then b=$(awk -v a="$(epoch "$first")" -v z="$(epoch "$done_ts")" 'BEGIN{printf "%.1f", z-a}'); fi
    if [[ -n "$first" && -n "$join_ts" ]]; then j=$(awk -v a="$(epoch "$first")" -v z="$(epoch "$join_ts")" 'BEGIN{printf "%.1f", z-a}'); fi
    boots="${boots:+$boots,}$b"
    joins="${joins:+$joins,}$j"
  done
  echo "${boots:-n/a} ${joins:-n/a}"
}

# Value of one XML attribute of a tag, 0 when absent.
attr_of() { # "<tag ...>" name
  local value
  value=$(sed -n "s/.* $2=\"\([0-9]*\)\".*/\1/p" <<< "$1" | head -1)
  echo "${value:-0}"
}

# Sums the JUnit <testsuite> counters of a directory: "tests skipped failures errors".
xml_counts() { # xmldir
  local t=0 s=0 f=0 e=0 tag
  while IFS= read -r tag; do
    t=$((t + $(attr_of "$tag" tests)))
    s=$((s + $(attr_of "$tag" skipped)))
    f=$((f + $(attr_of "$tag" failures)))
    e=$((e + $(attr_of "$tag" errors)))
  done < <(cat "$1"/*.xml 2> /dev/null | grep -o '<testsuite [^>]*' || true)
  echo "$t $s $f $e"
}

# One verdict type per failure message, joined with '+'. Nothing is retried or re-read to make it green.
classify() { # rc tests skipped failures errors xmldir gradlelog
  local rc=$1 tests=$2 skipped=$3 failures=$4 errors=$5 xmldir=$6 gradlelog=$7 msgs types t
  if [[ $rc -eq 0 && $tests -gt 0 && $failures -eq 0 && $errors -eq 0 && $skipped -eq 0 ]]; then
    echo "pass|"
    return
  fi
  if [[ $tests -eq 0 ]]; then
    echo "NORUN|$(grep -m1 -E 'What went wrong|FAILED' -A1 "$gradlelog" 2>/dev/null | tr '\t\r' '  ' | cut -c1-160)"
    return
  fi
  msgs=$(cat "$xmldir"/*.xml 2> /dev/null | grep -o '<failure message="[^"]*' | sed 's/^<failure message="//' | cut -c1-220)
  types=""
  if [[ -z "$msgs" ]]; then
    types="OTHER:no-failure-message(rc=$rc,skipped=$skipped,errors=$errors)"
  else
    while IFS= read -r m; do
      case "$m" in
        *"timed out joining"*) t=join-timeout ;;
        *"moved too quickly"*) t=ERRLOG ;;
        INVALID*) t=INVALID ;;
        DISAGREE*) t=DISAGREE ;;
        LEAK*) t=LEAK ;;
        VACUOUS*) t=VACUOUS ;;
        FLAKY*) t=FLAKY ;;
        *) t="OTHER:${m:0:90}" ;;
      esac
      types="${types:+$types+}$t"
    done <<< "$msgs"
  fi
  if [[ $skipped -gt 0 && -z "$msgs" ]]; then types="SKIPPED"; fi
  # Unique verdict types, in order of first appearance; the messages go to the detail column.
  types=$(awk '!seen[$0]++' <<< "$(tr '+' '\n' <<< "$types")" | paste -sd+ -)
  echo "$types|$(head -c 220 <<< "$msgs" | tr '\n\r\t' '   ')"
}

SUMMARY="$OUT/summary.tsv"
printf 'run\tverdict\ttests\tfailures\terrors\tskipped\twall_s\tboot_s\tjoin_s\tfree_gb_before\tcpu_pct_before\tdetail\n' > "$SUMMARY"
{
  echo "flake-loop: n=$N stress=$STRESS outdir=$OUT started=$(date -u +%FT%TZ)"
  echo "docker ps (names) at start:"; docker ps --format '{{.Names}}' 2>&1
} > "$OUT/loop-start.txt"

if [[ "$STRESS" == true ]]; then
  stress_start || { echo "flake-loop: could not start the stressor, no run was made" >&2; exit 1; }
fi

for ((run = 1; run <= N; run++)); do
  rn=$(printf '%02d' "$run")
  rdir="$OUT/run-$rn"
  mkdir -p "$rdir"
  # Stale evidence of an earlier run must never be read. Earlier runs were copied aside already (below).
  rm -rf "$EX/build/test-results/e2eTest" "$EX/build/proofstone"
  host_sample > "$rdir/host-before.txt"
  docker ps --format '{{.Names}}' > "$rdir/docker-ps-before.txt" 2>&1
  t0=$(date +%s)
  start_iso=$(date -u +%FT%TZ)

  watch_containers "$rdir" &
  WATCHER_PID=$!
  # The EULA is set in this command's environment only (AGENTS section 4). No retry: rc is recorded as it is.
  PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:gradle-paper-plugin:e2eTest \
    --no-parallel --max-workers=1 -Dproofstone.cells=paper@26.3 < /dev/null > "$rdir/gradle.log" 2>&1
  rc=$?
  kill "$WATCHER_PID" 2> /dev/null
  wait "$WATCHER_PID" 2> /dev/null
  WATCHER_PID=""
  t1=$(date +%s)

  host_sample > "$rdir/host-after.txt"
  docker ps --format '{{.Names}}' > "$rdir/docker-ps-after.txt" 2>&1
  printf 'run=%s start=%s end=%s wall_s=%s gradle_rc=%s stress=%s\n' "$rn" "$start_iso" "$(date -u +%FT%TZ)" \
    "$((t1 - t0))" "$rc" "$STRESS" > "$rdir/run-meta.txt"
  if [[ "$STRESS" == true ]]; then
    docker stats --no-stream "$STRESS_NAME" > "$rdir/stress-stats.txt" 2>&1
  fi

  # Copy the evidence aside now, before the next run can overwrite it.
  [[ -d "$EX/build/test-results/e2eTest" ]] && cp -r "$EX/build/test-results/e2eTest" "$rdir/test-results"
  [[ -d "$EX/build/proofstone" ]] && cp -r "$EX/build/proofstone" "$rdir/proofstone"

  read -r tests skipped failures errors <<< "$(xml_counts "$rdir/test-results")"
  verdict_detail=$(classify "$rc" "$tests" "$skipped" "$failures" "$errors" "$rdir/test-results" "$rdir/gradle.log")
  verdict=${verdict_detail%%|*}
  detail=${verdict_detail#*|}
  read -r boot join <<< "$(times_of "$rdir")"
  free=$(sed -n 's/^free_gb=\([0-9.]*\).*/\1/p' "$rdir/host-before.txt")
  cpu=$(sed -n 's/.*cpu_pct=\([0-9.]*\).*/\1/p' "$rdir/host-before.txt")
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$rn" "$verdict" "$tests" "$failures" "$errors" "$skipped" \
    "$((t1 - t0))" "$boot" "$join" "${free:-n/a}" "${cpu:-n/a}" "$detail" >> "$SUMMARY"
  echo "== run $rn: $verdict (rc=$rc tests=$tests failures=$failures errors=$errors skipped=$skipped)"
done

echo "flake-loop: done $N runs, summary $SUMMARY"
