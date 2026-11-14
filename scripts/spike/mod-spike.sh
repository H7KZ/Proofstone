#!/usr/bin/env bash
# SPIKE (T-M8-01). Not production code. Boots one Fabric, NeoForge or Paper server in itzg/minecraft-server (pinned by
# digest), copies the pinned loader jar, mods, a data pack and server properties in, joins with the mcpl driver through
# scripts/spike/ModJoin.java, and keeps every log under build/spike/.
#
# Usage (Git Bash on Windows needs MSYS_NO_PATHCONV=1 so container paths are not rewritten):
#   PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh bash scripts/spike/mod-spike.sh \
#     <fabric|neoforge|paper> <mc> <joins> [ModJoin steps...]
# Environment:
#   MODS="a.jar b.jar"   copied into /mods (the image copies them to /data/mods as the server user)
#   DATAPACK=x.zip       copied into /datapacks-in and installed with the image's DATAPACKS (world/datapacks)
#   PACK=x.zip           a resource pack: server.properties points at http://127.0.0.1:28765/pack.zip with its sha1,
#                        required; ModJoin serves the file from this host (SPIKE_PACK)
#   CONTENT=1            sets PROOFSTONE_SPIKE_CONTENT=1: the spike mod also registers one item (a "content mod")
#   SPIKE_PROBE=1        one raw probe connection before the counted joins (ModJoin)
#   EXPORT=1             copy /data/libraries and the loader's server jars out to $OUT/export (compile classpath for
#                        the spike mods; build/ only, never committed)
#   NO_NETWORK=1         first start with --network none (expected to fail: shows what the install fetches)
#   OFFLINE=1            start the container with --network none after the first boot (does the loader start without
#                        the internet once installed?)
#
# Inputs are copied in (docker cp), never bind-mounted (AGENTS.md section 3). Pins are spike pins recorded in
# docs/platforms/fabric.md and neoforge.md; versions.yml is not changed by this card.
set -euo pipefail

LOADER=${1:?loader: fabric | neoforge | paper}
MC=${2:?minecraft version}
JOINS=${3:-5}
shift 3 || true
if [ "${PROOFSTONE_ACCEPT_EULA:-}" != "true" ]; then
  echo "Minecraft EULA not accepted: set PROOFSTONE_ACCEPT_EULA=true for this command" >&2
  exit 2
fi

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
CACHE=$ROOT/build/spike/cache
OUT=${SPIKE_OUT:-$ROOT/build/spike/$LOADER-$MC-$(date +%Y%m%dT%H%M%S)}
mkdir -p "$OUT/stage/pins"

# itzg/minecraft-server, the per-Java-tag digests versions.yml already pins (ADR-0013). 26.x needs Java 25.
case "$MC" in
  26.*)   IMAGE=itzg/minecraft-server@sha256:e2fabc536673f8c6b343055c93f7a73e10caa79cd3368f645987593c65adf1e0 ;; # java25
  1.21.*) IMAGE=itzg/minecraft-server@sha256:04b5ee189f5f2c1992fa557e366cf2e4d09fe2abfbd57d2d1f977d8c038c1b98 ;; # java21
  *) echo "no image digest for $MC in this spike" >&2; exit 2 ;;
esac

# Loader pins (spike pins; sha256 computed from the download and, where the maven publishes one, compared with it).
FABRIC_LOADER=0.20.1
FABRIC_LAUNCHER_VER=1.1.2
NEOFORGE=26.3.0.103
declare -A PIN_SHA=(
  [fabric-server-mc.26.3-loader.0.20.1-launcher.1.1.2.jar]=70446947655b4f8f286aba896aa4c19a255e8ca8a730636ef2a6017ae4415d95
  [fabric-server-mc.1.21.11-loader.0.20.1-launcher.1.1.2.jar]=2b06e5e0719199ba100659a283626d9b055e5fded1cb87fa1cdf4df571c0754d
  [neoforge-26.3.0.103-installer.jar]=d8a92e17cf12b5739a2518b11afd47eeec0e636a6e63f94b6400c5dbd3603e48
)

C=ps-spike-mod
hostpath() { cygpath -m "$1" 2>/dev/null || echo "$1"; }

