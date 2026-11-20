# M11: API gaps found by plugin E2E suites

Written 2026-11-20. Cards for public API that a real plugin's end-to-end suite (the Disenchantment suite, its `docs/engineering/E2E.md`
"Proofstone gaps" list) needed and the test API lacks. Each is an `api-change` card: additions only, a reviewed `api-baseline.txt`
diff, no existing signature changed. The **Flake rule** in [M8-mods.md](M8-mods.md) applies to every real-server acceptance.

### T-M11-01 · `Agent.logLines()`: the server log as plain text
- tier: sonnet
- depends: —
- modules: proofstone-api, proofstone-core, conformance, docs/specs, docs/user
- adrs: 0002
- specs: specs/test-api.md §3, specs/agent-protocol.md §2
- test-first: yes

**Goal.** A test reads the server log lines since it started, as plain text, through the public API: to assert a per-cell
expectation that the server logged (for example a plugin's start-up line) and that no stack trace or `WARN` was logged during an
operation. Label `api-change`.

**Evidence.** Gap 1 of the Disenchantment E2E suite. The agent already captures every record (`LogBuffer`, `GET /v1/log`,
log-record.json) and the lifecycle already reads it for the per-test error check, but `Agent` exposes no way to read it.

**Do.**
- Add `Agent.logLines()` and `Agent.logLines(Pattern, Duration)` as default methods that throw `UnsupportedOperationException`
  (the style of `Client.bedrock` and `Cell.capabilities()`), so no implementer breaks.
- Implement them in core over `GET /v1/log?since=<test cursor>&minLevel=TRACE` for `HostAgent` and `ProxyAgent`, with the cursor
  the lifecycle already takes before the client joins (cell and topology invocations).
- A ring buffer that dropped records since the cursor throws; an empty window is an empty list.
- Unit tests in core and api, one real-server conformance scenario (C-L01), docs, a regenerated `api-baseline.txt`.

**Don't.**
- Change the wire protocol (no new message, no version bump: the endpoint and schema exist), the agents or add a capability.
- Add a sleep or retry; the wait goes through `Await`. Include boot lines (the window is "since the test started").

**Acceptance.**
- [ ] `api-baseline.txt` gains only the two `Agent` default methods; `ApiSurfaceTest` is green.
- [ ] Unit tests: window cursor, plain-text rendering with stack traces, dropped records throw, await match and timeout.
- [ ] C-L01 (`ConformanceLogLinesTest`) green on `paper@26.3` in three separate invocations, XML read, `skipped="0" failures="0" errors="0"`.
- [ ] `bash scripts/gates.sh --max-workers=1` green; test-api spec, writing-tests guide, self-tests table, API README and CHANGELOG updated.

**Verify.**
`bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@26.3 --tests '*ConformanceLogLinesTest' --no-parallel --max-workers=1`

### T-M11-02 · `PlayerState.openWindow()`: the server's view of a player's open container
- tier: sonnet
- depends: —
- modules: proofstone-api, proofstone-core, conformance, docs/specs, docs/user
- adrs: 0002
- specs: specs/test-api.md §3, specs/agent-protocol.md §3
- test-first: yes

**Goal.** A test reads, from the server side, the container window a player has open (its type, its title and its own slots), through
the public API: to assert a menu a plugin opened (for example a chest menu whose title carries a Bedrock-form prefix) without the
client's view. Label `api-change`.

**Evidence.** Gap 2 of the Disenchantment E2E suite. `player-state.json` already has `openWindow {type, title, slots[]}`, the
Bukkit-family agent already fills it and the host's `HostPlayer.state()` already reads the reply, but `PlayerState` drops it.

**Do.**
- Add `ServerWindow(type, title, List<ItemView> slots)` and an `Optional<ServerWindow> openWindow` last component of the record
  `PlayerState`. Keep the 7-argument constructor (no window), so no signature is removed.
- Map the wire `OpenWindow` in `HostPlayer`: a `null` window is empty, a `null` slot is `ItemView.EMPTY` in place, a window with no
  `slots` is an `IllegalStateException`.
- Unit tests in core, one real-server conformance scenario (C-W01), docs, a regenerated `api-baseline.txt`.

**Don't.**
- Change the wire protocol (no schema, golden or version change: the field exists), the agents or add a capability. The Fabric and
  NeoForge agents still answer `NOT_SUPPORTED` for a player with a window open (they cannot read a title): documented, not changed.
- Add a sleep or retry; the wait is `PlayerHandle.awaitState`. Include the player's 36 slots (the window is the container's own).

