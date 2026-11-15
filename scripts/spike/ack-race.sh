#!/usr/bin/env bash
# SPIKE (T-M7-18). Not production code. Boots one fresh server as PaperHost would (TYPE, pinned build, flat world, fixed
# seed, offline, connection throttle off), joins it with scripts/spike/AckRace.java in two teleport-ack modes, alternating,
# and counts `<name> moved too quickly!` per mode from the server log.
#
# Usage (Git Bash on Windows needs MSYS_NO_PATHCONV=1 so /data is not rewritten):
#   PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh bash scripts/spike/ack-race.sh <platform> [joins per mode]
# Platforms: purpur (26.3, build 2657), paper (26.3, build 244). Pins as versions.yml has them; versions.yml is not changed.
# Inputs are copied into the container (docker cp), never bind-mounted (AGENTS.md section 3).
set -euo pipefail

PLATFORM=${1:?platform: purpur | paper}
JOINS=${2:-10}
if [ "${PROOFSTONE_ACCEPT_EULA:-}" != "true" ]; then
  echo "Minecraft EULA not accepted: set PROOFSTONE_ACCEPT_EULA=true for this command" >&2
  exit 2
fi
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=${SPIKE_OUT:-$ROOT/build/spike/ack-race-$PLATFORM-$(date +%Y%m%dT%H%M%S)}
mkdir -p "$OUT/stage"
IMAGE=itzg/minecraft-server@sha256:e2fabc536673f8c6b343055c93f7a73e10caa79cd3368f645987593c65adf1e0  # java25
case "$PLATFORM" in
  purpur) BUILD_ENV=(-e TYPE=PURPUR -e PURPUR_BUILD=2657) ;;
  paper) BUILD_ENV=(-e TYPE=PAPER -e PAPER_BUILD=244) ;;
  *) echo "unknown platform $PLATFORM" >&2; exit 2 ;;
esac
NAME=ps-spike-ackrace
hostpath() { cygpath -m "$1" 2>/dev/null || echo "$1"; }
cleanup() {
  docker logs "$NAME" >"$OUT/server.log" 2>&1 || true
  docker rm -f "$NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

printf 'settings:\n  connection-throttle: -1\n' >"$OUT/stage/bukkit.yml"
docker create --name "$NAME" -p 127.0.0.1::25565 -e EULA=TRUE "${BUILD_ENV[@]}" -e VERSION=26.3 -e ONLINE_MODE=FALSE \
  -e ENFORCE_SECURE_PROFILE=FALSE -e LEVEL_TYPE=minecraft:flat -e SEED=1234567 -e SPAWN_PROTECTION=0 \
  -e DIFFICULTY=peaceful -e VIEW_DISTANCE=4 -e ENABLE_RCON=false "$IMAGE" >/dev/null
docker cp "$(hostpath "$OUT/stage/bukkit.yml")" "$NAME:/data/bukkit.yml"
docker start "$NAME" >/dev/null
# Bounded wait for the ready line (await-with-timeout shape, AGENTS.md section 3).
timeout 300 sh -c "until docker logs $NAME 2>&1 | grep -q 'Done ('; do
  [ \"\$(docker inspect -f '{{.State.Running}}' $NAME)\" = true ] || exit 1; sleep 1; done"
PORT=$(docker port "$NAME" 25565/tcp | head -1 | sed 's/.*://')
echo "$PLATFORM on 127.0.0.1:$PORT, output $OUT"

if [ -z "${SPIKE_CP:-}" ]; then
  SPIKE_CP=$(cd "$ROOT" && ./gradlew -q --init-script scripts/spike/classpath.init.gradle :drivers:mcpl:spikeClasspath \
    --max-workers=1 | sed -n 's/^SPIKE_CP=//p')
fi
JAVA=${SPIKE_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}
"$JAVA" -cp "$SPIKE_CP" "$(hostpath "$ROOT/scripts/spike/AckRace.java")" 127.0.0.1 "$PORT" "$JOINS" | tee "$OUT/client.txt"

docker logs "$NAME" >"$OUT/server.log" 2>&1
for mode in I T; do
  logins=$(grep -c "Ack${mode}[0-9]*\[.*logged in" "$OUT/server.log" || true)
  warns=$(grep -c "Ack${mode}[0-9]* moved too quickly" "$OUT/server.log" || true)
  echo "mode $mode: logins $logins, moved too quickly $warns"
done
echo "output: $OUT"
