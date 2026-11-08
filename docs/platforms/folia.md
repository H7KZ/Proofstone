# Folia

Status: **experimental** (rows 1.21.11, 26.1.2, 26.2, 26.3 in `versions.yml`). Conformance C01 to C08, C-F01 and C-F02
passed once per row (T-M5-03, fix round). See [platforms.md](../platforms.md).

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

## Gotchas
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
  client's first movement packets, as on Paper. The Folia pack allows it for harness clients only
  (`^T[0-9a-f]{8} moved too quickly! `, mirrored in `pack.yml`). It is a race, so it shows in some tests and not others.
  The allowlist entry was added after that red run; the rows were re-run afterwards. _26.3_
- **C-F01 and C-F02 declare Paper and Purpur too,** with `@Requires(FOLIA_REGIONS)`, so a `paper@latest` or `purpur@<v>`
  selection plans a cell and reports a capability skip with its reason (zero cells stays a loud error). _paper, purpur_
- **Any other boot ERROR on a new Folia row is invisible to the log guard** (it runs only inside the test window). Read the
  server log by hand after a new row boots.

### Prototype notes (1.21.11, RCON only)
- `/data` does **not exist** on Folia; the server replies "Unknown or incomplete command".
- `xp query <player> levels` over RCON returns an **empty** reply, because the result is produced on the region thread.
  Never use RCON or console output as an oracle on Folia.
- `execute at <player> run setblock …` returned an empty reply but did place the block.

## Open limits
- Rows 1.19.4 to 1.21.10 and the other 26.x builds are unpinned and unrun.
- Each row has one full run. Promotion to `required` is not done: ADR-0015 needs a maintainer decision here, because the
  allowlist entry above was added after a red run (smoke, 26.3). The four rows stay `experimental`.
- Cross-region teleports are not covered; reset and patch teleports are tested at the fixture spot only.
- The exact Folia region-thread name is not recorded (C-F02 logs it per run).
- Folia `SIMULATED_BEDROCK` is not declared and not run.
