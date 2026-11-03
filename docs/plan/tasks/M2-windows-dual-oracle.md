# M2: Windows, inventories and the dual oracle

**Exit criterion.** The Disenchantment anvil scenario passes natively on the newest Paper row. It checks
the result with a dual-oracle assertion and reads the cost from an event probe (`PostDisenchantEvent`
`xpCost`). Conformance C01–C06 pass. The test API v0 is frozen.

### T-M2-01 · Protocol schemas: window, world block, event, log, config, ticks
- tier: haiku
- depends: T-M1-99
- modules: testificate-protocol
- adrs: 0002
- specs: specs/agent-protocol.md §2–§3
- test-first: yes

**Goal.** Extend the v1 schemas and golden files for the M2 endpoints.

**Do.**
- Add schemas and golden cases for:
  - `openWindow` in `player-state.json`;
  - `block.json`;
  - `event.json`;
  - `log-record.json`;
  - config patch and restore;
  - ticks.
- Bump the protocol minor version to `1.1`.

**Don't.**
- Make breaking changes to existing schemas.

**Acceptance.**
- [ ] The new golden cases validate against the schemas, and the round-trip tests pass.

**Verify.**
`./gradlew :testificate-protocol:test`

### T-M2-02 · Bukkit agent: world, events, log capture, ticks
- tier: haiku
- depends: T-M2-01
- modules: platforms/bukkit/agent
- adrs: 0002
- specs: specs/agent-protocol.md §2
- test-first: yes

**Goal.** Give the agent the server-oracle endpoints that plugin tests need.

**Do.**
- `GET/PUT /v1/world/block`. Block IDs are namespaced. Map them to `Material` on 1.13+, and use the
  legacy lookup table below 1.13.
- `POST /v1/events/subscribe`:
  - load the event class by name through the **plugin classloaders** (iterate the plugins, try each
    loader);
  - register with `EventPriority.MONITOR` and `ignoreCancelled=false`;
  - serialise zero-arg public getters following the `event.json` rules;
  - keep a ring buffer of 1000 events per probe.
- Log capture: attach a handler to the root JUL logger (and the Log4j2 root when present, via
  reflection), and keep a ring buffer of 5000 records with stack traces.
- Tick counter: a repeating task (1 tick) plus `/v1/ticks/await`.

**Don't.**
- Let a listener change an event or call any setter.

**Acceptance.**
- [ ] Golden contract tests pass on the newest Paper row.
- [ ] A probe on `PlayerJoinEvent` captures the client's join with `getPlayer().name`.

**Verify.**
`./gradlew :platforms:bukkit:agent:integrationTest`

### T-M2-03 · Bukkit agent: config patch, snapshot, restore, reload
- tier: haiku
- depends: T-M2-01
- modules: platforms/bukkit/agent
- adrs: 0002
- specs: specs/agent-protocol.md §2
- test-first: yes

**Goal.** Let tests change a plugin's config safely.

**Do.**
- On the first PATCH, snapshot `plugins/<Plugin>/config.yml` (the file bytes) and the in-memory
  `getConfig()`.
- PATCH sets the path through `getConfig().set(path, value)`, then calls `saveConfig()`.
- `reload` either calls a declared hook (`testificate-reload-command` in the request, for example
  `disenchantment reload`), or runs `disablePlugin` followed by `enablePlugin`.
- `restore` writes the snapshot back and reloads.

**Don't.**
- Use `/reload` (the whole-server reload).

**Acceptance.**
- [ ] A patch → read → restore → read round trip on the conformance fixture plugin returns the original
  value.

**Verify.**
`./gradlew :platforms:bukkit:agent:integrationTest`

### T-M2-04 · mcpl: useBlock, open screen, window model from server sync
- tier: sonnet
- depends: T-M1-99
- modules: drivers/mcpl
- adrs: 0004
- specs: specs/client-driver.md §1–§3
- test-first: no

