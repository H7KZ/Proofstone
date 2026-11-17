#!/usr/bin/env bash
# SPIKE (T-M9-01). Not production code. Boots paper@26.3 with Geyser and Floodgate under Docker, joins it with an offline
# Bedrock client (CloudburstMC Protocol or Node bedrock-protocol), and keeps every log.
#
# Usage (Git Bash on Windows needs MSYS_NO_PATHCONV=1):
#   PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh \
#     bash scripts/spike/geyser/geyser-spike.sh <offline|floodgate> <cloudburst|node|none> <joins> <out dir>
# Environment knobs:
#   CACHE              dir holding the pinned downloads (see fetch.sh); default build/spike/geyser-cache (never committed)
#   VALIDATE           Geyser advanced.bedrock.validate-bedrock-login, default true
#   CLIENT_PLACE       host (default; UDP published on 127.0.0.1) | cell (Java client in a container on the cell network)
#   JOIN_TIMEOUT       seconds a client waits for the spawn status, default 30
#   STUB               1 = the "stub route": HostLog answers the two Microsoft discovery names with its own address and serves
#                      invented discovery, OpenID and JWKS documents over TLS; the server JVM trusts a copy of its default
#                      trust store plus the stub certificate (JAVA_TOOL_OPTIONS). Default 0.
#   WARM_OFFLINE       1 = before the restart (the "warm start") HostLog answers NXDOMAIN for every name except the stub's and
#                      papermc.io (the itzg image re-resolves the Paper build at every start and stops without it), so Geyser and
#                      Floodgate start as on a machine with no other network. Default 0.
#   FLOODGATE_KEY      a key.pem to copy to /plugins/floodgate/ before the first start (pinning test). Default: Floodgate makes its own.
#   CLIENT_NETLOG      1 = log what the client process itself contacts: the Java client runs with -Djavax.net.debug=ssl:handshake (every TLS
#                      server_name), the Node client with --require net-log.js (every lookup, connect, send). Default 0.
#   EGRESS             closed (default: the server's default route is removed before the joins) | open (diagnosis only: a
#                      red under closed that is green under open names a host the join needs)
#
# What it does: (1) cold start with Geyser's and Floodgate's default config (the "fetch at start" evidence), (2) edits the
# generated Geyser config (transport both, STUN off, auth-type, validate-bedrock-login) and restarts the container,
# (3) removes the container's default route, so nothing outside the cell network can be reached from the server while
# joining, (4) runs the joins, each with a hook that reads the Bukkit agent's players, (5) collects logs. Every
# host name the server looks up is written to dns.log by HostLog, a DNS logging forwarder that is the container's only
# resolver. Inputs are copied in, never bind-mounted (AGENTS.md section 3). EULA is read from the command environment only.
set -euo pipefail
AUTH=${1:?offline | floodgate}
DRIVER=${2:?cloudburst | node | none}
JOINS=${3:?number of joins}
OUT=${4:?out dir}
if [ "${PROOFSTONE_ACCEPT_EULA:-}" != "true" ]; then echo "set PROOFSTONE_ACCEPT_EULA=true for this command" >&2; exit 2; fi
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
HERE=$ROOT/scripts/spike/geyser
CACHE=${CACHE:-$ROOT/build/spike/geyser-cache}
VALIDATE=${VALIDATE:-true}
CLIENT_PLACE=${CLIENT_PLACE:-host}
JOIN_TIMEOUT=${JOIN_TIMEOUT:-30}
EGRESS=${EGRESS:-closed}
STUB=${STUB:-0}
# paper@26.3 exactly as versions.yml pins it (java25 digest, build 244).
IMAGE=itzg/minecraft-server@sha256:e2fabc536673f8c6b343055c93f7a73e10caa79cd3368f645987593c65adf1e0
NET=ps-gy-net; HL=ps-gy-hostlog; SRV=ps-gy-srv; CLI=ps-gy-client
HLIP=172.29.77.2
TOKEN=tgy
export PS_BASH; PS_BASH=$(cygpath -m "$(command -v bash)" 2>/dev/null || command -v bash)
hostpath() { cygpath -m "$1" 2>/dev/null || echo "$1"; }
now() { date -u +%Y-%m-%dT%H:%M:%S.%NZ; }
mark() { echo "$(now) $*" | tee -a "$OUT/markers.log"; }
mkdir -p "$OUT/stage/plugins" "$OUT/joins"
: >"$OUT/markers.log"

