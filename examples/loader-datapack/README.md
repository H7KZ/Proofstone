# examples:loader-datapack

## Purpose

The smallest test of a data pack on a mod loader row. `DataPackFunctionE2E` declares
`@Proofstone(datapacks = "src/test/datapacks/example-pack")` and `@Matrix(platform = Platform.FABRIC, versions = {"26.3"})`,
runs the pack's function with `agent.command`, and reads the result on both oracles: the agent and the client both see five
emeralds, and the client sees the chat line. It is C-D01 of the conformance suite cut down to a user-sized test, and uses only
what ran there. Guide: [mods, data packs and resource packs](../../docs/user/mods-and-packs.md).

It does **not** test a mod: for a server-only mod declared through `@Proofstone(mods)` see [`examples/loader-mod`](../loader-mod/README.md).

## Boundaries

- Not part of `check`: the test boots a real Fabric server, so it needs Docker and the EULA. `check` runs only `archTest`.
- Runs on `fabric@26.3` only. `neoforge@26.3` skips a data pack with `missing capability DATAPACKS_VANILLA`, so it is not
  declared here. Loader rows are `experimental`, so the cell runs only when you name it.
- Contains no framework code, only a test project that uses the public API.

## Public surface

None. The test class and the pack under `src/test/datapacks/example-pack` are the example to copy. The pack's data format is
`121` (Minecraft 26.3).

## How to test

Read <https://aka.ms/MinecraftEULA> first. The harness never accepts the EULA for you. Needs Docker, a Java 21 toolchain for the
harness and a Java 25 toolchain to build the Fabric agent. The first run of a loader cell downloads the pinned launcher and
Fabric API, and the server container fetches the vanilla jar from Mojang (ADR-0017 section 6); later runs reuse them.

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:loader-datapack:loaderExample \
  -Dproofstone.cells=fabric@26.3 --no-parallel --max-workers=1
```

Without `-Dproofstone.cells=fabric@26.3` the class plans no cell and prints `NOT SELECTED`, which is not a pass. Read the XML in
`examples/loader-datapack/build/test-results/loaderExample/` and expect `tests="1" skipped="0" failures="0" errors="0"`.

What was run (T-M8-12, 2026-11-15, warm loader cache): three separate invocations on `fabric@26.3`, all green, 1 test each,
`skipped="0" failures="0" errors="0"`, loader boot 14.0 to 15.0 s, 0 join timeouts. One earlier invocation was red before those
three: the example's build did not set `proofstone.agentJar`, which `CellInvocation.boot` then read even for a loader cell
(`ParameterResolutionException`, no server started). T-M8-14 moved that read to the Bukkit-family path, so a loader run sets only
`proofstone.fabric.agentJar` and the example's build no longer sets the Bukkit one. No flake rate is claimed from three runs.
