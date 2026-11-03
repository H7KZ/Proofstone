# proofstone-api

## Purpose

Public API types for test authors (layer L1, ADR-0001). The only module whose types users compile against.

## Boundaries

- Must not depend on any other Proofstone module.
- Must not contain implementation code. Types and interfaces only.
- Must not change a public type without an ADR or a card that says so.

## Public surface

The test API of [docs/specs/test-api.md](../docs/specs/test-api.md) §1–§2, in `dev.proofstone.api`:

- Annotations: `@Proofstone`, `@Matrix` (repeatable, with `Platform`), `@CellTest` (a JUnit `TestTemplate`).
- Injected parameters: `Cell`, `Agent` (`command`, `capabilities`) and `Client` (`name`, `command`, `awaitMessage`).
  `conformance` (T-M1-11) uses only these; a system-message flag or the agent protocol version is not on them yet.

The extension that runs them is `proofstone-core`'s `dev.proofstone.core.junit.ProofstoneExtension`, registered by
JUnit auto-detection (`junit.jupiter.extensions.autodetection.enabled=true`).

## How to test

`./gradlew :proofstone-api:test`
