# conformance

## Purpose

Shared vanilla scenarios and the runner that executes them against real servers (layer L4). They check the harness
itself against real vanilla behaviour, on the newest Paper row:

- C01 agent health and protocol, C02 `/help` gets a system reply (`ConformanceSmokeTest`).
- C03 `give` round trip, both oracles read the same enchants, levels not clamped (`C03GiveRoundTripTest`).
- C04 chest: a stack moves from slot A to slot B (`C04ChestMoveTest`).
- C05 vanilla anvil rename: the result name, one level paid, the experience sync on both oracles (`C05AnvilRenameCostTest`).
- C06 vanilla anvil combine: sword and enchanted book give the vanilla result and the levels paid (`C06AnvilCombineTest`).

- C07 grindstone removes enchants, the result slot and the input on both oracles (`C07GrindstoneTest`). Skipped on
  1.12.2, which has no grindstone (1.14 introduced it).
- C08 shift-click on a crafting table result moves the whole result into the inventory (`C08ShiftClickCraftTest`).
- C-B01 a prefixed client (`clients.create(".BedrockBot")`) is seen as Bedrock by the example plugin, and a normal client
  is not. Held on native Paper only (`SIMULATED_BEDROCK`, `ConformanceBedrockPrefixTest`). Not Bedrock support.

C09 onward (events, config, log, reset) arrive with later cards.

## Boundaries

- Must not run in `check`. The scenarios boot a real server, so they need Docker and the Minecraft EULA.
- Must not turn a missing prerequisite into a pass. Without the EULA or Docker, both tasks print a
  `SKIPPED (not a pass)` line and do not run locally. In CI (`CI=true`) they fail, so a skip is never green (AGENTS.md §3).
- Must not add a scenario that needs more than the public test API. Scenarios use `@Proofstone`, `@Matrix`, `@CellTest`
  and the types of `proofstone-api`. They never use `serverOnly` (self-tests §1: each check is a dual-oracle assertion).
- A scenario that fails on a real server is a harness finding: fix the harness in its own commit, never the scenario.

## Public surface

Two tasks, both on the same real Paper cell:

- `:conformance:smoke`: C01 and C02 only, the quick gate.
- `:conformance:run`: every scenario, C01 to C06. Filter with `--tests '*C03*'`. A bare `--tests C03` matches no class
  name, so Gradle reports no tests.

Both need a Java 21+ toolchain (set in `build.gradle.kts`), because the mcpl driver needs it.

## How to test

Read <https://aka.ms/MinecraftEULA> first. The harness never accepts the EULA for you. Then run it with the variable in
the command environment only:

```bash
PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run
PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run --tests '*C04*'
```

Needs Docker. Run one Docker-using Gradle command at a time (docs/platforms/paper.md, Docker Desktop).
