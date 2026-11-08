# Robustness: risk register and safeguards

Guiding rule: **a green result must mean the behaviour is correct.** Speed is traded away wherever that
helps.

## 1. The six safeguard principles

1. **Dual oracle.** Every assertion is checked through both the server oracle (the agent) and the client
   oracle (the driver). If they disagree, the verdict is `DISAGREE`, which is a harness bug and never a
   pass. ([ADR-0003](adr/0003-dual-oracle.md))
2. **The harness proves itself first.**
   - **Conformance** runs on every cell before plugin tests, and a failing cell makes its plugin
     verdicts `INVALID`.
   - The **control run** catches vacuous tests.
   - The **differential run** catches driver and translation bugs.
   - ([specs/self-tests.md](specs/self-tests.md))
3. **Pin everything, and upgrade only through gated PRs.** ([ADR-0006](adr/0006-pin-everything.md))
4. **No sleeps and no auto-retries; a flake is a bug.** ([ADR-0007](adr/0007-no-sleeps-no-retries.md))
5. **Fail loudly on unknowns.** Missing data is an error, and a skip always has a reason.
6. **One source of truth for versions**, `versions.yml`.
   ([ADR-0008](adr/0008-versions-yml-single-source-of-truth.md))

## 2. Risk register

Likelihood and impact are rated H (high), M (medium) or L (low). The *Detection* column says how the
risk shows up if the safeguard fails anyway.

| # | Risk | L/I | Safeguard | Detection | Residual |
|---|---|---|---|---|---|
| R1 | **ViaProxy translation hides or causes a bug** on bridged cells | M/H | Newest version always runs native (unbridged). **Differential runs** with the reference driver wherever it is native. Conformance includes window and inventory scenarios on every bridged cell. | `DISAGREE` verdict; conformance `INVALID` | Versions with no native driver at all rely on Via's real-world use. The cell is marked *bridged* in reports. |
| R2 | **Vacuous test**: it passes even when the feature is absent | H/H | Nightly **control run** without the jar under test. Paired negative tests (feature disabled → vanilla outcome). An assertion lint rejects tests with only negative assertions. | `VACUOUS` verdict | None for tests that run in the control run. |
| R3 | **Agent bug or observer effect** | M/H | Agents never act during the act phase. Listeners run at MONITOR priority and are read-only. Agent **contract tests** against golden files. **Agent round-trip conformance** on every cell (give, then read, then compare). Responses include raw data next to parsed data. | Conformance `INVALID`; golden-file test failure | Low |
| R4 | **Protocol churn breaks the driver** | H/M | The driver touches about 10 packet types behind one adapter. MCProtocolLib bumps arrive as Renovate PRs, gated by conformance on the newest version and a sample of bridged cells. | Conformance on the bump PR | A few days' gap after a new MC release. The new version stays `experimental` until it is green. |
| R5 | **Timing flakiness** (chunk sends, slot sync, tick races) | H/M | `await(predicate, timeout)` only, with generous timeouts. Agent tick waits. Fixed world setup. Fresh client per test. Nightly **repeat-N** (N=5) flake detector. | `FLAKY` verdict | Low |
| R6 | **State leaks between tests** | M/M | Player and world reset with config snapshot and restore for each test, plus a fresh client. `PER_TEST` isolation is available. A leak detector compares agent state before and after each test. | Leak-detector failure | Low |
| R7 | **Wrong version mapping in the plugin**, for example the wrong NMS module | M/H | A per-cell `expect` hook in the user's suite checks a log-line or agent query (for example, Disenchantment's `NMS module: NMS_v1_21_R5`). Nightly runs cover every Paper version, not just one per module. | Test failure on that cell | None |
| R8 | **Folia threading** | M/H | The agent runs each operation on the owning region's scheduler. Thread checks are never disabled. Log patterns catch thread-check violations. | Error-log failure | Low |
| R9 | **Hidden server errors** | M/M | A per-pack error-pattern list plus an explicit allowlist. Any other WARN or worse fails the test. | `assertNoErrorsLogged` | Allowlist drift, handled by review. |
| R10 | **Environment drift** (OS, Docker, paths) | M/M | No bind mounts; files are copied into containers. Images are pinned by digest. CI is Linux-only for e2e. The repro bundle records host info. | Bundle diff | Low |
| R11 | **Paper/Spigot divergence** (Paper hard fork) | M/M | Spigot is a separate pack built with BuildTools and cached, and runs in the nightly suite. The agent uses only Bukkit API plus capability-detected extras. | Spigot cells fail | Low |
| R12 | **The server download source changes or disappears** | L/H | Downloaded jars are cached by checksum. A mirror is configurable. `versions.yml` stores the checksums. | Checksum mismatch fails the run | Low |
| R13 | **Bedrock unknowns** (auth, protocol) | H/M | Delivered in stages: name-prefix simulation first, then a spike, then a real driver. Never claimed as supported before conformance passes. | — | Until M9 |
| R14 | **Framework too hard to maintain** | M/H | Strict layering. Small modules with READMEs. ADRs. Task cards. AI gates. Platform packs isolated behind an SPI. | Card cycle time, gate failure rate | — |
| R15 | **AI agent produces plausible but wrong code** | H/H | Tiered models. Hard gates (conformance, golden files, control runs). Mandatory higher-tier review on `core`, `protocol` and `drivers`. Card diffs capped around 400 lines. "Never weaken a test" rule. | Gates; review | See [ai/model-tiering.md](ai/model-tiering.md) |
| R16 | **EULA and licence missteps** | L/H | EULA acceptance is an explicit opt-in. No redistribution of server or client binaries. GPL components such as ViaProxy run only as separate containers. | Release checklist | — |

