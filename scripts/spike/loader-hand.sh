#!/usr/bin/env bash
# SPIKE (T-M8-16, diagnosis only). THROWAWAY, not production code, nothing here is a gate.
# Starts ONE loader server by hand in the pinned itzg image with the same environment LoaderEnvironment.env sets
# (no agent jar: the two WARN families are vanilla code paths), optionally with extra environment, runs optional
# console commands and an optional join stress (DisconnectRace.java through the mcpl driver), and keeps the log.
#
# Usage (Git Bash on Windows needs MSYS_NO_PATHCONV=1):
#   PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh bash scripts/spike/loader-hand.sh \
#     <fabric|neoforge> <out-dir> [-e KEY=VAL ...] -- [console command]...
# Environment:
#   RACE="<cycles> <max-jitter-ms>"   run DisconnectRace (needs SPIKE_CP = the mcpl driver's runtime classpath)
#   WAIT_LOG="regex"                  after the console commands, wait (bounded, 60 s) for this regex in the log
#   AGENT=1                           also copy the platform's agent-all.jar (needs a prior :platforms:<loader>:agent:shadowJar)
# Pins come from conformance/build/loader-cache (a prior :conformance:run filled it), checked against versions.yml's sha256.
set -euo pipefail
LOADER=${1:?fabric|neoforge}
OUT=${2:?out dir}
shift 2
EXTRA=()
while [ $# -gt 0 ] && [ "$1" != "--" ]; do EXTRA+=("$1" "$2"); shift 2; done
[ "${1:-}" = "--" ] && shift
CMDS=("$@")
[ "${PROOFSTONE_ACCEPT_EULA:-}" = "true" ] || { echo "set PROOFSTONE_ACCEPT_EULA=true for this command" >&2; exit 2; }

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
CACHE=$ROOT/conformance/build/loader-cache
mkdir -p "$OUT/stage/pins" "$OUT/stage/mods"
IMAGE=itzg/minecraft-server@sha256:e2fabc536673f8c6b343055c93f7a73e10caa79cd3368f645987593c65adf1e0 # java25, versions.yml
C=ps-t816-hand
hostpath() { cygpath -m "$1" 2>/dev/null || echo "$1"; }

ENV=(-e EULA=TRUE -e VERSION=26.3 -e ONLINE_MODE=FALSE -e ENFORCE_SECURE_PROFILE=FALSE -e LEVEL_TYPE=minecraft:flat
     -e SPAWN_PROTECTION=0 -e DIFFICULTY=peaceful -e VIEW_DISTANCE=4 -e ENABLE_RCON=false -e CREATE_CONSOLE_IN_PIPE=true)
case "$LOADER" in
  fabric)
    PIN=fabric-server-mc.26.3-loader.0.20.1-launcher.1.1.2.jar
    cp "$CACHE/$PIN" "$OUT/stage/pins/"; cp "$CACHE"/fabric-api-*.jar "$OUT/stage/mods/"
    ENV+=(-e TYPE=FABRIC -e FABRIC_LAUNCHER=/pins/$PIN) ;;
  neoforge)
    PIN=neoforge-26.3.0.103-installer.jar
    cp "$CACHE/$PIN" "$OUT/stage/pins/"
    ENV+=(-e TYPE=NEOFORGE -e NEOFORGE_INSTALLER=/pins/$PIN) ;;
  *) echo "unknown loader" >&2; exit 2 ;;
esac
if [ "${AGENT:-}" = 1 ]; then
  cp "$ROOT/platforms/$LOADER/agent/build/libs/agent-all.jar" "$OUT/stage/mods/proofstone-agent.jar"
  ENV+=(-e PROOFSTONE_AGENT_TOKEN=t816 -e PROOFSTONE_AGENT_PORT=25590)
fi

cleanup() { docker logs "$C" >"$OUT/server.log" 2>&1 || true; docker rm -f "$C" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker rm -f "$C" >/dev/null 2>&1 || true
docker create --name "$C" -p 127.0.0.1::25565 "${ENV[@]}" "${EXTRA[@]}" "$IMAGE" >/dev/null
docker cp "$(hostpath "$OUT/stage/pins")" "$C:/pins" 2>/dev/null || true
if ls "$OUT"/stage/mods/*.jar >/dev/null 2>&1; then docker cp "$(hostpath "$OUT/stage/mods")" "$C:/mods"; fi
docker start "$C" >/dev/null
await() { timeout "$2" sh -c "until docker logs $C 2>&1 | grep -q -E '$1'; do [ \"\$(docker inspect -f '{{.State.Running}}' $C)\" = true ] || exit 1; sleep 0.5; done"; }
await 'Done \(' 300 || { echo "BOOT FAIL"; exit 1; }
echo "BOOT ok"
for c in "${CMDS[@]}"; do
  echo "CONSOLE: $c"
  docker exec -u 1000 "$C" mc-send-to-console "$c"
done
[ -n "${WAIT_LOG:-}" ] && { await "$WAIT_LOG" 60 && echo "WAIT_LOG matched" || echo "WAIT_LOG not matched in 60 s"; }
if [ -n "${RACE:-}" ]; then
  PORT=$(docker port "$C" 25565/tcp | head -1 | sed 's/.*://')
  JAVA=${SPIKE_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}
  # shellcheck disable=SC2086
  "$JAVA" -cp "$SPIKE_CP" "$(hostpath "$ROOT/scripts/spike/DisconnectRace.java")" 127.0.0.1 "$PORT" $RACE | tee "$OUT/race.txt"
fi
docker logs "$C" >"$OUT/server.log" 2>&1
echo "WARN Skipping: $(grep -c 'Skipping Entity' "$OUT/server.log" || true)  handleDisconnection twice: $(grep -c 'handleDisconnection() called twice' "$OUT/server.log" || true)"