for f in Geyser-Spigot-2.12.1-b1303.jar floodgate-spigot-2.2.5-b141.jar; do [ -f "$CACHE/$f" ] || { echo "missing $CACHE/$f (run fetch.sh)" >&2; exit 2; }; done
[ "$(sha256sum "$CACHE/Geyser-Spigot-2.12.1-b1303.jar" | cut -d' ' -f1)" = 218f6e1a09a2fe458cfd531e7295a0f19d17b00db38ceec16222e8982a4d7f21 ] || { echo "geyser sha256 mismatch" >&2; exit 2; }
[ "$(sha256sum "$CACHE/floodgate-spigot-2.2.5-b141.jar" | cut -d' ' -f1)" = 21570aff9ce17d6983928e8552777760e1ede5050026b04c686b0ae112e6fd7e ] || { echo "floodgate sha256 mismatch" >&2; exit 2; }
cp "$CACHE"/Geyser-Spigot-2.12.1-b1303.jar "$OUT/stage/plugins/"
# Floodgate is installed on the floodgate route only: with it present Geyser hands every login to Floodgate whatever auth-type says.
if [ "$AUTH" = floodgate ]; then cp "$CACHE"/floodgate-spigot-2.2.5-b141.jar "$OUT/stage/plugins/"; fi
cp "$ROOT/platforms/bukkit/agent/build/libs/agent-all.jar" "$OUT/stage/plugins/proofstone-agent.jar"
mkdir -p "$OUT/stage/hl"; javac --release 21 -d "$(hostpath "$OUT/stage/hl")" "$(hostpath "$HERE/HostLog.java")"

