# Architecture

Related ADRs: [0001](adr/0001-java-junit5-public-api.md) (Java test API),
[0002](adr/0002-server-truth-via-agent.md) (agent),
[0003](adr/0003-dual-oracle.md) (dual oracle),
[0004](adr/0004-client-driver-latest-protocol-plus-viaproxy.md) (driver),
[0005](adr/0005-containers-testcontainers-no-bind-mounts.md) (containers),
[0016](adr/0016-topology-cells.md) (topology cells),
[0008](adr/0008-versions-yml-single-source-of-truth.md) (versions.yml),
[0009](adr/0009-gradle-monorepo.md) (build).

## 1. Layers

Each layer may depend only on the layers below it. Plugin authors touch **only Layer 1**.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ L1  proofstone-api        JUnit 5 extension + DSL. Semver. The only public API. │
├──────────────────────────────────────────────────────────────────────────────┤
│ L2  proofstone-core       Cell planning, topology lifecycle, oracles, await,    │
│                            verdicts (pass/fail/invalid/vacuous), repro bundles   │
├───────────────┬──────────────────────────────┬───────────────────────────────┤
│ L3a Platform  │ L3b Client driver SPI        │ L3c Wire protocol               │
│ pack SPI      │ (mcpl, mineflayer-ref,       │ (proofstone-protocol: JSON     │
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

### 2.1 `proofstone-api` (L1)
- **Annotations:**
  - `@Proofstone(plugins, mods, datapacks, acceptEula, config)`
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

### 2.2 `proofstone-core` (L2)
- **Cell planner:** expands `@Matrix` with `versions.yml` into cells. It filters by capabilities and by
  status (`required`/`experimental`), and by CLI or environment selectors such as
  `-Dproofstone.cells=paper@1.21.11`.
- **Lifecycle** (see §3).
- **Verdict engine.** It produces one of:
  - `PASS`;
  - `FAIL`;
  - `INVALID` (conformance failed for the cell);
  - `VACUOUS` (passed in the control run);
  - `FLAKY` (the verdict changed across repeats);
  - `DISAGREE` (the drivers disagreed in a differential run).
- **Repro bundles** are written to `build/proofstone/<platform>@<mc>/<TestClass>.<method>/`.
- **Reports:** JUnit XML plus `proofstone-report.json`, which is machine-readable for agents and CI.

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

**Proxy packs (M7, [ADR-0016](adr/0016-topology-cells.md)).** `velocity` and `bungee` are proxy packs. A proxy pack
supplies the proxy host (`itzg/mc-proxy`, by digest per Java tag), the proxy agent, the proxy config renderer for its one
forwarding mode (Velocity `modern`, BungeeCord `legacy`), the matching backend settings, and its log allowlist. Proxy rows
live under `proxies` in `versions.yml` (schema 3), paired with backend rows. A **topology cell** is one proxy row plus a
backend row set (N named backends on one backend row), with the identity `<proxy>@<version>+<backend>@<mc>`, for example
`velocity@4.3.1+paper@26.3`. Backends keep their own pack (Paper with the Bukkit agent), unchanged. Waterfall has no pack
(end-of-life; its newest build rejects the newest client).

**Mod packs (M8, [ADR-0017](adr/0017-mod-platform-packs.md)).** `fabric` and `neoforge` are mod packs: the host
(`itzg/minecraft-server`, `TYPE=FABRIC` or `NEOFORGE`, by digest per Java tag) started by `LoaderHost` next to `PaperHost`, the
pinned launcher or installer copied in, the mod agent (Java 25 bytecode on 26.x; Fabric's needs the pinned Fabric API), and
an empty-at-first log allowlist. Loader rows live under `loaders` in `versions.yml` (schema 4), newest native row only. A
**loader cell** has the identity `<loader>@<mc>` (`fabric@26.3`). Artefacts: mods to `/mods`, data packs to the world's
`datapacks` (any cell with `DATAPACKS_VANILLA`), resource packs served from the harness JVM on loopback.

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
- **Schema-first:** JSON Schema files in `proofstone-protocol/schemas/` produce Java types for the core
  and the Java agents. Non-Java agents, such as a Bedrock script pack, generate TypeScript types from the
  same schemas.
- **Golden files:** every endpoint has example request and response fixtures. Each agent must reproduce
  them in its contract tests.
- Full spec: [specs/agent-protocol.md](specs/agent-protocol.md).

### 2.6 `versions.yml` (L4 data)
Each row is a platform and version, with:
- the Java image tag;
- the server build pin;
- the image digest, per Java tag ([ADR-0013](adr/0013-host-digest-per-java-tag.md));
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

### 3.1 Topology cells ([ADR-0016](adr/0016-topology-cells.md))

```
plan topology cell ─► start backends (each: server + Bukkit agent, alias server-<name>)
   ─► start proxy (proxy agent, alias proxy; forwarding secret generated per cell, copied in from memory)
   ─► wait readiness on every container (log line + agent /v1/health)
   ─► CONFORMANCE P01..P05 (cached per cell per run) ── fail ─► all tests on the topology cell = INVALID
   ─► for each test: client joins via the proxy, awaited on the proxy agent and the default backend's agent
        reset on the backend the player is on; leak baseline on every backend
        ACT (client only; transferTo = /server <name>)  →  ASSERT (client, proxy agent, backend agents)
        log guard per container  →  restore configs on every backend  →  disconnect, await proxy lists no player
   ─► reports + one bundle with a log per container ─► stop every container
```

- `PER_CLASS` keeps one topology (proxy and all backends) per class per cell; `PER_TEST` starts a new one per test.
- Only the proxy's game port is published. Backends are reachable on the cell network only.
- A join timeout names the T-M6-14 stage and the hop: client to proxy, or proxy to backend `<name>`.

## 4. Planned repository layout

```
proofstone/
  versions.yml
  gradle/libs.versions.toml
  build-logic/                     convention plugins (java-host, java-agent-legacy, …)
  proofstone-api/                 L1
  proofstone-core/                L2
  proofstone-protocol/            L3c schemas + generated types + golden files
  drivers/
    mcpl/                          primary Java driver
    mineflayer-ref/                Node sidecar + Dockerfile (reference driver)
  platforms/
    agent-core/                    envelope, auth, HTTP server, log buffer shared by the agents (Java 8 bytecode)
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
