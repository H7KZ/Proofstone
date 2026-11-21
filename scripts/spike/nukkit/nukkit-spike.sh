#!/usr/bin/env bash
# SPIKE (T-M10-13). Not production code. Boots PowerNukkitX (a Java Bedrock server of the Nukkit family) on the pinned java25 image
# with a throwaway Java plugin agent (scripts/spike/nukkit/agent, health and players only), reads the agent over the cell network,
# and joins the server with the M9 Bedrock driver host (drivers/bedrock) using an unsigned chain. Every host name any container looks
# up is logged by the T-M9-01 stand-in (scripts/spike/geyser/HostLog.java), the only DNS server of every container.
#
# Usage (Git Bash on Windows needs MSYS_NO_PATHCONV=1):
#   PNX_VERSION=3.0.6 PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 \
#     bash scripts/dockerlock.sh bash scripts/spike/nukkit/nukkit-spike.sh build/spike/nukkit/<run>
# Results and the run log: docs/platforms/nukkit.md.
# Knobs (environment):
#   PNX_VERSION   3.0.6 (default) | 3.0.5. The jar is /c/pw/nukkit-cache/<version>/powernukkitx.jar, downloaded by hand from the
#                 GitHub release (docs/platforms/nukkit.md) and checked here against the sha256 below. Never committed.
#   XBOX_AUTH     false (default) | true: pnx.yml settings.xboxAuth for the second start
#   EGRESS        closed (default: the server and the driver host lose their default route before they start) | open
#   JOIN          1 (default) = join with the driver host; 0 = skip the join
#   DRIVER_JAR    default drivers/bedrock/build/libs/bedrock-host-all.jar (./gradlew :drivers:bedrock:shadowJar)
#   DRIVER_PROTOCOL  unset (default: the driver's own 2225) | a number: the driver's existing test-only switch (T-M10-05,
#                 ProtocolOverride) sends that number in RequestNetworkSettings while every packet stays the 2225 codec. A probe
#                 of the login route on PNX 3.0.5 (2193), never a working join.
# PNX asks for an LGPL-3.0 licence acceptance at its first start; PNX_SETUP_NON_INTERACTIVE=1 and --skip-setup continue with the
# defaults. The script still refuses to run without PROOFSTONE_ACCEPT_EULA=true in the command environment (AGENTS.md section 4).
set -euo pipefail
OUT=${1:?out dir}
if [ "${PROOFSTONE_ACCEPT_EULA:-}" != "true" ]; then echo "set PROOFSTONE_ACCEPT_EULA=true for this command" >&2; exit 2; fi
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
HERE=$ROOT/scripts/spike/nukkit
PNX_VERSION=${PNX_VERSION:-3.0.6}
case "$PNX_VERSION" in
  3.0.6) PNX_SHA256=10822d818c1f2c7d423092a235f3f04bd0eb915b2298b204ed1697d7a0dd9539 ;;
  3.0.5) PNX_SHA256=bc3c54236256c8e3bf8573ecf090e3853785c7622891d7d28d780a398fc1de60 ;;
  *) echo "unknown PNX_VERSION $PNX_VERSION" >&2; exit 2 ;;
esac
PNX_JAR=${PNX_JAR:-/c/pw/nukkit-cache/$PNX_VERSION/powernukkitx.jar}
XBOX_AUTH=${XBOX_AUTH:-false}; EGRESS=${EGRESS:-closed}; JOIN=${JOIN:-1}
DRIVER_JAR=${DRIVER_JAR:-$ROOT/drivers/bedrock/build/libs/bedrock-host-all.jar}
JDK=${JDK:-/c/Users/honzi/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2}
# The pinned java25 digest of itzg/minecraft-server (versions.yml), used for every container: no new image.
IMAGE=itzg/minecraft-server@sha256:e2fabc536673f8c6b343055c93f7a73e10caa79cd3368f645987593c65adf1e0
NET=ps-pnx-net; HL=ps-pnx-hostlog; SRV=ps-pnx-srv; DRV=ps-pnx-driver
HLIP=172.29.79.2
AGENT_TOKEN=$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')
DRV_TOKEN=$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')
hostpath() { cygpath -m "$1" 2>/dev/null || echo "$1"; }
now() { date -u +%Y-%m-%dT%H:%M:%S.%3NZ; }
mark() { echo "$(now) $*" | tee -a "$OUT/markers.log"; }
mkdir -p "$OUT/stage"
: >"$OUT/markers.log"

