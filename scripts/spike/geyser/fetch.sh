#!/usr/bin/env bash
# SPIKE (T-M9-01). Not production code. Downloads the spike's inputs into a cache directory that is never committed, checks
# each sha256 against the value recorded in docs/platforms/geyser.md, and builds the two client drivers' classpaths.
# Allowed by the maintainer for this spike only (M9 decision, 2026-11-17): the newest Geyser and Floodgate builds for
# paper@26.3 from the official download API, the CloudburstMC Protocol Maven artifacts and the npm package bedrock-protocol.
# No Microsoft or Xbox account, token or secret is used by any of it.
#
# Usage: bash scripts/spike/geyser/fetch.sh [cache dir]    (default build/spike/geyser-cache)
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
CACHE=${1:-$ROOT/build/spike/geyser-cache}
HERE=$ROOT/scripts/spike/geyser
mkdir -p "$CACHE"
hostpath() { cygpath -m "$1" 2>/dev/null || echo "$1"; }

fetch() { # url file sha256
  if [ ! -f "$CACHE/$2" ]; then curl -sfL -o "$CACHE/$2" "$1"; fi
  got=$(sha256sum "$CACHE/$2" | cut -d' ' -f1)
  [ "$got" = "$3" ] || { echo "sha256 mismatch for $2: $got (want $3)" >&2; exit 1; }
  echo "ok $2 $3"
}
# Geyser 2.12.1 build 1303 (2026-11-15) and Floodgate 2.2.5 build 141 (2026-09-17), the newest builds on 2026-11-17.
fetch https://download.geysermc.org/v2/projects/geyser/versions/2.12.1/builds/1303/downloads/spigot \
  Geyser-Spigot-2.12.1-b1303.jar 218f6e1a09a2fe458cfd531e7295a0f19d17b00db38ceec16222e8982a4d7f21
fetch https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/141/downloads/spigot \
  floodgate-spigot-2.2.5-b141.jar 21570aff9ce17d6983928e8552777760e1ede5050026b04c686b0ae112e6fd7e

# CloudburstMC Protocol 3.0.0.Beta15-SNAPSHOT (a snapshot: Gradle resolved the 20261116.161745-6 build). It resolves through a
# throw-away Gradle project in the cache, not through the Proofstone build, and nothing here is a Proofstone dependency.
mkdir -p "$CACHE/cb"
cat >"$CACHE/cb/settings.gradle" <<'E'
rootProject.name = 'cb'
E
cat >"$CACHE/cb/build.gradle" <<'E'
plugins { id 'java' }
repositories {
  mavenCentral()
  maven { url 'https://repo.opencollab.dev/maven-snapshots' }
  maven { url 'https://repo.opencollab.dev/maven-releases' }
}
configurations.all { resolutionStrategy.cacheChangingModulesFor 0, 'seconds' }
dependencies {
  implementation 'org.cloudburstmc.protocol:bedrock-connection:3.0.0.Beta15-SNAPSHOT'
  implementation 'org.cloudburstmc.protocol:bedrock-codec:3.0.0.Beta15-SNAPSHOT'
}
tasks.register('cp') { doLast {
  configurations.runtimeClasspath.resolvedConfiguration.resolvedArtifacts.each { a ->
    println "ART ${a.moduleVersion.id} ${a.file}" }
}}
E
"$ROOT/gradlew" -q -p "$(hostpath "$CACHE/cb")" cp --max-workers=1 | sed -n 's/^ART [^ ]* //p' | tr -d '\r' | tr '\\' '/' >"$CACHE/cb/cp.u.txt"
echo "cloudburst classpath: $(wc -l <"$CACHE/cb/cp.u.txt") jars"
while read -r j; do echo "  $(sha256sum "$j" | cut -d' ' -f1) $(basename "$j")"; done <"$CACHE/cb/cp.u.txt"
mkdir -p "$CACHE/jb"
javac --release 21 -cp "$(paste -sd';' "$CACHE/cb/cp.u.txt")" -d "$(hostpath "$CACHE/jb")" "$(hostpath "$HERE/BedrockJoin.java")"

# bedrock-protocol 3.61.0 (npm, 2026-10-29), installed without lifecycle scripts (the native RakNet build is skipped; the
# client uses the pure JS jsp-raknet backend).
mkdir -p "$CACHE/node"
(cd "$CACHE/node" && { [ -f package.json ] || npm init -y >/dev/null; } && npm install --ignore-scripts --save-exact bedrock-protocol@3.61.0 >/dev/null 2>&1)
(cd "$CACHE/node" && node -e 'console.log("bedrock-protocol", require("bedrock-protocol/package.json").version, "minecraft-data", require("minecraft-data/package.json").version)')
echo "npm tarball integrity: $(cd "$CACHE/node" && npm view bedrock-protocol@3.61.0 dist.integrity)"
