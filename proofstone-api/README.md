# proofstone-api

## Purpose

Public API types for test authors (layer L1, ADR-0001). The only module whose types users compile against.

## Boundaries

- Must not depend on any other Proofstone module.
- Must not contain implementation code. Types and interfaces only.
- Must not change a public type without an ADR or a card that says so.

## Public surface

The test API of [docs/specs/test-api.md](../docs/specs/test-api.md) §1–§2, in `io.github.h7kz.proofstone.api`:

- Annotations: `@Proofstone`, `@Matrix` (repeatable, with `Platform`), `@CellTest` (a JUnit `TestTemplate`).
- Per-cell expectations (T-M4-15, api-change, test-api spec §9): `@Expect` (class or method) with a `CellExpectation`,
  checked after the test body on each cell. Added after v0; the four new baseline lines are the only change.
- Capabilities (T-M5-01, api-change, test-api spec section 10): `Capability` (an enum), `@Requires` (class or method) and
  `Cell.capabilities()`. A cell without a required capability is skipped with its reason. The baseline gains only these members; no frozen v0 member changed. The fix PR (`fix/m5-capability-api`) made
  `Cell.capabilities()` a default method that throws, and removed `Capability.NMS` and `Capability.DATAPACKS` (no agent token).
- Server log (T-M11-01, api-change, additions only): `Agent.logLines()` and `Agent.logLines(Pattern, Duration)`, default methods that
  throw `UnsupportedOperationException`. No protocol change (`GET /v1/log` already exists), no capability.
- Boot log (T-M11-03, api-change, addition only): `Agent.bootLogLines()`, a default method that throws `UnsupportedOperationException`. No
  protocol change (`GET /v1/log?since=0`), no capability.
- Server window (T-M11-02, api-change, additions only): `PlayerState.openWindow()` (an `Optional<ServerWindow>`, a new last record
  component) and `ServerWindow(type, title, slots)`. The 7-argument `PlayerState` constructor stays. No protocol change (`openWindow` is in
  `player-state.json` already), no capability.
- Data packs (T-M8-09, api-change, additions only, ADR-0017): `Proofstone.datapacks()` (globs, default none) and
  `Capability.DATAPACKS_VANILLA` (last constant). Declaring a pack implies the capability.
- Mods (T-M8-13, api-change, additions only, ADR-0017): `Proofstone.mods()` (globs, one jar each, default none) and
  `Capability.MODS` (last constant). Declaring a mod implies the capability; a cell without it skips.
- Resource packs (T-M8-10, api-change, additions only, ADR-0017 §7): `@ServerResourcePack` (class: `value`, `required`,
  `prompt`, `sha1`), `@ResourcePackAnswer` (class or method, `Answer.ACCEPT`, `DECLINE`, `FAIL_DOWNLOAD`, no default),
  `ResourcePackPush` (a record) and `Client.resourcePacks()` (default method, throws), `Capability.RESOURCE_PACKS` (last
  constant). Declaring a pack implies the capability. `CapturedEvent` now keeps a getter that returned null (its record
  constructor is experimental; no signature changed).
- Bedrock (T-M9-07, api-change, additions only, ADR-0018 section 9): `Platform.GEYSER`, `Capability.BEDROCK_CLIENT` (last constants)
  and `Clients.bedrock(String)`, a default method that throws until the core overrides it. No public `Edition`.
  `SIMULATED_BEDROCK` keeps its meaning (a Java client under a `.` name) and never claims a real Bedrock client.
- Injected parameters: `Cell`, `Agent` (`command`, `capabilities`, `player`) and `Client` (`name`, `command`,
  `awaitMessage`, `position`, `awaitPosition`, `openBlock`, `inventory`).
- Setup and views (T-M2-07): `PlayerHandle` and `PlayerState` (server oracle), `Window`, `Click`, `Item` (build),
  `ItemView` (read), `BlockPos`, `Position`.
- Dual oracle (T-M2-07): `DualAssert` and `DisagreeException`. `io.github.h7kz.proofstone.api.spi.OracleWaiter` is how it waits;
  `proofstone-core` provides it, so this module still has no wait of its own and no dependency on another module.
- **API v0 is frozen (T-M2-99).** `api-baseline.txt` is the v0 contract: `ApiSurfaceTest` dumps every public member and
  fails on any difference. There is no version constant; the baseline and the status line of the spec are the marker.
  Change it only with a card marked `api-change`: regenerate with
  `./gradlew :proofstone-api:test -Dproofstone.updateApiBaseline=true` and review the diff.
- **Experimental inside v0**: `io.github.h7kz.proofstone.api.spi.*`, `DualAssert.serverOnlyReasons()`, the record constructors of
  `PlayerState`, `CapturedEvent` and `ItemView`, and the close semantics of `Window.close()`. Not in v0 at all:
  `Agent.world()`, `awaitTicks`, `gamemode`, `Clients`, `Topology`, the fixture reset and the
  leak detector. Details are in [docs/specs/test-api.md](../docs/specs/test-api.md) section 8.

The extension that runs them is `proofstone-core`'s `io.github.h7kz.proofstone.core.junit.ProofstoneExtension`, registered by
JUnit auto-detection (`junit.jupiter.extensions.autodetection.enabled=true`).

## How to test

`./gradlew :proofstone-api:test`
