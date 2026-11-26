#!/usr/bin/env bash
# SPIKE (T-M12-15). Builds a LOCAL image `proofstone-viaproxy-diag:local` from the pinned ViaProxy image with the two classes of
# viaproxy-autoread-diagnostic.patch recompiled (log lines in ChannelUtil and ConfigurationPacketHandler, `[PSDIAG]` on stdout).
# Only those two classes are replaced, because a full rebuild floats the ViaVersion snapshot and the proxy then fails to start.
# Run the loop with PS_VIAPROXY_IMAGE=proofstone-viaproxy-diag:local after applying proofstone-viaproxy-image-override.patch to
# ViaProxyHost (never commit the patch). usage: viaproxy-diag-image.sh <ViaProxy checkout at 3e81750 with the patch applied> <scratch dir>
set -euo pipefail
SRC=${1:?ViaProxy checkout}; WORK=${2:?scratch dir}
PIN=$(sed -n 's#.*viaproxy:.*image: "\([^"]*\)", digest: "\([^"]*\)".*#\1@\2#p' "$(dirname "$0")/../../../versions.yml")
mkdir -p "$WORK/cls"
MSYS_NO_PATHCONV=1 docker run --rm -v "$WORK:/out" --entrypoint cp "$PIN" /app/ViaProxy.jar /out/orig.jar
cp "$WORK/orig.jar" "$WORK/ViaProxy.jar"
javac --release 17 -nowarn -cp "$WORK/orig.jar" -d "$WORK/cls" \
  "$SRC/src/main/java/net/raphimc/viaproxy/proxy/util/ChannelUtil.java" \
  "$SRC/src/main/java/net/raphimc/viaproxy/proxy/packethandler/ConfigurationPacketHandler.java"
jar uf "$WORK/ViaProxy.jar" -C "$WORK/cls" .
printf 'FROM %s\nCOPY ViaProxy.jar /app/ViaProxy.jar\n' "$PIN" > "$WORK/Dockerfile"
docker build -q -t proofstone-viaproxy-diag:local "$WORK"
