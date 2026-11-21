# Folia

Status: **required** (rows 1.21.11, 26.1.2, 26.2, 26.3 in `versions.yml`, promoted 2026-11-08 under ADR-0015). Conformance C01 to C08,
C-F01 and C-F02 passed in at least two full per-row runs (T-M5-03 and its verifier, the M5 review), and C06 repeated 10 of 10
on folia@26.3 and on folia@1.21.11 (`-Dproofstone.repeat=10`, 2026-11-08). The extra row folia@1.20.6 is not added (two reds, below). See [platforms.md](../platforms.md).

## Host
- Image `itzg/minecraft-server:<javaTag>` with `TYPE=FOLIA`. Builds are listed at
  `https://fill.papermc.io/v3/projects/folia` (1.19.4 → 26.3 as of 2026-11).
- Java tag per row: 1.21.11 on `java21`, 26.x on `java25` (`java-rules`).
- `PAPER_BUILD` selects the build. itzg's `start-deployFolia` hands it to `start-deployPaper` with
  `PAPER_PROJECT=folia`. The server jar is `/data/folia-*.jar`, and `PaperHost` checks its sha256 against the pin.
- Pinned rows: 1.21.11 (build 14), 26.1.2 (build 8), 26.2 (build 7), 26.3 (build 6). Older Folia builds (1.19.4 to
  1.21.10) are not pinned and have not been run.

## Agent notes
- There is no main thread. Player reads and writes go through `player.getScheduler()` (entity scheduler), block
  operations through `RegionScheduler`, and console commands, plugin config and event probes through
  `GlobalRegionScheduler`. The agent picks the owner per operation (`Affinity`). `FoliaScheduler` reaches these APIs
  through reflection only, so the agent still compiles for 1.8.8 (ADR-0002, T-M5-02). Every endpoint completes a
  `CompletableFuture` and enforces a timeout.
- Never set `folia.disable-thread-check`. Thread-check errors are never allowlisted (`ErrorLogGuard.Patterns.FOLIA`).
- A teleport is `Player.teleportAsync` here, and the agent waits for its result. The synchronous `teleport` throws
  on a region thread.
- `plugin.yml` needs `folia-supported: true`, or Folia does not load the plugin (the fixture plugin declares it).
- Plugin disable cannot use `Bukkit.getScheduler().cancelTasks`. The agent cancels the tasks its own scheduler started
  (`FoliaScheduler.close`).
- Per-region ticks: `ticks.json` `regions` counts the ticks of each chunk region that an operation has touched, keyed
  `world:chunkX,chunkZ`, because the API exposes no region id. Folia merges chunks into regions, so one region can carry
  several keys. The `regions` token is reported on Folia only.

## Capabilities
`FOLIA_REGIONS`, `BUKKIT_EVENTS`, `CONSOLE`, but not every vanilla command (see Gotchas). `CapabilityModel` maps the
`regions` token to `FOLIA_REGIONS`. Folia does not declare `SIMULATED_BEDROCK` (not run).

## Conformance (T-M5-02, T-M5-03)
Per-row runs, one `dockerlock` invocation each, read from the JUnit XML: `skipped=0 failures=0 errors=0`, no reasoned skips.
C07 runs on every row (all at or above 1.14).

| Row | Driver path | C01-C08, C-F01, C-F02 |
|---|---|---|
| folia 1.21.11 | bridged (ViaProxy) | pass, once |
| folia 26.1.2 | bridged (ViaProxy) | pass, once |
| folia 26.2 | bridged (ViaProxy) | pass, once |
| folia 26.3 | native | pass, once |

Also: `AgentContractIT` 25 of 25 on folia@26.3 (T-M5-02), and `:conformance:smoke` 4 tests, 0 failed, on the four rows.
Each row has one full run. None is repeated, and no repeat was used to turn a red run green.

## M5 follow-up (2026-11-08): folia@1.20.6 not added
Tried as the older Folia row (build 6, channel ALPHA, Java 21, protocol 766 from the jar). Full `:conformance:run` twice,
one Docker invocation each, XML read per run. Both runs red, on different tests, so the row is not in `versions.yml`:
- Run 1: `ConformanceFoliaTeleportTest` (C-F02) `ClientDriverException: timed out joining ... after PT30S`. The transcript
  shows `ClientIntention` and `ServerboundHelloPacket`, then nothing. The server log has no login, and the ViaProxy log has
  no connection line. The other 10 tests on the row joined fine. Same symptom as the bridged prefixed stall (viaproxy.md).
- Run 2: `C07GrindstoneTest` `AssertionFailedError: [the bare sword on the cursor] Expecting value to be true but was false`
  (C07.java:55). Every other test passed, including C-F02.
Not diagnosed. Two different reds on one row is an unexplained flake, so that row is not added. The four pinned rows are unaffected. _folia 1.20.6 ·
M5 follow-up_

## Gotchas

- **Items handed back on a menu close arrive as `ClientboundSetPlayerInventoryPacket` (1.21.2+), with no window id and no state id** (T-M11-06,
  `folia@26.3`, 1 run in 4; the driver is shared, so Paper cells are affected too). The mcpl driver ignored it, so the client oracle missed a book the server had put in slot 9. The driver now applies
  it by inventory index (0-8 hotbar, 9-35 main, 36-39 feet to head, 40 offhand) to window 0 and to an open window's player section.
