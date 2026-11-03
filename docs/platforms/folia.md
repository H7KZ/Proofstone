# Folia

## Host
- Image `itzg/minecraft-server:<javaTag>` with `TYPE=FOLIA`. Builds are listed at
  `https://fill.papermc.io/v3/projects/folia` (1.19.4 → 26.3 as of 2026-11).

## Agent notes
- There is no main thread. Player reads and writes go through `player.getScheduler()`, block operations
  through `RegionScheduler`, and console commands through `GlobalRegionScheduler`. Every endpoint
  completes a `CompletableFuture` and enforces a timeout.
- Never set `folia.disable-thread-check`.

## Capabilities
`FOLIA_REGIONS`, `BUKKIT_EVENTS`, `CONSOLE`, but not every vanilla command (see Gotchas).

## Gotchas
- `/data` does **not exist** on Folia, and the server replies "Unknown or incomplete command".
  (Prototype, 1.21.11.)
- `xp query <player> levels` over RCON returns an **empty** reply, because the result is produced on the
  region thread. Never use RCON or console output as an oracle on Folia.
- `execute at <player> run setblock …` returned an empty reply but did place the block.