## 3. What cannot be fully removed (stated honestly)

1. **Translation risk on versions with no native driver.** It is reduced by conformance and differential
   runs, and Via is battle-tested by real players, but it can't be zero.
2. **A gap of a few days after each new Minecraft release**, until MCProtocolLib and ViaProxy update. It
   is handled by `experimental` status, so nothing blocks.
3. **Bedrock** stays experimental until its spike and conformance pass.

## 4. Cost of the safeguards (measured in T-M4-08)

These numbers replace the M0 estimates. Sources: GitHub Actions run data (`gh run view`, read only) and one local
`:examples:bad-tests:metaTest` run through the Docker lock on 2026-11-08 (Windows 11, Docker Desktop 29.9.2, one
suite per bad test). Nothing here was triggered on Actions.

**What runs where today**

| Run | Real server? | Where it runs | Measured wall clock |
|---|---|---|---|
| PR `gates` (`./gradlew check docsCheck`) | No (no Docker) | GitHub, `ubuntu-latest` | 2:01, 2:34, 2:43, 2:43, 3:13 (five M4 PR runs) |
| PR `renovate-validate` | No | GitHub | 0:35 to 2:41 (five M4 PR runs) |
| PR `smoke` | Yes | `workflow_dispatch` only; `skipped` on every PR run | not run |
| Nightly set | Yes | **Does not exist.** No `schedule:` and no dispatch job yet. T-M4-99 adds the dispatch job. | not measured |
| Local `:examples:bad-tests:metaTest` | Yes | Local, through `scripts/dockerlock.sh` | 1:16 wall for all five bad tests |

So a PR runs **zero real-server cells** in CI. The M0 row "Per PR, about 7 cells, 5 to 10 min" was wrong and is removed.
Real-server evidence on a PR is local and pasted into the PR.

**Per-test cost, from the JUnit XML `time` attribute of the same local run**

