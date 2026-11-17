#!/usr/bin/env bash
# SPIKE (T-M9-01). Not production code. Runs TcUdpProbe against the pinned itzg image.
# Usage: bash scripts/dockerlock.sh bash scripts/spike/geyser/tc-udp-probe.sh
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
hostpath() { cygpath -m "$1" 2>/dev/null || echo "$1"; }
IMAGE=itzg/minecraft-server@sha256:e2fabc536673f8c6b343055c93f7a73e10caa79cd3368f645987593c65adf1e0
SPIKE_CP=$(cd "$ROOT" && ./gradlew -q --init-script scripts/spike/geyser/tc-classpath.init.gradle :proofstone-core:spikeClasspath --max-workers=1 | sed -n 's/^SPIKE_CP=//p')
JAVA=${SPIKE_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}
"$JAVA" -cp "$SPIKE_CP" "$(hostpath "$ROOT/scripts/spike/geyser/TcUdpProbe.java")" "$IMAGE"
