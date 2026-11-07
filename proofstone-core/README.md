# proofstone-core

## Purpose

Host-side engine (layer L2): cell lifecycle, waits, oracles and verdicts. Depends on `proofstone-api` and `proofstone-protocol`.

Today it loads `versions.yml` (ADR-0008) and plans cells from it (T-M1-06).

## Boundaries

- Must not depend on a platform or driver module. Containers go through Testcontainers only, with no bind mounts (ADR-0005).
- Must not call `Thread.sleep` or any sleep outside `dev.proofstone.core.await.Await` (AGENTS.md §3).
- Must not retry a failing check. Retry exists only in the flake detector.
- The version matrix is read only from an explicit path (`VersionsCatalog.load`). Never look it up by a bare relative path.
- A `TODO` pin is never used. The planner throws instead, so no container starts on an unpinned row.

## Public surface

- `dev.proofstone.core.versions.VersionsCatalog`: `load(versionsFile, schemaFile)`, `platform(id)`, `platformIds()`, `javaTag(row)`.
- `dev.proofstone.core.versions`: `Platform`, `Host`, `Row`, `Status`, `VersionNumber`, `VersionsException`.
- `dev.proofstone.core.cell.Cell`: one resolved environment (platform, mc, build, sha256, java, host image and digest, notes).
- `dev.proofstone.core.cell.CellPlanner`: `plan(platformId, versions, scope, families)`; `latest` means the highest `required` row, or the highest `experimental` row with a warning; `all` means the rows in `scope` plus the family versions.
- `dev.proofstone.core.cell.CellSelector`: parses `-Dproofstone.cells` / `PROOFSTONE_CELLS` (`platform@version`, `latest`, `all`); `Scope` (`pr`, `nightly`) and `VersionFamilies` (the `version-families` of `proofstone.yml`).

- `dev.proofstone.core.await.Await`: `until` and `untilBoth` predicate polling (25 ms to 250 ms backoff).
- `dev.proofstone.core.await.AwaitTimeoutException`: thrown on timeout, carries description, elapsed time and last observations.
- `dev.proofstone.core.host.PaperHost`: `start(cell, options)` boots one pinned Paper container, copies the agent and artefacts into `/plugins`, awaits `Done (` plus agent health, and returns it ready. `command(String)` runs a setup command through the agent; `health()`, `logs()` and `close()`.
- `PaperHost.gameHost()` and `gamePort()`: where a client driver joins. `agentToken()`: the bearer token for the agent
  (harness code only, never logged). `PaperHostOptions.withDebugPorts(true)` publishes the agent port for host-side tests
  (T-M1-05).
- `dev.proofstone.core.host.PaperHostOptions`, `Eula`, `LogBuffer`, `PaperHostException`: options, the EULA gate (`PROOFSTONE_ACCEPT_EULA=true`), the log buffer for repro bundles, and the failure type (message includes the log tail).

Java tags come from `java-rules` (first match wins). A row's `java:` field overrides them. A version that matches no rule is an error.

## How to test

`./gradlew :proofstone-core:test`

The tests read the real schema and seed from the repo root (`proofstone.repoRoot`, set in `build.gradle.kts`).

`./gradlew :proofstone-core:integrationTest --tests '*PaperHostIT*'` needs Docker and `PROOFSTONE_ACCEPT_EULA=true` (read <https://aka.ms/MinecraftEULA> first; the harness never accepts it for you). Without the variable the server tests are skipped with that reason. It is not part of `check`.
- `dev.proofstone.core.driver`: the client driver SPI (`ClientDriver`, `ClientSession`, `ConnectSpec`, `ChatMessage`, `ClientDriverException`). Implemented by `drivers/mcpl`.
- `dev.proofstone.core.junit.ProofstoneExtension`: the JUnit 5 extension behind `@Proofstone`, `@Matrix` and `@CellTest` (T-M1-10). One invocation per cell, displayed `paper@26.3`; one server per class and cell; a fresh `mcpl` client per test named `T<hash>`. It skips with a printed reason when the EULA is not accepted or Docker is missing. It reads the system properties `proofstone.repoRoot` and `proofstone.agentJar` and finds the driver through `ServiceLoader`.
