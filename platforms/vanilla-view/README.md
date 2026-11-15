# Vanilla view (`platforms:vanilla-view`)

## Purpose
The code of the Fabric and NeoForge agents that reads the vanilla server, written once (card T-M8-15, [ADR-0002](../../docs/adr/0002-server-truth-via-agent.md),
[ADR-0017](../../docs/adr/0017-mod-platform-packs.md)): players, inventories, open windows, blocks, item JSON, console commands, the
server-thread hop (`ServerThread`), the tick counter and the event-value rules (`Values`). 26.x ships unobfuscated, so the vanilla
calls are the same bytes on both loaders. What needs a loader's API stays in that agent: the mod list, the data packs, the event
probes, readiness and the entrypoint.

## Boundaries
- **Internal.** Not a public artefact and not part of `proofstone-api`. It is shaded into both agent jars, under its own package
  `io.github.h7kz.proofstone.agent.vanilla` (no relocation needed: nothing else bundles it).
- **No loader API, no new dependency.** Nothing from `net.fabricmc` or `net.neoforged`; it compiles against the same pinned vanilla
  server jar as the agents (the catalog's `minecraft-server`, checked by sha256 in `unpackGame`, no extra download) and uses
  `platforms:agent-core` only. The game is compile only; the server provides it.
- **Observe only** (AGENTS.md section 3). It reads, and writes only what an endpoint asks (give, reset, patch, set block).
  `ObserveOnlyTest` scans its classes, and each agent's `ObserveOnlyTest` scans them again with that loader's forbidden names.
- **Java 25 bytecode.** Java 25 toolchain, `--release 25`, plain `javac` like the agents (ADR-0017 section 5).
- A loader-specific answer is an abstract method (`MinecraftView.mods()`) or lives in the agent, never an `if` on the loader here.
  `MinecraftView` takes the loader's name for the one error text that names the agent.

## Public surface
- `GameView`: the server state an agent's endpoints read and change, as JSON-ready trees.
- `MinecraftView`: the real server behind it (abstract: `mods()` is the loader's), `ServerThread`, `TickCounter`, `Values`.
- Test fixtures (`testFixtures`, never shaded): `MockView` (an in-memory server for the contract tests), `AgentClient` (HTTP
  client), `ClassScan` (the classes of a module, for the observe-only scans).

## How to test
- `./gradlew :platforms:vanilla-view:test`: the observe-only scan of its own classes, `ServerThread`, `TickCounter` and `Values`.
- The two agents' tests replay the golden contract over HTTP on `MockView` and scan these classes too:
  `./gradlew :platforms:fabric:agent:test :platforms:neoforge:agent:test`.
- The real server: `:platforms:fabric:agent:integrationTest` and `:platforms:neoforge:agent:integrationTest`, which run this code
  from inside each agent jar.
