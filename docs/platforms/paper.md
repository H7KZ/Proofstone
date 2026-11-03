# Paper

## Host
- Image `itzg/minecraft-server:<javaTag>` with `TYPE=PAPER`, `VERSION=<mc>` and `PAPER_BUILD=<pinned>`.
- Java tags as observed: `java17` for 1.18.2, `java21` for 1.21.11, `java25` for 26.1.2 and 26.3.
  Versions up to 1.16.5 need `java8`; verify on card T-M3-02.
- The Paper download API is `https://fill.papermc.io/v3/projects/paper`.
- Ready when the log shows `Done (` **and** the agent's `/v1/health` returns OK.

## Agent notes
- Bukkit API only. Paper-specific extras are detected at runtime.
- Item syntax in commands changed at 1.20.5, from NBT `{Enchantments:[…]}` to components
  `[enchantments={…}]`. The agent builds items through the Bukkit API, so tests never see that change.

## Capabilities
`NMS`, `DATAPACKS`, `BUKKIT_EVENTS`, `CONSOLE`.

## Gotchas
- **`Bukkit.getOnlinePlayers()` changed its return type.** 1.8.8 returns `Player[]`; later APIs return a
  `Collection`. Code compiled against 1.8.8 that calls it directly throws `NoSuchMethodError` on newer servers.
  The agent reads it through reflection (`BukkitGame.onlinePlayers`). (Found while writing T-M1-04, unverified
  on a live server until T-M1-05.)
- **RCON truncates long SNBT** with `...`. On 26.1, `data get entity <p> Inventory` was cut before the
  enchant list. Never use RCON as an oracle. (Prototype, 2026-11-03.)
- A flat world with no generator settings logs `No key layers in MapLike[{}]` at ERROR on 1.21.11 and
  later. This is vanilla noise and goes on the allowlist.
- After `closeWindow`, the client inventory was intermittently stale for more than 500 ms on 1.18.2.
  Always await the expected state.
- The anvil result slot fills about one tick after both inputs are placed. Await slot 2; never read it
  straight away.
- Disenchantment-style plugins put the result on the cursor themselves (`setItemOnCursor`). The driver
  must handle a cursor that is not empty after a result click.
- **The itzg digest is per Java tag.** `java17`, `java21` and `java25` have different digests, but `versions.yml`
  holds one digest per host. It is pinned to `java25` (Paper 26.x), so older rows would run on the wrong Java.
  `PaperHost` checks the container's Java major against the cell and fails loudly. Fixing this needs a
  per-tag digest in the spec (ADR challenge, not done here).
- The image has `curl` and `sha256sum`, so `PaperHost` reaches the agent with `docker exec curl` and
  publishes no port. A published port is debug mode only.
- itzg copies `/plugins` into `/data/plugins` at start, so `copyFileToContainer` into `/plugins` before start
  is enough. It stores the downloaded server jar as `/data/paper-*.jar`, which `PaperHost` hashes against the pin.
- **Bukkit version string on 26.x** is `26.3.build.238-R0.1-SNAPSHOT`: the Paper build sits in the first segment.
  The agent strips `.build.N` (`MinecraftVersion.parse`), or it reports `26.3.build.238` and core refuses the cell.
