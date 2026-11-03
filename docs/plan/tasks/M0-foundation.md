# M0: Foundation

Exit: `./gradlew check` is green in CI. `docsCheck` and the lint rules enforce the AGENTS.md hard rules.
The `versions.yml` schema validates.

### T-M0-01 · Create the Gradle monorepo skeleton
- tier: haiku
- depends: —
- modules: settings.gradle.kts, build-logic, gradle/libs.versions.toml, testificate-api, testificate-core, testificate-protocol
- adrs: 0009, 0006
- specs: architecture.md §4
- test-first: no

**Goal.** An empty but buildable multi-project layout that matches architecture.md §4. It contains only
the modules M1 needs.

**Do.**
- Add the Gradle wrapper (pinned version), using `settings.gradle.kts` with `includeBuild("build-logic")`.
- Create the version catalog with pinned JUnit 5, Testcontainers 2.x, Jackson, AssertJ, ArchUnit and
  MCProtocolLib `26.3-2` (repository `https://repo.opencollab.dev/main/`).
- Add the convention plugins:
  - `testificate.java-host`: Java 17 toolchain, `-Werror`, JUnit platform;
  - `testificate.java-agent-legacy`: `--release 8`, shading with relocation into
    `dev.testificate.shaded.*`.
- Create the empty modules `testificate-api`, `testificate-core` and `testificate-protocol`. Each gets a
  `README.md` from the module template (T-M0-06 may land later; use the four headings from AGENTS.md §6).

**Don't.**
- Add platform or driver modules (that is M1).
- Use dynamic versions.

**Acceptance.**
- [ ] `./gradlew build` succeeds on a clean clone with JDK 17+.
- [ ] Every dependency version comes from the catalog, with no `+` or ranges.
- [ ] Each module has a README with Purpose, Boundaries, Public surface and How to test.

**Verify.**
`./gradlew build --configuration-cache`

### T-M0-02 · Add gate tasks and the CI workflow
- tier: haiku
- depends: T-M0-01
- modules: build-logic, .github/workflows
- adrs: 0006, 0010
- specs: AGENTS.md §4
- test-first: no

**Goal.** Make the gate commands in AGENTS.md real, and run them in CI on every PR.

**Do.**
- Make `check` depend on compile, unit tests, `archTest` (T-M0-03) and `validateVersions` (T-M0-04).
- Register `docsCheck`. It can be a placeholder that passes until T-M0-05 lands.
- Register `:conformance:smoke` as a placeholder that prints "not implemented until T-M1-11" and **fails**
  when `CI_REQUIRE_SMOKE=true`.
- Add `.github/workflows/ci.yml` for ubuntu-latest:
  - JDK 17 via setup-java;
  - Gradle cache;
  - `./gradlew check docsCheck`;
  - actions pinned by commit SHA.

**Don't.**
- Run Docker jobs yet.

**Acceptance.**
- [ ] A CI run on a PR executes `check` and `docsCheck`.
- [ ] Every action in the workflow is pinned by SHA.

**Verify.**
`./gradlew check docsCheck`

### T-M0-03 · Enforce the hard rules with lint
- tier: haiku
- depends: T-M0-01
- modules: build-logic, testificate-core (test)
- adrs: 0005, 0006, 0007
- specs: AGENTS.md §3
- test-first: yes

**Goal.** CI rejects code that breaks the hard rules.

**Do.**
- Add ArchUnit rules, applied to every host module:
  - forbid calls to `Thread.sleep` and `TimeUnit.*.sleep` outside the class `dev.testificate.core.await.Await`;
  - forbid Testcontainers `withFileSystemBind` and `withClasspathResourceMapping` in `BindMode` usage;
  - forbid `org.junit.jupiter.api.RepeatedTest` and any retry extension in test sources outside
    `conformance/flake`.
- Add a Gradle task `lintVersions` that greps build files and `versions.yml` for `latest`, `+`, `SNAPSHOT`
  and version ranges in runtime coordinates. Allowlist: none.
- Write one deliberately violating fixture per rule under `build-logic/src/test`, to prove each rule
  fails.

**Don't.**
- Suppress a rule with an annotation.

**Acceptance.**
- [ ] Each rule has a failing fixture test and a passing fixture test.
- [ ] `check` depends on `archTest` and `lintVersions`.

**Verify.**
`./gradlew check`

