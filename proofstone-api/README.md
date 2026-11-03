# proofstone-api

## Purpose

Public API types for test authors (layer L1, ADR-0001). The only module whose types users compile against.

## Boundaries

- Must not depend on any other Proofstone module.
- Must not contain implementation code. Types and interfaces only.
- Must not change a public type without an ADR or a card that says so.

## Public surface

None yet. Empty in M0; the test API arrives with its card (see [docs/specs/test-api.md](../docs/specs/test-api.md)).

## How to test

`./gradlew :proofstone-api:test`
