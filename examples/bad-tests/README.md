# examples:bad-tests

## Purpose

Deliberately bad tests that prove the safety nets catch what they claim to catch (self-tests spec). Each test is
expected to produce one verdict. `VacuousE2E` (T-M4-03) passes without its plugin, so a control run must report it as
`VACUOUS`. `differential/DriverFaultDisagreeE2E` (T-M4-06) asserts only what both drivers report correctly, but the
reference driver is faulted (`proofstone.injectDriverFault`, one extra damage point on the inventory), so a
differential run must report it as `DISAGREE`. Later cards add flaky, invalid and leak tests (T-M4-07) and a meta-test
that asserts them.

## Boundaries

- Must stay out of user-facing suites. These tests exist to fail in the expected way, not to be green.
- Not part of `check`: its tests boot a real server, so they need Docker.
- Contains no framework code, only a test project that uses the public API and the verdict switch.

## Public surface

None. The test classes are the examples.

## How to test

Read <https://aka.ms/MinecraftEULA> first. The harness never accepts the EULA for you.

```bash
PROOFSTONE_ACCEPT_EULA=true ./gradlew :examples:bad-tests:test
PROOFSTONE_ACCEPT_EULA=true ./gradlew :examples:bad-tests:test -Dproofstone.controlRun=true
```

The first run is green: the vacuous test passes with its plugin. The control run is expected to fail with
`VACUOUS` on `VacuousE2E` (the failure message names the verdict). Needs Docker and a Java 21+ toolchain. Run both
through `scripts/dockerlock.sh`, one Docker run at a time.

```bash
PROOFSTONE_ACCEPT_EULA=true ./gradlew :examples:bad-tests:test -Dproofstone.differential=true
```

The differential run is expected to fail with `DISAGREE` on `DriverFaultDisagreeE2E` (the mineflayer-ref invocation).
Its repro bundle is `build/proofstone/paper@26.1.2/DriverFaultDisagreeE2E.<test>/mineflayer-ref/`, with both drivers'
observations and transcripts under `differential/`. The differential package is left out of every other run, and the
fault flag is set only by this run. A differential run runs only that package: the other bad tests use `latest`, which
is not the reference driver's row, so they have no differential cell.
