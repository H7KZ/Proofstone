#!/usr/bin/env bash
# Local replacement for the removed `ci.yml` (maintainer decision 2026-11-08: no CI, local only).
# Runs what the old required checks ran: `./gradlew check docsCheck` (the `gates` job), and the pinned Renovate
# config validation (the `renovate-validate` job) when renovate.json differs from origin/main.
# Needs no Docker. Real-server gates (`:conformance:smoke`, the card's Verify commands) still go through
# `scripts/dockerlock.sh` with PROOFSTONE_ACCEPT_EULA=true in the command environment only.
# Usage: bash scripts/gates.sh [extra gradle args, e.g. --max-workers=1]
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew check docsCheck "$@"

# Release-script tests (T-M6-09): fixture-based, no publish, no Docker.
bash scripts/release-gate-test.sh

if ! git diff --quiet origin/main -- renovate.json 2>/dev/null; then
  # Version pinned like the old workflow did (renovate@44.171.1); Node comes from the machine.
  npx --yes --package renovate@44.171.1 -- renovate-config-validator --strict renovate.json
else
  echo "renovate.json unchanged against origin/main: validation skipped."
fi
echo "gates: OK"
