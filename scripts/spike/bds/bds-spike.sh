#!/usr/bin/env bash
# SPIKE (T-M10-01). Not production code. Boots Bedrock Dedicated Server (BDS) with a behaviour pack that uses
# @minecraft/server-net, measures whether the pack can reach a harness over HTTP and how fast, runs one read (players) and one
# event (playerJoin) through the inverted transport (the pack polls the harness), and joins BDS with the M9 Bedrock driver host
# (drivers/bedrock) using an unsigned chain. Every host name any container looks up is logged by the T-M9-01 stand-in
# (scripts/spike/geyser/HostLog.java), which is the only DNS server of every container.
#
# Usage (Git Bash on Windows needs MSYS_NO_PATHCONV=1):
#   PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh bash scripts/spike/bds/bds-spike.sh <out dir>
# Knobs (environment):
#   BDS_ZIP        the BDS Linux zip (default /c/pw/bds-cache/bedrock-server-1.26.66.1.zip); checked against BDS_SHA256.
#                  It is downloaded by hand from minecraft.net (docs/platforms/bds.md) and never committed or redistributed.
#   SERVER_VERSION / NET_VERSION   the @minecraft/server and @minecraft/server-net versions written into the pack manifest
#   BETA           1 (default) = turn on the Beta APIs experiment in level.dat between the first and second start; 0 = leave it off
#   ONLINE_MODE    BDS online-mode, default false
#   NET_ALLOWED    1 (default) = add @minecraft/server-net to config/default/permissions.json; 0 = BDS's shipped list
#   EGRESS         closed (default: BDS and the driver host lose their default route before they start) | open
#   JOIN           1 (default) = join with the driver host; 0 = skip the join
#   DRIVER_JAR     default drivers/bedrock/build/libs/bedrock-host-all.jar (./gradlew :drivers:bedrock:shadowJar)
# Inputs are copied into containers, never bind-mounted (AGENTS.md section 3). The EULA is read from the command environment only.
set -euo pipefail
OUT=${1:?out dir}
if [ "${PROOFSTONE_ACCEPT_EULA:-}" != "true" ]; then echo "set PROOFSTONE_ACCEPT_EULA=true for this command" >&2; exit 2; fi
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
HERE=$ROOT/scripts/spike/bds
BDS_ZIP=${BDS_ZIP:-/c/pw/bds-cache/bedrock-server-1.26.66.1.zip}
BDS_SHA256=${BDS_SHA256:-e87bcd95bec5cc34b065ca956825c6abb7a0c356ae79408302845069cecaa6bf}
SERVER_VERSION=${SERVER_VERSION:?set SERVER_VERSION (the @minecraft/server version)}
NET_VERSION=${NET_VERSION:-1.0.0-beta}
BETA=${BETA:-1}; ONLINE_MODE=${ONLINE_MODE:-false}; NET_ALLOWED=${NET_ALLOWED:-1}; EGRESS=${EGRESS:-closed}; JOIN=${JOIN:-1}
DRIVER_JAR=${DRIVER_JAR:-$ROOT/drivers/bedrock/build/libs/bedrock-host-all.jar}
JDK=${JDK:-/c/Users/honzi/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2}
# The pinned java25 digest of itzg/minecraft-server (versions.yml), used for every container: no new image.
IMAGE=itzg/minecraft-server@sha256:e2fabc536673f8c6b343055c93f7a73e10caa79cd3368f645987593c65adf1e0
NET=ps-bds-net; HL=ps-bds-hostlog; HAR=ps-bds-harness; SRV=ps-bds-srv; DRV=ps-bds-driver
HLIP=172.29.78.2
LEVEL="Bedrock level"
PACK_TOKEN=$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')
DRV_TOKEN=$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')
hostpath() { cygpath -m "$1" 2>/dev/null || echo "$1"; }
now() { date -u +%Y-%m-%dT%H:%M:%S.%3NZ; }
mark() { echo "$(now) $*" | tee -a "$OUT/markers.log"; }
mkdir -p "$OUT/stage"
: >"$OUT/markers.log"

