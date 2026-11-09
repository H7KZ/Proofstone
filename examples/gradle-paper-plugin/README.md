# examples:gradle-paper-plugin

## Purpose

The Gradle half of the T-M6-04 example pair. `AnvilMechanic` is a minimal Paper plugin: one iron ingot and one gold ingot
on an anvil make one diamond. `AnvilMechanicE2E` (source set `e2eTest`) runs it on a real Paper 26.3 server through
Proofstone. It shows the intended setup for a Gradle project. The Maven twin (`examples/maven-paper-plugin`) is not in this change: Maven is
not installed on the machine that wrote it, so it could not be run (see the PR).

Paper 26.3's API is Java 25 bytecode, so the plugin compiles on a JDK 26 at `release 25`, and the server needs Java 25 or
newer. The compile-only Paper API comes from `repo.papermc.io` (stable release, pinned in the catalog); it is the only
artefact outside Maven Central and the repository's mavenLocal.

The plugin carries two injected bugs, both off by default, so the suite can prove it catches them:

| Bug (`bugs.<name>`) | Effect | Test that catches it |
|---|---|---|
| `double-output` | the result is two diamonds | `ironAndGoldOnTheAnvilMakeOneDiamond` |
| `accept-any-right` | coal on the right also makes a diamond | `coalOnTheRightMakesNothing` |

## Boundaries

- Proofstone is consumed from **mavenLocal only**: `io.github.h7kz.proofstone:proofstone-{api,core,protocol,drivers-mcpl}`
  at the version in `gradle/libs.versions.toml` (`proofstone`). Nothing is published to Maven Central, GHCR or any other
  remote repository. Remote snapshots are never used.
- The agent jar (`platforms/bukkit/agent/build/libs/agent-all.jar`) is a plugin, not a library. It is built from this
  repository, not taken from mavenLocal (not published anywhere, T-M6-01 decides that).
- Needs a JDK 26 and a JDK 21 on the machine (toolchains: plugin compile and the e2e suite).
- The e2e suite needs Docker. It is not part of `check`.
- The plugin under test is test-only. It is never shipped.
- The test reads the cell pins from the repository's `versions.yml`, through `proofstone.repoRoot`.

## Public surface

- The anvil recipe above. Config: `bugs.double-output` and `bugs.accept-any-right`, both `false` by default.
- The e2e tests are the example to copy. Nothing else is public.

## How to test

Read <https://aka.ms/MinecraftEULA> first. The harness never accepts the EULA for you. Run one Docker command at a time, under
the lock (`scripts/dockerlock.sh`).

```bash
# 1. Publish Proofstone's libraries to mavenLocal (local only, never remote).
./gradlew :proofstone-api:publishToMavenLocal :proofstone-core:publishToMavenLocal \
  :proofstone-protocol:publishToMavenLocal :drivers:mcpl:publishToMavenLocal --max-workers=1

# 2. Green run: both bugs off, one cell.
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:gradle-paper-plugin:e2eTest \
  -Dproofstone.cells=paper@26.3 --no-parallel --max-workers=1

# 3. Mutation check: each injected bug must turn the suite red (one run per bug).
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:gradle-paper-plugin:e2eTest \
  -Dproofstone.cells=paper@26.3 -Dproofstone.bug=double-output --no-parallel --max-workers=1
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:gradle-paper-plugin:e2eTest \
  -Dproofstone.cells=paper@26.3 -Dproofstone.bug=accept-any-right --no-parallel --max-workers=1
```

Read the results in `examples/gradle-paper-plugin/build/test-results/e2eTest/*.xml`. Every suite must show
`skipped="0" failures="0" errors="0"` on the green run. On each mutation run the suite must fail. A skip is not a pass.
