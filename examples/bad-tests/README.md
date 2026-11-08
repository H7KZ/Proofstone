# examples:bad-tests

## Purpose

Deliberately bad tests that prove the safety nets catch what they claim to catch (self-tests spec). Each test is
expected to produce one verdict. `VacuousE2E` (T-M4-03) passes without its plugin, so a control run must report it as
`VACUOUS`. `FlakyE2E` (T-M4-04) draws its outcome from a fixed seed: a plain run passes, and `-Dproofstone.repeat=5`
must report it as `FLAKY`. `differential/DriverFaultDisagreeE2E` (T-M4-06) asserts only what both drivers report
correctly, but the reference driver is faulted (`proofstone.injectDriverFault`, one extra damage point on the
inventory), so a differential run must report it as `DISAGREE`. Later cards add invalid and leak tests (T-M4-07) and a
meta-test that asserts them.

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
PROOFSTONE_ACCEPT_EULA=true ./gradlew :examples:bad-tests:test -Dproofstone.repeat=5
PROOFSTONE_ACCEPT_EULA=true ./gradlew :examples:bad-tests:test -Dproofstone.differential=true
```

The first run is green: the vacuous test passes with its plugin, and the flaky test passes on its first draw. The
control run is expected to fail with `VACUOUS` on `VacuousE2E` (the failure message names the verdict). The repeat run
is expected to fail with `FLAKY` on `FlakyE2E` (every run's bundle is under `build/proofstone/`, one `.rN` per run).
The differential run is expected to fail with `DISAGREE` on `DriverFaultDisagreeE2E` (the mineflayer-ref invocation).
Its repro bundle is `build/proofstone/paper@26.1.2/DriverFaultDisagreeE2E.<test>/mineflayer-ref/`, with both drivers'
observations and transcripts under `differential/`. A differential run runs only the `differential` package, because
the other bad tests use `latest`, which is not the reference driver's row. The fault flag is set only by that run.
Needs Docker and a Java 21+ toolchain. Run each through `scripts/dockerlock.sh`, one Docker run at a time.
