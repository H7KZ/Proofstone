#!/usr/bin/env bash
# SPIKE (T-M12-02). One counted loop of joins on one cell, with the read-only watcher of viawatch.sh beside it. Local only.
# One locked invocation of `:conformance:run -Dproofstone.repeat=<joins>` on the smoke test c02 (one cell boot, <joins> joins one after
# another, the same server and, on a bridged cell, the same ViaProxy). A red stays red: no retry. Evidence is copied to <outdir>:
# gradle.log, JUnit XML, the repro bundles (server.log, viaproxy.log, driver-transcript.jsonl per failed repeat), socket-watch.txt.
#
# usage: bash scripts/spike/join-stall/loop.sh <outdir under build/> <joins> <cell> <watch pattern> [dump]
#   cell           for example paper@1.21.11 (native) or paper@1.8.8 (bridged through ViaProxy)
#   watch pattern  the container to watch: 'itzg/minecraft-server' (the Paper server) or 'ViaProxy.jar' (the proxy)
#   dump           also take a JVM thread dump (SIGQUIT) of the watched container's java process when a join sits at
#                  "UUID of player ..." for about 4 s; only meaningful for the Paper container
# Needs PROOFSTONE_ACCEPT_EULA=true in the environment. To also see the client's raw socket reads and writes, apply
# client-raw-io-diagnostic.patch to drivers/mcpl first (never commit it).
set -uo pipefail
cd "$(dirname "$0")/../../.." || exit 1
OUT=${1:?outdir under build/}; N=${2:?joins}; CELL=${3:?cell}; PAT=${4:?watch pattern}; DUMP=${5:-}
case "$OUT" in build/*) ;; *) echo "loop.sh: <outdir> must be under build/ (gitignored)" >&2; exit 2;; esac
mkdir -p "$OUT/xml" "$OUT/bundles"
export TMPDIR=${TMPDIR:-/tmp}
bash scripts/spike/join-stall/viawatch.sh "$OUT/socket-watch.txt" "$PAT" "$DUMP" &
WATCH=$!
sleep 1
rm -rf conformance/build/proofstone conformance/build/test-results
bash scripts/dockerlock.sh ./gradlew :conformance:run "-Dproofstone.cells=$CELL" "-Dproofstone.repeat=$N" --tests '*ConformanceSmokeTest.c02*' \
  --no-parallel --max-workers=1 >"$OUT/gradle.log" 2>&1
RC=$?
touch "$TMPDIR/viawatch.stop"
wait "$WATCH" 2>/dev/null
cp conformance/build/test-results/*/TEST-*.xml "$OUT/xml/" 2>/dev/null
cp -r conformance/build/proofstone "$OUT/bundles/" 2>/dev/null
echo "LOOP cell=$CELL joins=$N rc=$RC $(grep -h -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$OUT"/xml/*.xml | head -3 | tr '\n' ' ')"
echo "stalled repeats: $(grep -h -o 'run [0-9]*: could not join' "$OUT"/xml/*.xml 2>/dev/null | sort -u | tr '\n' ';')"
