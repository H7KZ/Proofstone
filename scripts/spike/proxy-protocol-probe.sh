#!/usr/bin/env bash
# SPIKE (T-M7-01). Not production code. Boots one proxy with NO backend and joins once with the mcpl driver
# (protocol 777, 26.3), to see whether the proxy build itself accepts the client. A proxy that accepts the protocol
# gets as far as "no backend" (a kick naming the missing server); one that does not rejects the version first.
#
# Usage: MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh bash scripts/spike/proxy-protocol-probe.sh <type> <version> <build>
#   <type> is VELOCITY or WATERFALL (itzg/mc-proxy TYPE). SPIKE_CP and SPIKE_JAVA as for proxy-spike.sh.
set -euo pipefail
TYPE=${1:?type}
VERSION=${2:?version}
BUILD=${3:?build}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=${SPIKE_OUT:-$ROOT/build/spike/probe-$TYPE-$VERSION-$BUILD-$(date +%Y%m%dT%H%M%S)}
mkdir -p "$OUT"
IMAGE=itzg/mc-proxy@sha256:8efdaa9d990f4c509f21b7ced003dd959060777784422fd72fdd537d613c1813  # java25-2026.11.1
PROXY=ps-spike-probe
hostpath() { cygpath -m "$1" 2>/dev/null || echo "$1"; }
cleanup() { docker logs "$PROXY" >"$OUT/$PROXY.log" 2>&1 || true; docker rm -f "$PROXY" >/dev/null 2>&1 || true; }
trap cleanup EXIT

case "$TYPE" in
  VELOCITY) ENV=(-e TYPE=VELOCITY -e VELOCITY_VERSION=$VERSION -e VELOCITY_BUILD_ID=$BUILD); READY='Done \(' ;;
  WATERFALL) ENV=(-e TYPE=WATERFALL -e WATERFALL_VERSION=$VERSION -e WATERFALL_BUILD_ID=$BUILD); READY='Listening on' ;;
  *) echo "unknown type $TYPE" >&2; exit 2 ;;
esac
# Offline mode on the proxy, so the probe needs no Mojang session. Velocity reads the env through the image's
# default config only when the file is absent, so the setting is patched after the first boot writes the file.
docker run -d --name "$PROXY" -p 127.0.0.1::25577 "${ENV[@]}" -e ENABLE_RCON=false -e SKIP_DOWNLOAD_DEFAULTS=true \
  "$IMAGE" >/dev/null
timeout 120 sh -c "until docker logs $PROXY 2>&1 | grep -q -E '$READY'; do
  [ \"\$(docker inspect -f '{{.State.Running}}' $PROXY)\" = true ] || exit 1; sleep 1; done"
docker exec "$PROXY" sh -c 'sha256sum /server/*.jar'
case "$TYPE" in
  VELOCITY) docker exec "$PROXY" sh -c "sed -i -e 's/^online-mode = true/online-mode = false/' \
      -e 's/^force-key-authentication = true/force-key-authentication = false/' -e 's/^bind = .*/bind = \"0.0.0.0:25577\"/' /server/velocity.toml" ;;
  WATERFALL) docker exec "$PROXY" sh -c "sed -i -e 's/^online_mode: true/online_mode: false/' /server/config.yml" ;;
esac
docker restart "$PROXY" >/dev/null
timeout 120 sh -c "until [ \$(docker logs $PROXY 2>&1 | grep -c -E '$READY') -ge 2 ]; do
  [ \"\$(docker inspect -f '{{.State.Running}}' $PROXY)\" = true ] || exit 1; sleep 1; done"
PORT=$(docker port "$PROXY" 25577/tcp | head -1 | sed 's/.*://')
JAVA=${SPIKE_JAVA:-java}
"$JAVA" -cp "$SPIKE_CP" "$(hostpath "$ROOT/scripts/spike/ProxyJoin.java")" 127.0.0.1 "$PORT" 1 "$(hostpath "$OUT")" lobby survival \
  | tee "$OUT/client.txt"
echo "output: $OUT"