**Goal.** Open container windows and mirror their slots exactly as the server sends them.

**Do.**
- `useBlock(pos, face)`: send look-at plus `UseItemOn` with the main hand and a sequence number.
- Handle `OpenScreen`, `ContainerSetContent` (stores `stateId`, all slots, the carried item) and
  `ContainerSetSlot`. Keep separate models for the player inventory window (id 0) and the open window.
- `awaitWindow(timeout)` returns a `WindowSession` with the type ID as a namespaced string.

**Don't.**
- Implement clicks (that is T-M2-05).

**Acceptance.**
- [ ] Opening a chest, anvil or grindstone placed by the agent yields the correct window type and slot
  count.
- [ ] The slot contents match the agent's `openWindow` view.

**Verify.**
`./gradlew :drivers:mcpl:integrationTest`

### T-M2-05 · mcpl: server-authoritative click, close, cursor
- tier: sonnet
- depends: T-M2-04
- modules: drivers/mcpl
- adrs: 0004, 0007
- specs: specs/client-driver.md §2
- test-first: no

**Goal.** Click slots without predicting results, and wait for the server's answer.

**Do.**
- Implement `click(slot, Click)` for these click types:
  - left and right;
  - shift-left;
  - number keys 1–9;
  - drop and ctrl-drop;
  - double;
  - swap-offhand.
- Each click sends `ContainerClick` with the current `stateId` and an empty predicted-changes map. On the
  newest protocol, use hashed stacks as required, with the carried item set to "unknown" or empty. Then
  wait for a sync with a newer `stateId`.
- `close()` sends close and awaits the player-inventory resync (`SetContent` for window 0). The waits are
  explicit, never a sleep.
- Expose the `cursor` from server sync only.

**Don't.**
- Apply local predictions, ever.

**Acceptance.**
- [ ] The chest move scenario (C04) passes in a loop 20 times without flaking.
- [ ] After a result click that puts an item on the cursor (plugin `setItemOnCursor`), `cursor()` shows
  that item.

**Verify.**
`./gradlew :drivers:mcpl:integrationTest`

### T-M2-06 · mcpl: item parsing from data components into ItemView
- tier: sonnet
- depends: T-M2-04
- modules: drivers/mcpl
- adrs: 0003, 0004
- specs: specs/test-api.md §6
- test-first: yes

**Goal.** The client oracle reads the same fields as the server oracle.

**Do.**
- Map MCProtocolLib `ItemStack` plus `DataComponents` to `ItemView` fields:
  - type, amount, enchants, stored enchants;
  - name, lore;
  - damage, repair cost.
- Resolve registry IDs to namespaced keys using the registries received in the configuration phase. Never
  hard-code numeric IDs.
- The newest protocol is the only one in scope: bridged cells arrive already translated by ViaProxy.

**Don't.**
- Parse NBT-era items. Not needed (ADR-0004).

**Acceptance.**
- [ ] Unit tests use captured component fixtures: a sword with two enchants and an enchanted book with
  stored enchants.
- [ ] C03 (give round trip) passes with both oracles equal.

**Verify.**
`./gradlew :drivers:mcpl:test :conformance:run --tests C03`

### T-M2-07 · API: Window, Click, Item, ItemView and DualAssert
- tier: sonnet
- depends: T-M2-05, T-M2-06, T-M2-02
- modules: testificate-api
- adrs: 0001, 0003
- specs: specs/test-api.md §3–§6
- test-first: no

**Goal.** The public DSL, as written in the spec.

**Do.**
- Implement the spec types and methods exactly. Any deviation must be listed in the PR for the API
  freeze review.
- `DualAssert` awaits both sides with `Await.untilBoth`, and fails with `DisagreeException` when only one
  side matches.
- `serverOnly(reason)` needs a non-blank reason, and the reason is recorded in the report.

**Don't.**
- Expose internal core or driver types in the public signatures.

