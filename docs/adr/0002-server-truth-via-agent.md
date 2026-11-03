# ADR-0002: Server truth comes from an in-server agent, never RCON or console

- Status: Accepted
- Date: 2026-11-03

## Context
The prototype ([research](../research/2026-11-03-e2e-survey.md)) found three independent RCON failures:
1. Long SNBT is truncated with `...` (26.1 `data get … Inventory`).
2. Command syntax changes per version (NBT before 1.20.5, components after).
3. On Folia, `/data` doesn't exist and `xp query` returns an empty reply.

## Decision
- Each platform pack ships an **agent**: a plugin, mod or script pack installed by the harness. It speaks
  the versioned JSON wire protocol ([spec](../specs/agent-protocol.md)) for setup and for reading server
  state.
- Agents use only stable platform APIs. For the Bukkit family that is the Bukkit API, compiled to Java 8
  bytecode, so one jar runs on every version.
- Agents **observe and never interfere**. They are not called during the act phase, and their listeners
  are read-only at MONITOR priority.
- Console commands remain available through the agent (`/v1/command`) for setup only, never as an oracle.

## Consequences
- Item specs and state are version-neutral JSON, so tests never handle SNBT or components.
- One agent implementation per platform family is needed (Bukkit, Velocity, Bungee, Fabric, NeoForge,
  BDS script, …).
- The agent is now critical code. It gets contract tests against golden files and its own conformance
  round trip on every cell.

## Alternatives considered
- **RCON or console as the oracle:** rejected for the reasons above.
- **Reading world and player files from disk:** this lags behind live state and the format varies by
  version. Rejected.
