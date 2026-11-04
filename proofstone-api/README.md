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
- Injected parameters: `Cell`, `Agent` (`command`, `capabilities`, `player`) and `Client` (`name`, `command`,
  `awaitMessage`, `position`, `awaitPosition`, `openBlock`, `inventory`).
- Setup and views (T-M2-07): `PlayerHandle` and `PlayerState` (server oracle), `Window`, `Click`, `Item` (build),
  `ItemView` (read), `BlockPos`, `Position`.
- Dual oracle (T-M2-07): `DualAssert` and `DisagreeException`. `dev.proofstone.api.spi.OracleWaiter` is how it waits;
  `proofstone-core` provides it, so this module still has no wait of its own and no dependency on another module.
- **API v0 is frozen (T-M2-99).** `api-baseline.txt` is the v0 contract: `ApiSurfaceTest` dumps every public member and
  fails on any difference. There is no version constant; the baseline and the status line of the spec are the marker.
  Change it only with a card marked `api-change`: regenerate with
  `./gradlew :proofstone-api:test -Dproofstone.updateApiBaseline=true` and review the diff.
- **Experimental inside v0**: `dev.proofstone.api.spi.*`, `DualAssert.serverOnlyReasons()`, the record constructors of
  `PlayerState`, `CapturedEvent` and `ItemView`, and the close semantics of `Window.close()`. Not in v0 at all:
  `Agent.world()`, `awaitTicks`, `logLines`, `gamemode`, `openWindow`, `Clients`, `Topology`, the fixture reset and the
  leak detector. Details are in [docs/specs/test-api.md](../docs/specs/test-api.md) section 8.

The extension that runs them is `proofstone-core`'s `dev.proofstone.core.junit.ProofstoneExtension`, registered by
JUnit auto-detection (`junit.jupiter.extensions.autodetection.enabled=true`).

## How to test

`./gradlew :proofstone-api:test`