[ "$(sha256sum "$BDS_ZIP" | cut -d' ' -f1)" = "$BDS_SHA256" ] || { echo "BDS zip sha256 mismatch: $BDS_ZIP" >&2; exit 2; }
[ -f "$DRIVER_JAR" ] || { echo "missing $DRIVER_JAR (./gradlew :drivers:bedrock:shadowJar)" >&2; exit 2; }
mark "PARAMS bds=$(basename "$BDS_ZIP") server=$SERVER_VERSION net=$NET_VERSION beta=$BETA online=$ONLINE_MODE netAllowed=$NET_ALLOWED egress=$EGRESS join=$JOIN"

# --- stage BDS, the pack and the two Java tools (outside the repo, in $OUT) ---
S=$OUT/stage
rm -rf "$S/bds"; mkdir -p "$S/bds"; unzip -q "$BDS_ZIP" -d "$S/bds"
P=$S/bds/server.properties
sed -i -e "s/^online-mode=.*/online-mode=$ONLINE_MODE/" -e 's/^allow-list=.*/allow-list=false/' -e 's/^transport=.*/transport=raknet/' \
  -e 's/^enable-lan-visibility=.*/enable-lan-visibility=false/' -e 's/^view-distance=.*/view-distance=4/' \
  -e 's/^content-log-console-output-enabled=.*/content-log-console-output-enabled=true/' \
  -e 's/^content-log-file-enabled=.*/content-log-file-enabled=true/' -e 's/^difficulty=.*/difficulty=peaceful/' "$P"
grep -E '^(online-mode|allow-list|transport|enable-lan-visibility|content-log|server-port)' "$P" >"$OUT/server.properties.used"
if [ "$NET_ALLOWED" = 1 ]; then
  sed -i 's#"@minecraft/server-admin",#"@minecraft/server-admin",\n    "@minecraft/server-net",#' "$S/bds/config/default/permissions.json"
fi
cp "$S/bds/config/default/permissions.json" "$OUT/permissions.used.json"
PK=$S/bds/behavior_packs/proofstone-spike
mkdir -p "$PK/scripts"
sed -e "s/@SERVER_VERSION@/$SERVER_VERSION/" -e "s/@NET_VERSION@/$NET_VERSION/" "$HERE/pack/manifest.json" >"$PK/manifest.json"
sed -e "s/@TOKEN@/$PACK_TOKEN/" "$HERE/pack/scripts/main.js" >"$PK/scripts/main.js"
cp "$PK/manifest.json" "$OUT/manifest.used.json"
mkdir -p "$S/bds/worlds/$LEVEL"
echo '[{"pack_id":"1c0fea80-1fa8-4482-a430-3e4737c3847c","version":[0,0,1]}]' >"$S/bds/worlds/$LEVEL/world_behavior_packs.json"
# Observe-only check: list every call in the pack that could write to the world. The expected result is "none".
grep -n -E 'runCommand|setDynamicProperty|setBlock|\.kill\(|teleport|addEffect|setGameMode|spawnEntity|beforeEvents|\.cancel|setItem|addTag|sendMessage|applyDamage|getComponent' \
  "$HERE/pack/scripts/main.js" >"$OUT/observe-only.txt" || echo "none" >"$OUT/observe-only.txt"
mark "observe-only grep of write-capable calls: $(tr '\n' ' ' <"$OUT/observe-only.txt")"

mkdir -p "$S/hl" "$S/har" "$S/trust" "$S/drv"
"$JDK/bin/javac" --release 21 -d "$(hostpath "$S/hl")" "$(hostpath "$ROOT/scripts/spike/geyser/HostLog.java")"
"$JDK/bin/javac" --release 21 -d "$(hostpath "$S/har")" "$(hostpath "$HERE/Harness.java")"
KT=$JDK/bin/keytool
"$KT" -genkeypair -alias stub -keyalg EC -groupname secp256r1 -dname CN=proofstone-spike-stub -validity 30 \
  -ext san=dns:client.discovery.minecraft-services.net,dns:authorization.franchise.minecraft-services.net \
  -keystore "$(hostpath "$S/hl")/stub.p12" -storetype PKCS12 -storepass changeit -keypass changeit >/dev/null 2>&1
"$KT" -exportcert -alias stub -keystore "$(hostpath "$S/hl")/stub.p12" -storepass changeit -rfc -file "$(hostpath "$S/hl")/stub.crt" >/dev/null 2>&1
cp "$DRIVER_JAR" "$S/drv/bedrock-host-all.jar"