[ "$(sha256sum "$PNX_JAR" | cut -d' ' -f1)" = "$PNX_SHA256" ] || { echo "PNX jar sha256 mismatch: $PNX_JAR" >&2; exit 2; }
[ -f "$DRIVER_JAR" ] || { echo "missing $DRIVER_JAR (./gradlew :drivers:bedrock:shadowJar)" >&2; exit 2; }
DRV_OPTS="-Djavax.net.ssl.trustStore=/trust/cacerts -Djavax.net.ssl.trustStorePassword=changeit"
if [ -n "${DRIVER_PROTOCOL:-}" ]; then
  DRV_OPTS="$DRV_OPTS -Dproofstone.injectGeyserFault=refused-protocol -Dproofstone.bedrock.protocolOverride=$DRIVER_PROTOCOL"
fi
mark "PARAMS pnx=$PNX_VERSION xboxAuth=$XBOX_AUTH egress=$EGRESS join=$JOIN driverProtocol=${DRIVER_PROTOCOL:-codec}"

S=$OUT/stage
mkdir -p "$S/in/plugins" "$S/agent" "$S/hl" "$S/trust" "$S/drv"
# The throwaway agent, compiled against the pinned server jar itself (nothing else on the class path) and packed as a plugin jar.
"$JDK/bin/javac" --release 21 -nowarn -cp "$(hostpath "$PNX_JAR")" -d "$(hostpath "$S/agent")" \
  "$(hostpath "$HERE/agent/proofstone/spike/nukkit/SpikeAgent.java")"
cp "$HERE/agent/plugin.yml" "$S/agent/plugin.yml"
"$JDK/bin/jar" --create --file "$(hostpath "$S/in/plugins")/proofstone-spike-agent.jar" -C "$(hostpath "$S/agent")" .
# Observe-only check: list every call in the agent that could write to the server. The expected result is "none".
grep -n -E 'dispatchCommand|setGamemode|teleport|kick|sendMessage|getInventory\(\)\.(set|add|clear)|setHealth|setCancelled|broadcast' \
  "$HERE/agent/proofstone/spike/nukkit/SpikeAgent.java" >"$OUT/observe-only.txt" || echo "none" >"$OUT/observe-only.txt"
mark "observe-only grep of write-capable calls: $(tr '\n' ' ' <"$OUT/observe-only.txt")"

"$JDK/bin/javac" --release 21 -d "$(hostpath "$S/hl")" "$(hostpath "$ROOT/scripts/spike/geyser/HostLog.java")"
KT=$JDK/bin/keytool
"$KT" -genkeypair -alias stub -keyalg EC -groupname secp256r1 -dname CN=proofstone-spike-stub -validity 30 \
  -ext san=dns:client.discovery.minecraft-services.net,dns:authorization.franchise.minecraft-services.net \
  -keystore "$(hostpath "$S/hl")/stub.p12" -storetype PKCS12 -storepass changeit -keypass changeit >/dev/null 2>&1
"$KT" -exportcert -alias stub -keystore "$(hostpath "$S/hl")/stub.p12" -storepass changeit -rfc -file "$(hostpath "$S/hl")/stub.crt" >/dev/null 2>&1
cp "$DRIVER_JAR" "$S/drv/bedrock-host-all.jar"

cleanup() {
  set +e
  for c in "$HL" "$SRV" "$DRV"; do echo "IP $c $(docker inspect -f "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}" "$c" 2>/dev/null)" >>"$OUT/markers.log"; done
  docker logs "$SRV" >"$OUT/pnx.log" 2>&1
  docker logs "$DRV" >"$OUT/driver.log" 2>&1
  docker cp "$HL:/hl/dns.log" "$(hostpath "$OUT")/dns.log" >/dev/null 2>&1
  docker rm -f "$SRV" "$HL" "$DRV" >/dev/null 2>&1
  docker network rm "$NET" >/dev/null 2>&1
  rm -f "$S/in/powernukkitx.jar"
  mark "cleanup done"
}
trap cleanup EXIT
docker rm -f "$SRV" "$HL" "$DRV" >/dev/null 2>&1 || true
docker network rm "$NET" >/dev/null 2>&1 || true

