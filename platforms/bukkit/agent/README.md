# Bukkit agent (`platforms:bukkit:agent`)

## Purpose
The in-server agent for Bukkit-family servers (Paper, Spigot, Purpur, Folia). It is a plugin jar that
answers the harness over the wire protocol v1 ([spec](../../../docs/specs/agent-protocol.md),
[ADR-0002](../../../docs/adr/0002-server-truth-via-agent.md)). It ships health, command and player reads.
`give`, `reset` and `PATCH` come in a follow-up card.

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
  - `GET /players/{name}`, reply `PlayerState`, or `PLAYER_NOT_FOUND`.
- Error codes: `UNAUTHORIZED` 401, `PLAYER_NOT_FOUND` 404, `INVALID_REQUEST` 400, `UNKNOWN_KEY` 400,
  `TIMEOUT` 504, `NOT_FOUND` 404, `METHOD_NOT_ALLOWED` 405, `INTERNAL` 500. The last four are additions
  within v1, not yet in the spec table.
- `dev.proofstone.agent.protocol`: `Health`, `ProtocolError`, `Envelope<T>`, `AgentException`.
- `dev.proofstone.agent.game.Game`: the state seam. Implemented by `dev.proofstone.agent.bukkit.BukkitGame`.
- `dev.proofstone.agent.scheduler`: `Scheduler` (main-thread hand-off, Folia comes in M5) and `MainThread.call`,
  which waits for a result with the request timeout.
- Platform detection: `dev.proofstone.agent.platform.PlatformDetector`.

## How to test
- `./gradlew :platforms:bukkit:agent:test`: auth, config, platform probes, the envelope over real HTTP,
  protocol round trips, `MainThread` timeouts, and the golden cases in `proofstone-protocol/golden`
  (`health/*`, `command/reply`, `players/not-found`, `players/empty-inventory`), replayed by `GoldenContractTest`
  through `GoldenShape`, a Java 8 twin of `GoldenMatcher`. `BukkitGame` needs a real server, so it is covered by
  the T-M1-05 contract test, not here.
- `./gradlew :platforms:bukkit:agent:verifyBytecode`: fails if any agent class is not major version 52.
- `./gradlew :platforms:bukkit:agent:shadowJar`: builds `build/libs/agent-all.jar` with Jackson relocated.
