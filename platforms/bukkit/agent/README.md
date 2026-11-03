# Bukkit agent (`platforms:bukkit:agent`)

## Purpose
The in-server agent for Bukkit-family servers (Paper, Spigot, Purpur, Folia). It is a plugin jar that
answers the harness over the wire protocol v1 ([spec](../../../docs/specs/agent-protocol.md),
[ADR-0002](../../../docs/adr/0002-server-truth-via-agent.md)). M1 ships `GET /v1/health` only.

## Boundaries
- Observes only. It never changes game state outside harness setup, and it never runs on the act path
  (ADR-0002).
- Java 8 bytecode, checked by `verifyBytecode`, so it loads on 1.8.8 servers. Use only the Bukkit 1.8.8 API
  or reflection behind a capability guard.
- Bundles only `jackson-databind`, relocated to `dev.proofstone.shaded.jackson`. No other runtime libraries.
- The protocol types here are Java 8 plain classes. They do not depend on `proofstone-protocol`'s Java 17
  records.
- Refuses to serve without `PROOFSTONE_AGENT_TOKEN`. Logs SEVERE and leaves the HTTP server down.

## Public surface
- Environment: `PROOFSTONE_AGENT_TOKEN` (required), `PROOFSTONE_AGENT_PORT` (default `25580`).
- Endpoint: `GET /v1/health`, bearer-token auth. The reply is the envelope from `protocol.Envelope`.
- `dev.proofstone.agent.protocol`: `Health`, `ProtocolError`, `Envelope<T>` (Java 8 mirrors of the wire types).
- `dev.proofstone.agent.scheduler.Scheduler`: main-thread hand-off. Folia comes in M5.
- Platform detection: `dev.proofstone.agent.platform.PlatformDetector`.

## How to test
- `./gradlew :platforms:bukkit:agent:test`: auth, config, platform probes, the health envelope over real
  HTTP, and protocol round trips. Health is checked against inline examples until the T-M1-02 golden files
  land.
- `./gradlew :platforms:bukkit:agent:verifyBytecode`: fails if any agent class is not major version 52.
- `./gradlew :platforms:bukkit:agent:shadowJar`: builds `build/libs/agent-all.jar` with Jackson relocated.
