# Architecture

Related ADRs: [0001](adr/0001-java-junit5-public-api.md) (Java test API),
[0002](adr/0002-server-truth-via-agent.md) (agent),
[0003](adr/0003-dual-oracle.md) (dual oracle),
[0004](adr/0004-client-driver-latest-protocol-plus-viaproxy.md) (driver),
[0005](adr/0005-containers-testcontainers-no-bind-mounts.md) (containers),
[0008](adr/0008-versions-yml-single-source-of-truth.md) (versions.yml),
[0009](adr/0009-gradle-monorepo.md) (build).

## 1. Layers

Each layer may depend only on the layers below it. Plugin authors touch **only Layer 1**.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ L1  testificate-api        JUnit 5 extension + DSL. Semver. The only public API. │
├──────────────────────────────────────────────────────────────────────────────┤
│ L2  testificate-core       Cell planning, topology lifecycle, oracles, await,    │
│                            verdicts (pass/fail/invalid/vacuous), repro bundles   │
├───────────────┬──────────────────────────────┬───────────────────────────────┤
│ L3a Platform  │ L3b Client driver SPI        │ L3c Wire protocol               │
│ pack SPI      │ (mcpl, mineflayer-ref,       │ (testificate-protocol: JSON     │
│ (paper, folia,│  bedrock, …)                 │  schemas + generated types,     │
│ velocity, …)  │                              │  versioned /v1)                 │
├───────────────┴──────────────────────────────┴───────────────────────────────┤
│ L4  Runtime: Docker via Testcontainers; images pinned by digest; versions.yml   │
└──────────────────────────────────────────────────────────────────────────────┘
          in containers:  server + agent ◄─(HTTP /v1)─ core
                          ViaProxy (only bridged cells)
                          reference driver sidecar (only differential runs)
```

## 2. Components

### 2.1 `testificate-api` (L1)
- **Annotations:**
  - `@Testificate(plugins, mods, datapacks, acceptEula, config)`
  - `@Matrix(platform, versions, java, drivers)` (repeatable)
  - `@CellTest` (a `@TestTemplate`)
  - `@Requires(Capability...)`
  - `@Isolation(PER_CLASS | PER_TEST)`
- **Injected types:**
  - `Cell`, with metadata such as `cell.version()` and `cell.capabilities()`;
  - `Agent`, the server oracle and setup;
  - `Client`, the client oracle and actions;
  - `Topology`, for multi-server cells.
- **Values:**
  - `Item` (a version-neutral item spec);
  - `BlockPos`;
  - `ItemView`, `PlayerState` and `WindowView` (snapshots, never live objects).
- **Assertions:**
  - `DualAssert.that(agentView, clientView)` requires both views to agree;
  - `agent.assertNoErrorsLogged()`.
- **Waits:** `await*` helpers only. Each takes a predicate and a timeout, and on failure prints the last
  observed value from **both** oracles.
- Full spec: [specs/test-api.md](specs/test-api.md).

### 2.2 `testificate-core` (L2)
- **Cell planner:** expands `@Matrix` with `versions.yml` into cells. It filters by capabilities and by
  status (`required`/`experimental`), and by CLI or environment selectors such as
  `-Dtestificate.cells=paper@1.21.11`.
- **Lifecycle** (see §3).
- **Verdict engine.** It produces one of:
  - `PASS`;
  - `FAIL`;
  - `INVALID` (conformance failed for the cell);
  - `VACUOUS` (passed in the control run);
  - `FLAKY` (the verdict changed across repeats);
  - `DISAGREE` (the drivers disagreed in a differential run).
- **Repro bundles** are written to `build/testificate/<cell>/<test>/`.
- **Reports:** JUnit XML plus `testificate-report.json`, which is machine-readable for agents and CI.

### 2.3 Platform packs (L3a)
A pack is a directory, `platforms/<id>/`, containing:
- `pack.yml`: id, family (`bukkit`, `velocity`, `bungee`, `fabric`, `neoforge`, `bedrock-bds`, …), host
  image recipe, supported version range, capabilities, readiness probe, and agent artefact coordinates;
- the agent source (Gradle subproject or subprojects);
- `conformance/`: scenarios that must pass on every cell of this pack;
- `GOTCHAS.md` (linked from `docs/platforms/<id>.md`).

The `PlatformPack` SPI (Java) consists of:
- `hostContainer(cell)`;
- `installAgent(container)`;
- `installUnderTest(container, artefacts)`;
- `readiness()`;
- `capabilities(cell)`;
- `logErrorPatterns()` and `logAllowlist()`.

Agent families share the **wire protocol**, so core code doesn't care which platform is on the other side.

### 2.4 Client drivers (L3b)
- SPI: `ClientDriver`, which provides `connect(name, endpoint)` and returns a `ClientSession` with
  `chat`, `command`, `awaitMessage`, `useBlock`, `openWindow`, `click(slot, button, mode)`,
  `closeWindow`, `window()`, `inventory()`, `experience()`, `position()` and `disconnect`.
- **Primary: `mcpl`.** A Java driver on MCProtocolLib that speaks **only the newest protocol**
  ([ADR-0004](adr/0004-client-driver-latest-protocol-plus-viaproxy.md)).
  - **The server is authoritative.** The driver never predicts slot contents. After each action it waits
    for the server's slot or content sync.
- **Reference: `mineflayer-ref`.** A separate implementation in a sidecar image, used for differential
  runs on versions where it is native.
- **Bedrock: `bedrock`.** Comes later ([platforms.md](platforms.md)).
- **Version translation:** the core inserts a ViaProxy container when
  `driver.protocol != server.protocol`, and marks the cell as *bridged*.
- Full spec: [specs/client-driver.md](specs/client-driver.md).

### 2.5 Wire protocol (L3c)
- HTTP/1.1 with JSON over loopback inside the Docker network, versioned under `/v1`. A per-cell bearer
  token is injected through an environment variable.
- **Schema-first:** JSON Schema files in `testificate-protocol/schemas/` produce Java types for the core
  and the Java agents. Non-Java agents, such as a Bedrock script pack, generate TypeScript types from the
  same schemas.
- **Golden files:** every endpoint has example request and response fixtures. Each agent must reproduce
  them in its contract tests.
- Full spec: [specs/agent-protocol.md](specs/agent-protocol.md).

### 2.6 `versions.yml` (L4 data)
Each row is a platform and version, with:
- the Java image tag;
- the server build pin;
- the image digest;
- the protocol number;
- `status`;
- `native drivers`;
- `notes`.

A scheduled workflow proposes new rows ([versioning.md](versioning.md)).

## 3. Lifecycle of one cell

```
plan cells ─► pull pinned images ─► start topology (server [+proxy] [+ViaProxy])
   ─► copy agent + artefacts in (no bind mounts) ─► wait readiness (log + agent /v1/health)
   ─► CONFORMANCE (cached per cell per run) ── fail ─► all tests on cell = INVALID
   ─► for each test:
        agent.snapshotConfig()  →  agent.resetWorld/Player  →  connect fresh client(s)
        ARRANGE (agent)  →  ACT (client only; agent observes)  →  ASSERT (both oracles)
        agent.assertNoErrorsLogged()  →  restore config  →  disconnect
   ─► collect reports + repro bundles ─► stop topology
