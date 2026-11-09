# M6: Dogfood and 0.1 release

**Exit criterion.** All of the following must be true:
- Disenchantment's full suite runs on Proofstone in Disenchantment's CI.
- The Maven and Gradle example projects work.
- Artefacts are published to Maven Central and GHCR.
- The GitHub Action is published.
- The licence is decided.
- The mutation check passes.

## What M1 to M5 produced (refined in T-M5-99, refreshed after the M5 follow-ups, 2026-11-08)

- Cell selector `-Dproofstone.cells=<cell>` for every real-server run. `required` rows (ADR-0015): Paper 1.8.8 to 26.3 (11),
  Folia 1.21.11, 26.1.2, 26.2, 26.3 (4), Purpur 1.20.6, 1.21.11, 26.3 (3), Spigot 1.8.8, 1.21.11, 26.3 (3). `printMatrix --scope=pr`
  plans 21 cells. Each was promoted on at least two full per-row runs and, per platform, a C06 `-Dproofstone.repeat=10` of 10 passes.
  Not added: folia@1.20.6 (two different reds, `docs/platforms/folia.md`). Rows older than that are unpinned and unrun.
- **There is no CI and no workflow in the repo** (maintainer decision 2026-11-08, PRs #68, #70, #72; ADR-0014 update). The local gate is
  `bash scripts/gates.sh`; real-server runs go through `scripts/dockerlock.sh`. Cards below that name `.github/workflows`
  (T-M6-01, T-M6-02, T-M6-03, T-M6-07) must be done as **local release scripts** (`scripts/release-*.sh`, Gradle tasks) that the
  maintainer runs by hand, with secrets read from the environment and never committed. Adding any workflow, even tag-triggered,
  needs the maintainer's approval first: record the question in the PR and build the local script so a workflow is optional.
  T-M6-05 (Disenchantment's own CI) is that repository's concern, not this one's.
- Capability model: `@Requires` skips with a reason. `SIMULATED_BEDROCK` is declared by the Paper pack only (native cells).
  `NMS` and `DATAPACKS` were removed; re-add one only with its agent token. `Cell.capabilities()` is a `default` method that throws
  unless overridden (PR #73).
- Self-tests (M4): control run, repeat run, differential run (`mineflayer-ref`, 26.1.2 only) and `metaTest` with the bad
  tests. The verdict report and repro bundles exist.
- Open before the 0.1 release claim: T-M4-10 (per-cell conformance, INVALID on window and inventory breaks) and T-M4-12
  (real agent snapshot in bundles, wider leak detector), per the M4 review. T-M4-13 and T-M4-14 are open and can wait.
- Risks for M6: the prefixed-Bedrock login stalls at ViaProxy on bridged cells (the image bundles ViaBedrock; C-B01 runs native only);
  two unexplained single reds on record (purpur@26.3 C06 join timeout, spigot@1.21.11 C06 inventory DISAGREE, neither
  reproduced in 10 repeats); folia@1.20.6 join stall and C07 cursor assertion.
- T-M6-05 (Disenchantment's Bedrock-prefix chest-menu scenario) can run only on `paper@26.3` today.
- T-M6-04 example projects: use `paper@26.3` for the native run; Folia, Purpur and Spigot rows are now `required` and may be added.

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
- [x] A dry-run publish to staging succeeds. (Evidence: author-reported, not re-verified. The author ran `bash scripts/release-maven.sh --dry-run`, which exited 0, unsigned, and signed with a throwaway key. The verifier could not confirm the signatures. See docs/release.md. Reproduce with `bash scripts/release-maven.sh --dry-run`, which uploads nothing.)

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
- [ ] The images can be pulled by digest. **UNVERIFIED**: no image has been pushed. Local tooling only (`scripts/release-images.sh`, docs/release.md): dry run builds both images and prints ids, `--push` refuses without `PROOFSTONE_RELEASE_CONFIRM=yes-push` and GHCR env, `--write-digests` refuses without a pushed digest. spigot-buildtools is not pushed (its Dockerfile forbids it): open question for the maintainer.

**Verify.**
The CI run link.

### T-M6-03 · GitHub Action
- tier: haiku
- depends: T-M6-01
- modules: integrations/github-action
- adrs: 0008
- specs: versioning.md §4
- test-first: no

**Goal.** A reusable GitHub Action that runs Proofstone in a consumer's CI.

**Do.**
- Write a composite action. Its inputs are `scope` (pr or nightly), `cells` and `build-tool`.
- Cache Gradle, Maven, Docker layers and server jars.
- Upload JUnit XML and repro bundles.

**Don't.**
- Hard-code version lists. Read them from the matrix task instead.

**Acceptance.**
- [ ] Both example projects use the action and pass. **UNVERIFIED**: no CI (AGENTS.md section 4), the Maven example does not exist yet, and the action is not published. Local only: `integrations/github-action/` (action, run-cells.sh, README), `scripts/action-selfcheck.sh` (plan-only and one real-server Gradle run on paper@26.3). That run was RED: 1 of 2 tests timed out joining (`docs/platforms/paper.md` gotchas). Docker layer cache is not wired (see its README).

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

### T-M6-05 · Disenchantment suite on Proofstone (in the Disenchantment repo)
- tier: haiku
- depends: T-M6-03
- modules: H7KZ/Disenchantment: e2e module (separate PR in that repo)
- adrs: 0003
- specs: research/2026-11-03-e2e-survey.md §6
- test-first: no

**Goal.** Disenchantment's full e2e suite running on Proofstone.

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
- [x] A surviving mutation demonstrably blocks the release, tested by temporarily disabling a bug. (Evidence: author-reported, not re-verified. The author reported that, with `double-output` disabled in an uncommitted edit, `bash scripts/release-mutation-check.sh` printed SURVIVED and exited 1. Reproduce: disable one `bugs:` toggle in an example's `config.yml`, run `bash scripts/release-mutation-check.sh`, and read its table; it must print SURVIVED and exit 1. Restore the toggle afterwards. The author also reported a clean run on commit 1591d77 with `maven-paper-plugin` NOT RUN allowed by `--allow-missing`. Since T-M6-09 the publish gate refuses any result with `allowed_missing` set, so that result cannot be published. The file `build/release/mutation-check.env` is untracked and is not evidence on its own.)

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
- [ ] The exit criterion is demonstrated. **NOT MET** (NO-GO): Disenchantment CI is in another repo, the Maven example does not exist, nothing is published, the licence is undecided, and the mutation check is INCOMPLETE without `--allow-missing`. See [ROADMAP, M6 review](../ROADMAP.md).
- [x] The release notes are drafted. (Evidence: `docs/release-notes-0.1.0-DRAFT.md`, marked DRAFT and NO-GO. Claims limited to verified items.)

**Verify.**
The links to the gate, nightly and mutation-check runs.

### T-M6-09 · Release hygiene fixes (M6 audit)
- tier: haiku
- depends: T-M6-99
- modules: scripts, examples/bad-tests, docs/user, docs/plan
- adrs: 0003, 0012
- specs: specs/self-tests.md §5
- test-first: yes

**Goal.** Close the release-hygiene findings of the M6 audit: an unfinished mutation check must not be publishable, the bad
tests must honour the cell selector, and the docs and card evidence must claim only what was verified.

**Do.**
- Move the mutation-check gate of `scripts/release-maven.sh` into `scripts/lib/mutation-gate.sh`. It accepts only a complete
  PASS for HEAD: `allowed_missing` empty, `dirty=false`, a matching `head`, every key present, and no older than 24 hours.
  Test it with fixture result files in `scripts/release-gate-test.sh` (run by `scripts/gates.sh`), never by publishing.
- `examples/bad-tests/build.gradle.kts`: forward `-Dproofstone.cells` (and `proofstone.scope`) to every `meta*` bad-test run.
  A differential run without `-Dproofstone.cells` fails with a message that names the cell to pass.
- `examples/bad-tests/README.md`: list all eight bad tests (including `metaInvalidWindow`) and the differential command with
  `-Dproofstone.cells=paper@26.1.2`.
- `docs/user/quickstart-gradle.md`: state the JDKs that were verified per task, and fix the bundle path to
  `build/proofstone/<platform>@<mc>/<TestClass>.<method>/`.
- `docs/user/quickstart-maven.md`: do not name the missing `examples/maven-paper-plugin/` directory.
- T-M6-01 and T-M6-07 evidence: T-M6-01 is "author-reported, not re-verified"; T-M6-07 points to a reproducible command, not
  to the untracked `build/release/mutation-check.env`.

**Don't.**
- Publish, upload, tag or push anything. Run `--publish` never.
- Weaken a bad test, a verdict expectation or a metaTest assertion. Change the injected bugs.
- Add a workflow.

**Acceptance.**
- [x] Gate test: a complete PASS is accepted; an `allowed_missing` PASS, a dirty result, a wrong HEAD, INCOMPLETE and FAIL are refused. (Evidence: `bash scripts/release-gate-test.sh` 9 passed, 0 failed, fixtures only; within `scripts/gates.sh`, `gates: OK`.)
- [x] `release-maven.sh --publish` reaches the mutation gate only through `mutation_gate_check`. (Evidence: `scripts/release-maven.sh` calls the lib; `--publish` was never run.)
- [x] `metaTest` with no selector (default cells) gives all eight expected verdicts. (Evidence: `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaTest --no-parallel --max-workers=1`, BUILD SUCCESSFUL in 5m17s, "every bad test produced its expected verdict (VACUOUS, FLAKY, DISAGREE, INVALID, INVALID, LEAK, FAIL, SKIPPED)". Cells per XML: cell-agnostic tasks on paper@26.3, pinned `metaDisagree`, `metaInvalidWindow`, `metaLeak` on paper@26.1.2. 0 join timeouts.)
- [x] A selector that a pinned task cannot run on is refused before any server starts, naming the task and its supported cell. (Evidence: `bash scripts/bad-tests-pin-check.sh`, run by `scripts/gates.sh`: `-Dproofstone.cells=paper@26.3` refused for `metaInvalidWindow`, `metaDisagree`, `metaLeak`, each with "Supported cells: paper@26.1.2"; no server started.)
- [x] A differential run without `-Dproofstone.cells` fails with a clear message, and pinned differential runs take their pin. (Evidence: earlier `./gradlew :examples:bad-tests:test -Dproofstone.differential=true` refusal; `metaDisagree` runs on its pin in the default `metaTest`.)
- [ ] `metaTest` with `-Dproofstone.cells=paper@26.1.2` only, for every task. **Not run.** Documented instead: `examples/bad-tests/README.md` "Cell pins" table lists which tasks accept which cells. The cell-agnostic tasks on 26.1.2 were not proven.
- [x] The quickstart JDK statements say exactly what was verified; the Maven page names no missing path. (Evidence: `docs/user/quickstart-gradle.md` JDK section; `quickstart-maven.md` no longer names `examples/maven-paper-plugin/`.)
- [x] T-M6-01 and T-M6-07 evidence is reworded as above. (Evidence: both lines in this card, now "author-reported, not re-verified" with reproducible commands.)

**Verify.**
`bash scripts/release-gate-test.sh`, then `bash scripts/gates.sh --max-workers=1`, then
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaTest -Dproofstone.cells=paper@26.3 --no-parallel --max-workers=1`.
