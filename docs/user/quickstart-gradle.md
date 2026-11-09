# Quickstart: Gradle

**Status: the verified path is the Gradle example inside this repository.** Running your own project outside the repository
is not verified (see [Limits](#limits-not-verified)). Nothing is published yet, so Proofstone is used from `mavenLocal`.
The 15-minute run-through of this page has **not been timed yet**.

The example is [`examples/gradle-paper-plugin`](../../examples/gradle-paper-plugin/README.md): a minimal Paper plugin (one
iron ingot and one gold ingot on an anvil make one diamond) with an `e2eTest` source set that runs it on a real Paper 26.3
server.

## Before you start

- **Docker** is running. Every real-server run starts containers.
- **JDK 21 and JDK 26** are installed. `settings.gradle.kts` has no toolchain resolver, so Gradle does not download them.
  The plugin compiles on JDK 26 at release 25; the e2e suite runs on JDK 21.
- **A Bash shell.** `scripts/dockerlock.sh` is a Bash script. On Windows use Git Bash (the maintainer's setup).
- **The Minecraft EULA.** Read <https://aka.ms/MinecraftEULA>. Proofstone never accepts it for you. You accept it per
  command with an environment variable (step 3). Never write it to a file or commit it.
- **Network.** Gradle reads Maven Central, `repo.papermc.io`, `repo.opencollab.dev` and `hub.spigotmc.org`
  (see `settings.gradle.kts`). The Paper server jar is downloaded at run time, not committed.

## 1. Clone the repository

```bash
git clone https://github.com/H7KZ/Proofstone.git
cd Proofstone
```

## 2. Publish Proofstone's libraries to mavenLocal

Nothing is published to Maven Central or GHCR yet. The example consumes Proofstone version `0.1.0-local` from `mavenLocal`
(`gradle/libs.versions.toml`, key `proofstone`).

```bash
./gradlew :proofstone-api:publishToMavenLocal :proofstone-core:publishToMavenLocal \
  :proofstone-protocol:publishToMavenLocal :drivers:mcpl:publishToMavenLocal --max-workers=1
```

This step is optional for the green run: the `e2eTest` task depends on these publish tasks too. The example README runs it
first.

## 3. Run the green example on one cell

Run one Docker command at a time, through the lock. Set the EULA variable for this command only.

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:gradle-paper-plugin:e2eTest \
  -Dproofstone.cells=paper@26.3 --no-parallel --max-workers=1
```

`-Dproofstone.cells=paper@26.3` selects one cell. Without it the `@Matrix` in the test (Paper 26.3) runs the same way, but the
selector keeps the run to one server.

## 4. Read the result

The result is in `examples/gradle-paper-plugin/build/test-results/e2eTest/*.xml`. The green run is good only when the
suite shows:

- `tests="2"`, `skipped="0"`, `failures="0"`, `errors="0"`.

A skipped test is not a pass. If the EULA variable is missing, the tests are reported as
`PROOFSTONE SKIPPED (not a pass): Minecraft EULA not accepted ...` and the XML shows skips. Fix the cause in
[troubleshooting.md](troubleshooting.md#eula-not-accepted-tests-are-skipped).

## 5. Check that the injected bugs turn the suite red

The example has two bugs, both off by default: `double-output` and `accept-any-right`. Each one must make the suite fail.
Run them one at a time:

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:gradle-paper-plugin:e2eTest \
  -Dproofstone.cells=paper@26.3 -Dproofstone.bug=double-output --no-parallel --max-workers=1

PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:gradle-paper-plugin:e2eTest \
  -Dproofstone.cells=paper@26.3 -Dproofstone.bug=accept-any-right --no-parallel --max-workers=1
```

Each run must show `failures` of at least 1. Check the failure message, not only the count:

| Bug | The failing test | Its assertion message |
|---|---|---|
| `double-output` | `ironAndGoldOnTheAnvilMakeOneDiamond` | `one iron and one gold make one diamond` |
| `accept-any-right` | `coalOnTheRightMakesNothing` | `coal is not an accepted right input` |

A red run whose message is a join timeout did not catch the bug. It is a harness problem; see
[troubleshooting.md](troubleshooting.md#join-timeout-timed-out-joining).

## 6. Write your own test

Read [writing-tests.md](writing-tests.md). Copy the shape of `AnvilMechanicE2E.java` (in
`examples/gradle-paper-plugin/src/e2eTest/java/...`), and keep the same `e2eTest` source set and task.

## Limits (not verified)

- **Your own project outside this repository is not verified.** The example is a subproject of this monorepo. Its build
  relies on the root `settings.gradle.kts` (repository rules, `FAIL_ON_PROJECT_REPOS`) and on the `proofstone.java-host`
  convention plugin in `build-logic/`. Neither is published. Copying the example elsewhere has not been tried.
- **The example is flaky.** `paper@26.3` was red in 2 of 4 runs through the GitHub Action path and 1 of 5 plain Gradle runs
  (join timeouts and an unrecoverable `AssertionError`). Read [troubleshooting.md](troubleshooting.md) before you judge a red run.
- **Maven** does not exist yet: [quickstart-maven.md](quickstart-maven.md).
- **Time** to a green test: not measured. The 15-minute target is open.