```

Isolation:
- **Default is `PER_CLASS`:** one server per class per cell, reset between tests.
- **`PER_TEST`:** a new server for each test, for tests that change the world destructively.

## 4. Planned repository layout

```
testificate/
  versions.yml
  gradle/libs.versions.toml
  build-logic/                     convention plugins (java-host, java-agent-legacy, …)
  testificate-api/                 L1
  testificate-core/                L2
  testificate-protocol/            L3c schemas + generated types + golden files
  drivers/
    mcpl/                          primary Java driver
    mineflayer-ref/                Node sidecar + Dockerfile (reference driver)
  platforms/
    bukkit/                        one agent for Paper/Folia/Spigot/Purpur (Java 8 bytecode)
      paper/  folia/  spigot/  purpur/   pack.yml + conformance per flavour
    velocity/  bungee/             (M7)
    fabric/  neoforge/             (M8, multi-version via Stonecutter)
    bedrock-bds/  geyser/          (M9)
  images/                          Dockerfiles we own (viaproxy, mineflayer-ref, slim server hosts)
  conformance/                     shared vanilla scenarios + runner
  integrations/
    gradle-plugin/  maven-plugin/  github-action/
  examples/
    maven-paper-plugin/  gradle-paper-plugin/  fabric-mod/
  docs/
```

## 5. Cross-cutting rules

- **Time:** all waits go through `Await` in core. Tick-based waits use `agent.awaitTicks(n)`.
- **Randomness:**
  - worlds use a fixed seed, are flat, and are generated without structures;
  - these game rules are set: `randomTickSpeed=0`, `doDaylightCycle=false`, `doWeatherCycle=false`,
    `doMobSpawning=false`;
  - difficulty is peaceful unless the test asks otherwise.
- **Logs:** each pack declares error patterns and an allowlist. Anything else at WARN or above from any
  plugin fails the test.
- **Security:** agents bind only on the Docker network, require the token, and never ship in user builds.
