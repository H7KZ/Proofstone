# M4: Self-verification

**Exit:** the control run, differential run, flake detector and repro bundles all work. The verdicts
`VACUOUS`, `DISAGREE` and `FLAKY` are each shown on purpose-built bad tests.

### T-M4-01 · Verdict engine and machine-readable report
- tier: sonnet
- depends: T-M3-99
- modules: proofstone-core, proofstone-api
- adrs: 0003, 0007
- specs: specs/test-api.md §7, specs/self-tests.md
- test-first: yes

**Goal.** Compute the full verdict set and write it to a machine-readable report.

**Do.**
- Compute these verdicts per test per cell:
  - `PASS`, `FAIL`, `INVALID`, `VACUOUS`, `FLAKY`, `DISAGREE`;
  - `SKIPPED(reason)`.
- Mark every plugin test on a cell `INVALID` when that cell's conformance fails. Run conformance once per
  cell per run and cache the result.
- Write `build/proofstone/proofstone-report.json`, plus JUnit XML that maps the extra verdicts onto
  failures with clear messages.

**Don't.**
- Let any verdict other than `PASS` or an allowed `SKIPPED` be green.

**Acceptance.**
- [x] Every verdict has a unit test, and a snapshot test covers the report schema.

**Verify.**
`./gradlew :proofstone-core:test`

### T-M4-02 · Repro bundles
- tier: haiku
- depends: T-M4-01
- modules: proofstone-core
- adrs: 0006
- specs: glossary.md (Repro bundle)
- test-first: no

**Goal.** Every failure leaves enough behind to reproduce it.

**Do.**
- On any result other than `PASS`, write `build/proofstone/<cell>/<test>/` containing:
  - `server.log`;
  - `agent-transcript.jsonl`;
  - `driver-transcript.jsonl`;
  - `cell.json` (all pins);
  - `snapshot.json`;
  - `rerun.txt` (the exact Gradle command with its cell selector, for example
    `-Dproofstone.cells=paper@1.8.8`; the selector exists since T-M3-04).
- Bridged cells (every Paper row except 26.3 in M3) also record the ViaProxy container's log in the bundle.
- `build/proofstone/` is not committed (M3 review). A PR that cites a failure links the Gradle command and the bundle
  path it printed, and states that the bundle is local.

**Don't.**
- Include the agent token in any bundle file.

**Acceptance.**
- [x] A forced failure produces a complete bundle. Evidence: `ForcedFailureE2E` on paper@26.3 (real server, run 2026-11-07, 3m32s) printed `PROOFSTONE BUNDLE: build\proofstone\paper@26.3\ForcedFailureE2E.failsOnPurposeWhenForced`; files present: server.log, agent-transcript.jsonl, driver-transcript.jsonl, cell.json, snapshot.json, rerun.txt. Bridged viaproxy.log not exercised (no bridged row in M4 smoke).
- [x] A secret-scan test proves the token is absent. Evidence: `BundleWriterTest` (7 tests, 0 failures); real bundle contains no `Bearer` and no `PROOFSTONE_AGENT_TOKEN`.

**Verify.**
`./gradlew :examples:smoke:test -Dproofstone.forceFail=true` (the test is expected to fail), then check
the bundle.

### T-M4-03 · Control-run mode
- tier: haiku
- depends: T-M4-01
- modules: proofstone-core
- adrs: 0003
- specs: specs/self-tests.md §2
- test-first: no

**Goal.** Detect tests that would pass even without the plugin.

**Do.**
- With `-Dproofstone.controlRun=true`, skip installing the `plugins` artefacts.
- A test that passes in this mode gets the verdict `VACUOUS`.
- Support `@ControlRun(EXPECTED_PASS, reason)`.

**Don't.**
- Remove the agent or the fixtures in control runs.

**Acceptance.**
- [x] A purpose-built vacuous test is reported as `VACUOUS`.

**Verify.**
`./gradlew :examples:bad-tests:test -Dproofstone.controlRun=true`

### T-M4-04 · Flake detector
- tier: haiku
- depends: T-M4-01
- modules: proofstone-core
- adrs: 0007
- specs: specs/self-tests.md §4
- test-first: no

**Goal.** Catch tests that give different verdicts across repeated runs.

**Do.**
- With `-Dproofstone.repeat=N` (N ≥ 3), run each test N times and report `FLAKY` if the verdicts differ.

