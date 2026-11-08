# Folia

## Host
- Image `itzg/minecraft-server:<javaTag>` with `TYPE=FOLIA`. Builds are listed at
  `https://fill.papermc.io/v3/projects/folia` (1.19.4 → 26.3 as of 2026-11).

## Agent notes
- There is no main thread. Player reads and writes go through `player.getScheduler()` (entity scheduler), block
  operations through `RegionScheduler`, and console commands, plugin config and event probes through
  `GlobalRegionScheduler`. The agent picks the owner per operation (`Affinity`), and `FoliaScheduler` reaches
  these APIs through reflection only, so the agent still compiles against 1.8.8 (ADR-0002, T-M5-02). Every
  endpoint completes a `CompletableFuture` and enforces a timeout.
- Never set `folia.disable-thread-check`.
- A teleport is `Player.teleportAsync` here, and the agent waits for its result. The synchronous `teleport` throws
  on a region thread (see below).
- `plugin.yml` needs `folia-supported: true`, or Folia does not load the plugin.
- Per-region ticks: `ticks.json` `regions` counts the ticks of each chunk region that an operation has touched.
  Folia merges chunks into regions, so one region can carry several keys.

## Capabilities
`FOLIA_REGIONS`, `BUKKIT_EVENTS`, `CONSOLE`, but not every vanilla command (see Gotchas).
The agent reports the token `regions` on Folia only, which confirms `FOLIA_REGIONS` (`CapabilityModel`).

## Gotchas
- `/data` does **not exist** on Folia, and the server replies "Unknown or incomplete command".
  (Prototype, 1.21.11.)
- `xp query <player> levels` over RCON returns an **empty** reply, because the result is produced on the
  region thread. Never use RCON or console output as an oracle on Folia.
- `execute at <player> run setblock …` returned an empty reply but did place the block.

### T-M5-02 boot results (C01 only, before any scheduler code, 2026-11-08)
- **Every Folia row boots, and the agent does not start on any of them.** Folia 1.21.11 (`java21`, folia-1.21.11.jar)
  and 26.1.2, 26.2, 26.3 (`java25`) log `Done (5 to 10 s)!`, then `/v1/health` never answers
  (`curl: (7) Failed to connect`). The cause is in the agent: `ProofstoneAgent.onEnable` calls
  `Bukkit.getScheduler().scheduleSyncRepeatingTask` for the tick counter, and Folia's `CraftScheduler` throws
  `UnsupportedOperationException` (there is no main thread). `onDisable` then fails on `cancelTasks` too. Fixed by
  `FoliaScheduler` (T-M5-02). _1.21.11, 26.1.2, 26.2, 26.3 · boot probe in this PR_
- **The fixture plugin is not loaded without `folia-supported: true`** (its `plugin.yml`). Folia refuses it, so
  `/v1/plugins/proofstone-fixture/...` answers `PLUGIN_NOT_FOUND` and the log case finds no fixture record.
  _26.3 · this PR_
- **`Player.teleport` throws on a region thread:** `Must use teleportAsync while in region threading`. `reset` and
  `PATCH` location failed with 500, and the player stayed where he was. Folia uses `teleportAsync`, and the agent
  waits for its future (`Teleports`), so a read after a reset sees the new position. _26.3 · this PR_
- **Plugin disable uses `Bukkit.getScheduler().cancelTasks` too,** and that throws on Folia. The agent cancels the
  tasks its own scheduler started instead (`FoliaScheduler.close`). _1.21.11, 26.3 · this PR_
- **Folia 1.21.11 boots with `java21` and Folia 26.x with `java25`, and `PAPER_BUILD` selects the build** (itzg's
  `start-deployFolia` hands `PAPER_BUILD` to `start-deployPaper` with `PAPER_PROJECT=folia`). The server jar is
  `/data/folia-*.jar`, and `PaperHost` checks its sha256 against the pin. _26.3 · this PR_