### T-M0-04 · Add the `versions.yml` schema and validator
- tier: haiku
- depends: T-M0-01
- modules: testificate-protocol, build-logic
- adrs: 0008
- specs: specs/versions-yml.md
- test-first: yes

**Goal.** Validate `versions.yml` against a JSON Schema and the semantic rules in every build.

**Do.**
- Write `testificate-protocol/schemas/versions.schema.json`, matching the spec exactly.
- Add the `validateVersions` task. It checks the schema, then the semantic rules:
  - `mc` values are unique per platform;
  - `required` rows contain no `TODO` values;
  - every `status` value is in the allowed enum.
- Add tests: valid fixture, duplicate `mc`, `required` with `TODO`, unknown status.

**Don't.**
- Change the seed `versions.yml` content, except to fix schema mismatches. Report those in the PR.

**Acceptance.**
- [ ] The seed `versions.yml` passes (every row is `experimental`, so `TODO`s are allowed).
- [ ] All four negative fixtures fail with readable messages.

**Verify.**
`./gradlew validateVersions :testificate-protocol:test`

### T-M0-05 · Implement `docsCheck`
- tier: haiku
- depends: T-M0-02
- modules: build-logic
- adrs: 0010
- specs: ai/workflow.md (card format), glossary.md
- test-first: yes

**Goal.** Automatically keep the docs and cards consistent.

**Do.**
- Check that relative markdown links resolve (files and anchors).
- Validate card format in `docs/plan/tasks/*.md`: every `### T-` heading has the six metadata bullets,
  plus the Goal, Do, Don't, Acceptance and Verify sections, and the ID matches the file's milestone.
- Make every `depends:` ID exist.
- Warn (don't fail) on glossary "Avoid saying" terms found in `docs/` and in Java identifiers.

**Don't.**
- Check external URLs. That would be flaky, so it is nightly only, later.

**Acceptance.**
- [ ] All current docs pass, or broken links are fixed in the same PR.
- [ ] Each check has a failing fixture.

**Verify.**
`./gradlew docsCheck`

### T-M0-06 · Add the module README, PR and issue templates
- tier: haiku
- depends: —
- modules: .github, docs/ai
- adrs: 0010
- specs: AGENTS.md §6, ai/workflow.md
- test-first: no

**Goal.** Standard templates, so every agent produces uniform artefacts.

**Do.**
- `docs/ai/module-readme-template.md` with the four sections.
- `.github/pull_request_template.md`:
  - card ID;
  - acceptance checklist;
  - gate output block;
  - "gotchas learned" section;
  - "did you weaken any test?" checkbox.
- Review `.github/ISSUE_TEMPLATE/task.yml` (already present) against the card format.

**Don't.**
- Duplicate AGENTS.md content. Link to it instead.

**Acceptance.**
- [ ] Opening a new PR or issue on GitHub shows the templates.

**Verify.**
Manual: open a draft PR and check that the template renders.

### T-M0-07 · Configure Renovate with pinning rules
- tier: haiku
- depends: T-M0-01
- modules: renovate.json
- adrs: 0006
- specs: versioning.md §5
- test-first: no

**Goal.** Automated, gated dependency bumps.

**Do.**
- Add `renovate.json`:
  - pin digests for Docker images and GitHub Actions;
  - group JUnit, Testcontainers and Jackson;
  - give MCProtocolLib its own PR with the label `driver-bump` (tier sonnet review);
  - add a custom manager for `versions.yml` driver and image entries;
  - set a schedule.

**Don't.**
- Enable automerge for anything in `drivers/` or `testificate-core`.

**Acceptance.**
- [ ] Renovate's config validator passes (`npx renovate-config-validator`, run via the pinned Node
  toolchain or in CI).

**Verify.**
CI job `renovate-validate`.

### T-M0-99 · Milestone review M0
- tier: sonnet
- depends: T-M0-01, T-M0-02, T-M0-03, T-M0-04, T-M0-05, T-M0-06, T-M0-07
- modules: docs/plan
- adrs: 0010
- specs: plan/ROADMAP.md
- test-first: no

**Goal.** Verify the exit criterion and refine the M1 cards.

**Do.**
- Run every gate and paste the output.
- Re-check the M1 cards against what M0 actually produced (paths, task names), and fix them.
- Re-estimate the work and update ROADMAP.md.

**Don't.**
- Start M1 work.

**Acceptance.**
- [ ] The exit criterion is shown green.
- [ ] The M1 cards are updated, and `docsCheck` is green.

**Verify.**
`./gradlew check docsCheck`
