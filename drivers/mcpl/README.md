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

- `io.github.h7kz.proofstone.driver.mcpl.McplDriver`: `ClientDriver` with id `mcpl`; `connect(ConnectSpec)` returns a `ClientSession` in the play state, or throws `ClientDriverException` with the reason (kick, refusal) or `JoinTimeoutException` (connect timeout) whose message names the stalled handshake stage (T-M6-14).
- Sessions offer `useBlock(BlockPos, Face)` (look packet, then use-item-on with main hand and a sequence number), `awaitWindow(Duration)` returning a `WindowSession` (type as a namespaced id such as `minecraft:generic_9x3`, id, `stateId`, all synced slots as `SlotStack` registry id plus amount, cursor), and `inventory()` for window 0. Clicking arrives in T-M2-05.
- Windows from a session (and `inventory()`) also implement `io.github.h7kz.proofstone.core.driver.ItemViews`: `itemViews()` and `cursorView()` parse the slots on demand into `ItemView` (type, amount, enchants, stored enchants, plain-text custom name, lore, damage, repair cost, custom data). Enchantment ids are named from the registry the server sends in the configuration phase; item ids from the bundled `items-<protocol>.txt` (the server's own registry report, regenerated per protocol pin, see `docs/platforms/paper.md`). An id that cannot be named is an error. `ItemView`, `Click` and `BlockPos` are public API types in `proofstone-api` (T-M2-07), which this module depends on.
- Sessions offer `chat`, `command`, `awaitMessage(Pattern, Duration)` over a buffer of system and player chat as plain text, and `transcript()` of timestamped packet names.
- `awaitServerSwitch(Duration)` (T-M7-09): survives a proxy moving the client to another backend (configuration phase, then a new login; state `PLAYING`, `SWITCHING`, `PLAYING`). Views reset to the new server, a kick is a failure with its reason, a stall is a `JoinTimeoutException` with a `SWITCH_*` stage. See client-driver spec §2a.

## How to test

```bash
./gradlew :drivers:mcpl:test                      # no Docker: flattening, refused connection, connect timeout and its stage (loopback listeners)
PROOFSTONE_ACCEPT_EULA=true ./gradlew :drivers:mcpl:integrationTest   # Docker: boots Paper with the agent, joins
# Docker, a proxy and two Paper backends, a client switching there and back (T-M7-09), one proxy per run:
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :proofstone-core:topologyTest --tests '*VelocitySwitchIT' --no-parallel --max-workers=1
```

The pinned library version lives in `gradle/libs.versions.toml` and `versions.yml` (`drivers.mcpl`); keep them in step.
