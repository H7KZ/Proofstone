#!/usr/bin/env bash
# T-M12-09 acceptance variant of nethernet-spike.sh. Same server side (PowerNukkitX 3.0.6, NetherNet only), but the client is the
# real driver host (drivers/bedrock, shaded jar) joined through its RPC with "transport":"nethernet", not the throwaway client.
# Needs the built jar: ./gradlew :drivers:bedrock:shadowJar (DRIVER_JAR, default drivers/bedrock/build/libs/bedrock-host-all.jar).
# ---- original header ----
# SPIKE (T-M10-19). Not production code. Boots PowerNukkitX 3.0.6 (NetherNet-only, Bedrock protocol 2225) on the pinned java25 image
# with the T-M10-13 throwaway agent (scripts/spike/nukkit/agent), then joins it with NetherNetJoin.java: an offline Bedrock client over
# NetherNet (HTTP signalling, WebRTC data channels) compiled against and run with the PowerNukkitX jar itself, so nothing new is
# downloaded. Every name any container looks up is logged by the T-M9-01 stand-in (scripts/spike/geyser/HostLog.java), the only DNS
# server of every container. Derived from scripts/spike/nukkit/nukkit-spike.sh.
#
# Usage (Git Bash on Windows needs MSYS_NO_PATHCONV=1):
#   PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 \
#     bash scripts/dockerlock.sh bash scripts/spike/nethernet/driver-host-join.sh build/spike/nethernet/<run>
# Results and the run log: docs/platforms/nethernet.md.
# Knobs (environment):
#   XBOX_AUTH     false (default) | true: pnx.yml settings.xboxAuth for the second start
#   EGRESS        closed (default: the server and the client lose their default route before they start) | open
#   SCHEME        AUTO (default: HTTPS probe, then plaintext, as the retail client) | HTTP | HTTPS: the client's signalling scheme
#   TRUST         1 (default): the server and the client JVMs trust the stand-in's certificate | 0: they do not
#   JOINS         1 (default) | n: joins one after another, each with its own name, XUID and key
set -euo pipefail
OUT=${1:?out dir}
if [ "${PROOFSTONE_ACCEPT_EULA:-}" != "true" ]; then echo "set PROOFSTONE_ACCEPT_EULA=true for this command" >&2; exit 2; fi
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
HERE=$ROOT/scripts/spike/nethernet
PNX_SHA256=10822d818c1f2c7d423092a235f3f04bd0eb915b2298b204ed1697d7a0dd9539
PNX_JAR=${PNX_JAR:-/c/pw/nukkit-cache/3.0.6/powernukkitx.jar}
DRIVER_JAR=${DRIVER_JAR:-$ROOT/drivers/bedrock/build/libs/bedrock-host-all.jar}
[ -f "$DRIVER_JAR" ] || { echo "no driver host jar at $DRIVER_JAR (run :drivers:bedrock:shadowJar)" >&2; exit 2; }
HOST_TOKEN=$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' 
')
XBOX_AUTH=${XBOX_AUTH:-false}; EGRESS=${EGRESS:-closed}; SCHEME=${SCHEME:-AUTO}; TRUST=${TRUST:-1}; JOINS=${JOINS:-1}
JDK=${JDK:-/c/Users/honzi/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2}
# The pinned java25 digest of itzg/minecraft-server (versions.yml), used for every container: no new image.
IMAGE=itzg/minecraft-server@sha256:e2fabc536673f8c6b343055c93f7a73e10caa79cd3368f645987593c65adf1e0
NET=ps-nn-net; HL=ps-nn-hostlog; SRV=ps-nn-srv; CLI=ps-nn-client
HLIP=172.29.81.2
AGENT_TOKEN=$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')
hostpath() { cygpath -m "$1" 2>/dev/null || echo "$1"; }
now() { date -u +%Y-%m-%dT%H:%M:%S.%3NZ; }
mark() { echo "$(now) $*" | tee -a "$OUT/markers.log"; }
mkdir -p "$OUT/stage"
: >"$OUT/markers.log"

