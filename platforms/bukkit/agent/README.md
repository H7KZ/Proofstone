# Bukkit agent (`platforms:bukkit:agent`)

## Purpose
The in-server agent for Bukkit-family servers (Paper, Spigot, Purpur, Folia). It is a plugin jar that
answers the harness over the wire protocol v1 ([spec](../../../docs/specs/agent-protocol.md),
[ADR-0002](../../../docs/adr/0002-server-truth-via-agent.md)). It ships health, command, player reads, and the
setup writes: give, reset and PATCH.

## Boundaries
- Observes only. It never changes game state outside harness setup, and it never runs on the act path
  (ADR-0002).
- Java 8 bytecode, checked by `verifyBytecode`, so it loads on 1.8.8 servers. Use only the Bukkit 1.8.8 API
  or reflection behind a capability guard.
- Bundles only `jackson-databind`, relocated to `dev.proofstone.shaded.jackson`. No other runtime libraries.
- The protocol types here are Java 8 plain classes. They do not depend on `proofstone-protocol`'s Java 17
  records.
- Refuses to serve without `PROOFSTONE_AGENT_TOKEN`. Logs SEVERE and leaves the HTTP server down.
- `server.AgentServer` does HTTP, auth, bodies and envelopes only. Game state comes through `game.Game`.
  `BukkitGame` is the only class that touches Bukkit state.

## Public surface
- Environment: `PROOFSTONE_AGENT_TOKEN` (required), `PROOFSTONE_AGENT_PORT` (default `25580`).
- Endpoints, bearer-token auth, all under `/v1`:
  - `GET /health`;
  - `POST /command` with `{command, timeoutMs?}`, reply `{raw}` (best-effort output);
  - `GET /players`, reply `{players: [name]}`;
  - `GET /players/{name}`, reply `PlayerState`, or `PLAYER_NOT_FOUND`;
  - `POST /players/{name}/give` with an `Item` body (`item.json`), reply `{}`. Levels are never clamped. Unknown
    material or enchantment: `UNKNOWN_KEY`, nothing given. Full inventory: `INVENTORY_FULL`;
  - `POST /players/{name}/reset`, reply `{}`. Clears inventory, armour, offhand and cursor; XP 0; survival;
    full heal; teleport to the main world's spawn + (0, 1, 0); closes any open window;
  - `PATCH /players/{name}` with any of `level`, `exp` (0–1), `gamemode`, `location` (`world`, `x`, `y`, `z`, and
    optional `yaw`, `pitch`), `op`, reply `{}`. All fields are checked before any is applied.
- Error codes: `UNAUTHORIZED` 401, `PLAYER_NOT_FOUND` 404, `INVALID_REQUEST` 400, `UNKNOWN_KEY` 400,
  `TIMEOUT` 504, `NOT_FOUND` 404, `METHOD_NOT_ALLOWED` 405, `INVENTORY_FULL` 409, `INTERNAL` 500. The last five
  are additions within v1, not yet in the spec table.
- `dev.proofstone.agent.protocol`: `Health`, `ProtocolError`, `Envelope<T>`, `AgentException`.
- `dev.proofstone.agent.game.Game`: the state seam (reads and writes). Implemented by
  `dev.proofstone.agent.bukkit.BukkitGame`; `game.Fields` checks request bodies.
- Key resolution (`bukkit.Keys`): `Material.matchMaterial` on the upper-case path (the 1.8 enum name). Its 1.13+
  behaviour is unverified until T-M1-05. Enchantments: legacy name on 1.8, `Enchantment.getByKey` on 1.13+ via
  reflection.
  Known gap: an item whose 1.8 enum name differs from its key (for example `minecraft:grass_block`) is
  `UNKNOWN_KEY` until a legacy table covers it.
- `dev.proofstone.agent.scheduler`: `Scheduler` (main-thread hand-off, Folia comes in M5) and `MainThread.call`,
  which waits for a result with the request timeout.
- Platform detection: `dev.proofstone.agent.platform.PlatformDetector`.

## How to test
- `./gradlew :platforms:bukkit:agent:test`: auth, config, platform probes, the envelope over real HTTP,
  protocol round trips, `MainThread` timeouts, and the golden cases in `proofstone-protocol/golden`
  (`health/*`, `command/reply`, `players/not-found`, `players/empty-inventory`, and `players/give-enchanted-sword`
  then `players/read-after-give` in order), replayed by `GoldenContractTest` through `GoldenShape`, a Java 8 twin
  of `GoldenMatcher`. `ItemBuilderTest` checks key resolution and `UNKNOWN_KEY` with the Bukkit registries alone.
  `BukkitGame` on a live server (reset, PATCH, enchantment levels) needs a real server, so it is covered by the
  T-M1-05 contract test, not here. Test-only dependencies: `spigot-api` (no transitives), Guava 17.0 and
  commons-lang 2.6, the versions its POM declares, so the static registries load.
- `./gradlew :platforms:bukkit:agent:verifyBytecode`: fails if any agent class is not major version 52.
- `./gradlew :platforms:bukkit:agent:shadowJar`: builds `build/libs/agent-all.jar` with Jackson relocated.
