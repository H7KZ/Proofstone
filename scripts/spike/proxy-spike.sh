#!/usr/bin/env bash
# SPIKE (T-M7-01). Not production code. Boots one proxy and two paper@26.3 backends on one Docker network, joins with
# the mcpl driver through the proxy, switches backend with the client's `/server <name>`, and keeps every log.
#
# Usage (Git Bash on Windows needs MSYS_NO_PATHCONV=1 so /data and /server are not rewritten):
#   PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh bash scripts/spike/proxy-spike.sh <mode> [joins]
# Modes: velocity-modern, velocity-legacy, bungeecord-legacy.
#
# Inputs are copied into the containers (docker cp), never bind-mounted (AGENTS.md section 3). The forwarding secret is
# generated per run and written only to the run's output directory under build/ (not committed).
# The pins below are spike pins, recorded in docs/platforms/velocity.md and bungeecord.md; versions.yml is not changed
# by this card.
set -euo pipefail

MODE=${1:?mode: velocity-modern | velocity-legacy | bungeecord-legacy}
JOINS=${2:-5}
if [ "${PROOFSTONE_ACCEPT_EULA:-}" != "true" ]; then
  echo "Minecraft EULA not accepted: set PROOFSTONE_ACCEPT_EULA=true for this command" >&2
  exit 2
fi

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=${SPIKE_OUT:-$ROOT/build/spike/$MODE-$(date +%Y%m%dT%H%M%S)}
mkdir -p "$OUT/stage/paper" "$OUT/stage/proxy"

# paper@26.3 exactly as versions.yml pins it (java25 digest, build 244).
PAPER_IMAGE=itzg/minecraft-server@sha256:e2fabc536673f8c6b343055c93f7a73e10caa79cd3368f645987593c65adf1e0
PAPER_SHA=9601f9a26a2a52a0f2c807cf1abf691f96ee4fb287878313b601cebc2c51eabf
# itzg/mc-proxy release 2026.11.1. Velocity 4.3.1 is Java 25 bytecode (class file 69), so it needs the java25 tag.
VELOCITY_IMAGE=itzg/mc-proxy@sha256:8efdaa9d990f4c509f21b7ced003dd959060777784422fd72fdd537d613c1813  # java25-2026.11.1
BUNGEE_IMAGE=itzg/mc-proxy@sha256:4e6901b4640a913ef6d8470de887b89ff6d19702e410edfbccdbdbce4b54159f    # java21-2026.11.1
VELOCITY_VERSION=4.3.1
VELOCITY_BUILD=45
VELOCITY_SHA=0844bcabdcdb4f5b875906fd4c3365bc1bb76918e42bc595ed88ad195258dd75
BUNGEE_BUILD=2111
BUNGEE_SHA=1d9dbc31b57b22a37553f160ac1b61762717e9e4a6a1341f3dd27a1f6f2671fd

NET=ps-spike-net
PROXY=ps-spike-proxy
BACKENDS=(lobby survival)

hostpath() { cygpath -m "$1" 2>/dev/null || echo "$1"; }

cleanup() {
  for c in "$PROXY" "${BACKENDS[@]/#/ps-spike-}"; do
    docker logs "$c" >"$OUT/$c.log" 2>&1 || true
    docker rm -f "$c" >/dev/null 2>&1 || true
  done
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Waits for a log line, bounded. This is the await-with-timeout shape (AGENTS.md section 3), not a fixed delay.
await_log() {
  local container=$1 pattern=$2 seconds=$3
  # A stopped container ends the wait at once (its log will never match).
  if ! timeout "$seconds" sh -c "until docker logs $container 2>&1 | grep -q -E '$pattern'; do
      [ \"\$(docker inspect -f '{{.State.Running}}' $container)\" = true ] || exit 1; sleep 1; done"; then
    echo "TIMEOUT: $container did not log /$pattern/ within ${seconds}s" >&2
    docker logs "$container" 2>&1 | tail -40 >&2
    exit 1
  fi
}

SECRET=$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')

# Backend side.
printf 'settings:\n  connection-throttle: -1\n' >"$OUT/stage/paper/bukkit.yml"
case "$MODE" in
  velocity-modern)
    # Into /config, which the image syncs to /data/config as the server user. A root-owned /data/config made by
    # docker cp stops Paper writing paper-world-defaults.yml, and the server crashes at world load (spike run 1).
    mkdir -p "$OUT/stage/paperconfig"
    printf 'proxies:\n  velocity:\n    enabled: true\n    online-mode: false\n    secret: %s\n' "$SECRET" \
      >"$OUT/stage/paperconfig/paper-global.yml"
    FORWARDING=modern ;;
  velocity-legacy|bungeecord-legacy)
    printf 'settings:\n  bungeecord: true\n' >"$OUT/stage/paper/spigot.yml"
    FORWARDING=legacy ;;
  *) echo "unknown mode $MODE" >&2; exit 2 ;;
esac

# Proxy side.
case "$MODE" in
  velocity-*)
    IMAGE=$VELOCITY_IMAGE
    PROXY_ENV=(-e TYPE=VELOCITY -e VELOCITY_VERSION=$VELOCITY_VERSION -e VELOCITY_BUILD_ID=$VELOCITY_BUILD)
    PROXY_SHA=$VELOCITY_SHA
    READY='Done \('
    printf '%s' "$SECRET" >"$OUT/stage/proxy/forwarding.secret"
    mkdir -p "$OUT/stage/proxy/plugins/bStats"
    printf 'enabled=false\n' >"$OUT/stage/proxy/plugins/bStats/config.txt"
    cat >"$OUT/stage/proxy/velocity.toml" <<EOF