**Acceptance.**
- [ ] The API surface is checked by a binary-compatibility baseline (japicmp or a similar pinned tool)
  that is created in this card.

**Verify.**
`./gradlew :testificate-api:check`

### T-M2-08 · Core: per-test lifecycle (reset, restore, fresh client, error log)
- tier: sonnet
- depends: T-M2-03, T-M2-07
- modules: testificate-core, testificate-api
- adrs: 0007
- specs: architecture.md §3
- test-first: no

**Goal.** Make each test independent of the others.

**Do.**
- Before each test:
  - reset the player (after the fresh client joins);
  - reset the world fixture region;
  - record the log cursor;
  - snapshot the agent state for the leak detector.
- After each test:
  - check `assertNoErrorsLogged` since the cursor, using the pack's allowlist;
  - restore the config;
  - disconnect the client;
  - compare the leak-detector state (warning in M2, failure from M4).
- Add `@Isolation(PER_TEST)`, which starts a new server per test.

**Don't.**
- Swallow lifecycle exceptions.

**Acceptance.**
- [ ] A test that modifies config doesn't affect the next test, proved by an example pair.

**Verify.**
`./gradlew :examples:smoke:test`

### T-M2-09 · Conformance C03–C06
- tier: haiku
- depends: T-M2-07, T-M2-08
- modules: conformance
- adrs: 0003
- specs: specs/self-tests.md §1
- test-first: no

**Goal.** Harness self-tests for items, chests and the vanilla anvil.

**Do.**
- Implement C03 (give round trip), C04 (chest move), C05 (anvil rename and cost) and C06 (anvil combine)
  using only the public API, as dual-oracle assertions.

**Don't.**
- Use `serverOnly` in these scenarios.

**Acceptance.**
- [ ] All four pass 10 times in a row on the newest Paper row.

**Verify.**
`./gradlew :conformance:run`

### T-M2-10 · Example: Disenchantment anvil scenario
- tier: haiku
- depends: T-M2-09
- modules: examples/disenchantment-anvil
- adrs: 0003
- specs: research/2026-11-03-e2e-survey.md §6, specs/test-api.md
- test-first: no

**Goal.** A real third-party plugin scenario that proves the exit criterion.

**Do.**
- Download a pinned Disenchantment release jar by SHA-256 from GitHub releases into
  `build/`. Never commit the jar.
- Write the test from the survey §6, adapted to the final API:
  - sword with Sharpness V and Unbreaking III, plus a book, then the result click;
  - the dual oracle checks the stored enchants and that the sword is stripped;
  - the event probe reads `xpCost`;
  - the level equals 30 − `xpCost`;
  - there are no logged errors.
- Patch `disenchantment.enchantment-chances` to empty, so the run is deterministic.

**Don't.**
- Add the other scenarios (those go in the M6 dogfood card).

**Acceptance.**
- [ ] It passes 10 times in a row on the newest Paper row.

**Verify.**
`./gradlew :examples:disenchantment-anvil:test`

### T-M2-99 · Milestone review M2 and API v0 freeze
- tier: sonnet
- depends: T-M2-01, T-M2-02, T-M2-03, T-M2-04, T-M2-05, T-M2-06, T-M2-07, T-M2-08, T-M2-09, T-M2-10
- modules: docs/plan, testificate-api
- adrs: 0001, 0010
- specs: specs/test-api.md
- test-first: no

**Goal.** Verify the exit criterion and freeze the public API v0.

**Do.**
- Reconcile `specs/test-api.md` with the implementation, and commit the API baseline.
- Refine the M3 cards.

**Don't.**
- Change behaviour in this card.

**Acceptance.**
- [ ] The exit criterion is demonstrated.
- [ ] The spec and the implementation match.
- [ ] The API baseline is committed.

**Verify.**
`./gradlew check :conformance:run :examples:disenchantment-anvil:test`
