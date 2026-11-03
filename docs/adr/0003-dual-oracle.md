# ADR-0003: Every assertion uses two oracles

- Status: Accepted
- Date: 2026-11-03

## Context
- The client's item model lagged the protocol in the prototype: prismarine-item couldn't read
  `stored_enchantments` and reported numeric enchantment IDs.
- The server view alone misses bugs where a client sees something different, such as desyncs, ghost
  items or missing slot updates.
- Either oracle alone can be wrong without anyone noticing.

## Decision
- Assertions on observable game state compare the **server oracle** (agent) with the **client oracle**
  (driver).
- If both match the expectation, the test passes. If one matches and the other doesn't, the verdict is
  `DISAGREE`: the test fails and is reported as a possible harness or desync bug, with both values.
- A test may opt out per assertion (`.serverOnly("reason")`) for state that clients can't see, such as
  persistent data containers or plugin config. The reason is mandatory and appears in reports.

## Consequences
- Assertions take longer, because they wait for both sides. This is accepted (correctness over speed).
- Driver item parsing must be at least as rich as the agent's item schema for visible fields.

## Alternatives considered
- **Server-only assertions:** miss client desyncs. Rejected as the default.
- **Client-only assertions:** too fragile across versions. Rejected.
