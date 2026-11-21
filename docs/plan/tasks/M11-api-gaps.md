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

### T-M11-05 · The Bukkit agent fills `ItemView.pdc()`
- tier: sonnet
- depends: —
- modules: platforms/bukkit/agent, proofstone-core, conformance, docs/specs, docs/platforms
- adrs: 0002
- specs: specs/agent-protocol.md §3, specs/test-api.md §6
- test-first: yes

**Goal.** A test reads an item's persistent data container from the server through the public API: to assert that a plugin marked an
item (for example Disenchantment's `disenchantment:special_anvil` byte) rather than guessing from its name and lore.

**Evidence.** Gap 5 of the Disenchantment E2E suite. `ItemView.pdc()` and the wire field `item.pdc` exist, but the Bukkit agent never
filled it, so `pdc()` was `null` for every item.

**Do.**
- Read `ItemMeta.getPersistentDataContainer()` by reflection (the agent is built against 1.8.8; PDC exists from 1.14; resolved defensively, verified on 1.18.2 and 26.3) and send each
  key as `{"type", "value"}`: byte, short, int, long, float, double, string, byte[], int[], long[], a nested container, and
  `unsupported` for a value it cannot read, never dropped. Report the agent token `pdc` where the API exists.
- Map the wire entries to typed Java values in core (`PdcWire`); an unknown type or shape is an error.
- Unit tests in the agent (stand-in 1.14 classes) and core, one real-server scenario (C-I01), docs, CHANGELOG.

**Don't.**
- Change the wire schema, the goldens or the protocol version (`pdc` is already an object; an empty container sends no field), the
  API surface (no `api-baseline.txt` change), or add a `pdc` setter on `give` (still `INVALID_REQUEST`). Add a sleep or retry.

**Acceptance.**
- [ ] Unit tests: every type, arrays, nested container, unsupported marker, empty container, unknown wire type fails, capability token.
- [ ] C-I01 (`ConformanceItemPdcTest`) green on `paper@26.3` in three separate invocations and and on `paper@1.18.2` (also in the test's `@Matrix`), XML read, `skipped="0" failures="0" errors="0"`.
- [ ] `bash scripts/gates.sh --max-workers=1` green; docs and CHANGELOG updated.

**Verify.**
`bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@26.3 --tests '*ConformanceItemPdcTest' --no-parallel --max-workers=1`

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

### T-M11-07 · mcpl driver: a window packet that arrives after the client's close still reaches window 0
- tier: sonnet
- depends: T-M11-06
- modules: drivers/mcpl, docs/specs, docs/platforms
- adrs: 0002
- specs: specs/client-driver.md §2
- test-first: yes

**Goal.** The client oracle sees every player-inventory stack the server sent for a window, even when the packet arrives after the
client closed that window. Bugfix, no API change.

**Evidence.** After T-M11-06, Disenchantment e2e on `folia@26.3` still failed 2 of 3 full runs, 1 test each
(`RestrictionsE2E.aDeletedEnchantmentIsDestroyed`, `DisenchantE2E.tooLittleExperienceDeliversNothingUntilThePlayerCanPay`; earlier
`PreEventCancelE2E.aCancelledPreDisenchantEventChangesNothing`): `DISAGREE ... expected present`, the server oracle saw the book in
slot 9, the client oracle never did. All three transcripts are the same: the click that places the cursor book in a player slot is sent
as the plugin's late anvil update arrives (slot 1 at stateId 14 and a resync at 15, two ticks after the result click). The click returns
on that update, the test closes, and a `ContainerSetContent id=1` arrives 16 to 24 ms after the close: `(no such window, ignored)`.
The server then sends window 0 only slot 45, the hand-back of slot 0 and slot 36, never slot 9. The transcript does not log slot
contents, so that this content was the click's answer holding the book is **inferred**: the server oracle has the book in slot 9, it
is the only window 1 packet after the place click, and on close the server takes the anvil's remote slots as window 0's (vanilla
`transferState`), which explains why slot 9 is never resent. Same mechanism as the T-M8-23 note in ROADMAP.md, which was fixed in the
conformance scenarios only. On that inference it is a driver bug, not a plugin or Folia one: the server sent the book and the driver
dropped it.

**Do.**
- Keep the window this client closed as the target of packets for its id until another window opens, the server closes a window or
  the session resets. A late `ContainerSetContent` hands all its player slots to window 0, a late `ContainerSetSlot` only its own slot;
  neither sets the cursor.
- Unit tests with the recorded packet order (`McplLateWindowPacketTest`), red before the fix.

**Don't.** Change the wire protocol, the public API or the click release condition, or add a sleep or retry.

**Acceptance.**
- [ ] `McplLateWindowPacketTest` fails without the fix and passes with it.
- [ ] C04 to C08 and `ConformanceOpenWindowTest` green on `paper@26.3` and `folia@26.3`, three separate invocations each, XML read,
  `skipped="0" failures="0" errors="0"`.
- [ ] `bash scripts/gates.sh --max-workers=1` green.

**Verify.**
`bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=folia@26.3 --tests '*ConformanceOpenWindowTest' --tests '*C0[4-8]*' --no-parallel --max-workers=1`

### T-M11-04 · `Client.placeBlock` and `Client.breakBlock`: a player's block place and break
- tier: sonnet
- depends: —
- modules: proofstone-api, proofstone-core, drivers/mcpl, conformance, docs/specs, docs/user
- adrs: 0002, 0004
- specs: specs/test-api.md §3, specs/client-driver.md §1
- test-first: yes

**Goal.** A test makes the client place a block against a known block and break a block as a player would, so the server fires its
real `BlockPlaceEvent` and `BlockBreakEvent` from the held item, and reads the result from the server. Label `api-change`.

**Evidence.** Gap 3 of the Disenchantment E2E suite (a special anvil is marked in chunk PDC when placed and swaps its drop when
broken). `ClientSession.useBlock` already sends use-item-on and the server answers `ClientboundBlockChangedAck`, but nothing on `Client`
places or breaks, and no public read of a block exists (`GET /v1/world/block` is agent-only).

**Design.** Additive, smallest set that closes the loop:
- `Client.placeBlock(BlockPos against, Face face)` and `Client.breakBlock(BlockPos)`, default methods that throw
  `UnsupportedOperationException`; `Face` becomes a public API enum (moved from core's non-public `driver` package).
- `ClientSession.placeBlock` / `breakBlock` default to a `ClientDriverException` (a driver without support fails loudly: Bedrock and
  the reference Mineflayer driver). `McplSession` implements them: look, use-item-on, or look, start-digging, finish-digging, each
  returning when `ClientboundBlockChangedAck` of its own sequence arrives (`Await`, no sleep).
- `Agent.blockType(String world, BlockPos)`, a default method that throws, over the existing `GET /v1/world/block`: the server oracle.
- No protocol change, no version bump, no golden file, no capability.

**Do.**
- Add `Face` to `proofstone-api`, `Client.placeBlock`, `Client.breakBlock` and `Agent.blockType` as throwing default methods.
- Add `ClientSession.placeBlock` / `breakBlock` (default throws `ClientDriverException`); implement them in `McplSession` over the
  block-change acknowledgement; make Bedrock throw naming the edition.
- Implement `blockType` in `HostAgent` over `GET /v1/world/block`.
- Unit tests, one real-server scenario (C-B01), docs, CHANGELOG, ROADMAP, a regenerated `api-baseline.txt`.

**Don't.** Change the wire protocol or the agents; add a sleep or retry; predict a result on the client (the server decides: reach,
protection and cancelled events leave the block unchanged and the test reads that from the server).

**Acceptance.**
- [ ] `api-baseline.txt` gains only `Face`, `Client.placeBlock`, `Client.breakBlock` and `Agent.blockType`; `ApiSurfaceTest` is green.
- [ ] Unit tests: api defaults throw, `HostAgent.blockType` request and bad reply, `McplSession` packets and ack wait, Bedrock throws.
- [ ] C-B01 (`ConformancePlaceBreakBlockTest`) green on `paper@26.3` in three separate invocations, XML read, `skipped="0" failures="0" errors="0"`.
- [ ] `bash scripts/gates.sh --max-workers=1` green; docs, self-tests table, API README, CHANGELOG and ROADMAP updated.

**Verify.**
`bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@26.3 --tests '*ConformancePlaceBreakBlockTest' --no-parallel --max-workers=1`

### T-M11-10 · Spike: fault injection for "the next inventory write fails"
- tier: opus
- depends: —
- modules: docs/platforms, docs/adr
- adrs: 0002
- specs: specs/agent-protocol.md §1
- test-first: no

**Goal.** Decide whether an agent can make "the next inventory write for player X fail" without being specific to one plugin, so a
suite can reach a plugin's "the write failed, refund" path. Spike: a verdict with evidence, API only if a generic, robust design exists.

**Evidence.** Gap 3 of the Disenchantment E2E suite: commit `4352679` refunds when writing the result fails, and mutation M9 (remove
the refund) stayed green because no API makes a write fail.

**Do.**
- Find what the plugin's write calls do on the server (`javap` on the cell's server jar) and try each candidate design.
- Write the verdict and evidence in `docs/platforms/fault-injection.md`; draft an ADR if a design needs one.

**Don't.** Add API, change the protocol or the agents on a No-go; re-decide ADR-0002; add a sleep or retry.

**Acceptance.**
- [ ] `docs/platforms/fault-injection.md` states the verdict, what was tried and what happened; evidence in `C:\pw\T-M11-10-evidence`.
- [ ] ADR-0020 drafted as Proposed and indexed; no API, protocol or agent change.
- [ ] `bash scripts/gates.sh --max-workers=1` green.

**Verify.**
`bash scripts/gates.sh --max-workers=1`
### T-M11-09 · `Window.drag`: an inventory drag across slots
- tier: sonnet
- depends: -
- modules: proofstone-api, proofstone-core, drivers/mcpl, conformance, docs/specs, docs/user
- adrs: 0002, 0004
- specs: specs/test-api.md §4, specs/client-driver.md §1
- test-first: yes

**Goal.** A test drags the carried stack across several slots as a player holding a mouse button, and the server distributes it.
Label `api-change`.

**Evidence.** Gap 4 of the Disenchantment E2E suite (the grindstone result refresh after a drag into the grindstone). `Window.click`
sends single clicks only; nothing sends the quick-craft sequence.

**Design.** Additive, smallest set:
- `Window.drag(List<Integer> slots, DragMode mode)`, a default method that throws `UnsupportedOperationException`; `DragMode` (`LEFT`,
  `RIGHT`, `MIDDLE`) is a new public enum.
- `WindowSession.drag` defaults to a `ClientDriverException`; `McplSession` sends start (slot -999), one add per slot, end (slot
  -999) as `SPREAD_ITEM` with the button's header codes, then waits (`Await`, no sleep) for a counted sync and the cursor update of the
  last stage.
- No protocol change, no version bump, no capability.

**Do.**
- Add `DragMode`, `Window.drag`, `WindowSession.drag`, `SessionWindow.drag`, the mcpl implementation and `WindowModel.cursorUpdates`.
- Unit tests (`McplDragTest`: packet order and headers per button, the wait, bad arguments; `WindowDragDefaultTest`), one real-server
  scenario (C-W02), docs, CHANGELOG, ROADMAP, a regenerated `api-baseline.txt`.

**Don't.** Change the wire protocol or the agents; add a sleep or retry; predict the result on the client.

**Acceptance.**
- [ ] `api-baseline.txt` gains only `DragMode` and `Window.drag`; `ApiSurfaceTest` is green.
- [ ] `McplDragTest` green. C-W02 (`ConformanceWindowDragTest`) green on `paper@26.3` in three separate invocations, XML read,
  `skipped="0" failures="0" errors="0"`.
- [ ] `bash scripts/gates.sh --max-workers=1` green; docs, self-tests table, API README, CHANGELOG and ROADMAP updated.

**Verify.**
`bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@26.3 --tests '*ConformanceWindowDragTest' --no-parallel --max-workers=1`

### T-M11-11 · `Window.awaitCursor`: wait for the client cursor
- tier: sonnet
- depends: -
- modules: proofstone-api, proofstone-core, conformance, docs/specs, docs/user
- adrs: 0002, 0007
- specs: specs/test-api.md §4
- test-first: yes

**Goal.** A test waits for the client cursor of an open window to match, as it waits for a slot, so it does not race the cursor packet
that trails a click. Label `api-change`.

**Evidence.** The Disenchantment E2E suite read `Window.cursor()` once right after `click()`. The click returns on the first state-id
change, the plugin delivers its result with `setItemOnCursor` after writing slots, and the server's `SetCursorItem` arrives about 1 ms
later: a race that showed on Folia (`C:\pw\dis-ev-dragolia-cursor-diagnosis.md`). Only the internal `ConformanceFixture.awaitCursor` existed.

**Design.** Additive, smallest set: `Window.awaitCursor(Predicate<ItemView>)`, a default method that throws
`UnsupportedOperationException` (as `drag`). `SessionWindow` implements it over `Await.until` with the default timeout, exactly as
`awaitSlot`; the failure is an `AwaitTimeoutException` with the last cursor observed. All drivers go through `SessionWindow`, so none
changes. No protocol change, no version bump, no capability.

**Do.**
- Add `Window.awaitCursor` and the `SessionWindow` implementation; unit tests (cursor arrives late, never arrives, already satisfied,
  the default throws); one real-server scenario (C-W03); docs, CHANGELOG, ROADMAP, a regenerated `api-baseline.txt`.

**Don't.** Change the wire protocol or the agents; add a sleep or retry; raise a timeout; touch the internal `ConformanceFixture.awaitCursor`
(the conformance fakes do not implement the new method).

**Acceptance.**
- [ ] `api-baseline.txt` gains only `Window.awaitCursor`; `ApiSurfaceTest` is green.
- [ ] `SessionWindowAwaitCursorTest` green. C-W03 (`ConformanceAwaitCursorTest`) green on `paper@26.3` in three separate invocations, XML
  read, `skipped="0" failures="0" errors="0"`.
- [ ] `bash scripts/gates.sh --max-workers=1` green; docs, self-tests table, API README, CHANGELOG and ROADMAP updated.

**Verify.**
`bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@26.3 --tests '*ConformanceAwaitCursorTest' --no-parallel --max-workers=1`