cleanup() {
  set +e
  docker logs "$SRV" >"$OUT/bds.log" 2>&1
  docker logs "$HAR" >"$OUT/harness.stdout.log" 2>&1
  docker logs "$DRV" >"$OUT/driver.log" 2>&1
  docker cp "$HL:/hl/dns.log" "$(hostpath "$OUT")/dns.log" >/dev/null 2>&1
  docker rm -f "$SRV" "$HL" "$HAR" "$DRV" >/dev/null 2>&1
  docker network rm "$NET" >/dev/null 2>&1
  mark "cleanup done"
}
trap cleanup EXIT
docker rm -f "$SRV" "$HL" "$HAR" "$DRV" >/dev/null 2>&1 || true
docker network rm "$NET" >/dev/null 2>&1 || true

# Bounded wait for a log line (await with timeout, AGENTS.md section 3); a stopped container ends it at once.
await_log() { # container pattern seconds [min count]
  local n=${4:-1}
  if ! timeout "$3" sh -c "until [ \$(docker logs $1 2>&1 | grep -c -E '$2') -ge $n ]; do
      [ \"\$(docker inspect -f '{{.State.Running}}' $1)\" = true ] || exit 1; sleep 0.5; done"; then
    echo "TIMEOUT or exit: $1 did not log /$2/ (x$n) within ${3}s" >&2; docker logs "$1" 2>&1 | tail -40 >&2; return 1
  fi
}

docker network create --subnet 172.29.78.0/24 "$NET" >/dev/null
docker create --name "$HL" --network "$NET" --ip $HLIP --entrypoint java "$IMAGE" -cp /hl HostLog /hl/dns.log /hl/stub.p12 $HLIP >/dev/null
docker cp "$(hostpath "$S/hl")" "$HL:/hl"
docker start "$HL" >/dev/null
await_log "$HL" 'dns up' 30
docker cp "$HL:/opt/java/openjdk/lib/security/cacerts" "$(hostpath "$S/trust")/cacerts"
"$KT" -importcert -noprompt -alias proofstone-spike-stub -file "$(hostpath "$S/hl")/stub.crt" -keystore "$(hostpath "$S/trust")/cacerts" -storepass changeit >/dev/null 2>&1

docker create --name "$HAR" --network "$NET" --network-alias harness --dns $HLIP --entrypoint java "$IMAGE" -cp /har Harness "$PACK_TOKEN" >/dev/null
docker cp "$(hostpath "$S/har")" "$HAR:/har"
docker start "$HAR" >/dev/null
await_log "$HAR" 'harness up' 30

ROUTE="true"; CAPS=()
if [ "$EGRESS" = closed ]; then ROUTE="ip route del default"; CAPS=(--cap-add NET_ADMIN); fi
# BDS runs from /bds as root in the pinned image (Ubuntu); stdin stays open (-i) so the console does not see EOF.
docker create -i --name "$SRV" --network "$NET" --network-alias bds --dns $HLIP "${CAPS[@]}" --entrypoint sh "$IMAGE" \
  -c "$ROUTE; ip route > /tmp/route.txt; cd /bds && LD_LIBRARY_PATH=. exec ./bedrock_server" >/dev/null
docker cp "$(hostpath "$S/bds")" "$SRV:/bds"

mark "PHASE first start (no Beta APIs flag yet)"
docker start "$SRV" >/dev/null
await_log "$SRV" 'Server started' 180
mark "PHASE first start done"
docker exec "$SRV" cat /tmp/route.txt >"$OUT/bds-route.txt" 2>&1 || true
docker logs "$SRV" >"$OUT/bds.first.log" 2>&1
if [ "$BETA" = 1 ]; then
  mark "PHASE stop for the level.dat edit"
  docker stop -t 60 "$SRV" >/dev/null
  docker logs "$SRV" 2>&1 | tail -5 >>"$OUT/markers.log"
  docker cp "$SRV:/bds/worlds/$LEVEL/level.dat" "$(hostpath "$S")/level.dat.orig"
  python -I "$(hostpath "$HERE/leveldat.py")" "$(hostpath "$S/level.dat.orig")" "$(hostpath "$S/level.dat")" | tee -a "$OUT/markers.log"
  docker cp "$(hostpath "$S/level.dat")" "$SRV:/bds/worlds/$LEVEL/level.dat"
  mark "PHASE second start (Beta APIs on)"
  docker start "$SRV" >/dev/null
  await_log "$SRV" 'Server started' 180 2
  mark "PHASE second start done"
fi

# --- inverted transport: the pack pings, then polls ---
if await_log "$SRV" 'proofstone-spike\] (ping|FAILED)' 60; then
  docker logs "$SRV" 2>&1 | grep -E 'proofstone-spike' | tee -a "$OUT/markers.log"
else
  mark "RESULT pack produced no ping line within 60 s"
fi
ctl() { docker exec "$HAR" curl -sS -m 30 -w ' http=%{http_code}' "$@"; }
mark "READ players #1 (nobody online): $(ctl -X POST 'http://127.0.0.1:8080/ctl/request?op=players&timeoutMs=15000' || true)"
mark "READ players #2: $(ctl -X POST 'http://127.0.0.1:8080/ctl/request?op=players&timeoutMs=15000' || true)"
mark "READ players #3: $(ctl -X POST 'http://127.0.0.1:8080/ctl/request?op=players&timeoutMs=15000' || true)"

# --- the M9 driver host joins BDS with an unsigned chain ---
if [ "$JOIN" = 1 ]; then
  docker create --name "$DRV" --network "$NET" --network-alias driver --dns $HLIP "${CAPS[@]}" \
    -e "JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/trust/cacerts -Djavax.net.ssl.trustStorePassword=changeit" \
    --entrypoint sh "$IMAGE" -c "$ROUTE; exec java -jar /driver/bedrock-host-all.jar $DRV_TOKEN" >/dev/null
  docker cp "$(hostpath "$S/drv")" "$DRV:/driver"
  docker cp "$(hostpath "$S/trust")" "$DRV:/trust"
  docker start "$DRV" >/dev/null
  await_log "$DRV" 'bedrock driver host ready' 60
  rpc() { docker exec "$DRV" curl -sS -m 60 -w ' http=%{http_code}' -H "Authorization: Bearer $DRV_TOKEN" -H 'Content-Type: application/json' \
    -H "X-Proofstone-Request-Id: spike-$RANDOM" "$@"; }
  mark "DRIVER health: $(rpc http://127.0.0.1:25590/rpc/v1/health || true)"
  mark "JOIN begin name=PsBds1"
  JR=$(rpc -X POST -d '{"host":"bds","port":19132,"name":"PsBds1","xuid":"2535400000000001","joinTimeoutMillis":30000}' \
    http://127.0.0.1:25590/rpc/v1/sessions || true)
  mark "JOIN result: $JR"
  mark "EVENT playerJoin: $(ctl 'http://127.0.0.1:8080/ctl/await-event?type=playerJoin&timeoutMs=15000' || true)"
  mark "READ players #4 (after the join): $(ctl -X POST 'http://127.0.0.1:8080/ctl/request?op=players&timeoutMs=15000' || true)"
  SID=$(echo "$JR" | sed -n 's/.*"sessionId":"\([^"]*\)".*/\1/p')
  if [ -n "$SID" ]; then
    mark "DRIVER position: $(rpc http://127.0.0.1:25590/rpc/v1/sessions/$SID/position || true)"
    mark "DRIVER transcript: $(rpc http://127.0.0.1:25590/rpc/v1/sessions/$SID/transcript | head -c 3000 || true)"
    mark "DRIVER disconnect: $(rpc -X DELETE http://127.0.0.1:25590/rpc/v1/sessions/$SID || true)"
  fi
fi
docker exec "$HAR" curl -sS -m 10 http://127.0.0.1:8080/ctl/log >"$OUT/harness.log" || true
docker exec "$SRV" sh -c 'ls /bds/worlds/*/ ; ls /bds/*.txt /bds/logs 2>/dev/null; cat /bds/ContentLog*.txt /bds/logs/*.txt 2>/dev/null | tail -100' >"$OUT/bds-contentlog.txt" 2>&1 || true
mark "DONE"
