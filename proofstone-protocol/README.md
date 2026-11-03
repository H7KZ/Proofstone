# proofstone-protocol

## Purpose

Wire-protocol schemas, generated types and golden files (layer L3c). Also holds the `versions.yml` JSON Schema from T-M0-04.

Protocol v1 (T-M1-01) is the JSON Schema set in `schemas/`: `envelope`, `error`, `health`, `item`, `player-state`
and `command`. Spec: [docs/specs/agent-protocol.md](../docs/specs/agent-protocol.md) §1–§3.

## Boundaries

- Schemas change only through a card that says so. Bump the protocol version and update the golden files together (AGENTS.md §3).
- Must not depend on a platform, driver or host-side engine module.
- Types have no runtime dependencies. JSON libraries are test-only; the tests serialise and validate.
- The Java types are hand-written records, not generated. Each one is pinned to its schema by the round-trip and
  schema tests. Decision recorded here because the card asked for one: we chose hand-written records over
  jsonschema2pojo, to avoid a generator dependency and keep the diff small.

## Public surface

Records in `dev.proofstone.protocol`, one per schema object:

- `Envelope<T>(ok, data, error)`, `ProtocolError(code, message, detail)`
- `Health(agentVersion, protocol, platform, mcVersion, capabilities)`
- `Item(type, amount, enchants, storedEnchants, name, lore, damage, repairCost, pdc, raw)`: optional fields are `null` when absent
- `PlayerState(name, uuid, level, exp, gamemode, health, location, inventory, cursor, openWindow)`, with `Location` and `OpenWindow`
- `Command(command, timeoutMs)`, `CommandReply(raw)`

Not yet: the Java 8 plain-class set for the Bukkit agent (the card's "second type set"). It is deferred to a follow-up card, because it duplicates these types for `--release 8`.

## How to test

`./gradlew :proofstone-protocol:test`

Examples are inline in `ProtocolExamples` until T-M1-02 adds `golden/`.