# Bounded wait for a log line (await with timeout, AGENTS.md section 3); a stopped container ends it at once.
await_log() { # container pattern seconds [min count]
  local n=${4:-1}
  if ! timeout "$3" sh -c "until [ \$(docker logs $1 2>&1 | grep -c -E '$2') -ge $n ]; do
      [ \"\$(docker inspect -f '{{.State.Running}}' $1)\" = true ] || exit 1; sleep 0.5; done"; then
    echo "TIMEOUT or exit: $1 did not log /$2/ (x$n) within ${3}s" >&2; docker logs "$1" 2>&1 | tail -60 >&2; return 1
  fi
}

docker network create --subnet 172.29.79.0/24 "$NET" >/dev/null
docker create --name "$HL" --network "$NET" --ip $HLIP --entrypoint java "$IMAGE" -cp /hl HostLog /hl/dns.log /hl/stub.p12 $HLIP >/dev/null
docker cp "$(hostpath "$S/hl")" "$HL:/hl"
docker start "$HL" >/dev/null
timeout 30 sh -c "until docker exec $HL grep -q 'dns up' /hl/dns.log 2>/dev/null; do sleep 0.5; done" || { echo 'TIMEOUT: HostLog did not write dns up' >&2; docker logs "$HL" >&2; exit 1; }
docker cp "$HL:/opt/java/openjdk/lib/security/cacerts" "$(hostpath "$S/trust")/cacerts"
"$KT" -importcert -noprompt -alias proofstone-spike-stub -file "$(hostpath "$S/hl")/stub.crt" -keystore "$(hostpath "$S/trust")/cacerts" -storepass changeit >/dev/null 2>&1

ROUTE="true"; CAPS=()
if [ "$EGRESS" = closed ]; then ROUTE="ip route del default"; CAPS=(--cap-add NET_ADMIN); fi
cp "$PNX_JAR" "$S/in/powernukkitx.jar"
# PNX runs from /srv; the jar, the agent plugin and nothing else are copied in (no bind mount). Sentry is off by property.
docker create -i --name "$SRV" --network "$NET" --network-alias pnx --dns $HLIP "${CAPS[@]}" \
  -e PNX_SETUP_NON_INTERACTIVE=1 -e "PROOFSTONE_AGENT_TOKEN=$AGENT_TOKEN" --entrypoint sh "$IMAGE" \
  -c "mkdir -p /srv && cd /srv && [ -f powernukkitx.jar ] || { cp /in/powernukkitx.jar . && rm /in/powernukkitx.jar && cp -r /in/plugins /srv/plugins; }; \
      $ROUTE; ip route > /tmp/route.txt; exec java -Xmx1G -DdisableSentry=true -jar powernukkitx.jar --skip-setup --language eng --disable-ansi" >/dev/null
docker cp "$(hostpath "$S/in")" "$SRV:/in"
rm -f "$S/in/powernukkitx.jar"

mark "PHASE first start (writes pnx.yml)"
T0=$(date +%s)
docker start "$SRV" >/dev/null
await_log "$SRV" 'Done \(' 240
mark "PHASE first start done in $(( $(date +%s) - T0 ))s"
docker logs "$SRV" >"$OUT/pnx.first.log" 2>&1
docker exec "$SRV" cat /tmp/route.txt >"$OUT/pnx-route.txt" 2>&1 || true
mark "PHASE stop for the pnx.yml edit"
docker stop -t 60 "$SRV" >/dev/null
docker logs "$SRV" 2>&1 | tail -5 >>"$OUT/markers.log"
docker cp "$SRV:/srv/pnx.yml" "$(hostpath "$S")/pnx.yml.orig"
cp "$S/pnx.yml.orig" "$OUT/pnx.default.yml"
grep -n -E 'xboxAuth|port:|ip:' "$S/pnx.yml.orig" | head -10 >>"$OUT/markers.log" || true
sed -E "s/^(\s*xboxAuth:).*/\1 $XBOX_AUTH/" "$S/pnx.yml.orig" >"$S/pnx.yml"
grep -q -E "^\s*xboxAuth: $XBOX_AUTH\s*$" "$S/pnx.yml" || { mark "RESULT pnx.yml has no xboxAuth key to set"; exit 1; }
cp "$S/pnx.yml" "$OUT/pnx.used.yml"
docker cp "$(hostpath "$S/pnx.yml")" "$SRV:/srv/pnx.yml"

mark "PHASE second start (xboxAuth $XBOX_AUTH)"
T0=$(date +%s)
docker start "$SRV" >/dev/null
await_log "$SRV" 'Done \(' 240 2
mark "PHASE second start done in $(( $(date +%s) - T0 ))s"
docker exec "$SRV" sh -c 'command -v ss >/dev/null && ss -lnut || cat /proc/net/udp /proc/net/tcp' >"$OUT/pnx-listeners.txt" 2>&1 || true
mark "LISTENERS (udp/tcp): $(tr '\n' '|' <"$OUT/pnx-listeners.txt" | cut -c1-1500)"

# --- the agent, read from another container on the cell network (the stand-in's container has curl: same image) ---
agent() { docker exec "$HL" curl -sS -m 15 -w ' http=%{http_code}' -H "Authorization: Bearer $AGENT_TOKEN" \
  -H "X-Proofstone-Request-Id: spike-$RANDOM" "$@"; }
mark "AGENT health: $(agent http://pnx:25580/v1/health || true)"
mark "AGENT players #1 (nobody online): $(agent http://pnx:25580/v1/players || true)"
mark "AGENT wrong token: $(docker exec "$HL" curl -sS -m 15 -w ' http=%{http_code}' -H 'Authorization: Bearer nope' http://pnx:25580/v1/players || true)"

# --- the M9 driver host joins with an unsigned chain ---
if [ "$JOIN" = 1 ]; then
  docker create --name "$DRV" --network "$NET" --network-alias driver --dns $HLIP "${CAPS[@]}" \
    -e "JAVA_TOOL_OPTIONS=$DRV_OPTS" \
    --entrypoint sh "$IMAGE" -c "$ROUTE; exec java -jar /driver/bedrock-host-all.jar $DRV_TOKEN" >/dev/null
  docker cp "$(hostpath "$S/drv")" "$DRV:/driver"
  docker cp "$(hostpath "$S/trust")" "$DRV:/trust"
  docker start "$DRV" >/dev/null
  await_log "$DRV" 'bedrock driver host ready' 60
  rpc() { docker exec "$DRV" curl -sS -m 60 -w ' http=%{http_code} curl_s=%{time_total}' -H "Authorization: Bearer $DRV_TOKEN" -H 'Content-Type: application/json' \
    -H "X-Proofstone-Request-Id: spike-$RANDOM" "$@"; }
  mark "DRIVER health: $(rpc http://127.0.0.1:25590/rpc/v1/health || true)"
  mark "JOIN begin name=PsPnx1"
  JR=$(rpc -X POST -d '{"host":"pnx","port":19132,"name":"PsPnx1","xuid":"2535400000000101","joinTimeoutMillis":30000}' \
    http://127.0.0.1:25590/rpc/v1/sessions || true)
  mark "JOIN result: $JR"
  mark "AGENT players #2 (after the join): $(agent http://pnx:25580/v1/players || true)"
  SID=$(echo "$JR" | sed -n 's/.*"sessionId":"\([^"]*\)".*/\1/p')
  if [ -n "$SID" ]; then
    mark "DRIVER position: $(rpc http://127.0.0.1:25590/rpc/v1/sessions/$SID/position || true)"
    mark "DRIVER inventory: $(rpc http://127.0.0.1:25590/rpc/v1/sessions/$SID/inventory | head -c 1500 || true)"
    mark "DRIVER transcript: $(rpc http://127.0.0.1:25590/rpc/v1/sessions/$SID/transcript | head -c 3000 || true)"
    mark "DRIVER disconnect: $(rpc -X DELETE http://127.0.0.1:25590/rpc/v1/sessions/$SID || true)"
  fi
fi
mark "DONE"
