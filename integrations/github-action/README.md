# integrations/github-action

**Status: not published. Not on the GitHub Marketplace. No release tag exists.** The action is in this repository and runs
only from a checkout of it. Consumers pin a full commit SHA (see [Versioning](#versioning)).

## Purpose

A composite GitHub Action (`action.yml`) that runs Proofstone real-server suites in a consumer's job. It is the release
artefact of T-M6-03 and the dogfooding path of M6 ([docs/plan/tasks/M6-dogfood-release.md](../../docs/plan/tasks/M6-dogfood-release.md)).
It is an action definition, not a workflow: the consumer's own workflow calls it. Nothing in this repository calls it, and
the repository has no CI (AGENTS.md section 4).

## Use it in your workflow

Copy this into `.github/workflows/e2e.yml` of your plugin project. Replace the two `<full-sha>` placeholders with full
40-character commit SHAs. Do not use a branch name or a short SHA.

```yaml
name: e2e
on:
  pull_request:
permissions:
  contents: read
jobs:
  e2e:
    runs-on: ubuntu-24.04
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@<full-sha> # your pin of actions/checkout
      - uses: H7KZ/Proofstone/integrations/github-action@<full-sha> # the Proofstone commit you pin
        with:
          scope: pr
          cells: all
          build-tool: gradle
          project-dir: .
          test-task: e2eTest
          proofstone-accept-eula: ${{ vars.PROOFSTONE_ACCEPT_EULA }}
```

Set the repository variable `PROOFSTONE_ACCEPT_EULA` to `true` yourself (Settings, Secrets and variables, Actions, Variables).
The Minecraft EULA is your decision, and a variable keeps the acceptance out of the committed workflow file. Any other value,
or an empty one, fails the action before any server starts. Your project needs an `e2eTest` task (Gradle) or Maven profiles
for `test-task` to run, and each test selects its cell through `-Dproofstone.cells`. See [examples/gradle-paper-plugin](../../examples/gradle-paper-plugin/README.md).

## Inputs

Copied verbatim from [action.yml](action.yml):

| Input | Required | Default | Description |
|---|---|---|---|
| `scope` | yes | | Matrix scope for printMatrix: pr or nightly. |
| `cells` | no | `all` | Comma-separated cell ids (for example paper@26.3), or all for every cell in the scope. |
| `build-tool` | yes | | Build tool of the consumer project: gradle or maven. |
| `project-dir` | no | `.` | Directory of the consumer project, relative to the workspace. Holds its gradlew or pom.xml. |
| `test-task` | no | `e2eTest` | Gradle task that runs the suite, or Maven goals and profiles (for example verify -Pe2e). |
| `proofstone-root` | no | `""` | Proofstone checkout whose printMatrix task plans the cells. Empty means the checkout this action lives in. |
| `java-version` | no | `"21"` | JDK for the build tool and the test JVM (ADR-0004: the e2e suite runs on Java 21). |
| `proofstone-accept-eula` | yes | none | Must be the string true. Forwarded to the test step as PROOFSTONE_ACCEPT_EULA. There is no default. |

## Outputs

`action.yml` declares no `outputs:` block. The action uploads two artifacts, which are the outputs a consumer reads:

| Artifact | Contents | Present when |
|---|---|---|
| `proofstone-junit-<scope>-<attempt>` | JUnit XML, one folder per cell | always (`if: always()`, `if-no-files-found: error`) |
| `proofstone-repro-<scope>-<attempt>` | `build/proofstone/`, repro bundles | red runs only; a green run writes none, which is not an error |

Retention is 30 days for both.

## Versioning

- A major tag `v1` is **planned, not created**. Until it exists, pin a full commit SHA. Do not pin `@main`, a branch, a
  short SHA or a tag that does not exist.
- Once `v1` exists it moves only with a release ([docs/release.md](../../docs/release.md)). A full SHA stays the most
  reproducible pin.
- Third-party actions inside `action.yml` are pinned to full SHAs with the release tag in a comment. That check runs in
  `docsCheck` (ActionCheck).

## Runner requirements

- A **Linux runner with Docker** and Linux containers. The real-server cells run in Docker through Testcontainers.
  GitHub-hosted `ubuntu-*` runners provide Docker. Not verified on hosted runners yet (see [Known limits](#known-limits)).
- **The EULA input.** `proofstone-accept-eula` must be `true`. The action never accepts the Minecraft EULA for you
  (https://aka.ms/MinecraftEULA) and never writes it to a file.
- **Shell tools:** bash, `node` (the cell plan is read with it), `sha256sum`.
- **Network** access to download the pinned server jars and pull digest-pinned images at run time.
- A **JDK** for the build tool: set `java-version` (default 21). The action installs Temurin.
- A consumer build that resolves the Proofstone artifacts (release state: [docs/release.md](../../docs/release.md)).

Cache: Gradle (`~/.gradle/caches`, `~/.gradle/wrapper`), Maven (`~/.m2/repository`) and Spigot BuildTools jars
(`<project-dir>/build/proofstone/spigot-cache`, keyed by `versions.yml`). Docker layer caching is **not** implemented.

## Known limits

Read these before you rely on a red or a green run. They are kept in one place:

- Flake rate, join timeouts and what a timeout looks like: [docs/limits.md, Correctness limits](../../docs/limits.md#correctness-limits).
- Which platforms and rows are `required`, `experimental` or unavailable (Bedrock, Folia and others):
  [docs/limits.md, Platforms and rows](../../docs/limits.md#platforms-and-rows).
- Run time per cell, matrix size and hosted-runner speed: [docs/limits.md, Speed and scale](../../docs/limits.md#speed-and-scale).

Action-specific behaviour: the action runs one cell at a time and does not retry. A cell that fails, skips or runs no test
fails the job, and the artifacts are still uploaded.

## Public surface

The inputs and outputs above, as defined in [action.yml](action.yml). The shell steps are in [run-cells.sh](run-cells.sh).
The action and [scripts/action-selfcheck.sh](../../scripts/action-selfcheck.sh) both run it, so the local dry run executes
the same steps.

## Boundaries

- No triggers, jobs or `.github/workflows` here, and none added without maintainer approval (AGENTS.md section 4).
- Never hard-codes a version list. Cells come from `./gradlew printMatrix --scope=<scope>` in the Proofstone checkout
  (docs/versioning.md section 4), which reads [versions.yml](../../versions.yml).
- Never accepts the Minecraft EULA. `proofstone-accept-eula` has no default, must be `true`, and is forwarded only to the
  test step as `PROOFSTONE_ACCEPT_EULA` (AGENTS.md section 3). The action never writes it to a file.
- Runs one cell at a time, one Gradle or Maven run per cell (AGENTS.md section 4, one Docker run at once).
- Does not retry. A cell that fails, skips, or runs no test fails the action.

## How to test

```bash
bash scripts/gates.sh --max-workers=1        # docsCheck includes the action schema check (ActionCheck)
bash scripts/action-selfcheck.sh --plan-only # printMatrix --scope=pr and the paper@26.3 selection, no Docker
PROOFSTONE_ACCEPT_EULA=true bash scripts/action-selfcheck.sh   # one real-server run of the Gradle example on paper@26.3
```

The real-server run needs Docker, the EULA variable and the built agent jar
(`./gradlew :platforms:bukkit:agent:shadowJar`). It exits non-zero with a `MISSING ...` message if one is missing. It takes
its own lock through `scripts/dockerlock.sh`, so do not wrap it in another lock. Read the XML it prints:
`skipped="0" failures="0" errors="0"`.

Not verified in this repository: a run on GitHub-hosted runners, the Maven example (not in the repository yet), and
consumers outside this repository (the Gradle example still points at Proofstone's own agent jar).