cleanup() {
  docker logs "$SRV" >"$OUT/server.log" 2>&1 || true
  docker cp "$HL:/hl/dns.log" "$(hostpath "$OUT")/dns.log" >/dev/null 2>&1 || true
  docker rm -f "$SRV" "$HL" "$CLI" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT
docker rm -f "$SRV" "$HL" "$CLI" >/dev/null 2>&1 || true
docker network rm "$NET" >/dev/null 2>&1 || true

# Bounded wait for a log line (the await-with-timeout shape, AGENTS.md section 3); a stopped container ends it at once.
await_log() { # container pattern seconds
  if ! timeout "$3" sh -c "until docker logs $1 2>&1 | grep -q -E '$2'; do
      [ \"\$(docker inspect -f '{{.State.Running}}' $1)\" = true ] || exit 1; sleep 0.5; done"; then
    echo "TIMEOUT: $1 did not log /$2/ within ${3}s" >&2; docker logs "$1" 2>&1 | tail -30 >&2; exit 1
  fi
}
count_log() { docker logs "$SRV" 2>&1 | grep -c -E "$1" || true; }

docker network create --subnet 172.29.77.0/24 "$NET" >/dev/null
HLARGS=(/hl/dns.log)
SRVENV=()
if [ "$STUB" = 1 ]; then
  KT=${JAVA_HOME:+$JAVA_HOME/bin/}keytool
  S=$(hostpath "$OUT/stage/hl")
  "$KT" -genkeypair -alias stub -keyalg EC -groupname secp256r1 -dname CN=proofstone-spike-stub -validity 30     -ext san=dns:client.discovery.minecraft-services.net,dns:authorization.franchise.minecraft-services.net     -keystore "$S/stub.p12" -storetype PKCS12 -storepass changeit -keypass changeit >/dev/null 2>&1
  "$KT" -exportcert -alias stub -keystore "$S/stub.p12" -storepass changeit -rfc -file "$S/stub.crt" >/dev/null 2>&1
  HLARGS=(/hl/dns.log /hl/stub.p12 $HLIP)
fi
docker create --name "$HL" --network "$NET" --ip $HLIP --entrypoint java "$IMAGE" -cp /hl HostLog "${HLARGS[@]}" >/dev/null
docker cp "$(hostpath "$OUT/stage/hl")" "$HL:/hl"
if [ "$STUB" = 1 ]; then
  # The server's own trust store (taken from the pinned image) plus the stub certificate; copied in, not mounted.
  mkdir -p "$OUT/stage/stub"
  docker cp "$HL:/opt/java/openjdk/lib/security/cacerts" "$(hostpath "$OUT/stage/stub")/cacerts"
  "$KT" -importcert -noprompt -alias proofstone-spike-stub -file "$S/stub.crt" -keystore "$(hostpath "$OUT/stage/stub")/cacerts" -storepass changeit >/dev/null 2>&1
  SRVENV=(-e "JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/stub/cacerts -Djavax.net.ssl.trustStorePassword=changeit")
fi
docker start "$HL" >/dev/null
docker create --name "$SRV" --network "$NET" --dns $HLIP --cap-add NET_ADMIN -p 127.0.0.1::25565 -p 127.0.0.1::19132/udp -p 127.0.0.1::19133/udp \
  -e EULA=TRUE -e TYPE=PAPER -e VERSION=26.3 -e PAPER_BUILD=244 -e ONLINE_MODE=FALSE -e ENFORCE_SECURE_PROFILE=FALSE \
  -e LEVEL_TYPE=minecraft:flat -e DIFFICULTY=peaceful -e VIEW_DISTANCE=4 -e ENABLE_RCON=false -e SKIP_DOWNLOAD_DEFAULTS=true \
  -e SPAWN_PROTECTION=0 -e PROOFSTONE_AGENT_TOKEN=$TOKEN -e PROOFSTONE_AGENT_PORT=25580 "${SRVENV[@]}" "$IMAGE" >/dev/null
if [ "$AUTH" = floodgate ] && [ -n "${FLOODGATE_KEY:-}" ]; then
  mkdir -p "$OUT/stage/plugins/floodgate"; cp "$FLOODGATE_KEY" "$OUT/stage/plugins/floodgate/key.pem"
fi
docker cp "$(hostpath "$OUT/stage/plugins")" "$SRV:/plugins"
if [ "$STUB" = 1 ]; then docker cp "$(hostpath "$OUT/stage/stub")" "$SRV:/stub"; fi

mark "PHASE cold-start begin"
docker start "$SRV" >/dev/null
await_log "$SRV" 'Done \(.*For help' 300
await_log "$SRV" 'Geyser-Spigot\] Done' 60
mark "PHASE cold-start done"
docker exec "$SRV" sh -c 'sha256sum /data/paper-*.jar; ls /data/plugins' >"$OUT/paper-jar.txt"
docker exec "$SRV" sh -c 'cat /data/plugins/Geyser-Spigot/config.yml' >"$OUT/geyser-config.default.yml"
docker exec "$SRV" sh -c 'cat /data/plugins/floodgate/config.yml' >"$OUT/floodgate-config.default.yml" 2>/dev/null || true
docker exec "$SRV" sh -c 'sha256sum /data/plugins/floodgate/key.pem; wc -c /data/plugins/floodgate/key.pem' >"$OUT/floodgate-key.txt" 2>/dev/null || true
docker cp "$SRV:/data/plugins/floodgate/key.pem" "$(hostpath "$OUT")/floodgate-key.pem" >/dev/null 2>&1 || true
docker exec "$SRV" sh -c 'ls -laR /data/plugins/Geyser-Spigot | head -60' >"$OUT/geyser-files.txt"
docker logs "$SRV" >"$OUT/server.coldstart.log" 2>&1

# Config edits as the server user (uid 1000), in place.
mark "PHASE config edit auth=$AUTH validate=$VALIDATE"
docker exec -u 1000 "$SRV" sh -c "
  f=/data/plugins/Geyser-Spigot/config.yml
  sed -i -e 's/^  transport: nethernet/  transport: both/' \
         -e 's/^  webrtc-port: 0/  webrtc-port: 19133/' \
         -e 's/^    stun-discovery: true/    stun-discovery: false/' \
         -e 's/^  auth-type: online/  auth-type: $AUTH/' \
         -e 's/^    validate-bedrock-login: true/    validate-bedrock-login: $VALIDATE/' \$f
  sed -i 's/^  connection-throttle: 4000/  connection-throttle: -1/' /data/bukkit.yml
  grep -n 'connection-throttle' /data/bukkit.yml
  grep -n -E '^  transport:|^  webrtc-port:|stun-discovery:|^  auth-type:|validate-bedrock-login:' \$f"  | tee "$OUT/geyser-config.edits.txt"
if [ "${WARM_OFFLINE:-0}" = 1 ]; then docker exec "$HL" sh -c 'echo papermc.io > /hl/block'; mark "PHASE dns blocked for the warm start (papermc.io allowed: the image re-resolves the Paper build at every start)"; fi
mark "PHASE restart begin"
docker restart -t 30 "$SRV" >/dev/null
# After a restart the log holds both boots: wait for the second "Done (" of Paper and of Geyser.
timeout 300 sh -c "until [ \$(docker logs $SRV 2>&1 | grep -c -E 'Geyser-Spigot\] Done') -ge 2 ]; do sleep 0.5; done" || { echo "TIMEOUT: second Geyser start" >&2; docker logs "$SRV" 2>&1 | tail -30 >&2; exit 1; }
mark "PHASE restart done (warm start)"
docker exec "$SRV" sh -c 'cat /data/plugins/Geyser-Spigot/config.yml' >"$OUT/geyser-config.used.yml"

# Close the way out. After this the server can reach only the cell network (the DNS forwarder and the docker gateway).
docker exec "$SRV" sh -c 'ip route' >"$OUT/route.before.txt"
if [ "$EGRESS" = closed ]; then docker exec -u 0 "$SRV" sh -c 'ip route del default'; fi
docker exec "$SRV" sh -c 'ip route' >"$OUT/route.after.txt"
docker exec "$SRV" sh -c 'curl -s -m 5 -o /dev/null -w "%{http_code}" https://example.com || echo "no-route(rc=$?)"' >"$OUT/egress-check.txt" || true
mark "PHASE egress $EGRESS: $(cat "$OUT/egress-check.txt")"
docker exec "$SRV" sh -c 'ss -lunp 2>/dev/null; ss -ltnp 2>/dev/null' >"$OUT/listeners.txt" || true
docker port "$SRV" >"$OUT/ports.txt"
UDP19132=$(docker port "$SRV" 19132/udp | head -1 | sed 's/.*://')
TCP25565=$(docker port "$SRV" 25565/tcp | head -1 | sed 's/.*://')
echo "published udp 19132 -> 127.0.0.1:$UDP19132, tcp 25565 -> 127.0.0.1:$TCP25565" | tee -a "$OUT/markers.log"

# The hook the client runs once it spawned: wait (bounded) for the agent to list the player, then print the agent view and the
# server log lines about the login.
hook() {
  local name=$1
  echo "bash $(hostpath "$HERE/agent-read.sh") $SRV $name $TOKEN"
}

if [ -n "${HOLD:-}" ]; then
  # Hand-debugging only: keep the cell up until $OUT/release exists (bounded: 20 minutes).
  echo "HOLD: cell up, udp $UDP19132 tcp $TCP25565; touch $OUT/release to continue"
  timeout 1200 sh -c "until [ -f '$OUT/release' ]; do sleep 2; done" || true
fi
if [ "$DRIVER" != none ]; then
  JAVA=${SPIKE_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}
  CP=$(paste -sd';' "$CACHE/cb/cp.u.txt");CP="$CP;$(hostpath "$CACHE/jb")"
  ok=0; bad=0
  for i in $(seq 1 "$JOINS"); do
    name="PsBed$i"
    # A claimed, unsigned XUID, unique per join: Geyser refuses a second login with an XUID that is still online.
    export PS_XUID=$((2535400000000000 + i))
    log="$OUT/joins/join-$i.log"
    mark "JOIN $i begin driver=$DRIVER auth=$AUTH name=$name"
    set +e
    case "$DRIVER" in
      cloudburst)
        if [ "$CLIENT_PLACE" = cell ]; then
          # A Java client in a container of the same pinned image on the cell network (no published port, no host network).
          docker rm -f "$CLI" >/dev/null 2>&1 || true
          # Its DNS is HostLog and (with STUB=1) its JVM trusts the stub, so the Cloudburst client's own start-up fetch of the
          # discovery documents (EncryptionUtils static initialiser) is answered by the stub too; its default route is removed
          # before the JVM starts, so nothing outside the cell network is reachable from the client either.
          docker create --name "$CLI" --network "$NET" --network-alias client --dns $HLIP --cap-add NET_ADMIN "${SRVENV[@]}" --entrypoint sh "$IMAGE"             -c "ip route del default; exec java -cp '/cp/*:/jb' BedrockJoin $SRV 19132 $name $JOIN_TIMEOUT" >/dev/null
          mkdir -p "$OUT/stage/cp"; while read -r j; do cp "$j" "$OUT/stage/cp/"; done <"$CACHE/cb/cp.u.txt"
          docker cp "$(hostpath "$OUT/stage/cp")" "$CLI:/cp"; docker cp "$(hostpath "$CACHE/jb")" "$CLI:/jb"
          if [ "$STUB" = 1 ]; then docker cp "$(hostpath "$OUT/stage/stub")" "$CLI:/stub"; fi
          docker start -a "$CLI" >"$log" 2>&1
        else
          "$JAVA" ${CLIENT_NETLOG:+-Djavax.net.debug=ssl:handshake} -cp "$CP" BedrockJoin 127.0.0.1 "$UDP19132" "$name" "$JOIN_TIMEOUT" "$(hook "$name")" >"$log" 2>&1
        fi ;;
      node)
        (cd "$CACHE/node" && NODE_PATH="$(hostpath "$CACHE/node/node_modules")" node ${CLIENT_NETLOG:+--require "$(hostpath "$HERE/net-log.js")"} "$(hostpath "$HERE/bedrock-join.js")" 127.0.0.1 "$UDP19132" "$name" "$JOIN_TIMEOUT" "$(hook "$name")") >"$log" 2>&1 ;;
    esac
    rc=$?
    set -e
    # Bounded wait until the agent lists nobody before the next join (the server notices the disconnect a moment later).
    for _ in $(seq 1 40); do
      case "$(docker exec "$SRV" curl -s -m 3 -H "Authorization: Bearer $TOKEN" localhost:25580/v1/players || true)" in *'"players":[]'*) break;; esac
      sleep 0.5
    done
    res=$(grep -E 'RESULT' "$log" | tail -1 || true)
    mark "JOIN $i end rc=$rc ${res:-no RESULT line}"
    if [ $rc -eq 0 ]; then ok=$((ok+1)); else bad=$((bad+1)); fi
  done
  mark "JOINS driver=$DRIVER auth=$AUTH total=$JOINS spawned=$ok not-spawned=$bad"
