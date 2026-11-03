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
- [ ] Every verdict has a unit test, and a snapshot test covers the report schema.

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
  - `rerun.txt` (the exact Gradle command with its cell selector).

**Don't.**
- Include the agent token in any bundle file.

**Acceptance.**
- [ ] A forced failure produces a complete bundle.
- [ ] A secret-scan test proves the token is absent.

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
- [ ] A purpose-built vacuous test is reported as `VACUOUS`.

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

**Acceptance.**
- [ ] A purpose-built random-outcome test is reported as `FLAKY` with N=5.

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

**Do.**
- Build a Node image with pinned `mineflayer` and `minecraft-data`. The SPI is exposed over WebSocket
  JSON-RPC.
- Item parsing must handle components, including the `stored_enchantments` fix and registry ID → name
  mapping.
- Write the `max-native` value back to the `versions.yml` driver metadata.

**Don't.**
- Use it as a primary driver.

**Acceptance.**
- [ ] C02–C06 pass on the reference driver on 1.21.11 and 26.1.x.

**Verify.**
`./gradlew :drivers:mineflayer-ref:integrationTest`

### T-M4-06 · Differential runner and comparator
- tier: sonnet
- depends: T-M4-05, T-M4-01
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
- [ ] A purpose-built driver-fault injection is reported as `DISAGREE`.

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

**Don't.**
- Let these tests run in normal user-facing suites.

**Acceptance.**
- [ ] The meta-test is green, and therefore every bad test produced its expected verdict.

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
- [ ] The review is written up, gap cards are created, and the measured costs are recorded.

**Verify.**
`./gradlew docsCheck`

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
- Turn the nightly workflow on, covering control, differential and repeat runs.
- Refine the M6 cards.

**Don't.**
- Start M6.

**Acceptance.**
- [ ] The exit criterion is demonstrated.
- [ ] The nightly run is green.

**Verify.**
The nightly workflow run link.
