# proofstone-protocol

## Purpose

Wire-protocol schemas, generated types and golden files (layer L3c). Also holds the `versions.yml` JSON Schema from T-M0-04.

## Boundaries

- Schemas change only through a card that says so. Bump the protocol version and update the golden files together (AGENTS.md §3).
- Must not depend on a platform, driver or host-side engine module.

## Public surface

None yet. Empty in M0.

## How to test

`./gradlew :proofstone-protocol:test`
