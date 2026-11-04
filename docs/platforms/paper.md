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
- **Client-initiated window close is not acknowledged (26.3).** The server sends no window 0 resync, and no cursor
  packet either: items in the container's player slots reach window 0 only as later diffs, and a carried stack is
  returned silently. A driver with a separate model per window (like mcpl) must copy the container's last 36
  slots into window 0 and clear the cursor itself on close, then await the inventory it expects.
- **A click with an empty carried item gets no full resync (26.3).** The server then sends the slot change with a
  bumped `stateId`, and the cursor arrives in a separate packet after it, so "newer stateId" alone can race the
  cursor. Claiming an impossible carried stack (air x1) makes the server answer with one `ContainerSetContent`
  that includes the cursor. A click that changes nothing gets no answer at all.
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
- **MCProtocolLib 26.3-2 needs Java 21+.** It is published for JVM 21 or newer, so `drivers:mcpl` overrides the
  Java 17 host toolchain. The library's default listeners already answer the configuration phase (known packs,
  registries, finish) and keep-alives; the driver only observes. (T-M1-08)
- **Chat arrives as translatable components.** The join message is `multiplayer.player.joined` with the name as an
  argument, not a sentence. The driver flattens to `key arg…` because no language file is loaded, so match on the
  key (`/joined/`). `/version` output is plain text and matches `/Paper/`.
- **Offline mode sends no signed-chat session**, so `chat()` sends an unsigned `ServerboundChatPacket`. A stopped
  server ends the session with `disconnect.endOfStream`, which the driver reports as the reason.
- **Vanilla commands reject custom senders.** Dispatching `gamerule`, `kick`, `give` or `say` through a
  `Proxy` `CommandSender` fails with `Cannot make ProofstoneAgent a vanilla command listener`: Paper only turns
  Console, Player, block, minecart, rcon and proxied senders into a command source. The agent dispatches as
  `Bukkit.getConsoleSender()` and reads the output from a log4j appender (by reflection, calling thread only,
  `LogCapture`), best effort. Output logged from another thread is missed, for example `version`, which replies
  "Checking version, please wait..." and the result later.
- **Game rules are snake_case from 26.1.** `randomTickSpeed` is `random_tick_speed`, `doDaylightCycle` is
  `advance_time`, `doWeatherCycle` is `advance_weather`, `doMobSpawning` is `spawn_mobs`. The old names fail with
  `Incorrect argument for command`. `PaperHost` picks the set by Minecraft major version (`GameRules`).
- **Enchant levels are capped at 255 by the server.** `addUnsafeEnchantment` with 255, 256, 1000 or 32767 all read
  back as 255 on Paper 26.3, in every stack. The agent passes levels through, so the server's value is the truth.
  The golden give case uses 255 for this reason (T-M1-05).
- **Bukkit spawn locations are block corners.** `getSpawnLocation()` returns whole coordinates, so a teleport to it
  stands the player on a corner. `reset` centres on the block (x and z + 0.5), which gives the golden `(0.5, 64.0, 0.5)`.
- **Flat worlds spawn underground.** The default flat spawn on 26.3 is about `y -60`. The contract test sets the spawn to
  `(0, 63, 0)` with `setworldspawn`, and puts a stone block there, so the reset position is solid ground (T-M1-05).
- `PaperHost.command` used to send `"timeoutMs": null`, which the agent rejects; it now omits null fields.
- **Second join from one address fails with `Connection throttled!`.** Paper refuses a reconnect within 4 s
  (`connection-throttle` in `bukkit.yml`). `PaperHost` copies a `bukkit.yml` with `connection-throttle: -1` into
  the container, so tests can join one client per test.
- **`saveConfig()` rewrites `config.yml`.** Bukkit drops comments and key order on a patch, so the agent snapshots
  the original bytes on the first patch and restore writes those bytes back. It does not re-save. (T-M2-03)
- **Restore must reload the config before the plugin.** `disablePlugin` and `enablePlugin` re-run `onEnable`, but
  `getConfig()` keeps the old in-memory values until `reloadConfig()`. The agent calls it first. (T-M2-03, verified on
  Paper 26.3 by `AgentContractIT` config cases.)
- **A plain player cannot run vanilla `/list`** (`Unknown command`): vanilla commands need op. `/version` and
  `/plugins` work for everyone. Use the agent (console) for setup commands.
- **MCProtocolLib does not answer a teleport.** The server ignores a client's packets, block use included, until it
  gets `ServerboundAcceptTeleportationPacket` for the last `ClientboundPlayerPositionPacket` (26.3 carries the position
  and rotation in it too). The mcpl driver sends it, and `ServerboundPlayerLoadedPacket` once after the first position
  (T-M2-04). Without it `useBlock` silently does nothing.
- **Chunks load only around players.** `setblock` before anyone joined fails with `That position is not loaded` and
  the test would continue on an empty world. Join first, build the world, then `tp` the player.
- **Offline join position is not the world spawn.** The first join landed at about `(9.5, -60, 1.5)` even after
  `setworldspawn`. Teleport the player explicitly.