| Bad test (switch) | Cell | Verdict | Testcase time |
|---|---|---|---|
| `InvalidConformanceE2E` (`injectConformanceFault`) | paper@26.3 | INVALID | 34.3 s |
| `VacuousE2E` (`controlRun`) | paper@26.3 | VACUOUS | 40.1 s |
| `LeakE2E` | paper@26.1.2 (bridged) | LEAK | 45.4 s |
| `DriverFaultDisagreeE2E` (`differential`), `mcpl` | paper@26.1.2 (bridged) | first arrival | 63.2 s |
| same, `mineflayer-ref` | paper@26.1.2 | DISAGREE | 1.6 s (shares the server) |
| `FlakyE2E` (`repeat=5`), run 1 | paper@26.3 | first run | 50.7 s (boot) |
| `FlakyE2E`, runs 2 to 5 | paper@26.3 | FLAKY on run 5 | 0.75 to 0.94 s each (shares the server) |

What the numbers say:
- One boot, join and teardown costs about 35 to 50 s per cell and driver run. A bridged cell adds ViaProxy.
- The control run costs the same as a normal run. It needs no extra boot.
- A repeat run reuses the class server, so repeats 2 to 5 cost about 1 s each. Repeat-N measures variance in the test
  body, not in the boot. Boot-level races are not repeated (card T-M4-13).
- The second driver of a differential run adds about 1.6 s on a shared boot.
- The nightly set is not measured. A projection from the rows above is 11 required Paper rows (ADR-0015) times one
  boot, about 40 s each, which is about 8 minutes before the test bodies and the second driver. That replaces the M0
  estimate of 30 to 60 minutes. It stays a projection until a nightly job runs.

**Overlap inside one locked run.** The five bad-test tasks ran concurrently, although the command had `--no-parallel`
and `gradle.properties` sets `org.gradle.parallel=true`. The XML suite start times are 10:22:20.67 to 10:22:20.78 UTC,
within 0.11 s. The testcase times add up to about 240 s, and the whole Gradle run took 76 s. Parallel Testcontainers
boots inside one invocation pass on this host, but AGENTS.md section 4 says one Docker run at a time (card T-M4-13
decides whether to serialise them).

**Release:** nightly plus mutation checks on example projects. Not measured: no mutation check exists yet (self-tests
section 5).

## 5. Review of the register against the build (T-M4-08)

Reviewed at `origin/main` 922bcf4 by reading the code, not the PR descriptions, plus the metaTest run in section 4.
"Built" means the safeguard runs and has a real-server or unit test. "Partly" means part of it is missing, listed in
the gap column. The gap cards are in [plan/tasks/M4-self-verification.md](plan/tasks/M4-self-verification.md), T-M4-09
to T-M4-15. Cards are not implemented here.

