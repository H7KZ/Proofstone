# proofstone-protocol

## Purpose

Wire-protocol schemas, generated types and golden files (layer L3c). Also holds the `versions.yml` JSON Schema from T-M0-04.

Protocol v1 (T-M1-01) is the JSON Schema set in `schemas/`: `envelope`, `error`, `health`, `item`, `player-state`
and `command`. Spec: [docs/specs/agent-protocol.md](../docs/specs/agent-protocol.md) §1–§3.

Golden request and response pairs for v1 are in `golden/` (T-M1-02). Their format is in [golden/README.md](golden/README.md).

## Boundaries

- Schemas change only through a card that says so. Bump the protocol version and update the golden files together (AGENTS.md §3).
- Must not depend on a platform, driver or host-side engine module.
- Types have no runtime dependencies. JSON libraries are test-only; the tests serialise and validate.
- The Java types are hand-written records, not generated. Each one is pinned to its schema by the round-trip and
  schema tests. Decision recorded here because the card asked for one: we chose hand-written records over
  jsonschema2pojo, to avoid a generator dependency and keep the diff small.

## Public surface

Records in `io.github.h7kz.proofstone.protocol`, one per schema object:

- `Envelope<T>(ok, data, error)`, `ProtocolError(code, message, detail)`
- `Health(agentVersion, protocol, platform, mcVersion, capabilities)`
- `Item(type, amount, enchants, storedEnchants, name, lore, damage, repairCost, pdc, raw)`: optional fields are `null` when absent
- `PlayerState(name, uuid, level, exp, gamemode, health, location, inventory, cursor, openWindow)`, with `Location` and `OpenWindow`
- `Command(command, timeoutMs)`, `CommandReply(raw)`
- Protocol 1.1 (T-M2-01): `Block(world, x, y, z, type, state)`, `BlockWrite(type, state)` (block.json);
  `ConfigEntry(path, value)` (config.json); `Ticks(global, regions)` (ticks.json);
  `EventSubscribe(className)`, `EventPage(events, next)`, `ProbeEvent(className, tick, thread, fields)` (event.json);
  `LogRecord(time, level, logger, message, thrown)`, `LogPage(records, next)` (log-record.json).

Decision (T-M2-01): the Java field `className` maps to JSON key `class`, which Java reserves. The round-trip test
skips `EventSubscribe` and `ProbeEvent`, so those two are checked by schema and golden tests only. The agent
serialiser must write the `class` key.

Decision (T-M2-01): no Java 8 twins for the new types. The agent does not use them until T-M2-02 and T-M2-03,
and those cards add only what they use.

Test fixtures in `io.github.h7kz.proofstone.protocol.testing` (`java-test-fixtures`, not on the main classpath):

- `GoldenMatcher.mismatches(expected, actual)` returns one message per difference, each with a JSON path. It
  understands the placeholders in `golden/README.md`. `GoldenMatcher.concrete(json)` replaces them with samples
  so a golden file can be schema-validated.

Not yet: the Java 8 plain-class set for the Bukkit agent (the card's "second type set"). It is deferred until an agent card uses the new types, because it duplicates these types for `--release 8`.

## How to test

`./gradlew :proofstone-protocol:test`

Schema and round-trip tests use the inline examples in `ProtocolExamples`. `GoldenContractTest` replays `golden/`.