- **Anvils fall and floor grindstones break** when placed over air; put a block below (T-M2-04).
- **Window slot counts**: a container window's `ContainerSetContent` lists the container's own slots then the player's
  36 (chest 27+36, anvil and grindstone 3+36), while window 0 has 46 (crafting, armour, inventory, offhand).
- **Menu types** come as a registry int; MCProtocolLib exposes the `ContainerType` enum, which the driver maps to
  `minecraft:` keys (cartography is `minecraft:cartography_table`).
- **The server sends enchantment ids but not item ids.** `minecraft:enchantment` is a data-driven registry, sent in the configuration phase (`ClientboundRegistryDataPacket`); an id is the entry's position, so the mcpl driver records it. Item types are a built-in registry that no packet carries. `drivers/mcpl/src/main/resources/.../items-26.3.txt` is the server's own report, one key per line in id order. Regenerate it for a new pin: start the pinned itzg image, then in the container run `java -DbundlerMainClass=net.minecraft.data.Main -jar /data/cache/mojang_<mc>.jar --reports --output /tmp/gen` and sort `reports/registries.json` `minecraft:item` entries by `protocol_id` (T-M2-06).
- **Stack data components are a patch.** A removed component arrives as null, an absent one is not there; both read as "not set". Paper's custom name and lore come as `{text:"", extra:["Blade"]}`, so flatten children, not only the root. The agent's `raw` for an item lists lore, damage and `stored_enchantments`, which its typed fields do not yet (T-M2-06).

- **`setblock` answers "That position is not loaded" right after boot** when the target chunk is not loaded. The reply
  is still a normal command reply, not an error, so `PaperHost.command` does not throw. The contract test loads chunk
  `0 0` with `forceload add 0 0` before it places blocks, and checks the block read back (T-M2-02).
- **The agent's log buffer reads JUL and the Log4j2 root** (`LogBuffer`, `Log4jTap`; Log4j added in T-M2-08, it was
  JUL only before). Paper forwards every JUL record to Log4j, so each such record arrives twice on one thread; the
  buffer drops the second copy by level, logger and message. A real run after boot logs INFO records
  (`ProofstoneAgent`, `spark`) and no WARN.

- **`reset` teleports the player** to the fixture spot, so teleport after it, never before (T-M2-07). A test that
  teleported first and reset second left the client far from its anvil; the window never opened and the server kicked
  the client with `multiplayer.disconnect.flying`.
- **`Enchantment.getName()` is a legacy name** and is missing for newer enchantments (`MENDING`). From 1.13 the agent
  reads the key with `Enchantment.getKey()` by reflection. The dual oracle found it (T-M2-07).
- **The agent did not read `storedEnchants`, `lore` or `damage` back** until T-M2-07, so an enchanted book looked
  unenchanted to the server oracle while the client parsed it right. Found as a `DISAGREE` in `DualOracleE2E`.
- **While a container is open the server syncs inventory changes to it, not to window 0.** The driver copies the
  container's player slots back on close; read `Client.inventory()` after closing a window (T-M2-07).

- **An anvil rename costs one level on 26.3** when the item has no earlier work (C05). The client types the name with
  `ServerboundRenameItemPacket`; the result slot updates with no click sync, so await slot 2. Taking the result needs the
  level, so set it first. Combining a sword with a book (C06) costs more and depends on the enchantment, so the scenario
  takes the cost from the server and checks both oracles agree on it.
- **Closing a container is what hands the player's slots back.** Until then the client's window 0 is stale for the
  inventory moves made in the container. The driver copies the container's player slots back on close, and the
  `ItemView` read must take those raw stacks too (T-M2-09 fix). Cursor reads come from the player, not the container.

## Timings (Paper 26.3, java25 image, maintainer's Windows machine, Docker Desktop, T-M1-99)
Measured from the JUnit XML suite times of one serial run, image already pulled:
- One suite that boots a cell, joins one client and tears down: about 31 to 32 s (`:conformance:smoke` 2 tests,
  `McplDriverIT`, `VersionCommandE2E`).
- The server log reports `Done (12.060s)!` for the world load in the first real boot; the rest is container start,
  the agent copy and the readiness poll.
- `PaperHostIT` (4 tests, three of which boot a server) takes about 91 s.
- A cold first run adds the image pull and the Paper jar download. CI `smoke` took about 3.5 min including Gradle
  setup.
- **The per-test log check reads WARN and worse, from JUL and Log4j (T-M2-08).** The fixture plugin logs one WARN at
  enable, before any test's log cursor is taken, so it never fails a test. A test that reloads the fixture logs it
  again and must expect that. The allowlist (`ErrorLogGuard.ALLOWLIST`) holds the `No key layers` noise and
  `T<8 hex> moved too quickly!`: with Log4j captured, the server's anti-cheat WARN for the harness client shows up,
  because the reset teleport races the client's first movement packets. It is allowed for harness client names only.
- **The client inventory from the driver lists empty slots as `air x0` items**, so "empty" means all items
  `isEmpty()`, not an empty list. The agent's `inventory` omits empty slots.
