# Quickstart: Maven

**Status: the verified path is the Maven example inside this repository.** Every command below was run on the maintainer's
Windows machine (Git Bash) at the commit that adds this page. Running your own Maven project outside the repository is not
verified (see [Limits](#limits-not-verified)). Nothing is published yet, so Proofstone is used from `mavenLocal`.

The example is [`examples/maven-paper-plugin`](../../examples/maven-paper-plugin/README.md): the same anvil plugin as the
Gradle example (one iron ingot and one gold ingot on an anvil make one diamond), with an `AnvilMechanicE2E` test that failsafe
runs on a real Paper 26.3 server.

## Before you start

- **Docker** is running. Every real-server run starts containers.
- **Apache Maven 3.9.9** on the machine, not on `PATH`. Set `MAVEN_HOME` to its install directory. The maintainer's install is
  `C:/Users/honzi/tools/apache-maven-3.9.9`. Maven resolves its dependencies from Maven Central, `repo.papermc.io` and
  `repo.opencollab.dev` (the repositories are declared in the example's `pom.xml`).
- **JDK 26 for Maven** (`JAVA_HOME`). It compiles the Paper plugin at `release 25` and runs the failsafe JVM. The e2e test
  classes are Java 21 bytecode, which JDK 26 runs.
- **JDK 21 and JDK 17 for Gradle.** Step 2 builds the agent jar and publishes Proofstone with Gradle, as in
  [quickstart-gradle.md](quickstart-gradle.md#before-you-start).
- **A Bash shell.** `scripts/dockerlock.sh` is a Bash script. On Windows use Git Bash.
- **The Minecraft EULA.** Read <https://aka.ms/MinecraftEULA>. Proofstone never accepts it for you. You accept it per command
  with an environment variable (step 3). Never write it to a file or commit it.

## 1. Clone the repository

```bash
git clone https://github.com/H7KZ/Proofstone.git
cd Proofstone
```

## 2. Build the agent jar and publish Proofstone's libraries to mavenLocal

The example consumes Proofstone version `0.1.0-local` from `mavenLocal` (`proofstone.version` in the example's `pom.xml`, the
same value as `gradle/libs.versions.toml`). Maven does not build the agent jar, so Gradle builds it here.

```bash
JAVA_HOME="C:/Program Files/Java/jdk-21" ./gradlew :platforms:bukkit:agent:shadowJar \
  :proofstone-api:publishToMavenLocal :proofstone-core:publishToMavenLocal \
  :proofstone-protocol:publishToMavenLocal :drivers:mcpl:publishToMavenLocal --max-workers=1
```

## 3. Run the green example on one cell

Run one Docker command at a time, through the lock. Set the EULA variable for this command only. Maven runs on JDK 26.

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-26"
export MAVEN_HOME="C:/Users/honzi/tools/apache-maven-3.9.9"

PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh "$MAVEN_HOME/bin/mvn" -B --no-transfer-progress \
  -f examples/maven-paper-plugin verify -Pe2e -Dproofstone.cells=paper@26.3
```

`-Pe2e` turns on failsafe, which runs the `*E2E.java` tests. `-Dproofstone.cells=paper@26.3` selects one cell.

## 4. Read the result

The result is in `examples/maven-paper-plugin/target/failsafe-reports/*.xml`. The green run is good only when the suite shows:

- `tests="2"`, `skipped="0"`, `failures="0"`, `errors="0"`.

A red run writes its repro bundle under the example's `build/proofstone/` directory. Bundles are local and never committed.

A skipped test is not a pass. If the EULA variable is missing, the tests are reported as
`PROOFSTONE SKIPPED (not a pass): Minecraft EULA not accepted ...` and the XML shows skips. Fix the cause in
[troubleshooting.md](troubleshooting.md#eula-not-accepted-tests-are-skipped).

## 5. Check that the injected bugs turn the suite red

The example has two bugs, both off by default: `double-output` and `accept-any-right`. Each one must make the suite fail.
Run them one at a time:

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh "$MAVEN_HOME/bin/mvn" -B --no-transfer-progress \
  -f examples/maven-paper-plugin verify -Pe2e -Dproofstone.cells=paper@26.3 -Dproofstone.bug=double-output

PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh "$MAVEN_HOME/bin/mvn" -B --no-transfer-progress \
  -f examples/maven-paper-plugin verify -Pe2e -Dproofstone.cells=paper@26.3 -Dproofstone.bug=accept-any-right
```

Each run must show `failures` of at least 1. Check the failure message, not only the count:

| Bug | The failing test | Its assertion message |
|---|---|---|
| `double-output` | `ironAndGoldOnTheAnvilMakeOneDiamond` | `one iron and one gold make one diamond` |
| `accept-any-right` | `coalOnTheRightMakesNothing` | `coal is not an accepted right input` |

A red run whose message is a join timeout did not catch the bug. It is a harness problem; see
[troubleshooting.md](troubleshooting.md#join-timeout-timed-out-joining).

## 6. Run the release mutation check

The release mutation check runs the Gradle and the Maven example with the same toggle list. It finds `mvn` through `MVN`
(a path to the executable), then `MAVEN_HOME/bin/mvn`, then `PATH`. If none of them works, it stops with that reason
and reports the example as NOT RUN.

```bash
MAVEN_HOME="C:/Users/honzi/tools/apache-maven-3.9.9" MVN_JAVA_HOME="C:/Program Files/Java/jdk-26" \
  JAVA_HOME="C:/Program Files/Java/jdk-21" PROOFSTONE_ACCEPT_EULA=true bash scripts/release-mutation-check.sh
```

`MVN_JAVA_HOME` is the JDK for the Maven runs (JDK 26). `JAVA_HOME` is the JDK for Gradle (JDK 21).

It exits 0 only when every run gives the expected verdict. Its result file is `build/release/mutation-check.env`.

## Limits (not verified)

- **Your own Maven project outside this repository is not verified.** The example is a module of this monorepo's file layout: it
  reads `../../versions.yml` and `../../platforms/bukkit/agent/build/libs/agent-all.jar` through `proofstone.repoRoot` and
  `proofstone.agentJar` in its `pom.xml`. Copying it elsewhere has not been tried.
- **The example is flaky in principle.** Read [troubleshooting.md](troubleshooting.md) before you judge a red run.
- **Time** to a green test: not measured.
