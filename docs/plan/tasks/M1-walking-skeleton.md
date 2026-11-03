# M1: Walking skeleton (Paper, newest version, native)

**Exit criterion.** A JUnit test does the following on the newest Paper row:
1. boots the server from `versions.yml`;
2. installs the Bukkit agent;
3. connects the `mcpl` client;
4. has the client run `/version`;
5. asserts the reply message.

In addition, agent `/v1/health` and `/v1/players/{n}` must match their golden files.

### T-M1-01 · Protocol v1 schemas: envelope, health, item, player-state, command
- tier: haiku
- depends: T-M0-04
- modules: testificate-protocol
- adrs: 0002
- specs: specs/agent-protocol.md §1–§3
- test-first: yes

**Goal.** Define the wire contract as JSON Schema and generate Java types from it.

**Do.**
- Write these schemas: `envelope.json`, `error.json`, `health.json`, `item.json`, `player-state.json`
  and `command.json`.
- Generate Java records with a pinned jsonschema2pojo, or hand-write records plus a schema test. Choose
  one and document the choice in the module README.
- Generate two type sets: one with Java 17 types for the host, and one with Java 8 bytecode types for
  the agent (a plain-class variant).

**Don't.**
- Add endpoints that are not in the spec.

**Acceptance.**
- [ ] Every schema validates its examples in `golden/` (T-M1-02 adds them; use inline examples until
  then).
- [ ] Generated types round-trip each example through serialise and parse with equal JSON output.

**Verify.**
`./gradlew :testificate-protocol:test`

### T-M1-02 · Golden files for health, players, give, command
- tier: haiku
- depends: T-M1-01
- modules: testificate-protocol
- adrs: 0002
- specs: specs/agent-protocol.md §4
- test-first: yes

**Goal.** Request and response fixtures that every agent must reproduce.

**Do.**
- Create `golden/<endpoint>/<case>.request.json` and `.response.json` files for these cases:
  - health OK;
  - 401 unauthorised;
  - player not found;
  - a player with an empty inventory;
  - giving an enchanted sword, then reading it back;
  - a command reply.
- Specify the placeholder rules for volatile fields (`uuid`, `time`, `requestId`) in `golden/README.md`.
- Add a `GoldenMatcher` test utility in `testificate-protocol` (`testFixtures`) that compares responses
  while ignoring the placeholders.

**Don't.**
- Encode version-specific raw strings in golden files. The `raw` field is matched as "present, a string".

**Acceptance.**
- [ ] `GoldenMatcher` has unit tests, including mismatch messages that show a JSON path.

**Verify.**
`./gradlew :testificate-protocol:test`

### T-M1-03 · Bukkit agent skeleton: HTTP server, auth, `/v1/health`
- tier: haiku
- depends: T-M1-01
- modules: platforms/bukkit/agent
- adrs: 0002, 0009
- specs: specs/agent-protocol.md §1, §2 (health), §5
- test-first: yes

**Goal.** A plugin jar that loads on every Bukkit-family server and answers `/v1/health`.

**Do.**
- Compile against the Spigot API at the lowest supported level (1.8.8 `spigot-api`, `compileOnly`), with
  `--release 8`.
- Use `com.sun.net.httpserver.HttpServer`. JSON comes from the shaded, relocated library chosen in T-M1-01.
- Read the token and port from environment variables. Without a token, the agent logs SEVERE and refuses
  to start the HTTP server.
- Detect the platform (Paper, Folia, Spigot or Purpur) via `Class.forName` probes. Report capabilities
  conservatively: only those actually detected.
- `plugin.yml`: `name: TestificateAgent`, `api-version` omitted (legacy-compatible),
  `folia-supported: true`, `load: STARTUP`.
- Add a `Scheduler` abstraction interface with a `MainThreadScheduler` implementation. Folia comes in M5.

**Don't.**
- Use any API newer than 1.8.8 without reflection and a capability guard.

**Acceptance.**
- [ ] `javap -v` shows major version 52 for all agent classes, checked by a build check.
- [ ] Unit tests cover the auth filter and the health JSON against the golden files.

**Verify.**
`./gradlew :platforms:bukkit:agent:test :platforms:bukkit:agent:verifyBytecode`

### T-M1-04 · Bukkit agent: command, players, give, reset, patch
- tier: haiku
- depends: T-M1-03, T-M1-02
- modules: platforms/bukkit/agent
- adrs: 0002
- specs: specs/agent-protocol.md §2, §3
- test-first: yes

**Goal.** The endpoints M1 and M2 need for setup and for reading player state.

**Do.**
- `POST /v1/command`: dispatch on the scheduler and capture output via a `CommandSender` proxy. The
  output is best-effort.
- `GET /v1/players`, `GET /v1/players/{name}`: build `PlayerState`. Items are serialised with
  `ItemMeta` → `item.json`. `raw` uses `ItemStack#serialize()` output for now.
