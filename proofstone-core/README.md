# proofstone-core

## Purpose

Host-side engine (layer L2): cell lifecycle, waits, oracles and verdicts. Depends on `proofstone-api`.

## Boundaries

- Must not depend on a platform, driver or Testcontainers implementation module.
- Must not call `Thread.sleep` or any sleep outside `dev.proofstone.core.await.Await` (AGENTS.md §3).
- Must not retry a failing check. Retry exists only in the flake detector.

## Public surface

None yet. Empty in M0.

## How to test

`./gradlew :proofstone-core:test`