[ "$(sha256sum "$PNX_JAR" | cut -d' ' -f1)" = "$PNX_SHA256" ] || { echo "PNX jar sha256 mismatch: $PNX_JAR" >&2; exit 2; }
mark "PARAMS pnx=3.0.6 xboxAuth=$XBOX_AUTH egress=$EGRESS scheme=$SCHEME trust=$TRUST joins=$JOINS"

S=$OUT/stage
mkdir -p "$S/in/plugins" "$S/agent" "$S/hl" "$S/trust" "$S/cli"
"$JDK/bin/javac" --release 21 -nowarn -cp "$(hostpath "$PNX_JAR")" -d "$(hostpath "$S/agent")" \
  "$(hostpath "$ROOT/scripts/spike/nukkit/agent/proofstone/spike/nukkit/SpikeAgent.java")"
cp "$ROOT/scripts/spike/nukkit/agent/plugin.yml" "$S/agent/plugin.yml"
"$JDK/bin/jar" --create --file "$(hostpath "$S/in/plugins")/proofstone-spike-agent.jar" -C "$(hostpath "$S/agent")" .
"$JDK/bin/javac" --release 21 -d "$(hostpath "$S/hl")" "$(hostpath "$ROOT/scripts/spike/geyser/HostLog.java")"
KT=$JDK/bin/keytool
"$KT" -genkeypair -alias stub -keyalg EC -groupname secp256r1 -dname CN=proofstone-spike-stub -validity 30 \
  -ext san=dns:client.discovery.minecraft-services.net,dns:authorization.franchise.minecraft-services.net \
  -keystore "$(hostpath "$S/hl")/stub.p12" -storetype PKCS12 -storepass changeit -keypass changeit >/dev/null 2>&1
"$KT" -exportcert -alias stub -keystore "$(hostpath "$S/hl")/stub.p12" -storepass changeit -rfc -file "$(hostpath "$S/hl")/stub.crt" >/dev/null 2>&1

cleanup() {
  set +e
  for c in "$HL" "$SRV" "$CLI"; do echo "IP $c $(docker inspect -f "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}" "$c" 2>/dev/null)" >>"$OUT/markers.log"; done
  docker logs "$SRV" >"$OUT/pnx.log" 2>&1
  docker cp "$HL:/hl/dns.log" "$(hostpath "$OUT")/dns.log" >/dev/null 2>&1
  docker rm -f "$SRV" "$HL" "$CLI" >/dev/null 2>&1
  docker network rm "$NET" >/dev/null 2>&1
  rm -f "$S/in/powernukkitx.jar" "$S/drv/bedrock-host-all.jar"
  mark "cleanup done"
}
trap cleanup EXIT
docker rm -f "$SRV" "$HL" "$CLI" >/dev/null 2>&1 || true
docker network rm "$NET" >/dev/null 2>&1 || true

