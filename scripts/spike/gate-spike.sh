#!/usr/bin/env bash
# SPIKE (T-M10-12). Not production code. Boots two paper@26.3 backends (with the Bukkit agent and the fixture plugin)
# and Gate v0.82.0 (a pinned Go release binary) with modern (Velocity) forwarding on one Docker network, then joins
# through Gate with the mcpl driver and records P01 to P05 by hand (GateProbe.java). The backends' agents are the oracle.
#
# Usage (Git Bash on Windows needs MSYS_NO_PATHCONV=1):
#   PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh bash scripts/spike/gate-spike.sh
# Inputs: GATE_BIN (default /c/pw/gate-cache/gate_0.82.0_linux_amd64, sha256 checked), AGENT_JAR, FIXTURE_JAR, SPIKE_CP,
# SPIKE_JAVA. Files are copied into containers (docker cp), never bind-mounted. The Gate binary is never committed.
set -euo pipefail

if [ "${PROOFSTONE_ACCEPT_EULA:-}" != "true" ]; then
  echo "Minecraft EULA not accepted: set PROOFSTONE_ACCEPT_EULA=true for this command" >&2
  exit 2
fi
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=${SPIKE_OUT:-$ROOT/build/spike/gate-$(date +%Y%m%dT%H%M%S)}
mkdir -p "$OUT/stage/paper" "$OUT/stage/paperconfig" "$OUT/stage/plugins" "$OUT/stage/gate"

# Pins. paper@26.3 exactly as versions.yml has it; Gate from the official v0.82.0 release (checksums.txt agrees).
PAPER_IMAGE=itzg/minecraft-server@sha256:e2fabc536673f8c6b343055c93f7a73e10caa79cd3368f645987593c65adf1e0
PAPER_SHA=9601f9a26a2a52a0f2c807cf1abf691f96ee4fb287878313b601cebc2c51eabf
GATE_BIN=${GATE_BIN:-/c/pw/gate-cache/gate_0.82.0_linux_amd64}
GATE_SHA=591ac49c9597f9e19dd17ee1b87fdca6caad1ad58dd60a23cd1dab4ee070397a
AGENT_JAR=${AGENT_JAR:-$ROOT/platforms/bukkit/agent/build/libs/agent-all.jar}
FIXTURE_JAR=${FIXTURE_JAR:-$ROOT/platforms/bukkit/fixture/build/libs/proofstone-fixture.jar}

NET=ps-gate-net
PROXY=ps-gate-proxy
BACKENDS=(lobby survival)
hostpath() { cygpath -m "$1" 2>/dev/null || echo "$1"; }

echo "$GATE_SHA  $GATE_BIN" | sha256sum -c - || { echo "gate sha256 mismatch" >&2; exit 1; }
ls -l "$GATE_BIN"

