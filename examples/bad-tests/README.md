# examples:bad-tests

## Purpose

Deliberately bad tests that prove the safety nets catch what they claim to catch (self-tests spec). Each test is
expected to produce one verdict, and `metaTest` asserts it:

- `VacuousE2E` (T-M4-03) passes without its plugin, so a control run must report it as `VACUOUS`.
- `FlakyE2E` (T-M4-04) draws its outcome from a fixed seed: a plain run passes, and `-Dproofstone.repeat=5` must report
  it as `FLAKY`.
- `differential/DriverFaultDisagreeE2E` (T-M4-06) asserts only what both drivers report correctly, but the reference
  driver is faulted (`proofstone.injectDriverFault`), so a differential run must report it as `DISAGREE`.
- `invalid/InvalidConformanceE2E` (T-M4-07) has a passing body, but the cell's conformance is broken on purpose
  (`proofstone.injectConformanceFault`), so it must report `INVALID` and its body must not run.
- `invalid/window/WindowConformanceInvalidE2E` has passing bodies, but the cell's window is broken on purpose
  (`proofstone.injectConformanceFault=window`), so every test on it must report `INVALID` (`metaInvalidWindow`).
- `leak/LeakE2E` (T-M4-07) changes the world's `random_tick_speed` and leaves it changed, so the leak detector must
  fail it with `LEAK` (self-tests section 4b).
- `expect/ExpectFailsE2E` (T-M4-15) has one wrong per-cell `@Expect` (it claims a capability Paper never reports) and one
  holding one. The wrong one must report `FAIL` with the expectation's message (`metaExpect`); the holding one passes.
- `requires/RequiresSkipsE2E` (T-M5-01) requires `FOLIA_REGIONS` on a Paper cell, so it must be `SKIPPED` with reason
  `missing capability FOLIA_REGIONS on cell` (`metaRequires`, asserted in `proofstone-report.json`). Its body fails if it
  runs. Its second test requires what Paper holds and must pass.

## Boundaries

- Must stay out of user-facing suites. These tests exist to fail in the expected way, not to be green. `invalid`, `leak`,
  `expect` and `requires` are excluded from `test` in every run; only `metaTest` runs them, each in its own Gradle task
  with its switch.
- Not part of `check`: its tests boot a real server, so they need Docker.
- Contains no framework code, only a test project that uses the public API and the verdict switch.

## Public surface

None. The test classes are the examples.

## How to test

Read <https://aka.ms/MinecraftEULA> first. The harness never accepts the EULA for you.

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaTest --no-parallel
```

`metaTest` runs the eight bad tests, each in its own task (`metaVacuous`, `metaFlaky`, `metaDisagree`, `metaInvalid`,
`metaInvalidWindow`, `metaLeak`, `metaExpect`, `metaRequires`), reads each task's JUnit XML under `build/test-results/<task>/`, and fails unless
every run has `skipped="0"` and `errors="0"`, and every failure starts with the expected verdict. A skip or a missing
report is red. `metaRequires` is the one task that expects a skip: its skipped test must name the missing capability in
the proofstone report.

The individual switches still work on `test` for the first three, as documented before:

```bash
PROOFSTONE_ACCEPT_EULA=true ./gradlew :examples:bad-tests:test
PROOFSTONE_ACCEPT_EULA=true ./gradlew :examples:bad-tests:test -Dproofstone.controlRun=true
PROOFSTONE_ACCEPT_EULA=true ./gradlew :examples:bad-tests:test -Dproofstone.repeat=5
PROOFSTONE_ACCEPT_EULA=true ./gradlew :examples:bad-tests:test -Dproofstone.differential=true -Dproofstone.cells=paper@26.1.2
```

The cell selector (`-Dproofstone.cells=<platform>@<mc>`) and `-Dproofstone.scope` reach every run, `test` and each `meta*`
task alike. The selector is subject to the cell pins below. A differential run (`-Dproofstone.differential=true`) needs the
native cell it runs on, `-Dproofstone.cells=paper@26.1.2`: without it the run stops before any server starts.

### Cell pins

The selector replaces each test's `@Matrix`, so a selector can move a test to a cell where its verdict means something else.
Tests whose `@Matrix` names one version are pinned to it. Without a selector a pinned task runs its pin. A selector that names any
other cell is refused in that task's `doFirst`, before any server starts. The refusal names the task and the supported cell
(`scripts/bad-tests-pin-check.sh`, run by `scripts/gates.sh`).

| Task | Test | `@Matrix` | Supported cells |
|---|---|---|---|
| `metaVacuous` | `VacuousE2E` | `latest` | any Paper cell |
| `metaFlaky` | `FlakyE2E` | `latest` | any Paper cell |
| `metaDisagree` | `differential/DriverFaultDisagreeE2E` | `26.1.2` | `paper@26.1.2` only |
| `metaInvalid` | `invalid/InvalidConformanceE2E` | `latest` | any Paper cell |
| `metaInvalidWindow` | `invalid/window/WindowConformanceInvalidE2E` | `26.1.2` | `paper@26.1.2` only |
| `metaLeak` | `leak/LeakE2E` | `26.1.2` | `paper@26.1.2` only |
| `metaExpect` | `expect/ExpectFailsE2E` | `latest` | any Paper cell |
| `metaRequires` | `requires/RequiresSkipsE2E` | `latest` | any Paper cell |

`-Dproofstone.cells=paper@26.3` on `metaTest` is therefore refused at `metaInvalidWindow`, `metaDisagree` and `metaLeak`. Run
the cell-agnostic tasks on another cell by name, for example `:examples:bad-tests:metaVacuous -Dproofstone.cells=paper@26.3`.
`metaTest` without a selector is the full check.

The first run is green: the vacuous test passes with its plugin, and the flaky test passes on its first draw. The
control run is expected to fail with `VACUOUS` on `VacuousE2E`. The repeat run is expected to fail with `FLAKY` on
`FlakyE2E` (every run's bundle is under `build/proofstone/`, one `.rN` per run). The differential run is expected to fail
with `DISAGREE` on `DriverFaultDisagreeE2E` (its repro bundle is under `build/proofstone/`, with both drivers'
observations under `differential/`). A differential run runs only the `differential` package, because the other bad tests
use `latest`, which is not the reference driver's row. The `invalid` and `leak` tests are not run by `test`, so use
`metaTest` for them.

Needs Docker and a Java 21+ toolchain. Run each through `scripts/dockerlock.sh`, one Docker run at a time.