**Acceptance.**
- [ ] `api-baseline.txt` gains only `ServerWindow`, the `PlayerState` 8-argument constructor and `PlayerState.openWindow()`; `ApiSurfaceTest` is green.
- [ ] Unit tests: null window, window with an empty slot kept in order, missing slots throw, the v0 constructor.
- [ ] C-W01 (`ConformanceOpenWindowTest`) green on `paper@26.3` in three separate invocations, XML read, `skipped="0" failures="0" errors="0"`.
- [ ] `bash scripts/gates.sh --max-workers=1` green; test-api spec, writing-tests guide, self-tests table, API README and CHANGELOG updated.

**Verify.**
`bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@26.3 --tests '*ConformanceOpenWindowTest' --no-parallel --max-workers=1`

### T-M11-03 · `Agent.bootLogLines()`: the server log from the agent's enable to the test start
- tier: sonnet
- depends: T-M11-01
- modules: proofstone-api, proofstone-core, conformance, docs/specs, docs/user
- adrs: 0002
- specs: specs/test-api.md §3, specs/agent-protocol.md §2
- test-first: yes

**Goal.** A test reads the lines the server logged before the test started (a plugin's start-up line, for example
`NMS module: NMS_v1_21_R5`), through the public API. Label `api-change`.

**Evidence.** `logLines()` (T-M11-01) reads from the cursor taken before the client joins, so boot lines are not visible.

**Do.**
- Add `Agent.bootLogLines()` as a default method that throws `UnsupportedOperationException`. Implement it in core for `HostAgent`
  and `ProxyAgent` over `GET /v1/log?since=0&minLevel=TRACE`, cut at the test cursor (disjoint from `logLines()`).
- A ring buffer (5000 records) that dropped records throws `IllegalStateException`.
- Document the coverage: the agent loads at STARTUP and attaches in `onEnable`, so lines from there on are held (every ordinary
  plugin's enable lines, the `Done` line); the banner, library loading and the agent's own "Enabling" line are not.
- Unit tests in core and api, one real-server scenario (C-L02), docs, a regenerated `api-baseline.txt`.

**Don't.** Change the wire protocol (no version bump, golden or schema change), the agents or add a capability; add a sleep or retry.

**Acceptance.**
- [ ] `api-baseline.txt` gains only `Agent.bootLogLines()`; `ApiSurfaceTest` is green.
- [ ] Unit tests: window cut at the cursor, stack traces, dropped records throw, cursor past the records throws, api default throws.
- [ ] C-L02 (`ConformanceBootLogLinesTest`) green on `paper@26.3` in three separate invocations, XML read, `skipped="0" failures="0" errors="0"`.
- [ ] `bash scripts/gates.sh --max-workers=1` green; docs and CHANGELOG updated.

**Verify.**
`bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@26.3 --tests '*ConformanceBootLogLinesTest' --no-parallel --max-workers=1`

### T-M11-06 · mcpl driver: apply `ClientboundSetPlayerInventoryPacket` to the client inventory
- tier: sonnet
- depends: T-M2-06
- modules: drivers/mcpl, docs/platforms
- adrs: 0002
- specs: specs/client-driver.md §2
- test-first: yes

**Goal.** The client oracle (`DualAssert.item().inInventory`) sees every stack the server put into the player's inventory. Bugfix, no API change.

**Evidence.** Disenchantment e2e `PreEventCancelE2E.aCancelledPreDisenchantEventChangesNothing` on `folia@26.3` failed 1 run in 4: the server oracle saw the
returned enchanted book in slot 9, the client oracle saw only the sword. The driver transcript ends with a click, a window resync, the window close, then
`ClientboundSetPlayerInventoryPacket` and a `SetSlot` for slot 36. `McplSession` had no branch for that packet (1.21.2+), so a stack handed back on a menu close was never applied.
The other suspect, `Window.click` racing the resync, is not the cause: click returns on the first sync and the oracle polls (ROADMAP.md documents that it can return before the rest of the answer).

**Do.**
- Apply the packet by inventory index (0-8 hotbar, 9-35 main, 36-39 feet to head, 40 offhand) to window 0, and to the player section of an open window so a later close does not adopt a stale stack.
- Unit tests with scripted packets (`McplPlayerInventoryTest`), red before the fix.

**Don't.** Change the wire protocol, public API, or add a sleep or retry.

**Acceptance.**
- [ ] `McplPlayerInventoryTest` fails without the fix and passes with it.
- [ ] The window and inventory conformance subset is green on `paper@26.3` in three separate invocations, XML read, `skipped="0" failures="0" errors="0"`.
- [ ] `bash scripts/gates.sh --max-workers=1` green.

**Verify.**
`bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@26.3 --tests '*ConformanceOpenWindowTest' --no-parallel --max-workers=1`
