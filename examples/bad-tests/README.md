# examples:bad-tests

## Purpose

Deliberately bad tests that prove the safety nets catch what they claim to catch (self-tests spec). Each test is
expected to produce one verdict. `VacuousE2E` (T-M4-03) passes without its plugin, so a control run must report it as
`VACUOUS`. Later cards add flaky, disagree, invalid and leak tests (T-M4-07) and a meta-test that asserts them.

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