cleanup() {
  for c in "$PROXY" "${BACKENDS[@]/#/ps-gate-}"; do
    docker logs "$c" >"$OUT/$c.log" 2>&1 || true
    docker rm -f "$c" >/dev/null 2>&1 || true
  done
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

await_log() {
  local container=$1 pattern=$2 seconds=$3
  if ! timeout "$seconds" sh -c "until docker logs $container 2>&1 | grep -q -E '$pattern'; do
      [ \"\$(docker inspect -f '{{.State.Running}}' $container)\" = true ] || exit 1; sleep 1; done"; then
    echo "TIMEOUT: $container did not log /$pattern/ within ${seconds}s" >&2
    docker logs "$container" 2>&1 | tail -40 >&2
    exit 1
  fi
}

SECRET=$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')
TOKEN=$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')

printf 'settings:\n  connection-throttle: -1\n' >"$OUT/stage/paper/bukkit.yml"
printf 'proxies:\n  velocity:\n    enabled: true\n    online-mode: false\n    secret: %s\n' "$SECRET" >"$OUT/stage/paperconfig/paper-global.yml"
cp "$AGENT_JAR" "$OUT/stage/plugins/agent-all.jar"
cp "$FIXTURE_JAR" "$OUT/stage/plugins/proofstone-fixture.jar"

# quota.* off: the defaults (3 logins burst, 0.4/s) throttle a harness that joins from one address.
cat >"$OUT/stage/gate/config.yml" <<EOT
config:
  bind: 0.0.0.0:25565
  onlineMode: false
  forceKeyAuthentication: false
  servers:
    lobby: lobby:25565
    survival: survival:25565
  try:
    - lobby
  forwarding:
    mode: velocity
    velocitySecret: $SECRET
  quota:
    connections:
      enabled: false
    logins:
      enabled: false
EOT
cp "$GATE_BIN" "$OUT/stage/gate/gate"

docker network create "$NET" >/dev/null
for b in "${BACKENDS[@]}"; do
  docker create --name "ps-gate-$b" --network "$NET" --network-alias "$b" -p 127.0.0.1::25580 -p 127.0.0.1::25565 \
    -e EULA=TRUE -e TYPE=PAPER -e VERSION=26.3 -e PAPER_BUILD=244 -e ONLINE_MODE=FALSE \
    -e ENFORCE_SECURE_PROFILE=FALSE -e LEVEL_TYPE=minecraft:flat -e DIFFICULTY=peaceful -e VIEW_DISTANCE=4 \
    -e PROOFSTONE_AGENT_TOKEN="$TOKEN" -e PROOFSTONE_AGENT_PORT=25580 \
    -e ENABLE_RCON=false -e MOTD="$b" "$PAPER_IMAGE" >/dev/null
  docker cp "$(hostpath "$OUT/stage/paper")/." "ps-gate-$b:/data/"
  docker cp "$(hostpath "$OUT/stage/paperconfig")/." "ps-gate-$b:/config/"
  docker cp "$(hostpath "$OUT/stage/plugins")/." "ps-gate-$b:/plugins/"
  docker start "ps-gate-$b" >/dev/null
done
for b in "${BACKENDS[@]}"; do await_log "ps-gate-$b" 'Done \(' 240; done
for b in "${BACKENDS[@]}"; do
  docker exec "ps-gate-$b" sh -c 'sha256sum /data/paper-*.jar' | grep -q "$PAPER_SHA" || { echo "paper sha256 mismatch" >&2; exit 1; }
  echo "backend $b paper jar sha256 ok"
done

# Gate runs from the pinned paper image (it has glibc); the binary is copied in, with the exec bit set in the command.
docker create --name "$PROXY" --network "$NET" -p 127.0.0.1::25565 --entrypoint sh "$PAPER_IMAGE" \
  -c 'chmod +x /tmp/gate/gate && cd /tmp/gate && exec ./gate -c config.yml' >/dev/null
docker cp "$(hostpath "$OUT/stage/gate")" "$PROXY:/tmp/gate"
docker start "$PROXY" >/dev/null
await_log "$PROXY" 'istening|started|bind' 60
echo "gate binary in container: $(docker exec "$PROXY" sha256sum /tmp/gate/gate)"
docker exec "$PROXY" sha256sum /tmp/gate/gate | grep -q "$GATE_SHA" || { echo "gate sha256 mismatch in container" >&2; exit 1; }
PORT=$(docker port "$PROXY" 25565/tcp | head -1 | sed 's/.*://')
LP=$(docker port ps-gate-lobby 25580/tcp | head -1 | sed 's/.*://')
SP=$(docker port ps-gate-survival 25580/tcp | head -1 | sed 's/.*://')
echo "gate on 127.0.0.1:$PORT, lobby agent $LP, survival agent $SP, output $OUT"

JAVA=${SPIKE_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}
"$JAVA" -cp "$SPIKE_CP" "$(hostpath "$ROOT/scripts/spike/GateProbe.java")" 127.0.0.1 "$PORT" "${PROBE_NAME:-GateSpike1}" "$LP" "$SP" "$TOKEN" "$(hostpath "$OUT")" \
  | tee "$OUT/client.txt"

# P02, second half: a client joins the lobby backend's own port, around Gate (bound to 127.0.0.1 only).
LG=$(docker port ps-gate-lobby 25565/tcp | head -1 | sed 's/.*://')
echo "--- P02 direct join to the backend port"
"$JAVA" -cp "$SPIKE_CP" "$(hostpath "$ROOT/scripts/spike/ProxyJoin.java")" 127.0.0.1 "$LG" 1 "$(hostpath "$OUT")/direct" lobby survival \
  | tee "$OUT/direct.txt" | head -5
for c in "$PROXY" "${BACKENDS[@]/#/ps-gate-}"; do docker logs "$c" >"$OUT/$c.log" 2>&1; done
echo "--- P02 addresses and P04 reply"
grep -h "logged in with entity id" "$OUT"/ps-gate-lobby.log "$OUT"/ps-gate-survival.log || true
grep -h "fixture BungeeCord reply" "$OUT"/ps-gate-lobby.log || echo "no fixture reply in lobby log"
echo "--- gate log"
cat "$OUT/$PROXY.log"
echo "output: $OUT"
