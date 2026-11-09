# examples:maven-paper-plugin

## Purpose

The Maven twin of `examples/gradle-paper-plugin` (T-M6-11). `AnvilMechanic` is a minimal Paper plugin: one iron ingot and one
gold ingot on an anvil make one diamond. `AnvilMechanicE2E` (in `src/test/java`, run by failsafe) runs it on a real Paper 26.3
server through Proofstone. It shows the intended setup for a Maven project. The mutation check
(`scripts/release-mutation-check.sh`) runs it next to the Gradle example, with the same two toggles.

Paper 26.3's API is Java 25 bytecode, so the plugin compiles on a JDK 26 at `release 25`, and the server needs Java 25 or
newer. The compile-only Paper API comes from `repo.papermc.io` (stable build 244, the same pin as the Gradle catalog). The
MCProtocolLib artefacts that the mcpl driver needs at test time come from `repo.opencollab.dev`. Both are declared in
`pom.xml`.

The plugin carries two injected bugs, both off by default, so the suite can prove it catches them:

| Bug (`bugs.<name>`) | Effect | Test that catches it |
|---|---|---|
| `double-output` | the result is two diamonds | `ironAndGoldOnTheAnvilMakeOneDiamond` |
| `accept-any-right` | coal on the right also makes a diamond | `coalOnTheRightMakesNothing` |

## Boundaries

- Proofstone is consumed from **mavenLocal only**: `io.github.h7kz.proofstone:proofstone-{api,core,drivers-mcpl}` at version
  `0.1.0-local`. The version is pinned as `proofstone.version` in `pom.xml`, the same value as the `proofstone` key in
  `gradle/libs.versions.toml`. Nothing is published to Maven Central, GHCR or any other remote repository. Remote snapshots are
  never used.
- The agent jar (`platforms/bukkit/agent/build/libs/agent-all.jar`) is a plugin, not a library. It is built from this
  repository by Gradle, not taken from mavenLocal.
- Needs a JDK 26 on `JAVA_HOME` for Maven (it compiles the plugin at `release 25`). The e2e test classes are `release 21`, and
  the failsafe JVM is the one that runs Maven. Gradle does not run this project.
- The e2e suite needs Docker. It is not part of a plain `mvn verify`; it runs only under `-Pe2e`.
- The plugin under test is test-only. It is never shipped.
- The test reads the cell pins from the repository's `versions.yml`, through `proofstone.repoRoot` (set in the `e2e` profile).

## Public surface

- The anvil recipe above. Config: `bugs.double-output` and `bugs.accept-any-right`, both `false` by default.
- The e2e tests are the example to copy. Nothing else is public.

## How to test

Read <https://aka.ms/MinecraftEULA> first. The harness never accepts the EULA for you. Run one Docker command at a time, under
the lock (`scripts/dockerlock.sh`). Maven is not on PATH on the maintainer's machine: set `MAVEN_HOME` to the install
directory, as the commands below do.

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-26"   # Maven runs on JDK 26 (see Boundaries)
export MAVEN_HOME="C:/Users/honzi/tools/apache-maven-3.9.9"

# 1. Build the agent jar and publish Proofstone's libraries to mavenLocal (local only, never remote).
JAVA_HOME="C:/Program Files/Java/jdk-21" ./gradlew :platforms:bukkit:agent:shadowJar \
  :proofstone-api:publishToMavenLocal :proofstone-core:publishToMavenLocal \
  :proofstone-protocol:publishToMavenLocal :drivers:mcpl:publishToMavenLocal --max-workers=1

# 2. Green run: both bugs off, one cell.
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh "$MAVEN_HOME/bin/mvn" -B --no-transfer-progress \
  -f examples/maven-paper-plugin verify -Pe2e -Dproofstone.cells=paper@26.3

# 3. Mutation check: each injected bug must turn the suite red (one run per bug).
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh "$MAVEN_HOME/bin/mvn" -B --no-transfer-progress \
  -f examples/maven-paper-plugin verify -Pe2e -Dproofstone.cells=paper@26.3 -Dproofstone.bug=double-output
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh "$MAVEN_HOME/bin/mvn" -B --no-transfer-progress \
  -f examples/maven-paper-plugin verify -Pe2e -Dproofstone.cells=paper@26.3 -Dproofstone.bug=accept-any-right
```

Read the results in `examples/maven-paper-plugin/target/failsafe-reports/*.xml`. Every suite must show
`skipped="0" failures="0" errors="0"` on the green run. On each mutation run the suite must fail. A skip is not a pass.
