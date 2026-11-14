#!/usr/bin/env bash
# SPIKE (T-M8-01). Builds the spike's test inputs with the JDK alone (no Loom, no ModDevGradle, no new dependency):
# the Fabric and NeoForge test mods from scripts/spike/testmod, the data pack from scripts/spike/datapack and a
# minimal resource pack. The compile classpath is what mod-spike.sh EXPORT=1 copied out of a booted 26.3 container
# (build/ only, never committed): 26.x ships unobfuscated, so the mods compile against the server's own names.
#
# Usage: SPIKE_JDK=<JDK 25+> bash scripts/spike/build-testmods.sh <fabric export dir> <neoforge export dir> <fabric-api jar>
# Output: build/spike/testmods/{proofstone-spike-fabric.jar, proofstone-spike-neoforge.jar, spike-datapack.zip,
#         spike-resourcepack.zip}
set -euo pipefail

FABRIC_EXPORT=$(realpath "${1:?fabric export dir}")
NEO_EXPORT=$(realpath "${2:?neoforge export dir}")
FABRIC_API=$(realpath "${3:?fabric-api jar}")
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SRC=$ROOT/scripts/spike
OUT=$ROOT/build/spike/testmods
JDK=${SPIKE_JDK:?SPIKE_JDK: a JDK 25 or newer (26.x game classes are class file 69)}
rm -rf "$OUT" && mkdir -p "$OUT/fabric-classes" "$OUT/neo-classes" "$OUT/nested"
sep=";"; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) ;; *) sep=":" ;; esac
win() { cygpath -m "$1" 2>/dev/null || echo "$1"; }
join_cp() { local IFS="$sep"; echo "$*"; }

# Fabric API's lifecycle module is a nested jar inside fabric-api; the game classes are a nested jar inside Mojang's
# server bundler (.fabric/server/<mc>-server.jar).
(cd "$OUT/nested" && "$JDK/bin/jar" xf "$(win "$FABRIC_API")" META-INF/jars)
for bundler in "$FABRIC_EXPORT"/fabric/server/*-server.jar; do
  (cd "$OUT/nested" && "$JDK/bin/jar" xf "$(win "$bundler")" META-INF/versions)
done
mapfile -t fabric_jars < <(find "$FABRIC_EXPORT" "$OUT/nested" -name '*.jar' | while read -r j; do win "$j"; done)
"$JDK/bin/javac" --release 25 -nowarn -d "$(win "$OUT/fabric-classes")" -cp "$(join_cp "${fabric_jars[@]}")" \
  $(find "$SRC/testmod/common" "$SRC/testmod/fabric" -name '*.java' | while read -r f; do win "$f"; done)
command cp "$SRC/testmod/fabric-res/fabric.mod.json" "$OUT/fabric-classes/"
"$JDK/bin/jar" cf "$(win "$OUT/proofstone-spike-fabric.jar")" -C "$(win "$OUT/fabric-classes")" .

mapfile -t neo_jars < <(find "$NEO_EXPORT" -name '*.jar' | while read -r j; do win "$j"; done)
"$JDK/bin/javac" --release 25 -nowarn -d "$(win "$OUT/neo-classes")" -cp "$(join_cp "${neo_jars[@]}")" \
  $(find "$SRC/testmod/common" "$SRC/testmod/neoforge" -name '*.java' | while read -r f; do win "$f"; done)
command cp -r "$SRC/testmod/neoforge-res/META-INF" "$OUT/neo-classes/"
"$JDK/bin/jar" cf "$(win "$OUT/proofstone-spike-neoforge.jar")" -C "$(win "$OUT/neo-classes")" .

"$JDK/bin/jar" cfM "$(win "$OUT/spike-datapack.zip")" -C "$(win "$SRC/datapack")" .
"$JDK/bin/jar" cfM "$(win "$OUT/spike-resourcepack.zip")" -C "$(win "$SRC/resourcepack")" .
sha256sum "$OUT"/*.jar "$OUT"/*.zip