cleanup() {
  docker logs "$C" >"$OUT/server.log" 2>&1 || true
  docker rm -f "$C" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Waits for a log line, bounded: the await-with-timeout shape (AGENTS.md section 3), not a fixed delay.
await_log() {
  local pattern=$1 seconds=$2
  if ! timeout "$seconds" sh -c "until docker logs $C 2>&1 | grep -q -E '$pattern'; do
      [ \"\$(docker inspect -f '{{.State.Running}}' $C)\" = true ] || exit 1; sleep 1; done"; then
    echo "BOOT FAIL: $C stopped or did not log /$pattern/ within ${seconds}s (host finding, not a join timeout)" >&2
    docker logs "$C" 2>&1 | tail -60 >&2
    exit 1
  fi
}

ENV=(-e EULA=TRUE -e VERSION="$MC" -e ONLINE_MODE=FALSE -e ENFORCE_SECURE_PROFILE=FALSE -e LEVEL_TYPE=minecraft:flat
     -e DIFFICULTY=peaceful -e VIEW_DISTANCE=4 -e ENABLE_RCON=false -e CREATE_CONSOLE_IN_PIPE=true -e MOTD=spike)
case "$LOADER" in
  fabric)
    PIN=fabric-server-mc.$MC-loader.$FABRIC_LOADER-launcher.$FABRIC_LAUNCHER_VER.jar
    ENV+=(-e TYPE=FABRIC -e FABRIC_LAUNCHER=/pins/$PIN) ;;
  neoforge)
    PIN=neoforge-$NEOFORGE-installer.jar
    ENV+=(-e TYPE=NEOFORGE -e NEOFORGE_INSTALLER=/pins/$PIN) ;;
  paper)
    PIN=
    ENV+=(-e TYPE=PAPER -e PAPER_BUILD=244) ;;
  *) echo "unknown loader $LOADER" >&2; exit 2 ;;
esac
if [ -n "$PIN" ]; then
  [ -f "$CACHE/$PIN" ] || { echo "missing pinned input $CACHE/$PIN (download it first, see docs/platforms/$LOADER.md)" >&2; exit 2; }
  got=$(sha256sum "$CACHE/$PIN" | cut -d' ' -f1)
  [ "$got" = "${PIN_SHA[$PIN]}" ] || { echo "sha256 mismatch for $PIN: $got" >&2; exit 2; }
  cp "$CACHE/$PIN" "$OUT/stage/pins/"
fi

if [ -n "${MODS:-}" ]; then
  mkdir -p "$OUT/stage/mods"
  for m in $MODS; do cp "$m" "$OUT/stage/mods/"; done
fi
if [ -n "${DATAPACK:-}" ]; then
  mkdir -p "$OUT/stage/datapacks"
  cp "$DATAPACK" "$OUT/stage/datapacks/"
  ENV+=(-e DATAPACKS="/datapacks-in/$(basename "$DATAPACK")")