- `POST .../give`: construct from `item.json` through the Bukkit API (`Material.matchMaterial`,
  `Enchantment.getByKey` on 1.13+, legacy names via a lookup table on older versions).
  `storedEnchants` uses `EnchantmentStorageMeta`. **Never clamp levels.**
- `POST .../reset`:
  - clear the inventory, armour, offhand and cursor;
  - set XP to 0 and the game mode to survival;
  - heal fully;
  - teleport to the world spawn + (0, 1, 0);
  - close any open inventory.
- `PATCH .../{name}`: level, exp, gamemode, location, op.

**Don't.**
- Add the world, events or config endpoints (those are M2).

**Acceptance.**
- [ ] Every golden case from T-M1-02 passes in the contract test (T-M1-05).
- [ ] An unknown material or enchantment returns error `UNKNOWN_KEY`, not a partial item.

**Verify.**
`./gradlew :platforms:bukkit:agent:test`, then the contract test after T-M1-05.

### T-M1-05 · Agent contract test runner (golden replay against a real server)
- tier: haiku
- depends: T-M1-04, T-M1-07
- modules: platforms/bukkit/agent (integrationTest)
- adrs: 0002, 0005
- specs: specs/agent-protocol.md §4
- test-first: no

**Goal.** Prove the agent matches the golden files on a real Paper server.

**Do.**
- Write an `integrationTest` source set that uses `PaperHost` (T-M1-07) to boot the newest Paper row with
  the agent.
- Replay each golden request and compare the response with `GoldenMatcher`.
- Tests that need a player use the `mcpl` client once T-M1-08 lands. Until then they are skipped with the
  reason "needs driver".

**Don't.**
- Use RCON.

**Acceptance.**
- [ ] Every golden case without a player passes on the newest Paper row.

**Verify.**
`./gradlew :platforms:bukkit:agent:integrationTest`

### T-M1-06 · Core: versions.yml loader, cell model, minimal planner
- tier: haiku
- depends: T-M0-04
- modules: testificate-core
- adrs: 0008
- specs: specs/versions-yml.md, architecture.md §2.2
- test-first: yes

**Goal.** Turn `versions.yml` plus a request (`platform`, `versions[]`) into `Cell` objects.

**Do.**
- Load the YAML, validate it with the schema, and resolve the Java tag from `java-rules`.
- Resolve `latest` to the highest `required` row. With no `required` rows, use the highest
  `experimental` row and add a warning to `Cell.notes`.
- Throw on an unknown version, with a list of the valid versions.

**Don't.**
- Implement the `all` keyword, selectors or capabilities (that is M3 or M5).

**Acceptance.**
- [ ] Unit tests cover: exact version, `latest`, unknown version, Java rule edges (1.20.4 → java17,
  1.20.5 → java21, 26.1 → java25).

**Verify.**
`./gradlew :testificate-core:test`

### T-M1-07 · Core: PaperHost container (Testcontainers), copy-in, readiness
- tier: sonnet
- depends: T-M1-06, T-M1-03
- modules: testificate-core
- adrs: 0005, 0006
- specs: architecture.md §3
- test-first: no

**Goal.** Start a Paper cell reliably, with no bind mounts.

**Do.**
- Use `GenericContainer` with the image pinned by digest from `versions.yml`. Set these environment
  variables:
  - `EULA`, set only when the user accepted it;
  - `TYPE`, `VERSION` and `PAPER_BUILD`;
  - `ONLINE_MODE=false`;
  - `ENFORCE_SECURE_PROFILE=false`;
  - `LEVEL_TYPE=minecraft:flat` with a fixed seed;
  - `SPAWN_PROTECTION=0`, `DIFFICULTY=peaceful`, `VIEW_DISTANCE=4`;
  - `ENABLE_RCON=false`;
  - the agent token and port.
- Copy the agent and the artefacts under test into `/plugins` with `copyFileToContainer` before start.
  Verify that the copy happened by checking the plugin list through the agent.
- Readiness means `Done (` in the log **and** agent health OK, polled through `Await`.
- Apply game rules after readiness through the agent command endpoint: `randomTickSpeed 0`,
  `doDaylightCycle false`, `doWeatherCycle false`, `doMobSpawning false`.
- Stream container logs into a buffer for repro bundles.

**Don't.**
- Expose ports to the host except in debug mode.

**Acceptance.**
- [ ] Boots the newest Paper row and reports the agent's capabilities.
- [ ] A missing artefact glob fails before the container starts, with the glob in the message.
- [ ] A server that loads without the agent (simulated by a bad jar) fails readiness with a clear message
  that includes the log tail.

**Verify.**
`./gradlew :testificate-core:integrationTest --tests '*PaperHostIT*'`

### T-M1-08 · mcpl driver: offline login, configuration, keep-alive, chat, command
- tier: sonnet
- depends: T-M0-01
- modules: drivers/mcpl
- adrs: 0004
- specs: specs/client-driver.md §1, §3
- test-first: no

