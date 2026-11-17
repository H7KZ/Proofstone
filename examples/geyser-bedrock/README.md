# examples:geyser-bedrock

## Purpose

The smallest test of a plugin's Bedrock-only branch with a real Bedrock client through Geyser. `BedrockBranchE2E` declares
`@Matrix(platform = Platform.GEYSER)` and `@Requires(Capability.BEDROCK_CLIENT)`, joins `clients.bedrock("PsEx1")` and checks that the
plugin detects the Floodgate name (`.PsEx1`) as Bedrock and a Java player as not Bedrock. The plugin under test is
[`examples/bedrock-prefix`](../bedrock-prefix/README.md), built from source. Guide: [testing Bedrock through Geyser](../../docs/user/bedrock.md).

## Boundaries

- Not part of `check`: the test boots a real Geyser cell (Paper, Geyser, Floodgate, a discovery stand-in and the Bedrock driver host),
  so it needs Docker and the EULA. `check` runs only `archTest`.
- The Geyser row is `experimental`, so a cell runs only when you name it: `-Dproofstone.cells=geyser@2.12.1+paper@26.3` (or
  `PROOFSTONE_CELLS`). **One cell per invocation.** Without it the class prints `NOT SELECTED`, which is not a pass.
- The cell is *offline-stub*: Xbox login validation is off. It cannot test a plugin that needs a verified identity.
- It does not click windows, use blocks, answer forms or resource packs: the v1 Bedrock driver has none of them.
- Contains no framework code, only a test project that uses the public API. Not published.

## Public surface

None. The test class is the example to copy. To test your own plugin, replace the glob in `@Proofstone(plugins)` with your jar
(relative to the project directory, exactly one match).

## How to test

Read <https://aka.ms/MinecraftEULA> first. The harness never accepts the EULA for you. Needs Docker and a Java 21 toolchain.

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:geyser-bedrock:geyserExample \
  -Dproofstone.cells=geyser@2.12.1+paper@26.3 --no-parallel --max-workers=1
```

Read the XML in `examples/geyser-bedrock/build/test-results/geyserExample/` and expect `tests="2" skipped="0" failures="0" errors="0"`.
