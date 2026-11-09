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
- [x] The LICENSE file exists. Evidence: `LICENSE` at the root, byte-identical to the Apache-2.0 text in
  `jackson-core-2.22.4.jar` (META-INF/LICENSE, 11358 bytes, MD5 3B83EF96387F14655FC854DDC3C6BD57).
- [x] ADR-0011 is Accepted. Evidence: `docs/adr/0011-licensing.md` Status line, 2026-11-09.

**Verify.**
Review. Plus `bash scripts/release-maven.sh --dry-run` shows no licence warning.

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

### T-M6-10 · Diagnose the join-timeout and Gradle-example flake (M6 audit)
- tier: sonnet
- depends: T-M6-09
- modules: proofstone-core, examples/gradle-paper-plugin, docs/platforms
- adrs: 0003, 0010
- specs: specs/self-tests.md §3
- test-first: no

**Goal.** Explain, or bound, the intermittent reds on record: join timeouts (`purpur@26.3` C06, Gradle example twice, `metaExpect`),
the Gradle example `VerdictGate.java:150` red and the `PSConf moved too quickly` ErrorLogGuard hit (ROADMAP "M6 pre-release findings").

**Do.**
- Run the Gradle example on `paper@26.3` at least 30 times, one run at a time through `scripts/dockerlock.sh`, copying each run's
  XML, bundle and container log aside **before** the next run overwrites them (the `scripts/release-mutation-check.sh` loop pattern).
- Record per run: verdict, boot time, join time, host CPU and memory, and `docker ps` (other containers on the host).
- Run the same loop once with Docker Desktop otherwise idle and once with another project's containers busy, to test the
  resource-contention hypothesis.
- Write the result in ROADMAP "M6 pre-release findings": red count, cause if shown, or "bounded at N of M, cause unknown".

**Don't.**
- Add a retry or raise a timeout to make runs green. A timeout change needs its own measurement and an ADR note.
- Count a rerun as a pass.