**Goal.** A Java client that joins the newest-version server and exchanges chat.

**Do.**
- Use MCProtocolLib `ClientSession` with offline auth (`MinecraftProtocol(username)`).
- Handle the configuration phase:
  - acknowledge registries;
  - answer known packs;
  - finish configuration.
- Answer keep-alives.
- Implement `chat`, `command` (`ServerboundChatCommandPacket`) and `awaitMessage` over a buffered queue
  of system and player chat. Expose plain text from components.
- Implement `ClientDriver` and `ClientSession` from the SPI (place the SPI interfaces in
  `testificate-core`'s `driver` package).
- Write a transcript (timestamped packet names and summaries) for repro bundles.

**Don't.**
- Implement windows (that is M2), or predict client-side state.

**Acceptance.**
- [ ] Joins the newest Paper row and receives the join message.
- [ ] `command("version")` followed by `awaitMessage(/Paper/)` passes.
- [ ] A disconnect or kick fails with a reason, never a hang. The connect timeout is enforced.

**Verify.**
`./gradlew :drivers:mcpl:integrationTest`

### T-M1-09 · Core: Await utility
- tier: haiku
- depends: T-M0-03
- modules: testificate-core
- adrs: 0007
- specs: specs/test-api.md §5
- test-first: yes

**Goal.** The only allowed way to wait.

**Do.**
- `Await.until(String description, Supplier<T> probe, Predicate<T> ok, Duration timeout)`, returning `T`.
- Use exponential polling from 25 ms up to 250 ms. Record the last observed value. Exceptions thrown by
  the probe are recorded and polling continues.
- `AwaitTimeoutException` carries the description, the elapsed time, the last value and the last
  exception.
- Add a two-sided variant for the dual oracle, `untilBoth`, that records both last values.

**Don't.**
- Add retry semantics around assertions.

**Acceptance.**
- [ ] Unit tests use a fake clock, and the failure message format is pinned by a snapshot test.

**Verify.**
`./gradlew :testificate-core:test`

### T-M1-10 · API: minimal JUnit 5 extension and parameter resolvers
- tier: sonnet
- depends: T-M1-06, T-M1-07, T-M1-08, T-M1-09
- modules: testificate-api, testificate-core
- adrs: 0001
- specs: specs/test-api.md §1–§2
- test-first: no

**Goal.** Make `@Testificate`, `@Matrix` and `@CellTest` work for one platform.

**Do.**
- `@CellTest` is a `TestTemplate` with one invocation context per cell. The display name is
  `paper@1.21.11`.
- Start one server per class per cell (`PER_CLASS`), stored in the `ExtensionContext.Store` and closed
  at the end.
- Resolve `Cell`, `Agent` (thin wrapper over the protocol client) and `Client` (a fresh `mcpl` session
  per test, with a unique name `T<hash>`).
- When the EULA is not accepted, skip with that reason. When Docker is missing, skip with that reason.

**Don't.**
- Add the dual oracle, isolation modes or verdicts (those are M2 and M4).

**Acceptance.**
- [ ] Example test `examples/smoke/VersionCommandE2E` passes on the newest Paper row.
- [ ] Two test methods in one class share one server and get different client names.

**Verify.**
`./gradlew :examples:smoke:test`

### T-M1-11 · Conformance C01, C02 and the `:conformance:smoke` task
- tier: haiku
- depends: T-M1-10
- modules: conformance
- adrs: 0003
- specs: specs/self-tests.md §1
- test-first: no

**Goal.** The first harness self-tests, wired into the gate task.

**Do.**
- C01: health and protocol version check.
- C02: the client runs `/help` and receives a system message.
- `:conformance:smoke` runs C01 and C02 on the newest Paper row. Remove the placeholder from T-M0-02.
- Add a CI job, `smoke`, using ubuntu-latest with Docker. It is required on PRs.

**Don't.**
- Add more scenarios (those are M2).

**Acceptance.**
- [ ] The CI `smoke` job is green and required.

**Verify.**
`./gradlew :conformance:smoke`

### T-M1-99 · Milestone review M1
- tier: sonnet
- depends: T-M1-01, T-M1-02, T-M1-03, T-M1-04, T-M1-05, T-M1-06, T-M1-07, T-M1-08, T-M1-09, T-M1-10, T-M1-11
- modules: docs/plan
- adrs: 0010
- specs: plan/ROADMAP.md
- test-first: no

**Goal.** Verify the exit criterion and refine the M2 cards.

**Do.**
- Run all the gates, record boot and join timings, and record gotchas in `docs/platforms/paper.md`.
- Update the M2 cards with the real package and class names.

**Don't.**
- Start M2.

**Acceptance.**
- [ ] The exit criterion is demonstrated with gate output.
- [ ] The M2 cards are updated, and `docsCheck` is green.

**Verify.**
`./gradlew check docsCheck :conformance:smoke`