fi
STEPS_ENV=()
if [ -n "${PACK:-}" ]; then
  PACK_SHA1=$(sha1sum "$PACK" | cut -d' ' -f1)
  ENV+=(-e RESOURCE_PACK=http://127.0.0.1:28765/pack.zip -e RESOURCE_PACK_SHA1="$PACK_SHA1" -e RESOURCE_PACK_ENFORCE=TRUE)
  # The prompt is a text component (JSON); a plain string is dropped with "Failed to parse resource pack prompt".
  DEFAULT_PROMPT='{"text":"Proofstone spike pack"}'
  ENV+=(-e RESOURCE_PACK_PROMPT="${PACK_PROMPT:-$DEFAULT_PROMPT}")
  STEPS_ENV+=(SPIKE_PACK="$(hostpath "$PACK")")
fi
if [ "${CONTENT:-}" = 1 ]; then
  ENV+=(-e PROOFSTONE_SPIKE_CONTENT=1)
fi

docker rm -f "$C" >/dev/null 2>&1 || true
if [ "${NO_NETWORK:-}" = 1 ]; then
  # First start with no network at all: the error names what the install still fetches, and from where.
  docker create --name "$C" --network none "${ENV[@]}" "$IMAGE" >/dev/null
else
  docker create --name "$C" -p 127.0.0.1::25565 -p 127.0.0.1::25590 "${ENV[@]}" "$IMAGE" >/dev/null
fi
[ -n "$PIN" ] && docker cp "$(hostpath "$OUT/stage/pins")" "$C:/pins"
[ -d "$OUT/stage/mods" ] && docker cp "$(hostpath "$OUT/stage/mods")" "$C:/mods"
[ -d "$OUT/stage/datapacks" ] && docker cp "$(hostpath "$OUT/stage/datapacks")" "$C:/datapacks-in"
t0=$(date +%s)
docker start "$C" >/dev/null
await_log 'Done \(' "${BOOT_TIMEOUT:-600}"
echo "BOOT $LOADER@$MC ready in $(( $(date +%s) - t0 ))s (first start, includes install)"
# The spike mod's HTTP port (only when a spike mod is loaded): the agent's shape and its ready flag.
HPORT=$(docker port "$C" 25590/tcp | head -1 | sed 's/.*://')
echo "spike mod /health: $(curl -s -m 5 "http://127.0.0.1:$HPORT/health" || echo 'no answer')"
grep -a '\[proofstone-spike\]' <(docker logs "$C" 2>&1) || true

if [ "${OFFLINE:-}" = 1 ]; then
  # Second start of the same installed container with no network: what does the loader still fetch?
  docker stop "$C" >/dev/null
  docker logs "$C" >"$OUT/server-first-start.log" 2>&1
  docker network disconnect bridge "$C"
  t0=$(date +%s)
  docker start "$C" >/dev/null
  until [ "$(docker logs "$C" 2>&1 | grep -c -E 'Done \(')" -ge 2 ]; do
    [ "$(docker inspect -f '{{.State.Running}}' "$C")" = true ] || { echo "OFFLINE RESTART FAILED"; docker logs "$C" 2>&1 | tail -40; exit 1; }
    [ $(( $(date +%s) - t0 )) -lt 300 ] || { echo "OFFLINE RESTART TIMEOUT"; docker logs "$C" 2>&1 | tail -40; exit 1; }
    sleep 1
  done
  echo "OFFLINE restart ready in $(( $(date +%s) - t0 ))s with no network"
  docker logs "$C" >"$OUT/server.log" 2>&1
  echo "output: $OUT"
  exit 0
fi

# What the install left in /data: every jar with its sha256 (pins and fetched libraries).
docker exec "$C" sh -c 'cd /data && find . -name "*.jar" -not -path "./world/*" | sort | xargs sha256sum' >"$OUT/data-jars.txt"
docker exec "$C" sh -c 'cat /data/.install-fabric.env /data/.run-neoforge.env 2>/dev/null; ls -la /data /data/mods 2>/dev/null; true' >"$OUT/data-ls.txt"
echo "jars under /data: $(wc -l <"$OUT/data-jars.txt") (list in $OUT/data-jars.txt)"
if [ "${EXPORT:-}" = 1 ]; then
  mkdir -p "$OUT/export"
  docker cp "$C:/data/libraries" "$(hostpath "$OUT/export")/" 2>/dev/null || true
  docker exec "$C" sh -c 'ls /data/*.jar /data/.fabric 2>/dev/null' || true
  for f in $(docker exec "$C" sh -c 'ls /data/*.jar 2>/dev/null'); do docker cp "$C:$f" "$(hostpath "$OUT/export")/"; done
  docker cp "$C:/data/.fabric" "$(hostpath "$OUT/export")/fabric" 2>/dev/null || true
fi

PORT=$(docker port "$C" 25565/tcp | head -1 | sed 's/.*://')
echo "server $LOADER@$MC on 127.0.0.1:$PORT, output $OUT"
if [ -z "${SPIKE_CP:-}" ]; then
  SPIKE_CP=$(cd "$ROOT" && ./gradlew -q --init-script scripts/spike/classpath.init.gradle :drivers:mcpl:spikeClasspath \
    --max-workers=1 | sed -n 's/^SPIKE_CP=//p')
fi
JAVA=${SPIKE_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}
env SPIKE_CONSOLE="$C" "${STEPS_ENV[@]}" "$JAVA" -cp "$SPIKE_CP" "$(hostpath "$ROOT/scripts/spike/ModJoin.java")" \
  127.0.0.1 "$PORT" "$JOINS" "$(hostpath "$OUT")" "$@" | tee "$OUT/client.txt"

docker logs "$C" >"$OUT/server.log" 2>&1
echo "server: login lines $(grep -c 'logged in with entity id' "$OUT/server.log" || true), joined lines $(grep -c 'joined the game' "$OUT/server.log" || true)"
echo "output: $OUT"
