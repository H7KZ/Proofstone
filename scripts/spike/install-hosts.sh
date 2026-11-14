#!/usr/bin/env bash
# T-M8-07 measurement, not production code. Which hosts does a loader cell's cold-start install reach from inside the
# container (ADR-0017 section 6)? The server container sits on a docker --internal network, so it has no route out except a
# logging proxy (scripts/spike/ConnectLog.java, run in a second container of the same pinned image). Anything that ignored
# the proxy would fail the install loudly, so the proxy log is the complete list.
#
# Usage: PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh bash scripts/spike/install-hosts.sh <fabric|neoforge> <out dir>
# Inputs (the pinned launcher, installer) are read from build/spike/cache and checked by sha256; copied in, never mounted.
set -euo pipefail
LOADER=${1:?fabric | neoforge}
OUT=${2:?out dir}
if [ "${PROOFSTONE_ACCEPT_EULA:-}" != "true" ]; then echo "set PROOFSTONE_ACCEPT_EULA=true for this command" >&2; exit 2; fi
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
CACHE=${CACHE:-$ROOT/build/spike/cache}
IMAGE=itzg/minecraft-server@sha256:e2fabc536673f8c6b343055c93f7a73e10caa79cd3368f645987593c65adf1e0
MC=26.3
NET=ps-hosts-net-$LOADER
SRV=ps-hosts-srv-$LOADER
PRX=ps-hosts-proxy-$LOADER
hostpath() { cygpath -m "$1" 2>/dev/null || echo "$1"; }
mkdir -p "$OUT"
cleanup() {
  docker logs "$SRV" >"$OUT/server.log" 2>&1 || true
  docker logs "$PRX" >"$OUT/proxy-container.log" 2>&1 || true
  docker cp "$PRX:/proxy/hosts.log" "$(hostpath "$OUT")/hosts.log" || docker exec "$PRX" ls -la /proxy >&2 || true
  docker rm -f "$SRV" "$PRX" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

case "$LOADER" in
  fabric)
    PIN=fabric-server-mc.26.3-loader.0.20.1-launcher.1.1.2.jar
    SHA=70446947655b4f8f286aba896aa4c19a255e8ca8a730636ef2a6017ae4415d95
    TYPEENV=(-e TYPE=FABRIC -e FABRIC_LAUNCHER=/pins/$PIN) ;;
  neoforge)
    PIN=neoforge-26.3.0.103-installer.jar
    SHA=d8a92e17cf12b5739a2518b11afd47eeec0e636a6e63f94b6400c5dbd3603e48
    TYPEENV=(-e TYPE=NEOFORGE -e NEOFORGE_INSTALLER=/pins/$PIN) ;;
  *) echo "unknown loader" >&2; exit 2 ;;
esac
got=$(sha256sum "$CACHE/$PIN" | cut -d' ' -f1)
[ "$got" = "$SHA" ] || { echo "sha256 mismatch for $PIN: $got" >&2; exit 2; }
mkdir -p "$OUT/stage/pins" "$OUT/stage/proxy"
cp "$CACHE/$PIN" "$OUT/stage/pins/"
# The image holds a JRE without the compiler, so the proxy is compiled here (JDK 21, class file 65) and copied in.
JAVAC=${JAVA_HOME:+$JAVA_HOME/bin/}javac
"$JAVAC" --release 21 -d "$(hostpath "$OUT/stage/proxy")" "$(hostpath "$ROOT/scripts/spike/ConnectLog.java")"

docker rm -f "$SRV" "$PRX" >/dev/null 2>&1 || true
docker network rm "$NET" >/dev/null 2>&1 || true
docker network create --internal "$NET" >/dev/null
# The proxy: on the default bridge (it reaches the internet) and on the internal network (the server reaches it).
docker create --name "$PRX" --entrypoint java "$IMAGE" -cp /proxy ConnectLog 3128 /proxy/hosts.log >/dev/null
docker cp "$(hostpath "$OUT/stage/proxy")" "$PRX:/proxy"
docker network connect "$NET" "$PRX"
docker start "$PRX" >/dev/null
PROXY_OPTS="-Dhttp.proxyHost=$PRX -Dhttp.proxyPort=3128 -Dhttps.proxyHost=$PRX -Dhttps.proxyPort=3128"
docker create --name "$SRV" --network "$NET" -e EULA=TRUE -e VERSION=$MC -e ONLINE_MODE=FALSE -e LEVEL_TYPE=minecraft:flat \
  -e ENABLE_RCON=false -e "JAVA_TOOL_OPTIONS=$PROXY_OPTS" -e http_proxy=http://$PRX:3128 -e https_proxy=http://$PRX:3128 \
  "${TYPEENV[@]}" "$IMAGE" >/dev/null
docker cp "$(hostpath "$OUT/stage/pins")" "$SRV:/pins"
t0=$(date +%s)
docker start "$SRV" >/dev/null
# Bounded wait, the await-with-timeout shape; a loader that gives up ends it at once.
if ! timeout 400 sh -c "until docker logs $SRV 2>&1 | grep -q -E 'Done \(|Failed to start'; do
    [ \"\$(docker inspect -f '{{.State.Running}}' $SRV)\" = true ] || exit 1; sleep 1; done"; then
  echo "INSTALL FAIL: $LOADER did not reach Done within 400 s or exited" >&2
  docker logs "$SRV" 2>&1 | tail -40 >&2
  exit 1
fi
echo "INSTALL $LOADER@$MC reached Done in $(( $(date +%s) - t0 )) s through the proxy only"
if docker logs "$SRV" 2>&1 | grep -q 'Failed to start'; then echo "INSTALL FAIL: loader failed to start" >&2; exit 1; fi