fi

if [ -n "${JAVA_JOIN:-}" ]; then
  # Java client (mcpl) on the Java port under the given names, for the "plain or prefixed Java login" question.
  SPIKE_CP=$(cd "$ROOT" && ./gradlew -q --init-script scripts/spike/classpath.init.gradle :drivers:mcpl:spikeClasspath --max-workers=1 | sed -n 's/^SPIKE_CP=//p')
  mark "JAVA JOIN begin names=$JAVA_JOIN"
  # shellcheck disable=SC2086
  HOOK="$(echo "bash $(hostpath "$HERE/agent-read.sh") $SRV {name} $TOKEN")" "${SPIKE_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}" -cp "$SPIKE_CP" "$(hostpath "$HERE/JavaJoin.java")" 127.0.0.1 "$TCP25565" $JAVA_JOIN >"$OUT/joins/java-join.log" 2>&1 || true
  mark "JAVA JOIN end: $(grep -E 'JAVA SUMMARY' "$OUT/joins/java-join.log" || echo no-summary)"
fi
docker logs "$SRV" >"$OUT/server.log" 2>&1
docker exec "$SRV" sh -c 'curl -s -H "Authorization: Bearer '$TOKEN'" localhost:25580/v1/players' >"$OUT/players.after.txt" || true
echo "output: $OUT"
