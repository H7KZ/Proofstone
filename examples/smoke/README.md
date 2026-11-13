# examples:smoke

## Purpose

The smallest Proofstone user project: `VersionCommandE2E` joins the newest Paper row with a real client and runs
`/version`. It shows `@Proofstone`, `@Matrix`, `@CellTest` and the `Cell`, `Agent` and `Client` parameters.

## Boundaries

- Not part of `check`: its tests boot a real server, so they need Docker.
- Contains no framework code, only a test project that uses the public API.

## Public surface

None. The test class is the example to copy. `junit-platform.properties` turns on JUnit extension
auto-detection, which is how `proofstone-core` registers its extension.

## How to test

Read <https://aka.ms/MinecraftEULA> first. The harness never accepts the EULA for you.

```bash
PROOFSTONE_ACCEPT_EULA=true ./gradlew :examples:smoke:test
```

Needs Docker and a Java 21+ toolchain (the mcpl driver needs it). Without the EULA variable or without Docker the
tests are skipped with that reason. A skip is not a pass.

Repro bundles (T-M4-02): `ForcedFailureE2E` passes normally and fails on purpose with `-Dproofstone.forceFail=true`.
Its failure writes `build/proofstone/paper@<mc>/ForcedFailureE2E.failsOnPurposeWhenForced/`, and the run prints that
path as `PROOFSTONE BUNDLE:`.

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:smoke:test -Dproofstone.forceFail=true --no-parallel
```

Differential run (T-M4-06): `DifferentialAgreeE2E` runs with both drivers on 26.1.2, the reference driver's native row,
and passes only when their client observations match. The run selects it with `--tests`: the other classes here use
`latest`, which is not that row, so a differential run would not run them (`PROOFSTONE NOT DIFFERENTIAL`).

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:smoke:test -Dproofstone.differential=true --tests '*DifferentialAgreeE2E*' --no-parallel
```

Topology example (T-M7-14): `topology/ServerSwitchE2E` runs Velocity in front of two Paper backends and switches the client
with `transferTo`. `:examples:smoke:test` leaves it out; run it on one topology cell per invocation
([guide](../../docs/user/topologies.md)):

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:smoke:topologyExample -Dproofstone.cells='velocity@4.3.1+paper@26.3' --no-parallel --max-workers=1
```
