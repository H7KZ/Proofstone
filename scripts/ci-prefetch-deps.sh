#!/usr/bin/env bash
# Downloads every Gradle dependency before the gates run (T-M6-113, ADR-0019). Hosted `gates` failed on a cold cache when a repository
# answered "not found" for an artefact that exists (text-serializer-nbt-0.2.1.jar on repo.opencollab.dev, kotlin-stdlib on
# plugins.gradle.org); Gradle does not retry a 404. Only this download step is retried, and only when its own output names a dependency
# resolution error. Compile, test and every other failure ends it at once. The gates command itself is never retried.
# Usage: bash scripts/ci-prefetch-deps.sh [extra gradle args]
set -uo pipefail
cd "$(dirname "$0")/.."

attempts=3
log=$(mktemp)
trap 'rm -f "$log"' EXIT

for ((i = 1; i <= attempts; i++)); do
  ./gradlew -I scripts/prefetch-deps.init.gradle.kts prefetchDependencies "$@" 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  if [[ $status -eq 0 ]]; then
    echo "prefetch: OK (attempt $i of $attempts)"
    exit 0
  fi
  if ! grep -qE "Could not (find|resolve|GET|HEAD|download)|Could not parse POM|Received status code" "$log"; then
    echo "prefetch: failed for a reason that is not a dependency download; not retrying." >&2
    exit "$status"
  fi
  if [[ $i -lt $attempts ]]; then
    echo "prefetch: dependency download failed (attempt $i of $attempts); retrying after $((i * 20))s." >&2
    sleep $((i * 20))
  fi
done
echo "prefetch: dependency download failed $attempts times." >&2
exit 1