| # | Status | What is real | Gap | Card |
|---|---|---|---|---|
| R1 | Partly | Native `mcpl` on 26.3. Bridged cells go through ViaProxy. Differential runs only on paper@26.1.2, the one row where the reference driver is native (`MineflayerRefDriver.PROTOCOL_VERSION` 26.1.2). Bridged bundles now hold a real `viaproxy.log` (metaDisagree and metaLeak, 2.4 KB). | Conformance on bridged cells is only `list`. The window and inventory scenarios (C03 to C08) run by hand in `:conformance:run`. One differential row. | 10, 11 |
| R2 | Partly | Control run works: `metaVacuous` gives VACUOUS. | A single-run VACUOUS writes **no bundle**. The bundle is written before `judgePass` (`CellInvocation.afterEach`); the `VacuousE2E` bundle directory is absent in the metaTest run. No assertion lint and no paired-negative rule exist, although this register says they do. | 09, 14 |
| R3 | Partly | Golden-file contract tests (`AgentContractIT`, M2). Dual oracle on every assertion. | Agent round-trip conformance is not wired to any cell (only `list`). The MONITOR-only observer rule was not re-verified here. | 10 |
| R4 | Partly, manual | A bump PR passes `gates`, which has no Docker. | No real-server gate runs on a bump PR. `smoke` is manual, so conformance on the newest version is a local step. This is a residual risk by design (CI cost). | 10 and a maintainer decision |
| R5 | Partly | Repeat-N works: `metaFlaky` gives FLAKY. | Repeats share the class server, so boot-level races are not repeated. In the XML, repeats 1, 2 and 4 show green testcases, and FLAKY is on repeat 5 only. The flake detector has no nightly job yet. | 09, 13 |
| R6 | Partly | The leak detector gives LEAK for `random_tick_speed` (`metaLeak`). | It reads four world rules only (`GameRules.names`). `GET /v1/snapshot` is missing, so every bundle's `snapshot.json` holds a `NOT_FOUND` error. Player, inventory and config leaks are not seen. | 12 |
| R7 | Not built | None. | No per-cell `expect` hook in `proofstone-api`. | 15 |
| R8 | Not built | None. | No Folia row has booted (ROADMAP M3 open item 5). No thread-check patterns exist. | 15 (before M5) |
| R9 | Partly | `ErrorLogGuard` with a static allowlist, covered by `ErrorLogFailsTheTestE2E`. | No per-pack error-pattern list. | 15 |
| R10 | Partly | No bind mounts. Image digests are pinned. No bundle holds the agent token (checked: only the redaction comment in `rerun.txt`). | The bundle has no host info (OS, Docker, Java). `cell.json` has no ViaProxy digest and no driver pins. | 09 |
| R11 | Not built | Expected at M5. | Spigot pack not built. | M5 |
| R12 | Not reviewed | `Cell.sha256` is recorded. | The download and cache path was not reviewed in this card. | none |
| R13 | Not built | Expected until M9. | No Bedrock code. | M9 |
| R14 | Not assessed | Module READMEs exist. | Maintainability was not measured. | none |
| R15 | Partly | Gates, control run, meta-tests and this review. | `metaTest` asserts at least one failure per task, not per test. `metaFlaky` exempts any failure containing "random draw". Two claims in this file were wrong before this review: the PR row in section 4 and the self-tests spec. | 13 |
| R16 | Built | EULA is an explicit opt-in. Without it, the test skips with a reason (`ProofstoneExtension`). | None found. | none |

**Cross-cutting findings**

- **X1. Skips printed to stderr only.** `NOT DIFFERENTIAL` and `NOT SELECTED` are printed to stderr and never reported
  as JUnit skips. That breaks "a skip is reported as a skip" (AGENTS.md section 3). Cards 11 and 13.
- **X2. The machine-readable report is never written.** `ReportWriter` and `JunitXml` have no caller outside their
  unit tests, so `build/proofstone/proofstone-report.json` does not exist at run time. The T-M4-01 Do list is not met,
  though its acceptance box was ticked. Card 09.
- **X3. Differential compares only assertion points.** `DifferentialRun.compare` returns no disagreement when both
  observation lists are empty. A test with no `DualAssert` cannot be DISAGREE. Card 11.
- **X4. Repro commands drop switches.** `rerun.txt` omits `-Dproofstone.repeat`, `-Dproofstone.controlRun` and
  `-Dproofstone.injectConformanceFault`. Run as written, the FLAKY and INVALID rerun files pass, so they do not
  reproduce the verdict. Card 09.
- **X5. Stale pin.** `versions.yml` has `mineflayer-ref` `max-native: "26.1"`. The driver's protocol is `26.1.2`.
  Card 11.
- **X6. ROADMAP M4 follow-ups.** Item 2 (bridged `viaproxy.log`) now has real evidence from this run. Item 1
  (`GET /v1/snapshot`) is still open (card 12). Item 3's full suite is still open (card 10).
- **X7. FLAKY and VACUOUS semantics.** `FlakyE2E` draws from a seeded generator, so it proves the engine's FLAKY rule,
  not that real timing flakes are caught. VACUOUS needs the control run. A repeat that is `PASS` in some runs and
  `SKIPPED` in others is FLAKY, because a skip is a change of verdict (`VerdictEngine`). That is intended.

**Open decisions for the maintainer**

1. Whether to add a `schedule:` trigger for the nightly set. Actions minutes were cut before (PR #40), so this stays
   a decision. Nothing schedules today.
2. Whether cards 09 to 13 block T-M4-99. They are not in its `depends:`, so the milestone can close without them.
3. Whether the per-cell conformance (card 10) is worth its cost on every cell. Measure it first.
