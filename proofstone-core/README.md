# proofstone-core

## Purpose

Host-side engine (layer L2): cell lifecycle, waits, oracles and verdicts. Depends on `proofstone-api` and `proofstone-protocol`.

Today it loads `versions.yml` (ADR-0008) and plans cells from it (T-M1-06).

## Boundaries

- Must not depend on a platform or driver module. Containers go through Testcontainers only, with no bind mounts (ADR-0005).
- Must not call `Thread.sleep` or any sleep outside `io.github.h7kz.proofstone.core.await.Await` (AGENTS.md §3).
- Must not retry a failing check. Retry exists only in the flake detector.
- The version matrix is read only from an explicit path (`VersionsCatalog.load`). Never look it up by a bare relative path.
- A `TODO` pin is never used. The planner throws instead, so no container starts on an unpinned row.

## Public surface

- `io.github.h7kz.proofstone.core.versions.VersionsCatalog`: `load(versionsFile, schemaFile)`, `platform(id)`, `platformIds()`, `javaTag(row)`.
- `io.github.h7kz.proofstone.core.versions`: `Platform`, `Host`, `Row`, `Status`, `VersionNumber`, `VersionsException`.
- `io.github.h7kz.proofstone.core.cell.Cell`: one resolved environment (platform, mc, build, sha256, java, host image and digest, notes).
- `io.github.h7kz.proofstone.core.cell.CellPlanner`: `plan(platformId, versions, scope, families)`; `latest` means the highest `required` row, or the highest `experimental` row with a warning; `all` means the rows in `scope` plus the family versions.
- Loader cells (T-M8-04, ADR-0017), package `cell`: `LoaderCell` (identity `fabric@26.3`, every pin; a `TODO` vanilla or Fabric API
  hash stays `null` for the host to refuse), `CellPlanner.planLoaders(loader, versions, scope)` and `isLoader(id)` (`latest` is the
  highest `required` row and never an experimental one), `CellSelector.loaders()` (`fabric@`/`neoforge@` entries, never half of a
  `+` topology), and `CiMatrix` loader entries (`kind: "loader"`, after the topology cells). `VersionsCatalog` reads the schema 4
  `loaders` key and refuses a bridged or malformed row at load. Paper and topology cells select and plan exactly as before. No host
  runs a loader cell yet (T-M8-07).
- `io.github.h7kz.proofstone.core.cell.CellSelector`: parses `-Dproofstone.cells` / `PROOFSTONE_CELLS` (`platform@version`, `latest`, `all`); `Scope` (`pr`, `nightly`) and `VersionFamilies` (the `version-families` of `proofstone.yml`).

- `io.github.h7kz.proofstone.core.await.Await`: `until` and `untilBoth` predicate polling (25 ms to 250 ms backoff).
- `io.github.h7kz.proofstone.core.await.AwaitTimeoutException`: thrown on timeout, carries description, elapsed time and last observations.
- `io.github.h7kz.proofstone.core.driver.JoinTimeoutException`: a `ClientDriverException` for a join that timed out; carries the player, stalled stage, protocol offered and last packet received (T-M6-14).
- `io.github.h7kz.proofstone.core.host.PaperHost`: `start(cell, options)` boots one pinned Paper container, copies the agent and artefacts into `/plugins`, awaits `Done (` plus agent health, and returns it ready. `command(String)` runs a setup command through the agent; `health()`, `logs()` and `close()`.
- `PaperHost.gameHost()` and `gamePort()`: where a client driver joins. `agentToken()`: the bearer token for the agent
  (harness code only, never logged). `PaperHostOptions.withDebugPorts(true)` publishes the agent port for host-side tests
  (T-M1-05).
- `io.github.h7kz.proofstone.core.host.PaperHostOptions`, `Eula`, `LogBuffer`, `PaperHostException`: options, the EULA gate (`PROOFSTONE_ACCEPT_EULA=true`), the log buffer for repro bundles, and the failure type (message includes the log tail).
- Topologies (T-M7-07, ADR-0016), package `host`: `TopologyHost.start(cell, backendNames, backendOptions, proxyOptions)`
  starts N `PaperHost` backends (alias `server-<name>`) and then one `ProxyHost` (alias `proxy`, `itzg/mc-proxy` by digest,
  jar sha256 and Java checked in the container) on one network, each ready by log line and agent health. `TopologyConfig`
  renders the copied proxy and backend files (Velocity modern, BungeeCord legacy, the harness rate limits) and the
  per-topology forwarding secret, which reaches containers only (never a host file, log, message or bundle). A failed start
  stops every container and names the one that failed with every log tail. `mark()`, `hop(...)` and `describe(timeout, marks,
  target)` add the hop (client to proxy, or proxy to backend) and the proxy and backend log tails to a join timeout.
  `PaperHostOptions.withNetwork(network, alias)` and `withFiles(...)` are what a backend needs; their defaults leave the
  single-server path as it was.

- `io.github.h7kz.proofstone.core.cell.CiMatrix`: the CI matrix JSON for `printMatrix --scope=pr|nightly` (T-M3-07), built with `CellPlanner` and `all`. A platform with a `TODO` pin is skipped with its reason.

Java tags come from `java-rules` (first match wins). A row's `java:` field overrides them. A version that matches no rule is an error.

## How to test

`./gradlew :proofstone-core:test`

