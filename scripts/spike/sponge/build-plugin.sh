#!/usr/bin/env bash
# SPIKE (T-M10-11). Builds the throwaway Sponge plugin with the JDK alone (no SpongeGradle, no new dependency). The compile
# classpath is the SpongeVanilla universal jar's nested jars/spongevanilla-mod.jar (it carries the SpongeAPI classes) plus the
# libraries the first boot fetched into /data/libraries, copied out by `EXPORT=1 bash scripts/spike/mod-spike.sh sponge ...`.
# Everything stays outside the repository (docs/platforms/sponge.md).
#
# Usage: SPIKE_JDK=<JDK 25+> bash scripts/spike/sponge/build-plugin.sh <universal jar> <export dir> <out dir>
# Output: <out dir>/proofstone-spike-sponge.jar
set -euo pipefail

UNIVERSAL=$(realpath "${1:?SpongeVanilla universal jar}")
EXPORT=$(realpath "${2:?export dir from mod-spike.sh EXPORT=1}")
OUT=$(realpath -m "${3:?out dir}")
SRC=$(cd "$(dirname "$0")" && pwd)
JDK=${SPIKE_JDK:?SPIKE_JDK: a JDK 25 or newer}
sep=";"; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) ;; *) sep=":" ;; esac
win() { cygpath -m "$1" 2>/dev/null || echo "$1"; }
join_cp() { local IFS="$sep"; echo "$*"; }

mkdir -p "$OUT/classes" "$OUT/nested"
(cd "$OUT/nested" && "$JDK/bin/jar" xf "$(win "$UNIVERSAL")" jars/spongevanilla-mod.jar)
mapfile -t jars < <(find "$EXPORT" "$OUT/nested" -name '*.jar' | while read -r j; do win "$j"; done)
# -g is required, not cosmetic: Sponge reads a listener's generic event type (StartedEngineEvent<Server>) from the
# LocalVariableTable, so a plain javac build (no local variable table) fails plugin construction (run d2, docs/platforms/sponge.md).
"$JDK/bin/javac" -g --release 25 -nowarn -d "$(win "$OUT/classes")" -cp "$(join_cp "${jars[@]}")" \
  $(find "$SRC/plugin" -name '*.java' | while read -r f; do win "$f"; done)
command cp -r "$SRC/plugin-res/META-INF" "$OUT/classes/"
"$JDK/bin/jar" cf "$(win "$OUT/proofstone-spike-sponge.jar")" -C "$(win "$OUT/classes")" .
sha256sum "$OUT/proofstone-spike-sponge.jar"
