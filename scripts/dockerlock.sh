#!/usr/bin/env bash
# Usage: dockerlock.sh <command...>
# Runs the command while holding a machine-wide lock, so only one Docker/Testcontainers Gradle run is active at once.
# Docker Desktop on this machine fails ("Could not find a valid Docker environment") when several suites run in parallel.
LOCK="${TMPDIR:-/tmp}/proofstone-docker.lock"
waited=0
until mkdir "$LOCK" 2>/dev/null; do
  # A lock older than 40 minutes is stale (crashed run): take it over.
  if [ -d "$LOCK" ] && [ -n "$(find "$LOCK" -maxdepth 0 -mmin +40 2>/dev/null)" ]; then
    rmdir "$LOCK" 2>/dev/null
    continue
  fi
  sleep 10
  waited=$((waited + 10))
  if [ $((waited % 120)) -eq 0 ]; then echo "dockerlock: waiting for the Docker lock (${waited}s)" >&2; fi
done
trap 'rmdir "$LOCK" 2>/dev/null' EXIT INT TERM
"$@"