config-version = "2.9"
bind = "0.0.0.0:25577"
motd = "proofstone spike"
show-max-players = 500
online-mode = false
force-key-authentication = false
prevent-client-proxy-connections = false
player-info-forwarding-mode = "$FORWARDING"
forwarding-secret-file = "forwarding.secret"
announce-forge = false
kick-existing-players = false

[servers]
lobby = "lobby:25565"
survival = "survival:25565"
try = ["lobby"]

[forced-hosts]

[advanced]
login-ratelimit = 0
log-player-connections = true
${SPIKE_VELOCITY_ADVANCED:-}
EOF
    ;;
  bungeecord-*)
    IMAGE=$BUNGEE_IMAGE
    PROXY_ENV=(-e TYPE=BUNGEECORD -e BUNGEE_JOB_ID=$BUNGEE_BUILD)
    PROXY_SHA=$BUNGEE_SHA
    READY='Listening on'
    cat >"$OUT/stage/proxy/config.yml" <<EOF
online_mode: false
ip_forward: true
enforce_secure_profile: false
connection_throttle: -1
connection_throttle_limit: 3
server_connect_timeout: 5000
timeout: 30000
servers:
  lobby:
    motd: lobby
    address: lobby:25565
    restricted: false
  survival:
    motd: survival
    address: survival:25565
    restricted: false
permissions:
  default:
  - bungeecord.command.server
  - bungeecord.command.list
listeners:
- host: 0.0.0.0:25577
  query_port: 25577
  query_enabled: false
  motd: proofstone spike
  max_players: 20
  tab_list: GLOBAL_PING
  tab_size: 60
  forced_hosts: {}
  ping_passthrough: false
  priorities:
  - lobby
  bind_local_address: true
  force_default_server: false
  proxy_protocol: false
EOF
    ;;
esac

docker network create "$NET" >/dev/null
for b in "${BACKENDS[@]}"; do
  docker create --name "ps-spike-$b" --network "$NET" --network-alias "$b" \
    -e EULA=TRUE -e TYPE=PAPER -e VERSION=26.3 -e PAPER_BUILD=244 -e ONLINE_MODE=FALSE \
    -e ENFORCE_SECURE_PROFILE=FALSE -e LEVEL_TYPE=minecraft:flat -e DIFFICULTY=peaceful -e VIEW_DISTANCE=4 \
    -e ENABLE_RCON=false -e MOTD="$b" "$PAPER_IMAGE" >/dev/null
  docker cp "$(hostpath "$OUT/stage/paper")/." "ps-spike-$b:/data/"
  if [ -d "$OUT/stage/paperconfig" ]; then
    docker cp "$(hostpath "$OUT/stage/paperconfig")/." "ps-spike-$b:/config/"
  fi
  docker start "ps-spike-$b" >/dev/null
done
for b in "${BACKENDS[@]}"; do await_log "ps-spike-$b" 'Done \(' 240; done
for b in "${BACKENDS[@]}"; do
  echo "backend $b jar: $(docker exec "ps-spike-$b" sh -c 'sha256sum /data/paper-*.jar')"
  docker exec "ps-spike-$b" sh -c 'sha256sum /data/paper-*.jar' | grep -q "$PAPER_SHA" || { echo "paper sha256 mismatch" >&2; exit 1; }
done

docker create --name "$PROXY" --network "$NET" -p 127.0.0.1::25577 "${PROXY_ENV[@]}" \
  -e ENABLE_RCON=false -e SKIP_DOWNLOAD_DEFAULTS=true "$IMAGE" >/dev/null
docker cp "$(hostpath "$OUT/stage/proxy")/." "$PROXY:/server/"
docker start "$PROXY" >/dev/null
await_log "$PROXY" "$READY" 120
SUM=$(docker exec "$PROXY" sh -c 'sha256sum /server/*.jar')
echo "proxy jar: $SUM"
echo "$SUM" | grep -q "$PROXY_SHA" || { echo "proxy sha256 mismatch" >&2; exit 1; }
PORT=$(docker port "$PROXY" 25577/tcp | head -1 | sed 's/.*://')
echo "proxy $MODE on 127.0.0.1:$PORT, output $OUT"

if [ -z "${SPIKE_CP:-}" ]; then
  SPIKE_CP=$(cd "$ROOT" && ./gradlew -q --init-script scripts/spike/classpath.init.gradle :drivers:mcpl:spikeClasspath \
    --max-workers=1 | sed -n 's/^SPIKE_CP=//p')
fi
JAVA=${SPIKE_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}
"$JAVA" -cp "$SPIKE_CP" "$(hostpath "$ROOT/scripts/spike/ProxyJoin.java")" 127.0.0.1 "$PORT" "$JOINS" "$(hostpath "$OUT")" lobby survival \
  | tee "$OUT/client.txt"

for c in "$PROXY" "${BACKENDS[@]/#/ps-spike-}"; do docker logs "$c" >"$OUT/$c.log" 2>&1; done
for b in "${BACKENDS[@]}"; do
  echo "backend $b: login lines $(grep -c 'UUID of player' "$OUT/ps-spike-$b.log" || true), joined lines $(grep -c 'joined the game' "$OUT/ps-spike-$b.log" || true)"
done
echo "output: $OUT"
