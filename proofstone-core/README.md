# proofstone-core

## Purpose

Host-side engine (layer L2): cell lifecycle, waits, oracles and verdicts. Depends on `proofstone-api` and `proofstone-protocol`.

Today it loads `versions.yml` (ADR-0008) and plans cells from it (T-M1-06).

## Boundaries

- Must not depend on a platform, driver or Testcontainers implementation module.
- Must not call `Thread.sleep` or any sleep outside `dev.proofstone.core.await.Await` (AGENTS.md §3).
- Must not retry a failing check. Retry exists only in the flake detector.
- The version matrix is read only from an explicit path (`VersionsCatalog.load`). Never look it up by a bare relative path.
- A `TODO` pin is never used. The planner throws instead, so no container starts on an unpinned row.

## Public surface

- `dev.proofstone.core.versions.VersionsCatalog`: `load(versionsFile, schemaFile)`, `platform(id)`, `platformIds()`, `javaTag(row)`.
- `dev.proofstone.core.versions`: `Platform`, `Host`, `Row`, `Status`, `VersionNumber`, `VersionsException`.
- `dev.proofstone.core.cell.Cell`: one resolved environment (platform, mc, build, sha256, java, host image and digest, notes).
- `dev.proofstone.core.cell.CellPlanner`: `plan(platformId, versions)`; `latest` means the highest `required` row, or the highest `experimental` row with a warning.

Java tags come from `java-rules` (first match wins). A row's `java:` field overrides them. A version that matches no rule is an error.

## How to test

`./gradlew :proofstone-core:test`

The tests read the real schema and seed from the repo root (`proofstone.repoRoot`, set in `build.gradle.kts`).