**Acceptance.**
- [x] At least 30 runs recorded with per-run evidence kept (paths in the PR). Evidence: 30 no-stress runs in `build/flake/nostress/run-NN/` and 15 stress runs in `build/flake/stress/run-NN/` (XML, bundles, gradle.log, container logs, host samples), plus 1 smoke pass in `build/flake/smoke/`. Four no-stress pass runs (12, 26, 27, 28) lack a join time (watcher race, fixed in the script). Summary table in ROADMAP "M6 pre-release findings" item 3.
- [x] Every red has its verdict type named from the kept XML or bundle. Evidence: no-stress run 29 (`INVALID`, `join-timeout`, conformance client `PSConf`) and stress run 8 (`join-timeout`, plugin test client), both from `test-results/*.xml` and `proofstone/paper@26.3/*/server.log`.
- [x] The contention hypothesis is confirmed or rejected with numbers, or marked unresolved. Marked **unresolved**: stress raised boot and join times (+2.6 s and +3.2 s) but 1 of 15 stress runs red against 1 of 30 no-stress runs does not separate the two conditions.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:gradle-paper-plugin:e2eTest --no-parallel --max-workers=1`, repeated.

### T-M6-11 · Maven example project (blocked on mvn)
- tier: haiku
- depends: T-M6-09
- modules: examples/maven-paper-plugin, docs/user
- adrs: 0001, 0009
- specs: specs/test-api.md
- test-first: no

**Goal.** The Maven half of T-M6-04, so the mutation check no longer needs `--allow-missing`.

**Do.**
- Needs Apache Maven on the machine (the maintainer installs it; agents must not download it unasked).
- Mirror `examples/gradle-paper-plugin`: same anvil mechanic and two toggleable bugs, failsafe with `*E2E.java`, Proofstone from `mavenLocal`.
- Register it in `scripts/release-mutation-check.sh`'s single toggle list and in `docs/user/quickstart-maven.md` (only what was run).

**Don't.**
- Document Maven steps that were not run. Depend on unpublished remote snapshots.

**Acceptance.**
- [x] `mvn -f examples/maven-paper-plugin verify -Pe2e` is green on `paper@26.3`. (Evidence: `TEST-...AnvilMechanicE2E.xml` tests=2 skipped=0 failures=0 errors=0; PR.)
- [x] Each injected bug makes it fail. (Evidence: `double-output` failures=1 "expected: 1 but was: 2"; `accept-any-right` failures=1 "coal is not an accepted right input"; PR.)
- [x] `bash scripts/release-mutation-check.sh` (no `--allow-missing`) reports a complete PASS. (Evidence: `mutation-check: PASS` log in PR; `dirty` per the run.)

**Verify.**
`mvn -f examples/maven-paper-plugin verify -Pe2e` and `bash scripts/release-mutation-check.sh`.

### T-M6-12 · Rename packages `dev.proofstone` to `io.github.h7kz.proofstone`
- tier: haiku
- depends: T-M6-06
- modules: all modules with Java sources, build-logic, examples, platforms/bukkit, proofstone-api, scripts, docs
- adrs: 0012
- specs: specs/test-api.md §8 (API v0 frozen), specs/agent-protocol.md (wire unchanged)
- test-first: no

**Goal.** The maintainer does not own `proofstone.dev` (decided 2026-11-09), so the ADR-0012 default namespace
`io.github.h7kz.proofstone.*` applies. The rename is mechanical and happens before the first publish, while nothing is
published under the old name.

**Do.**
- Move every `dev/proofstone` package directory with `git mv` so history follows. Rename the three `META-INF/services` files
  whose names are `dev.proofstone.*`.
- Rewrite every `dev.proofstone` and `dev/proofstone` occurrence in live Java, Kotlin, Gradle, XML, YAML, JSON, properties,
  service, golden and doc files. This includes the plugin descriptor `main:` lines, the shading relocation
  `dev.proofstone.shaded.jackson`, ArchUnit rules, build-logic, examples, scripts, and the Docker label keys in
  `scripts/release-images.sh`.
- Regenerate `proofstone-api/api-baseline.txt` with `-Dproofstone.updateApiBaseline=true`. The diff must be package-name only:
  a sed-normalised comparison with the old file must be identical. The PR carries the `api-change` label.
- Update ADR-0012 to say the default namespace is in force, dated 2026-11-09.
- Leave `docs/research` (read-only), ADR text that records history, and historical ROADMAP and card text unchanged.

**Don't.**
- Change the wire protocol. The protocol version and golden files stay as they are, except where a class or package name
  appears in them.
- Change the Maven groupId (already `io.github.h7kz.proofstone`) or any public API signature.
- Raise the agent's bytecode target above Java 8.

**Acceptance.**
- [x] No `dev.proofstone` or `dev/proofstone` remains outside `docs/research` and historical ADR and ROADMAP text. (Evidence: final grep in the PR; remaining hits are ADR-0011, ADR-0012 history, NEXT-SESSION-M7 prompt, ROADMAP, and M0 to M3 cards.)
- [x] `api-baseline.txt` differs from the old file only by the package name. (Evidence: after sed-normalising, the line set is identical. The dump is sorted, and `io.github` sorts after `int`, so the line order changed too. Flagged in the PR.)
- [x] `bash scripts/gates.sh --max-workers=1` passes. (Evidence: `gates: OK`, `BUILD SUCCESSFUL`, log in PR.)
- [x] `:platforms:bukkit:agent:integrationTest` is 24/24 on the real server. (Evidence: `TEST-...AgentContractIT.xml` tests=24 skipped=0 failures=0 errors=0.)
- [x] `:conformance:smoke` passes on `paper@26.3`. (Evidence: `ConformanceSmokeTest` tests=2, 0 skip/fail/err.)
- [x] `:examples:bad-tests:metaTest` gives all eight verdicts with no selector. (Evidence: log line "every bad test produced its expected verdict (VACUOUS, FLAKY, DISAGREE, INVALID, INVALID, LEAK, FAIL, SKIPPED)".)
- [x] `:examples:gradle-paper-plugin:e2eTest` passes on `paper@26.3`. (Evidence: `AnvilMechanicE2E` tests=2, 0 skip/fail/err.)
- [x] `bash scripts/release-maven.sh --dry-run` shows groupId `io.github.h7kz.proofstone`. (Evidence: `DRY RUN OK`; staged POM groupId `io.github.h7kz.proofstone`.)

**Verify.**
`bash scripts/gates.sh --max-workers=1`, then, one run at a time under `scripts/dockerlock.sh`, `:platforms:bukkit:agent:integrationTest`,
`:conformance:smoke` on `paper@26.3`, `:examples:bad-tests:metaTest`, `:examples:gradle-paper-plugin:e2eTest` on `paper@26.3`,
then `bash scripts/release-maven.sh --dry-run`.

**Diff size.** This is a pure mechanical rename. The 400-changed-line rule in AGENTS.md section 2.4 does not apply.
The PR body says so.

### T-M6-13 · Pre-M7 readiness: re-run the release checks and rewrite the M7 prompt
- tier: haiku
- depends: T-M6-12, T-M6-11, T-M6-10
- modules: docs, scripts (no code change)
- adrs: 0011, 0012, 0014
- specs: specs/self-tests.md §5
- test-first: no

**Goal.** Re-run every local release gate at one commit, record the result in the ROADMAP "M6 review addendum", and hand the
next session an accurate M7 prompt. No code or test changes.

**Do.**
- Run, one at a time and under `scripts/dockerlock.sh`: `bash scripts/gates.sh --max-workers=1`, `:conformance:smoke` on
  `paper@26.3`, `:conformance:run -Dproofstone.cells=paper@26.1.2`, `:examples:bad-tests:metaTest` (no selector), then
  `bash scripts/release-mutation-check.sh` (no `--allow-missing`) on the committed clean tree.
- Run the quickstarts (`docs/user/quickstart-gradle.md`, `docs/user/quickstart-maven.md`) command by command, as a check that
  each command exists and is correct. Fix doc errors.
- Run `bash scripts/release-maven.sh --dry-run`, and confirm that `--publish`, `release-images.sh --push` and
  `--write-digests` each refuse with the release environment cleared.
- Update ROADMAP, `docs/release-notes-0.1.0-DRAFT.md` (still DRAFT) and `docs/plan/NEXT-SESSION-M7-PROMPT.md`.

**Don't.**
- Publish, push to any registry, set `PROOFSTONE_RELEASE_CONFIRM`, tag, add a workflow, or touch the `ohlidame-*` containers.
- Retry a red run, raise a timeout, or count a rerun as a pass. A red run's XML is kept aside before the next run.

**Acceptance.**
- [x] `bash scripts/gates.sh --max-workers=1` exits 0. (Evidence: `gates: OK` in the T-M6-13 log, exit 0; re-run before the PR, see PR body.)
- [x] `:conformance:smoke` on `paper@26.3`: `ConformanceSmokeTest` tests=2 skipped=0 failures=0 errors=0.
- [x] `:conformance:run` on `paper@26.1.2`: failures=0, errors=0. 3 reasoned skips: `missing capability FOLIA_REGIONS` (2) and `missing capability SIMULATED_BEDROCK` (1).
- [x] `:examples:bad-tests:metaTest` (no selector): BUILD SUCCESSFUL, "every bad test produced its expected verdict (VACUOUS, FLAKY, DISAGREE, INVALID, INVALID, LEAK, FAIL, SKIPPED)".
- [x] Quickstart Gradle: `e2eTest` paper@26.3 tests=2 skipped=0 failures=0 errors=0; `double-output` and `accept-any-right` both red on assertions (`AnvilMechanicE2E.java:42`, `:60`), not join timeouts.
- [x] Quickstart Maven: `verify -Pe2e` paper@26.3 tests=2 skipped=0 failures=0 errors=0; both bugs red with the table's messages (`one iron and one gold make one diamond`, `coal is not an accepted right input`).
- [x] `bash scripts/release-mutation-check.sh` (no flag): `mutation-check: PASS (head 4df12c8, cell paper@26.3, killed 4, dirty false)`, `allowed_missing` empty.
- [x] `release-maven.sh --dry-run`: DRY RUN OK, nothing uploaded, signatures NOT CHECKED (no key set).
- [x] `release-maven.sh --publish`, `release-images.sh --push`, `release-images.sh --write-digests`: each refused with the release environment cleared.
- [x] No `.github/workflows`, no tags, LICENSE and NOTICE present, no secret pattern in tracked files, `git status` clean after the runs. No join timeout and no `PSConf moved too quickly` in any run log of this card.
- [x] Live `dev.proofstone` leftovers fixed: ADR-0011 jackson package. Historical text left as written (ADR-0012, ROADMAP, M0 to M3 cards).

**Verify.**
`bash scripts/gates.sh --max-workers=1`, then the real-server runs above, then `bash scripts/release-mutation-check.sh`.

### T-M6-14 · Log the server's view at join and name it in INVALID (flake follow-up)
- tier: sonnet
- depends: T-M6-10
- modules: proofstone-core, docs/user/troubleshooting.md, docs/platforms
- adrs: 0003, 0010
- specs: specs/self-tests.md §3
- test-first: yes

**Goal.** The 3 plain-path join timeouts on record (`paper@26.3`, `timed out joining ... after PT30S`) have no cause. The next one
must say which handshake step the server never answered.

**Do.**
- On a join timeout, log the server's view: login start seen (yes or no), protocol version offered, last packet received,
  and the server log tail at that moment.
- Put the stage that stalled in the INVALID message, in place of the bare `timed out`.
- Test the message composition in unit tests, then the real-server path with `scripts/flake-loop.sh` (30 runs, XML kept aside).

**Don't.**
- Change the PT30S timeout, add a retry, or treat a logged reason as a fix. Explaining the cause is a separate card.

**Acceptance.**
- [x] A join timeout's INVALID message names the stalled handshake stage. Evidence: `JoinDiagnosisTest` (15), `JoinStallTest` (4), two loopback tests in `McplDriverTest`, and a 30-run loop on paper@26.3 with 30 pass, 0 red (no real-server stall occurred to classify).
- [x] The server's view is written to the bundle (`join-stall.txt`, tested in `BundleWriterTest`). Evidence: bundle file list in the PR.
- [x] ROADMAP "M6 pre-release findings" item 3 updated with the new evidence.

**Verify.**
`bash scripts/gates.sh --max-workers=1`, then `scripts/flake-loop.sh` on `:examples:gradle-paper-plugin:e2eTest`, `paper@26.3`, under `scripts/dockerlock.sh`.