**Don't.**
- Use repetition anywhere else as a retry.
- Use the real C06 flake on 1.8.8 as the acceptance test. Its ~1 in 4 failure rate before PR #39 is not proven fixed
  (`docs/platforms/viaproxy.md`), so it is a measurement target only: run it with N=5 on 1.8.8 and record the verdicts in
  the PR. The purpose-built test stays the acceptance test.

**Acceptance.**
- [x] A purpose-built random-outcome test is reported as `FLAKY` with N=5. Evidence: `FlakyE2E` on paper@26.3,
  `:examples:bad-tests:test -Dproofstone.repeat=5` (1m10s): JUnit XML `FLAKY FlakyE2E.theOutcomeOfThisRunIsRandom ...
  verdicts differ across 5 runs: PASS, FAIL`; `VacuousE2E` 5/5 PASS in the same run.

**Verify.**
`./gradlew :examples:bad-tests:test -Dproofstone.repeat=5`

### T-M4-05 · mineflayer-ref image and JSON-RPC server
- tier: sonnet
- depends: T-M3-99
- modules: drivers/mineflayer-ref, images
- adrs: 0004, 0006
- specs: specs/client-driver.md §4
- test-first: no

**Goal.** An independent reference driver for differential runs.

**Part 1 of 2 (split, maintainer accepted).** Windows, blocks, inventory and item parsing moved to T-M4-05b.
Part 1 covers join, chat, replies and disconnect on 26.1.2 (native) and 1.21.11 (bridged). Acceptance ticked
after T-M4-05b (PR #53) landed the windows, blocks and items.

**Do.**
- Build a Node image with pinned `mineflayer` and `minecraft-data`. The SPI is exposed over WebSocket
  JSON-RPC.
- Write the `max-native` value back to the `versions.yml` driver metadata.
- M3 fact: only Paper 26.3 is native to the mcpl driver. Every other Paper row runs bridged through ViaProxy. First
  check which protocol the reference driver speaks, and record the result in `docs/platforms/` before choosing the
  cells in the acceptance test; a row that needs the bridge needs the bridge in this driver too.

**Don't.**
- Use it as a primary driver.

**Acceptance.**
- [x] C02–C06 pass on the reference driver on 1.21.11 and 26.1.x.

**Verify.**
`./gradlew :drivers:mineflayer-ref:integrationTest`

### T-M4-05b · mineflayer-ref windows, blocks, inventory and items
- tier: sonnet
- depends: T-M4-05
- modules: drivers/mineflayer-ref, images/mineflayer-ref
- adrs: 0004, 0006
- specs: specs/client-driver.md §4
- test-first: no

**Goal.** Part 2 of T-M4-05: the reference driver answers windows, blocks and inventory, so C03–C06 pass on it.

**Do.**
- Implement `useBlock`, `awaitWindow` and `inventory` in the sidecar JSON-RPC and in `MineflayerRefSession`
  (replacing the T-M4-05 "not in the reference driver yet" failures).
- Item parsing must handle components, including the `stored_enchantments` fix and registry ID → name
  mapping (minecraft-data).

**Don't.**
- Return empty data for anything still unimplemented. Keep failing loudly.
- Change the protocol version or the native row.

**Acceptance.**
- [x] C02–C06 pass on the reference driver on 1.21.11 and 26.1.x.
- [x] `MineflayerRefDriverIT` XML shows `skipped="0" failures="0" errors="0"`.

**Verify.**
`./gradlew :drivers:mineflayer-ref:integrationTest`

### T-M4-06 · Differential runner and comparator
- tier: sonnet
- depends: T-M4-05, T-M4-05b, T-M4-01
- modules: proofstone-core, drivers/mineflayer-ref
- adrs: 0003
- specs: specs/self-tests.md §3
- test-first: yes

**Goal.** Run the same test through two drivers and compare what each client saw.

**Do.**
- With `-Dproofstone.differential=true`, run each test with both drivers on cells where the reference
  driver is native.
- Record every client-oracle observation at each assertion point, and compare them in order.
- Report a mismatch as `DISAGREE`, with both transcripts attached.

**Don't.**
- Compare timing-dependent fields such as positions while moving. Pin the comparison list in the code.

**Acceptance.**
- [x] A purpose-built driver-fault injection is reported as `DISAGREE`. Evidence (real server, paper@26.1.2, 2026-11-08): `./gradlew :examples:bad-tests:test -Dproofstone.differential=true` ran `DriverFaultDisagreeE2E`; mcpl PASSED, mineflayer-ref FAILED with `DISAGREE ... on paper@26.1.2`, XML skipped=0 errors=0; bundle `build/proofstone/paper@26.1.2/DriverFaultDisagreeE2E.<test>/mineflayer-ref/` holds `differential/` with both transcripts and observations (local, not committed).

**Verify.**
`./gradlew :examples:bad-tests:test -Dproofstone.differential=true`

### T-M4-07 · Purpose-built bad tests
- tier: haiku
- depends: T-M4-03, T-M4-04, T-M4-06
- modules: examples/bad-tests
- adrs: 0003, 0007
- specs: specs/self-tests.md
- test-first: no

**Goal.** Living proof that the safety nets catch what they claim to catch.

**Do.**
- Write one test for each of these verdicts:
  - vacuous;
  - flaky;
  - disagree (via the fault-injection flag);
  - invalid (a conformance-breaking cell override);
  - leak (state left behind).
- Each test is expected to produce its verdict, and the expectation is asserted by a meta-test.
- **Gap from T-M4-03:** the extension passes `ConformanceResult.pass(cell)` (`VerdictGate.judgePass`), so `INVALID`
  cannot fire from it. The invalid test needs a conformance runner wired to `ConformanceCache` first.

**Don't.**
- Let these tests run in normal user-facing suites.

**Acceptance.**
- [x] The meta-test is green, and therefore every bad test produced its expected verdict. (Evidence:
  `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaTest --no-parallel` on
  paper@26.3 and paper@26.1.2, "metaTest: every bad test produced its expected verdict (VACUOUS, FLAKY, DISAGREE,
  INVALID, LEAK)", BUILD SUCCESSFUL; XML skipped=0 errors=0 in all five runs.)

**Verify.**
`./gradlew :examples:bad-tests:metaTest`

### T-M4-08 · Self-test design review and cost measurement
- tier: opus
- depends: T-M4-07
- modules: docs/robustness.md, docs/specs/self-tests.md
- adrs: 0003, 0007
- specs: robustness.md
- test-first: no

**Goal.** An independent review of whether the safety nets actually guard against the risks R1–R16.

**Do.**
- Review the risk register against what was built. Find the gaps and open cards for them.
- Measure the real PR and nightly durations and update robustness.md §4.

**Don't.**
- Implement the fixes here.

**Acceptance.**
- [x] The review is written up, gap cards are created, and the measured costs are recorded. Evidence:
  `docs/robustness.md` §4 (measured) and §5 (R1 to R16 review, X1 to X7); gap cards T-M4-09 to T-M4-15; local
  `:examples:bad-tests:metaTest` run 2026-11-08 (1:16); `gh run view` timings for five M4 PR `gates` runs.

**Verify.**
`./gradlew docsCheck`

### T-M4-09 · Repro bundles and verdict report complete (review gap)
- tier: sonnet
- depends: T-M4-07
- modules: proofstone-core
- adrs: 0006
- specs: specs/self-tests.md, specs/test-api.md §7
- test-first: yes

**Goal.** Every non-PASS verdict leaves a complete bundle with a rerun command that reproduces it, and the machine-readable
report exists at run time (robustness.md §5, R2, R5, R10, X2, X4).

**Do.**
- Write the bundle for a single-run VACUOUS and for any verdict from `judgePass`. Today `CellInvocation.afterEach` writes
  it before `judgePass`, so the `VacuousE2E` run writes none.
- Add `-Dproofstone.repeat`, `-Dproofstone.controlRun` and `-Dproofstone.injectConformanceFault` to `rerun.txt` when set.
- Record host info (OS, Docker server version, Java) and the ViaProxy image digest and driver pins in the bundle.
- Call `ReportWriter` and `JunitXml` at run end, so `build/proofstone/proofstone-report.json` exists (T-M4-01's Do list).
- Show a FLAKY verdict on every repeat of that test, or list the verdicts per test in the report. Today only repeat 5 is
  non-green in the XML.

**Don't.**
- Change any verdict rule in `VerdictEngine`.

**Acceptance.**
- [ ] `metaVacuous` writes a bundle for `VacuousE2E`, and its `rerun.txt` names `-Dproofstone.controlRun=true`.
- [ ] `metaFlaky`'s `rerun.txt` names `-Dproofstone.repeat=5`, and running that command reproduces the flake.
- [ ] `build/proofstone/proofstone-report.json` exists after `metaTest`, and a schema snapshot test covers it.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaTest --no-parallel`, then
check the bundle files and the report.

### T-M4-10 · Full conformance per cell (review gap)
- tier: sonnet
- depends: T-M4-07
- modules: proofstone-core, conformance
- adrs: 0003
- specs: specs/self-tests.md §1
- test-first: yes

**Goal.** INVALID fires on a broken window, inventory or agent round trip, not only on a broken `list` (robustness.md
§5, R1, R3, R4, X6).

**Do.**
- Run the C03 to C08 scenarios of the conformance module in the per-cell check (`CellConformance`), once per cell per run
  through `ConformanceCache`.
- Run C03 (agent round trip) on every cell, and the window scenarios C04 to C08 on every bridged cell.
- Record the added cost per cell in robustness.md §4.
- Add C09 to C12, or record in the PR why each one is deferred.

**Don't.**
- Skip a scenario silently on a row. A scenario that does not apply (C07 before 1.14) keeps its reason.

**Acceptance.**
- [ ] A conformance-breaking override on the window scenario makes every test on that cell `INVALID` (a new bad test in
  metaTest).
- [ ] Real-server run on paper@26.3 and paper@26.1.2 (bridged): C01 to C08 pass, with XML `skipped="0" failures="0"
  errors="0"` except the reasoned C07 skip.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run :examples:bad-tests:metaTest --no-parallel`

### T-M4-11 · Differential integrity and coverage (review gap)
- tier: sonnet
- depends: T-M4-06
- modules: proofstone-core, drivers/mineflayer-ref, versions.yml
- adrs: 0004
- specs: specs/self-tests.md §3
- test-first: yes

**Goal.** A differential run cannot pass by comparing nothing, its skips are visible, and its pins are right (robustness.md
§5, R1, X1, X3, X5).

**Do.**
- Fail a differential test that recorded zero client observations. A test with no `DualAssert` must not be "equal".
- Report `NOT DIFFERENTIAL` rows as JUnit skips with their reason, not only on stderr.
- Correct `versions.yml` `mineflayer-ref` `max-native` from `"26.1"` to `26.1.2`, the protocol the driver speaks.
- Record in `docs/platforms/` which rows the reference driver covers. Widening coverage changes the design, so it needs an
  ADR challenge if the answer is to widen.

**Don't.**
- Change the comparison list in `DifferentialRun.canonicalItem`.

**Acceptance.**
- [ ] A test with no dual assertion fails under `-Dproofstone.differential=true` (unit test, plus a meta run if one is added).
- [ ] A non-native row appears as a skipped testcase in the XML.
- [ ] `lintVersions` and `validateVersions` pass with the corrected value.

**Verify.**
`./gradlew check docsCheck`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaTest --no-parallel`.

### T-M4-12 · Agent state snapshot and wider leak detector (review gap)
- tier: sonnet
- depends: T-M4-02, T-M4-07
- modules: platforms/bukkit/agent, proofstone-core, docs/specs
- adrs: 0002, 0006
- specs: specs/agent-protocol.md §2, specs/self-tests.md §4b
- test-first: yes

**Goal.** Bundles carry real agent state (ROADMAP M4 follow-up 1), and the leak detector sees more than four world rules
(robustness.md §5, R6, X6).

**Do.**
- Implement `GET /v1/snapshot`. The agent-protocol spec lists it, and it returns `NOT_FOUND` today. Output must be
  deterministic: loaded plugins, config snapshots, world rules, players with their inventory and XP. No timestamps.
- Use the snapshot in `LeakDetector`, in addition to the gamerules.
- Spec change in agent-protocol §2 (an additive endpoint). The agent stays Java 8 bytecode.

**Don't.**
- Put the agent token in the output. `BundleWriter` redacts it, but the endpoint must not rely on that.

**Acceptance.**
- [ ] `snapshot.json` of a real bundle holds the snapshot, not the `NOT_FOUND` error.
- [ ] A new bad test that leaves a player's XP changed fails with `LEAK` (added to metaTest).
- [ ] `AgentContractIT` stays green, with a new golden file for the endpoint.

**Verify.**
`./gradlew :platforms:bukkit:agent:integrationTest`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaTest --no-parallel`.

### T-M4-13 · Harness test hardening (review gap)
- tier: sonnet
- depends: T-M4-07
- modules: examples/bad-tests, proofstone-core
- adrs: 0007
- specs: specs/self-tests.md §4
- test-first: yes

**Goal.** `metaTest` asserts what its name says, per test, and runs its Docker work the way AGENTS.md section 4 requires
(robustness.md §4, §5, R5, R15, X1).

**Do.**
- Decide, and record with XML timestamps, whether the five bad-test tasks may overlap inside one locked run. The T-M4-08
  run overlapped them (starts within 0.11 s). If they must not, order them with `mustRunAfter`.
- Assert per test: each bad test's verdict and its bundle, not "at least one failure per task".
- Remove the `random draw` exemption in `metaFlaky`. Match the FLAKY message instead.
- Report `NOT SELECTED` rows as JUnit skips. `ProofstoneExtension` prints them to stderr only today.
- Measure a repeat run that re-boots per repeat (PER_TEST isolation). Repeats share one boot today, so boot races are not
  covered. Record the cost, and keep or drop the mode.

**Don't.**
- Weaken a meta expectation to make it pass.

**Acceptance.**
- [ ] A unit test of the metaTest checker shows red when one bad test gives the wrong verdict.
- [ ] The overlap question is answered in the PR with XML timestamps.
- [ ] The repeat-with-boot cost is in robustness.md §4.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaTest --no-parallel`.

### T-M4-14 · Assertion lint and negative pairs, or retire the claim (review gap)
- tier: haiku
- depends: T-M4-07
- modules: build-logic, examples/bad-tests
- adrs: 0007
- specs: specs/self-tests.md §2
- test-first: no

**Goal.** Decide whether R2's assertion lint and paired-negative rule are real. Build them or remove them from the register
(robustness.md §5, R2).

**Do.**
- Define "negative assertion" for `DualAssert` and AssertJ calls, for example checks that only use `isEmpty`, `isNull`
  or `isFalse`.
- Add a lint (in `docsCheck`, or an `archTest` rule) that fails a test class where every assertion is negative, unless
  its method has `@ControlRun`.
- If the lint is not worth it, change R2 in robustness.md to say so, and name the control run as the only check.

**Don't.**
- Lint the framework's own tests.

**Acceptance.**
- [ ] A fixture test with only negative assertions fails the lint. The current examples pass it.
- [ ] `./gradlew check docsCheck` is green.

**Verify.**
`./gradlew check docsCheck`

### T-M4-15 · Per-cell expect hook and per-pack error patterns (review gap)
- tier: opus
- depends: T-M4-07
- modules: proofstone-api, proofstone-core, platforms/bukkit/agent
- adrs: 0003, 0006
- specs: specs/test-api.md, docs/platforms/paper.md
- test-first: yes

**Goal.** A wrong version mapping (R7) or a logged error (R9) fails a test. Folia's thread-check patterns (R8) come with the
Folia row, before M5 (robustness.md §5).

**Do.**
- Add an additive per-cell `expect` hook to the test API. This is an `api-change`: regenerate `proofstone-api/api-baseline.txt`
  deliberately and review the diff in `ApiSurfaceTest`. v0 signatures stay frozen.
- Add per-pack error patterns to `ErrorLogGuard`. Each pack supplies its list. Paper keeps the current allowlist.
- Document both in the test-api spec.

**Don't.**
- Change a frozen v0 signature. Additive only.

**Acceptance.**
- [ ] A bad test whose `expect` fails on a cell reports FAIL with the expect message (metaTest).
- [ ] `ApiSurfaceTest` is green with the regenerated baseline.

**Verify.**
`./gradlew :proofstone-api:test :proofstone-core:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaTest --no-parallel`.

### T-M4-99 · Milestone review M4
- tier: sonnet
- depends: T-M4-01, T-M4-02, T-M4-03, T-M4-04, T-M4-05, T-M4-06, T-M4-07, T-M4-08
- modules: docs/plan
- adrs: 0010
- specs: plan/ROADMAP.md
- test-first: no

**Goal.** Confirm M4 met its exit criterion, and prepare M6.

**Do.**
- Run every gate and paste the output.
- Add the nightly set (control, differential and repeat runs) as a job that runs only on `workflow_dispatch`. Do
  not add a `schedule:` trigger: scheduled Actions minutes were cancelled at PR #40 (billed). Turning on a schedule
  is a maintainer decision, recorded in the review.
- Refine the M6 cards.

**Don't.**
- Start M6.
- Run the nightly set on every Paper row. Pick the cells the self-tests need and say why in the review.

**Acceptance.**
- [ ] The exit criterion is demonstrated.
- [ ] One manual `workflow_dispatch` run of the nightly set is green, linked from the review. A run is evidence only
  when its XML has `skipped="0" failures="0"` or the skip has a reason, as in M3.

**Verify.**
The `workflow_dispatch` run link, plus the local `:examples:bad-tests:metaTest` output.