await_log() { # container pattern seconds [min count]; bounded (AGENTS.md section 3), a stopped container ends it at once
  local n=${4:-1}
  if ! timeout "$3" sh -c "until [ \$(docker logs $1 2>&1 | grep -c -E '$2') -ge $n ]; do
      [ \"\$(docker inspect -f '{{.State.Running}}' $1)\" = true ] || exit 1; sleep 0.5; done"; then
    echo "TIMEOUT or exit: $1 did not log /$2/ (x$n) within ${3}s" >&2; docker logs "$1" 2>&1 | tail -60 >&2; return 1
  fi
}

docker network create --subnet 172.29.81.0/24 "$NET" >/dev/null
docker create --name "$HL" --network "$NET" --ip $HLIP --entrypoint java "$IMAGE" -cp /hl HostLog /hl/dns.log /hl/stub.p12 $HLIP >/dev/null
docker cp "$(hostpath "$S/hl")" "$HL:/hl"
docker start "$HL" >/dev/null
timeout 30 sh -c "until docker exec $HL grep -q 'dns up' /hl/dns.log 2>/dev/null; do sleep 0.5; done" || { echo 'TIMEOUT: HostLog did not write dns up' >&2; docker logs "$HL" >&2; exit 1; }
docker cp "$HL:/opt/java/openjdk/lib/security/cacerts" "$(hostpath "$S/trust")/cacerts"
"$KT" -importcert -noprompt -alias proofstone-spike-stub -file "$(hostpath "$S/hl")/stub.crt" -keystore "$(hostpath "$S/trust")/cacerts" -storepass changeit >/dev/null 2>&1
TRUST_OPTS=""
[ "$TRUST" = 1 ] && TRUST_OPTS="-Djavax.net.ssl.trustStore=/trust/cacerts -Djavax.net.ssl.trustStorePassword=changeit"

ROUTE="true"; CAPS=()
if [ "$EGRESS" = closed ]; then ROUTE="ip route del default"; CAPS=(--cap-add NET_ADMIN); fi
cp "$PNX_JAR" "$S/in/powernukkitx.jar"
docker create -i --name "$SRV" --network "$NET" --network-alias pnx --dns $HLIP "${CAPS[@]}" \
  -e PNX_SETUP_NON_INTERACTIVE=1 -e "PROOFSTONE_AGENT_TOKEN=$AGENT_TOKEN" -e "JAVA_TOOL_OPTIONS=$TRUST_OPTS" --entrypoint sh "$IMAGE" \
  -c "mkdir -p /srv && cd /srv && [ -f powernukkitx.jar ] || { cp /in/powernukkitx.jar . && rm /in/powernukkitx.jar && cp -r /in/plugins /srv/plugins; }; \
      $ROUTE; exec java -Xmx1G -DdisableSentry=true -jar powernukkitx.jar --skip-setup --language eng --disable-ansi" >/dev/null
docker cp "$(hostpath "$S/in")" "$SRV:/in"
docker cp "$(hostpath "$S/trust")" "$SRV:/trust"
rm -f "$S/in/powernukkitx.jar"

mark "PHASE first start (writes pnx.yml)"
T0=$(date +%s)
docker start "$SRV" >/dev/null
await_log "$SRV" 'Done \(' 240
mark "PHASE first start done in $(( $(date +%s) - T0 ))s"
docker stop -t 60 "$SRV" >/dev/null
docker cp "$SRV:/srv/pnx.yml" "$(hostpath "$S")/pnx.yml.orig"
cp "$S/pnx.yml.orig" "$OUT/pnx.default.yml"
sed -E "s/^(\s*xboxAuth:).*/\1 $XBOX_AUTH/" "$S/pnx.yml.orig" >"$S/pnx.yml"
grep -q -E "^\s*xboxAuth: $XBOX_AUTH\s*$" "$S/pnx.yml" || { mark "RESULT pnx.yml has no xboxAuth key to set"; exit 1; }
cp "$S/pnx.yml" "$OUT/pnx.used.yml"
docker cp "$(hostpath "$S/pnx.yml")" "$SRV:/srv/pnx.yml"

mark "PHASE second start (xboxAuth $XBOX_AUTH)"
T0=$(date +%s)
docker start "$SRV" >/dev/null
await_log "$SRV" 'Done \(' 240 2
mark "PHASE second start done in $(( $(date +%s) - T0 ))s"
docker logs "$SRV" 2>&1 | grep -i -E 'nethernet|signal|identif|webrtc|ice' | tail -12 | sed 's/^/PNXLOG /' >>"$OUT/markers.log" || true

agent() { docker exec "$HL" curl -sS -m 15 -w ' http=%{http_code}' -H "Authorization: Bearer $AGENT_TOKEN" \
  -H "X-Proofstone-Request-Id: spike-$RANDOM" "$@"; }
mark "AGENT health: $(agent http://pnx:25580/v1/health || true)"
mark "AGENT players before: $(agent http://pnx:25580/v1/players || true)"
mark "PROBE from the stand-in container (plain HTTP GET /v1/join): $(docker exec "$HL" curl -sS -m 10 -w ' http=%{http_code}' http://pnx:19132/v1/join || true)"

# The client container: the real driver host (shaded jar) on the cell network, DNS and trust as the spike client had them.
HOOK="curl -sS -m 15 -H 'Authorization: Bearer $AGENT_TOKEN' -H 'X-Proofstone-Request-Id: driver-join' http://pnx:25580/v1/players"
docker create --name "$CLI" --network "$NET" --network-alias driver --dns $HLIP "${CAPS[@]}" -e "JAVA_TOOL_OPTIONS=$TRUST_OPTS"   --entrypoint sh "$IMAGE" -c "$ROUTE; exec java -jar /driver/bedrock-host-all.jar $HOST_TOKEN" >/dev/null
mkdir -p "$S/drv" && cp "$DRIVER_JAR" "$S/drv/bedrock-host-all.jar"
docker cp "$(hostpath "$S/drv")" "$CLI:/driver"
docker cp "$(hostpath "$S/trust")" "$CLI:/trust"
docker start "$CLI" >/dev/null
await_log "$CLI" 'bedrock driver host ready' 60
rpc() { docker exec "$CLI" curl -sS -m "$1" -w ' http=%{http_code}' -H "Authorization: Bearer $HOST_TOKEN" -H 'X-Proofstone-Request-Id: driver-join' "${@:2}"; }
mark "RPC health: $(rpc 10 http://127.0.0.1:25590/rpc/v1/health || true)"
FAILED=0
for i in $(seq 1 "$JOINS"); do
  NAME="PsNether$i"; XUID="25354000000002$(printf %02d "$i")"
  mark "JOIN $i begin name=$NAME xuid=$XUID transport=nethernet"
  T0=$(date +%s%3N)
  set +e
  REPLY=$(rpc 60 -X POST -H 'Content-Type: application/json'     -d "{\"host\":\"pnx\",\"port\":19132,\"name\":\"$NAME\",\"xuid\":\"$XUID\",\"joinTimeoutMillis\":45000,\"transport\":\"nethernet\"}"     http://127.0.0.1:25590/rpc/v1/sessions)
  RC=$?
  set -e
  echo "$REPLY" >"$OUT/join$i.reply"
  mark "JOIN $i rc=$RC wall_ms=$(( $(date +%s%3N) - T0 )) reply=$(echo "$REPLY" | cut -c1-600)"
  SID=$(echo "$REPLY" | sed -n 's/.*"sessionId":"\([^"]*\)".*/\1/p' | tr -d '\r\n')
  if [ -z "$SID" ]; then mark "JOIN $i FAILED: no session"; FAILED=1; continue; fi
  PL=$(docker exec "$HL" curl -sS -m 15 -H "Authorization: Bearer $AGENT_TOKEN" -H 'X-Proofstone-Request-Id: driver-join' http://pnx:25580/v1/players || true)
  mark "AGENT players while joined: $PL"
  echo "$PL" | grep -q "\"$NAME\"" || { mark "JOIN $i FAILED: the agent does not list $NAME"; FAILED=1; }
  set +e
  LEAVE=$(rpc 30 -X DELETE "http://127.0.0.1:25590/rpc/v1/sessions/$SID" 2>&1)
  LRC=$?
  set -e
  mark "RPC leave rc=$LRC: $LEAVE"
  # A failed leave: a thread dump of the host (SIGQUIT) lands in its log, which is kept as driver-host.log
  echo "$LEAVE" | grep -q '"ok":true' || docker exec "$CLI" sh -c 'kill -3 1' || true
  timeout 30 sh -c "until docker exec $HL curl -sS -m 5 -H 'Authorization: Bearer $AGENT_TOKEN' -H 'X-Proofstone-Request-Id: driver-w' http://pnx:25580/v1/players | grep -q '\"players\":\[\]'; do sleep 0.5; done"     && mark "AGENT players after join $i: empty" || { mark "AGENT players after join $i: not empty within 30 s"; FAILED=1; }
done
docker logs "$CLI" >"$OUT/driver-host.log" 2>&1 || true
docker logs "$SRV" 2>&1 | grep -i -E 'PsNether|login|nethernet|identity|refus|disconnect|error|exception' | tail -40 | sed 's/^/PNXLOG /' >>"$OUT/markers.log" || true
mark "DONE failed=$FAILED"
exit $FAILED
