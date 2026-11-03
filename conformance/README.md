# conformance

## Purpose

Shared vanilla scenarios and the runner that executes them against real servers (layer L4). `:conformance:smoke`
runs the first two, C01 (agent health and protocol) and C02 (`/help` gets a system reply), on the newest Paper row
(T-M1-11). The rest of the suite (C03 onwards) arrives with M2.

## Boundaries

- Must not run in `check`. The scenarios boot a real server, so they need Docker and the Minecraft EULA.
- Must not turn a missing prerequisite into a pass. Without the EULA or Docker, `smoke` skips with the reason
  locally. In CI (`CI=true`) it fails the task, so a skip is never green (AGENTS.md §3).
- Must not add a scenario that needs more than the public test API. Scenarios use `@Proofstone`, `@Matrix` and
  `@CellTest` from `proofstone-api`.

## Public surface

The `smoke` task only. Its scenarios are `ConformanceSmokeTest` (C01, C02). The task needs a Java 21+ toolchain,
set in `build.gradle.kts`, because the mcpl driver needs it.

## How to test

Read <https://aka.ms/MinecraftEULA> first. The harness never accepts the EULA for you. Then run it with the
variable in the command environment only:

```bash
PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:smoke
```

Needs Docker. Without the EULA variable the task prints a `smoke SKIPPED (not a pass)` line and does not run.
