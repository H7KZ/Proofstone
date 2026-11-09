# Agent core (`platforms:agent-core`)

## Purpose
The part of an in-server agent that does not touch the game: the wire envelope, bearer auth, the HTTP server with its
routes, and the log buffer. The Bukkit and Velocity agents use it, and the BungeeCord agent reuses it (T-M7-04, T-M7-05,
[ADR-0016](../../docs/adr/0016-topology-cells.md)). Spec:
[agent protocol](../../docs/specs/agent-protocol.md) §1, §5.

## Boundaries
- No platform API: no Bukkit, Velocity or BungeeCord types, and no dependency on any of them. Game state comes only
  through `game.Game`, which each agent implements.
- Java 8 bytecode, checked by `verifyBytecode`, so every agent can load it.
- Bundles only `jackson-databind`. It is not relocated here: each agent's shadow jar relocates it to
  `io.github.h7kz.proofstone.shaded.jackson`.
- Not a public artefact. It is shaded into agent jars, never published and never used by `proofstone-api`.
- No behaviour of its own that differs per platform. A change here changes every agent, so wire bytes and log lines move
  only through a card that says so.

## Public surface
Package names are the ones the Bukkit agent had, so nothing that shaded it is affected.
- `io.github.h7kz.proofstone.agent.protocol`: `Health`, `ProtocolError`, `Envelope<T>`, `AgentException`.
- `io.github.h7kz.proofstone.agent.server`: `AgentServer` (HTTP, auth, bodies, envelopes, routing), `AgentConfig`,
  `BearerAuth`, `ObservationRoutes`, `PluginRoutes`, `ProxyRoutes` (`/v1/proxy/servers`, protocol 1.2: a `Game` that does not override `proxyServers`/`proxyServer` answers 404, so a backend agent never serves them). Endpoints and error codes are listed in the
  [Bukkit agent README](../bukkit/agent/README.md).
- `io.github.h7kz.proofstone.agent.game`: `Game` (the state seam) and `Fields` (request body checks).
- `io.github.h7kz.proofstone.agent.log`: `LogBuffer` (JUL and Log4j2 capture with twin matching, spec §2 `log`) and
  `Log4jTap` (the reflective Log4j2 appender). `LogBuffer.attach()` reads JUL and Log4j2 (Bukkit); `attachPreferringLog4j()` reads Log4j2 alone when present (Velocity bridges JUL itself).

## How to test
- `./gradlew :platforms:agent-core:test`: auth, config, the envelope over real HTTP with a `FakeGame`, protocol round
  trips, and the golden cases in `proofstone-protocol/golden` replayed by `GoldenContractTest`.
- `./gradlew :platforms:agent-core:verifyBytecode`: fails if any class is not major version 52.
- The real-server checks are the Bukkit agent's `:platforms:bukkit:agent:integrationTest` (Paper with the shaded jar) and the Velocity agent's `:platforms:velocity:agent:integrationTest` (Velocity 4.3.1).
