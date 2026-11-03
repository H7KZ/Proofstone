# M6: Dogfood and 0.1 release

**Exit criterion.** All of the following must be true:
- Disenchantment's full suite runs on Testificate in Disenchantment's CI.
- The Maven and Gradle example projects work.
- Artefacts are published to Maven Central and GHCR.
- The GitHub Action is published.
- The licence is decided.
- The mutation check passes.

### T-M6-01 · Maven Central publishing pipeline
- tier: haiku
- depends: T-M4-99
- modules: build-logic, .github/workflows
- adrs: 0009, 0012
- specs: adr/0012-name.md (coordinates)
- test-first: no

**Goal.** Publish signed artefacts to Maven Central.

**Do.**
- Verify the `io.github.h7kz` namespace on Central, and record the result in ADR-0012.
- Add signing, sources jars and javadoc jars.
- Add a release workflow that triggers on tags, with its secrets documented.

**Don't.**
- Publish anything before T-M6-06 (licence) is merged.

**Acceptance.**
- [ ] A dry-run publish to staging succeeds.

**Verify.**
`./gradlew publishToMavenLocal`, then the staging dry run.

### T-M6-02 · GHCR image publishing
- tier: haiku
- depends: T-M4-99
- modules: images, .github/workflows
- adrs: 0006
- specs: architecture.md §4
- test-first: no

**Goal.** Publish the project's container images to GHCR.

**Do.**
- Build and push the mineflayer-ref and Spigot BuildTools images with provenance.
- Write the digests back into `versions.yml` through a PR.

**Don't.**
- Push mutable tags that the runtime depends on.

**Acceptance.**
- [ ] The images can be pulled by digest.

**Verify.**
The CI run link.

### T-M6-03 · GitHub Action
- tier: haiku
- depends: T-M6-01
- modules: integrations/github-action
- adrs: 0008
- specs: versioning.md §4
- test-first: no

**Goal.** A reusable GitHub Action that runs Testificate in a consumer's CI.

**Do.**
- Write a composite action. Its inputs are `scope` (pr or nightly), `cells` and `build-tool`.
- Cache Gradle, Maven, Docker layers and server jars.
- Upload JUnit XML and repro bundles.

**Don't.**
- Hard-code version lists. Read them from the matrix task instead.

**Acceptance.**
- [ ] Both example projects use the action and pass.

**Verify.**
The CI run links for both example projects.

### T-M6-04 · Example projects: Maven and Gradle Paper plugins
- tier: haiku
- depends: T-M4-99
- modules: examples/maven-paper-plugin, examples/gradle-paper-plugin
- adrs: 0001, 0009
- specs: specs/test-api.md
- test-first: no

**Goal.** Two minimal plugins showing the intended setup with each build tool.

**Do.**
- Build one tiny plugin for each build tool, with an anvil mechanic and two toggleable injected bugs for
  the mutation check.
- Maven uses failsafe with the `*E2E.java` naming convention. Gradle uses an `e2eTest` source set.

**Don't.**
- Depend on unpublished snapshots.

**Acceptance.**
- [ ] Both examples are green.
- [ ] Each injected bug makes the suite fail.

**Verify.**
`./gradlew :examples:gradle-paper-plugin:e2eTest`, `mvn -f examples/maven-paper-plugin verify -Pe2e`

### T-M6-05 · Disenchantment suite on Testificate (in the Disenchantment repo)
- tier: haiku
- depends: T-M6-03
- modules: H7KZ/Disenchantment: e2e module (separate PR in that repo)
- adrs: 0003
- specs: research/2026-11-03-e2e-survey.md §6
- test-first: no

**Goal.** Disenchantment's full e2e suite running on Testificate.

**Do.**
- Write these scenarios:
  - disenchant;
  - shatter;
  - grindstone extract and transfer;
  - unsafe result clicks are cancelled (no duplication);
  - insufficient XP;
  - disabled world gives the vanilla result;
  - erase mode;
  - special anvils;
  - Bedrock-prefix chest-menu fallback;
  - a per-cell NMS-module expectation.
- Use one cell per NMS module, plus `latest`, plus Folia.

**Don't.**
- Change Disenchantment's production code in the same PR.

**Acceptance.**
- [ ] Green in Disenchantment's CI.
- [ ] The control run produces no `VACUOUS` tests.

**Verify.**
The Disenchantment CI run link.

### T-M6-06 · Licence decision
- tier: opus
- depends: —
- modules: LICENSE, docs/adr/0011-licensing.md
- adrs: 0011
- specs: —
- test-first: no

**Goal.** Choose and apply the project licence.

**Do.**
- Present the Apache-2.0 vs MIT trade-off to the maintainer. **The maintainer decides.**
- Add `LICENSE`, update ADR-0011 to Accepted, and add DCO sign-off to CONTRIBUTING.

**Don't.**
- Decide without the maintainer.

**Acceptance.**
- [ ] The LICENSE file exists.
- [ ] ADR-0011 is Accepted.

**Verify.**
Review.

### T-M6-07 · Mutation check in the release pipeline
- tier: sonnet
- depends: T-M6-04
- modules: .github/workflows, examples
- adrs: 0003
- specs: specs/self-tests.md §5
- test-first: no

**Goal.** Make releases prove that the suite can actually catch bugs.

**Do.**
- Have the release workflow enable each injected bug in turn and require the suite to fail.
- Block the release if any mutation survives.

**Don't.**
- Skip mutations to make a release pass.

**Acceptance.**
- [ ] A surviving mutation demonstrably blocks the release, tested by temporarily disabling a bug.

**Verify.**
The CI run link.

### T-M6-08 · Quickstart docs (under 15 minutes)
- tier: haiku
- depends: T-M6-03, T-M6-04
- modules: docs/user
- adrs: 0001
- specs: vision.md (success criteria)
- test-first: no

**Goal.** A newcomer can get a first test running quickly.

**Do.**
- Write `docs/user/quickstart-maven.md`, `quickstart-gradle.md`, `writing-tests.md` and
  `troubleshooting.md`.
- Time a fresh run-through by following only the docs, and record the time.

**Don't.**
- Document APIs that don't exist.

**Acceptance.**
- [ ] A fresh agent session following only the docs reaches a green test in 15 minutes or less.

**Verify.**
The recorded run-through.

### T-M6-99 · Release go/no-go for 0.1.0
- tier: sonnet
- depends: T-M6-01, T-M6-02, T-M6-03, T-M6-04, T-M6-05, T-M6-06, T-M6-07, T-M6-08
- modules: docs/plan
- adrs: 0010, 0011
- specs: vision.md
- test-first: no

**Goal.** Decide whether 0.1.0 is ready to ship.

**Do.**
- Run every gate, the nightly suite and the mutation check.
- Draft the release notes.
- Hand over to the maintainer for the final go.

**Don't.**
- Tag the release without the maintainer's approval.

**Acceptance.**
- [ ] The exit criterion is demonstrated.
- [ ] The release notes are drafted.

**Verify.**
The links to the gate, nightly and mutation-check runs.