- **The answer to the last click can arrive after the test closed the window** (T-M11-07, `folia@26.3`, 2 of 3 Disenchantment runs after
  T-M11-06; the driver is shared, so Paper is exposed too, at other timings). A plugin task updates the anvil two ticks after the result
  click; the next click returns on that update, the close follows, and a `ContainerSetContent id=1` arrives after the close and was
  dropped. The transcript does not log slot contents: that this packet held the book in slot 9 is inferred, from the server oracle
  (book in slot 9) and from the server never sending slot 9 to window 0 afterwards (vanilla `transferState` on close; this is the only
  window 1 packet after the place click). The driver now hands such a packet's player slots to window 0. By the same inference, the
  T-M11-06 diagnosis of this red named the wrong packet: the only `ClientboundSetPlayerInventoryPacket` after the close is for slot 0.
- **Boot used to fail on every row.** `ProofstoneAgent.onEnable` called `Bukkit.getScheduler().scheduleSyncRepeatingTask`
  for the tick counter. Folia's `CraftScheduler` throws `UnsupportedOperationException` (there is no main thread), and
  `onDisable` failed on `cancelTasks` too. Fixed by `FoliaScheduler` (T-M5-02). _1.21.11, 26.1.2, 26.2, 26.3_
- **`Player.teleport` throws on a region thread:** `Must use teleportAsync while in region threading`. `reset` and PATCH
  location failed with 500 before the fix. _26.3_
- **A console `tp` does not fire `PlayerTeleportEvent`,** on Folia 1.21.11 and 26.3. The entity moves, but no event
  reaches a probe. `gamemode creative <name>` fires `PlayerGameModeChangeEvent`, which C-F02 uses. _1.21.11, 26.3_
- **The agent's reset does not fire `PlayerTeleportEvent` either,** even though the player moves through `teleportAsync`.
  A reset from the spawn to itself is not a move, so it fires nothing. _26.3_
- **Boot logs two ERRORs on every Folia row, and neither fails a test:** `No key layers in MapLike[{}]` (the same line
  Paper logs on 1.21.11+ flat worlds) and `[CraftServer] Could not save bukkit.yml`. Both come before any test runs, so the
  log guard does not see them. The test-side ERROR allowlist gained `Could not save bukkit.yml` (harness copy issue, see
  paper.md); thread-check text is never allowlisted. _1.21.11, 26.1.2, 26.2, 26.3_
- **`moved too quickly!` (WARN) on Folia.** In `:conformance:smoke` on folia@26.3 (c02), the reset teleport raced the
  client's first movement packets, as on Paper. The Folia pack allowed it for harness clients (`^T[0-9a-f]{8} moved too quickly! `).
  **That entry was dropped in T-M7-19**: the cause was the mcpl driver acknowledging the join teleport before the player's first
  tick, not the reset teleport (client-driver.md section 3a). `folia@26.3` ran with no entry and 0 such WARNs (ROADMAP
  "T-M7-19"). _26.3_
- **C-F01 and C-F02 declare Paper and Purpur too,** with `@Requires(FOLIA_REGIONS)`, so a `paper@latest` or `purpur@<v>`
  selection plans a cell and reports a capability skip with its reason (zero cells stays a loud error). _paper, purpur_
- **Any other boot ERROR on a new Folia row is invisible to the log guard** (it runs only inside the test window). Read the
  server log by hand after a new row boots.
- **`PSConf moved too quickly!` failed c02 in the full smoke: not a Folia behaviour, fixed in T-M7-17.** Cause found in T-M7-15
  (ROADMAP "M7 follow-up findings (T-M7-15)"): the harness keyed the class-shared server by mc version only, so in the full
  four-cell smoke `folia@26.3` ran on the Paper server booted for `paper@26.3`. The host is now keyed by the full cell and the
  harness refuses a server whose agent reports another platform than the cell. Full smoke runs before T-M7-17 did not run this
  row on Folia. After the fix `folia@26.3` booted its own server (`Resolving type given FOLIA`) in the full smoke and alone, 0
  `PSConf` WARN (ROADMAP "T-M7-17"). _26.3_

### Prototype notes (1.21.11, RCON only)
- `/data` does **not exist** on Folia; the server replies "Unknown or incomplete command".
- `xp query <player> levels` over RCON returns an **empty** reply, because the result is produced on the region thread.
  Never use RCON or console output as an oracle on Folia.
- `execute at <player> run setblock …` returned an empty reply but did place the block.

## Open limits
- Rows 1.19.4 to 1.21.10 and the other 26.x builds are unpinned and unrun.
- Promoted to `required` 2026-11-08: each row has at least two full runs, and the C06 repeat on folia@26.3 and folia@1.21.11 was
  10 of 10 with no failure (the allowlist entry above came from a red smoke run on 26.3, fixed and rerun green).
- **M5 follow-up (ADR-0015):** no C06 repeat was run on Folia, so there is no repeat evidence for this platform, and
  folia@1.20.6 failed twice on different tests (above). No Folia row is promoted in this PR.
- Cross-region teleports are not covered; reset and patch teleports are tested at the fixture spot only.
- The exact Folia region-thread name is not recorded (C-F02 logs it per run).
- Folia `SIMULATED_BEDROCK` is not declared and not run.

- **The cursor can trail a click (T-M11-11).** A plugin that writes slots and then calls `setItemOnCursor` makes the server send the window content (empty carried stack) first and `SetCursorItem` about 1 ms later. `Window.click` returns on the first state-id change, so a one-shot `cursor()` read right after it races; use `Window.awaitCursor`. Seen with Disenchantment on Folia; Paper has the same packet order and only the timing differs.
