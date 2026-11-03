# drivers/mcpl

## Purpose

The primary client driver (ADR-0004): a Java client on MCProtocolLib that speaks only the newest protocol, with offline auth. It joins a server, sends chat and commands, and buffers received chat for `awaitMessage`. It implements the SPI in `proofstone-core`'s `driver` package.

## Boundaries

- Newest protocol only. Older servers are reached through a ViaProxy container that core starts (ADR-0004), never by shading more MCProtocolLib versions.
- Server-authoritative: it never predicts client state. Windows and the player inventory are mirrored from `ContainerSetContent`/`ContainerSetSlot` only.
- Touches only the packets in client-driver spec §3. The library's default listeners handle login, configuration (registries, known packs, finish) and keep-alive.
- Waits only through `Await`; no sleeps (AGENTS.md §3).
- This is the one host-side module on Java 21, because MCProtocolLib 26.3 is published for Java 21 or newer. Its dependents must run on 21+.

## Public surface

- `dev.proofstone.driver.mcpl.McplDriver`: `ClientDriver` with id `mcpl`; `connect(ConnectSpec)` returns a `ClientSession` in the play state, or throws `ClientDriverException` with the reason (kick, refusal, connect timeout).
- Sessions offer `useBlock(BlockPos, Face)` (look packet, then use-item-on with main hand and a sequence number), `awaitWindow(Duration)` returning a `WindowSession` (type as a namespaced id such as `minecraft:generic_9x3`, id, `stateId`, all synced slots as `SlotStack` registry id plus amount, cursor), and `inventory()` for window 0. Clicking arrives in T-M2-05.
- Windows from a session (and `inventory()`) also implement `ItemViews`: `itemViews()` and `cursorView()` parse the slots on demand into `ItemView` (type, amount, enchants, stored enchants, plain-text custom name, lore, damage, repair cost, custom data). Enchantment ids are named from the registry the server sends in the configuration phase; item ids from the bundled `items-<protocol>.txt` (the server's own registry report, regenerated per protocol pin, see `docs/platforms/paper.md`). An id that cannot be named is an error. `ItemView` moves to `proofstone-api` in T-M2-07.
- Sessions offer `chat`, `command`, `awaitMessage(Pattern, Duration)` over a buffer of system and player chat as plain text, and `transcript()` of timestamped packet names.

## How to test

```bash
./gradlew :drivers:mcpl:test                      # no Docker: flattening, refused connection, connect timeout
PROOFSTONE_ACCEPT_EULA=true ./gradlew :drivers:mcpl:integrationTest   # Docker: boots Paper with the agent, joins
```

The pinned library version lives in `gradle/libs.versions.toml` and `versions.yml` (`drivers.mcpl`); keep them in step.
