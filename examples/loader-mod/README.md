# examples:loader-mod

## Purpose

The smallest test of a server-only mod on a mod loader row. `ServerOnlyModE2E` declares
`@Proofstone(mods = "build/example-mod/proofstone-example-mod.jar")` and runs on `fabric@26.3` and `neoforge@26.3`. The mod is
the repository's own example mod (`platforms/<loader>/agent/src/exampleMod`, built by the agent's `exampleModJar`), which
registers nothing, so no artefact is downloaded. The harness copies it to `/mods` before the server starts and checks that the
agent lists it; the test then shows a vanilla client joins and the agent sees it. Guide:
[mods, data packs and resource packs](../../docs/user/mods-and-packs.md).

## Boundaries

- Not part of `check`: the test boots a real Fabric or NeoForge server, so it needs Docker and the EULA. `check` runs only
  `archTest`.
- Loader rows are `experimental`, so a cell runs only when you name it, **one cell per invocation**. The module's build takes
  the loader from `-Dproofstone.cells` and builds and copies that loader's example mod to `build/example-mod`, which is the one
  path the test's glob names. A cell that is not a loader row fails the task.
- It does not test a content mod (items, blocks, entities): no vanilla client can join a Fabric server that adds one.
- Contains no framework code, only a test project that uses the public API.

## Public surface

None. The test class is the example to copy. To test your own mod, replace the glob with the path of your jar (relative to the
project directory, exactly one match) and keep `@Matrix` on the loader rows you support.

## How to test

Read <https://aka.ms/MinecraftEULA> first. The harness never accepts the EULA for you. Needs Docker, a Java 21 toolchain for the
harness and a Java 25 toolchain to build the loader agent. The first run of a loader cell downloads the pinned launcher and
Fabric API (or the NeoForge installer), and the server container fetches the vanilla jar from Mojang (ADR-0017 section 6);
later runs reuse them.

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:loader-mod:loaderExample \
  -Dproofstone.cells=fabric@26.3 --no-parallel --max-workers=1
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:loader-mod:loaderExample \
  -Dproofstone.cells=neoforge@26.3 --no-parallel --max-workers=1
```

Without `-Dproofstone.cells` the build defaults to the Fabric agent and the class fails by name with `NOT SELECTED: ...` for each row, never a skip or a pass
for both rows. Read the XML in `examples/loader-mod/build/test-results/loaderExample/` and expect
`tests="1" skipped="0" failures="0" errors="0"`.
