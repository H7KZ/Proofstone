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

## 4. Cost of the safeguards (estimates, to be measured in M4)

| Run | Contents | Wall clock (parallel jobs) |
|---|---|---|
| Per PR | About 7 cells (newest, one per plugin-relevant version family, Folia). Conformance and dual oracle. | 5–10 min |
| Nightly | Every `required` and `experimental` version, plus differential runs, the control run, repeat-N and Spigot | 30–60 min |
| Release | Nightly plus mutation checks on example projects | 60–90 min |