The tests read the real schema and seed from the repo root (`proofstone.repoRoot`, set in `build.gradle.kts`).

`./gradlew :proofstone-core:integrationTest --tests '*PaperHostIT*'` needs Docker and `PROOFSTONE_ACCEPT_EULA=true` (read <https://aka.ms/MinecraftEULA> first; the harness never accepts it for you). Without the variable the server tests are skipped with that reason. It is not part of `check`.

`./gradlew :proofstone-core:topologyTest --tests '*VelocityTopologyIT'` (or `*BungeeCordTopologyIT`, `*TopologyFaultIT`)
boots a proxy and `paper@26.3` backends. Docker, the EULA variable and `scripts/dockerlock.sh` as above; one proxy per
invocation (M7 flake rule). Not part of `check`.
- `io.github.h7kz.proofstone.core.verdict` (T-M4-01): the verdict engine. `VerdictEngine.resolve(CellRun, ConformanceResult)` gives
  one `CellResult` per test and cell; `Verdict` (`PASS`, `FAIL`, `INVALID`, `VACUOUS`, `FLAKY`, `DISAGREE`, `SKIPPED`,
  green = `PASS` or `SKIPPED` only); `Attempt` (one run: PASS, FAIL or SKIPPED); `SkipReason` (the allowed skip reasons);
  `ConformanceCache` (conformance once per cell per run); `ReportWriter` (`proofstone-report.json`, schema
  `proofstone-report/1`) and `JunitXml` (`proofstone-junit.xml`, non-green verdicts as failures). `RunReport` collects one verdict per test, cell and driver and writes both files when the JVM's run ends (T-M4-09; the directory is `-Dproofstone.reportDir`, default `build/proofstone`). Precedence: INVALID,
  DISAGREE, FLAKY, VACUOUS, then the run's own result. The engine is pure: it takes run results as input and never runs
  a test. The verdict types stay in core, not `proofstone-api`, so the frozen v0 baseline does not change.
- `io.github.h7kz.proofstone.core.driver`: the client driver SPI (`ClientDriver`, `ClientSession`, `ConnectSpec`, `ChatMessage`, `ClientDriverException`). Implemented by `drivers/mcpl`.
- `io.github.h7kz.proofstone.core.junit.ProofstoneExtension`: the JUnit 5 extension behind `@Proofstone`, `@Matrix` and `@CellTest` (T-M1-10). One invocation per cell, displayed `paper@26.3`; one server per class and cell; a fresh `mcpl` client per test named `T<hash>`. It skips with a printed reason when the EULA is not accepted or Docker is missing. It reads the system properties `proofstone.repoRoot` and `proofstone.agentJar` and finds the driver through `ServiceLoader`.
- `io.github.h7kz.proofstone.core.junit.VerdictGate` (T-M4-03): applies `VerdictEngine` to each passing test. With `-Dproofstone.controlRun=true` no plugin jar is installed (the agent and fixture world stay), so a pass is `VACUOUS` and fails the test, unless the test declares `@ControlRun(EXPECTED_PASS, reason)`. Any other value of the property is an error.

- Differential run (T-M4-06, package `junit`, `DifferentialRun`): with `-Dproofstone.differential=true` each test on the reference driver's native row (`mineflayer-ref`, 26.1.2) runs twice on one server, first with `mcpl`, then with `mineflayer-ref`. `AwaitOracleWaiter` records the client side of every dual-oracle assertion point; the second run compares the two lists in order and reports a mismatch as `DISAGREE`, through `VerdictEngine`. The compared fields are the pinned list in `DifferentialRun.canonicalItem` (no timing fields, no one-sided fields). Both drivers' observations and transcripts go to `differential/` in the bundle, under `<test>/<driver>/`. Any other cell is not run in that mode (`PROOFSTONE NOT DIFFERENTIAL`, not a pass). Any other value of the property is an error.
- Flake detector (T-M4-04, package `junit`, `FlakeDetector`, `RepeatLedger`): `-Dproofstone.repeat=N` (N at least 3, anything else is an error) runs each cell N times in a row. Every run gets a repro bundle (`<TestClass>.<method>.rK`). After the last run, `VerdictGate.judgeRepeats` applies `VerdictEngine` to all runs: differing verdicts give `FLAKY`, a control run where every run passes gives `VACUOUS`, and a test that fails every time is left to JUnit's per-run failures. No run is retried.
- Repro bundles (T-M4-02, package `junit`, `BundleWriter`): a test that does not pass writes `build/proofstone/<platform>@<mc>/<TestClass>.<method>/` from afterEach, while the server is still up. Files: `server.log`, `viaproxy.log` (bridged cells only), `agent-transcript.jsonl` (`PaperHost.agentTranscript()`), `driver-transcript.jsonl`, `cell.json`, `snapshot.json` (`GET /v1/snapshot`, or the error if the agent does not serve it), `host.json` (OS, Java, Docker server version, driver protocol and the ViaProxy image digest when bridged), `rerun.txt` (the Gradle command with every switch that was set: `-Dproofstone.repeat`, `-Dproofstone.controlRun`, `-Dproofstone.injectConformanceFault` and the others). A single-run VACUOUS gets a bundle too (T-M4-09). The agent token is redacted from every file and each file is scanned again after writing. Bundles are local and never committed.
