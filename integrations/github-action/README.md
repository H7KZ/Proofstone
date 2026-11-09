# integrations/github-action

## Purpose

A composite GitHub Action (`action.yml`) that runs Proofstone real-server suites in a consumer's job. It is the release
artefact of T-M6-03 and the dogfooding path of M6 ([docs/plan/tasks/M6-dogfood-release.md](../../docs/plan/tasks/M6-dogfood-release.md)).
It is an action definition, not a workflow: the consumer's own workflow calls it. Nothing in this repository calls it, and
the repository has no CI (AGENTS.md section 4).

## Boundaries

- No triggers, jobs or `.github/workflows` here, and none added without maintainer approval (AGENTS.md section 4).
- Never hard-codes a version list. Cells come from `./gradlew printMatrix --scope=<scope>` in the Proofstone checkout
  (docs/versioning.md section 4), which reads [versions.yml](../../versions.yml).
- Never accepts the Minecraft EULA. `proofstone-accept-eula` has no default, must be `true`, and is forwarded only to the
  test step as `PROOFSTONE_ACCEPT_EULA` (AGENTS.md section 3). The action never writes it to a file.
- Runs one cell at a time, one Gradle or Maven run per cell (AGENTS.md section 4, one Docker run at once).
- Third-party actions are pinned to a commit SHA with the release tag in a comment. The check is in `docsCheck`.
- Does not retry. A cell that fails, skips, or runs no test fails the action.

## Public surface

Inputs (defined in `action.yml`):

| Input | Required | Default | Meaning |
|---|---|---|---|
| `scope` | yes | | `pr` or `nightly` (printMatrix scope) |
| `cells` | no | `all` | `all`, or comma-separated cell ids such as `paper@26.3`; an id not in the scope fails |
| `build-tool` | yes | | `gradle` or `maven` |
| `proofstone-accept-eula` | yes | none | must be `true` |
| `project-dir` | no | `.` | consumer project directory (holds its `gradlew` or `pom.xml`) |
| `test-task` | no | `e2eTest` | Gradle task, or Maven goals and profiles (for example `verify -Pe2e`) |
| `proofstone-root` | no | this checkout | Proofstone checkout that holds `versions.yml` and `gradlew` |
| `java-version` | no | `21` | JDK for the build tool and the e2e JVM (ADR-0004) |

Outputs: artefacts `proofstone-junit-<scope>-<attempt>` (JUnit XML, one folder per cell) and
`proofstone-repro-<scope>-<attempt>` (`build/proofstone/`, repro bundles; missing on a green run, which is not an error).

The shell steps are in [run-cells.sh](run-cells.sh). The action and [scripts/action-selfcheck.sh](../../scripts/action-selfcheck.sh)
both run it, so the local dry run executes the same steps.

Caches: Gradle (`~/.gradle/caches`, `~/.gradle/wrapper`), Maven (`~/.m2/repository`), Spigot BuildTools jars
(`build/proofstone/spigot-cache`, keyed by `versions.yml`). Docker layer caching is **not** implemented: the harness pulls
digest-pinned images and has no buildx cache hook.

## How to test

```bash
bash scripts/gates.sh --max-workers=1        # docsCheck includes the action schema check (ActionCheck)
bash scripts/action-selfcheck.sh --plan-only # printMatrix --scope=pr and the paper@26.3 selection, no Docker
PROOFSTONE_ACCEPT_EULA=true bash scripts/action-selfcheck.sh   # one real-server run of the Gradle example on paper@26.3
```

The real-server run goes through `scripts/dockerlock.sh`. Read the XML it prints: `skipped="0" failures="0" errors="0"`.
Not verified in this repository: a run on GitHub-hosted runners, the Maven example (not in the repository yet), and
consumers outside this repository (the Gradle example still points at Proofstone's own agent jar).
