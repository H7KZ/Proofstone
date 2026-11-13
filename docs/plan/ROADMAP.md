# Roadmap

Each milestone has an **exit criterion**, a gate that must be demonstrably green before the next
milestone starts. Cards live in [tasks/](tasks/). Model tiers are explained in
[../ai/model-tiering.md](../ai/model-tiering.md).

The order is deliberate: **gates first, features second.** Cheap models become safe only once
conformance, golden files and the verdict engine exist.

| M | Name | Exit criterion | Cards | Haiku / Sonnet / Opus | Estimate (focused dev-weeks, AI-assisted) |
|---|---|---|---|---|---|
| **M0** | Foundation (done, reviewed in T-M0-99) | `./gradlew check` green in CI. `docsCheck` and lint rules enforce the AGENTS.md hard rules. The `versions.yml` schema validates. | 8 | 7 / 1 / 0 | 1 |
| **M1** | Walking skeleton (Paper, newest; done, reviewed in T-M1-99) | On Paper (newest), the server boots from `versions.yml` with the Bukkit agent. The `mcpl` client joins, runs `/version`, and the JUnit test receives the message. Agent `/v1/health` and `/v1/players/{n}` are verified by golden files. | 12 | 8 / 4 / 0 | 2.5 |
| **M2** | Windows and the dual oracle (done, reviewed in T-M2-99) | The Disenchantment anvil scenario passes natively on the newest Paper version with a dual-oracle assertion and an event probe. Conformance C01–C06 pass. The test API is frozen (v0). | 11 | 5 / 6 / 0 | 2 (took about 1 day of agent time, see the M2 review) |
| **M3** | Version span (done, reviewed in T-M3-99) | ViaProxy bridging works. Every Paper row from 1.8.8 to the newest passes C01–C08. `version-watch` opens PRs automatically. | 9 | 5 / 4 / 0 | 2.5 (was 2; T-M3-02 grew, see the M2 review; it took about one day of agent time, see the M3 review) |
| **M4** | Self-verification (exit demonstrated, done, reviewed in T-M4-99; gap cards T-M4-10 to 15 open) | Control run, differential run (mineflayer-ref), flake detector and repro bundles all work. Verdicts `VACUOUS`, `DISAGREE` and `FLAKY` are demonstrated on purpose-built bad tests. | 9 | 4 / 4 / 1 | 2.5 (was 2; more Docker runs and a new Node driver, see the M3 review). Actual: about 15 h wall for PRs #46 to #58 (see the M4 review) |
| **M5** | Bukkit family complete (exit demonstrated, done, reviewed in T-M5-99) | Folia (regions), Purpur and Spigot (BuildTools cache) pass conformance. Capability model done. Bedrock-prefix simulation works. | 8 | 5 / 3 / 0 | 3 (was 2; no Folia row had booted, see the M3 review). Actual: about 7 h wall for PRs #62 to #69 and about 75 min of Docker runs for the review (see the M5 review) |
| **M6** | Dogfood and 0.1 release | Disenchantment's full suite runs on Proofstone in its CI. Maven and Gradle example projects work. Published to Maven Central and GHCR. GitHub Action published. Licence decided. Mutation check passes. | 9 | 6 / 2 / 1 | 2 |
| **M7** | Proxies | Velocity and BungeeCord topologies, with proxy agents, pass conformance. | 15 (refined 2026-11-09, [M7-proxies.md](tasks/M7-proxies.md), awaiting approval) | 1 / 9 / 5 | 3 (was 2; honest range 1.5 to 3, see the card file) |
| **M8** | Mods and data | Fabric and NeoForge agents, multi-version via Stonecutter, plus data pack and resource-pack tests. | epic | refine first (Sonnet/Opus) | 4 |
| **M9** | Bedrock | Spike (offline Bedrock through Geyser). Then a Bedrock driver, the Geyser topology and the BDS Script-API agent. | epic | spike = Opus, then refine | 4 |
| **M10** | Ecosystem | Community pack template, a TS front end (optional), more forks, a docs site, Sponge, PocketMine and others. | epic | refine | ongoing |

**Cards M0–M6:** 66 in total. 40 are Haiku cards (61%), 24 Sonnet and 2 Opus (T-M3-02 moved from Haiku to Sonnet at T-M2-99).

**Total to v0.1 (M0–M6): about 16 focused weeks** (sum of the estimates, M5 run after M4; was 15.5 before the M5 review, see the M5 review). Full ecosystem (M7–M10): about 10+ more weeks.
These are estimates, and they will be re-estimated at the end of each milestone (card `T-Mx-99`).

## Milestone rules
- Every milestone ends with a card `T-Mx-99` (**tier: sonnet**), "Milestone review". It:
  - verifies the exit criterion with gate output;
  - updates this roadmap;
  - re-estimates the remaining work;
  - **refines the next milestone's cards**. Epics become concrete cards in the same format.
- Changes to scope go through this file plus an ADR when they touch a decision.

## Critical path
M0 → M1 (agent + driver login) → M2 (window clicks + dual oracle) → M3 (ViaProxy) → M4 (self-tests) → M6.
M5 can run in parallel with M4 once M3 is done.

## M0 review (T-M0-99)
- **Exit criterion met.** `./gradlew check docsCheck` is green: 0 docsCheck errors, 50 glossary warnings
  (advisory). `check` runs `archTest`, `lintVersions`, `validateVersions` and the build-logic tests, each
  with failing fixtures. Cards T-M0-01 to T-M0-07 are merged.
- **Not yet verified:** the CI run of the gate workflow on a PR, and Renovate's live behaviour. Neither
  can be checked from a local run.
- **Re-estimate:** M0 took about its 1 week. M1 goes from 2 to 2.5 weeks. M0 created only
  `proofstone-api`, `proofstone-core`, `proofstone-protocol` and `conformance`, so M1 must also create the
  `platforms:bukkit:agent`, `drivers:mcpl` and `examples:smoke` modules, an `integrationTest` convention
  and the Spigot repository. The seed `versions.yml` still has `TODO` pins, which T-M1-07 needs first.
  M2 and later estimates are unchanged.
- **M1 card fixes** (T-M1-01 to T-M1-11): real paths for schemas and golden files, module creation steps
  and README duty, the `integrationTest` convention owner (T-M1-07; T-M1-08 now depends on it), the
  `Await` class name required by `archTest`, the core loader's new dependencies, the `smoke`
  placeholder and CI header to replace, and the Spigot API 1.8.8 pinned by its frozen timestamped build
  (a SNAPSHOT would fail `lintVersions`).

## M1 review (T-M1-99)
- **Exit criterion met**, with real runs on Paper 26.3 (one suite at a time, EULA accepted in the command
  environment): `:conformance:smoke` 2/2, `:examples:smoke:test` 2/2, `:platforms:bukkit:agent:integrationTest`
  8/8 (the golden replay), `:proofstone-core:integrationTest` 4/4, `:drivers:mcpl:integrationTest` 1/1, all with 0 skipped.
  `./gradlew check docsCheck` is green (0 docsCheck errors). In words: a JUnit test boots the server from
  `versions.yml`, installs the Bukkit agent, joins with the `mcpl` client, runs `/version` and sees the Paper reply;
  `/v1/health` and `/v1/players/{n}` match their golden files on the real server.
- **CI** (historical: removed 2026-11-08, see ADR-0014 update): `gates`, `smoke` and `renovate-validate` ran on every PR. The `smoke`
  job accepted the Minecraft EULA by the maintainer's instruction and fails (never skips) without it.
- **Bugs only a real server found** (each fixed and merged): the agent reported `26.3.build.238` as the Minecraft
  version; the agent's proxy `CommandSender` could not run vanilla commands; the golden enchant level 32767 is stored
  as 255 by Paper; `reset` put the player on a block corner. Lesson, now in the M2 cards: every endpoint card needs a
  real-server test, not only fakes.
- **Open limits** (not decided here):
  - `versions.yml` has one host digest per platform but itzg digests are per Java tag, so only the Paper 26.x rows
    can run. The fix is a spec change; it is written into card T-M3-02.
  - Only the Paper 26.3 row is fully pinned (`build`, `sha256`). The `folia` host digest and the other rows are `TODO`.
  - The `Docker missing` skip path of the JUnit extension and a real `kick` are not covered by a test.
  - The `docsCheck` glossary warnings are advisory and count every `.claude` worktree if present locally; they do
    not appear in CI.
- **Re-estimate**: M1 was estimated at 2.5 focused weeks. All 12 cards plus 5 follow-up fixes merged in about one
  day of agent time, with a human only accepting the EULA and restarting Docker. The cost was not the coding but
  verification: the real-server runs found four bugs that unit tests did not. M2 and later estimates stay as they
  are, because M2 adds the harder driver work (windows, clicks, item parsing) and each card needs a real-server test.
- **M2 cards refined** with the real package, class and task names and the M1 lessons (see the "What M1 produced"
  section at the top of `tasks/M2-windows-dual-oracle.md`).

## M2 review (T-M2-99)
- **Exit criterion met**, with real runs on Paper 26.3 (the only pinned row), one suite at a time through the Docker lock,
  EULA accepted in the command environment only. Every XML below has `skipped="0" failures="0" errors="0"`:
  - Disenchantment anvil scenario passes natively with a dual-oracle assertion and an event probe: `:examples:disenchantment-anvil:test`
    1/1 (`AnvilDisenchantE2E`: `DualAssert.item` and `DualAssert.level` on both oracles, `agent.events().subscribe(PostDisenchantEvent)`
    reads `xpCost`, the level equals 30 minus `xpCost`, no logged errors). T-M2-10 also ran it 10 times in a row.
  - Conformance C01 to C06: `:conformance:run` 6/6 (C03, C04, C05, C06 one test each, `ConformanceSmokeTest` 2) and `:conformance:smoke` 2/2.
  - Test API v0 frozen: `proofstone-api/api-baseline.txt` is the contract, `ApiSurfaceTest` guards it (green in `check`), and
    `docs/specs/test-api.md` section 8 lists the frozen surface, the experimental parts, the deviations and the post-v0 backlog.
  - Other suites: `:examples:smoke:test` 11/11, `:platforms:bukkit:agent:integrationTest` 21/21 (`AgentContractIT`, the golden
    replay), `:drivers:mcpl:integrationTest` 5/5, `:proofstone-core:integrationTest` 4/4.
  - `./gradlew check docsCheck` is green: 0 docsCheck errors (227 advisory glossary warnings, which count every `.claude` worktree locally).
  - Timings are in `docs/platforms/paper.md`, "Timings": one cell boot, join and teardown is about 29 s.
- **CI**: the `ci` workflow on `main` succeeded for the merges of PRs #30 to #33 (checked with `gh run list`). Cards T-M2-01 to T-M2-10
  are merged as PRs #24 to #33.
- **API v0 freeze** (no code changed in this card). Frozen: the annotations, `Cell`, `Agent`, `Client`, `PlayerHandle`, `PluginHandle`,
  `Events`/`EventProbe`, `Window`, `Click`, `Item`, `ItemView`, `DualAssert`, `DisagreeException`. Experimental inside v0: the `spi`
  package, `serverOnlyReasons()`, record constructors that will gain components, `Window.close()` semantics. Not in v0 (post-v0 backlog):
  `Agent.world`, `awaitTicks`, `logLines`, an explicit `assertNoErrorsLogged`, `gamemode`, `PlayerState.location` and `openWindow`,
  experience points, `Item.repairCost`/`pdc`, `Clients`, `Topology`, the world fixture region reset, the leak detector, `@Requires`.
- **Bugs only real runs found** (each fixed in its card): the agent did not read `storedEnchants`, `lore` or `damage` (a `DISAGREE` in
  `DualOracleE2E`); `Enchantment.getName()` misses newer enchantments (`MENDING`); MCProtocolLib ignores a teleport until it is
  accepted, so `useBlock` did nothing; a test that teleported before `reset` was kicked for flying; a client close is not acknowledged,
  so window 0 was stale; the config restore through `disablePlugin`/`enablePlugin` broke Disenchantment's classloader (restore now
  reloads the config only); a second join within 4 s was throttled; `setblock` before a chunk loads silently does nothing; the log
  check needed Log4j as well as JUL, and then saw the anti-cheat `moved too quickly!` WARN.
- **Open limits** (not decided here):
  - **GitHub issue #23 (open): host digest per Java tag.** Only the Java 25 tag is pinned, so only Paper 26.x rows can run. Card T-M3-02.
  - **Only the Paper 26.3 row is pinned** (`build`, `sha256`); the Folia digest and all other rows are `TODO`. Card T-M3-01.
  - No run-time cell selector (`-Dproofstone.cells` exists only in the docs) and no bridging (`Cell.bridged()` is always false). Cards T-M3-04, T-M3-03.
  - The agent has no `openWindow` endpoint, so the chest contents (C04) and the card T-M2-04 box "slot contents match the agent's
    `openWindow` view" stay unticked; it needs an `api-change` card (post-v0 backlog).
  - Not covered by a test: the `Docker missing` skip path of the JUnit extension, a real `kick`.
  - Every scenario was observed on 26.3 only; the anvil costs and the 255 enchant cap are unverified on other versions.
- **Re-estimate.** M2 was estimated at 2 focused weeks. All 11 cards (PRs #24 to #33 plus this review) took about one day of agent time
  with a human accepting the EULA; most of the cost was real-server verification, as in M1. One serial gate pass is about 670 s of suite
  time. M3 goes from 2 to 2.5 weeks: it needs the per-Java-tag digest spec change (moved from Haiku to Sonnet), pins for every row, and
  the ViaProxy topology, which are the first work on versions other than 26.3. M4 onward are unchanged. Total to v0.1: about 14 weeks.
- **M3 cards refined** (see the "What M1 and M2 produced" section at the top of `tasks/M3-version-span.md`): the cell selector
  (T-M3-04) is now a dependency of the cards that use `-Dproofstone.cells`; T-M3-03 depends on the pins and digests; the `--tests`
  filters use real Gradle syntax.

## M3 review (T-M3-99)
- **Exit criterion: green on the Paper span**, from the XML of real runs, parsed per row (`testsuite` `skipped`, `failures`,
  `errors`; skip reasons read from the XML). One row per Gradle invocation, one suite at a time through the Docker lock,
  EULA accepted in the command environment only. Every row 1.8.8 to 26.3 passes C01 to C08 except C07 on 1.8.8 and 1.12.2,
  which is a reasoned skip (no grindstone before 1.14; the skip reason is in the XML). No failure, no error, no other skip.
  Result: 88 test methods (8 per row), 86 pass, 2 skip. Ten rows run bridged through ViaProxy (the log says `(bridged)`);
  26.3 is native.

  | Row | Bridged | C01 | C02 | C03 | C04 | C05 | C06 | C07 | C08 |
  |---|---|---|---|---|---|---|---|---|---|
  | 1.8.8 | yes | PASS | PASS | PASS | PASS | PASS | PASS | SKIP (no grindstone) | PASS |
  | 1.12.2 | yes | PASS | PASS | PASS | PASS | PASS | PASS | SKIP (no grindstone) | PASS |
  | 1.16.5 | yes | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
  | 1.17.1 | yes | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
  | 1.18.2 | yes | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
  | 1.20.4 | yes | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
  | 1.20.6 | yes | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
  | 1.21.11 | yes | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
  | 26.1.2 | yes | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
  | 26.2 | yes | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
  | 26.3 | no (native) | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |

  C01 and C02 come from `ConformanceSmokeTest`, C03 to C08 from one class each. Logs: `m99/row-<v>.log`, XML: `m99/xml-<v>/`
  (scratchpad of the review agent, not committed).
- **Gates** (local, real output, XML checked `skipped="0" failures="0" errors="0"` on every suite): `./gradlew check docsCheck`
  BUILD SUCCESSFUL, `docsCheck: 0 errors, 253 warning(s)` (259 on a clean, uncached rerun) (some `check` tasks came from the build cache). `:conformance:smoke`
  2/2. `:drivers:mcpl:integrationTest` 5/5. `:platforms:bukkit:agent:integrationTest` 67/67 (1.8.8, 1.12.2, latest).
  `:proofstone-core:integrationTest` 4/4. `:examples:smoke:test` 11/11. `:examples:disenchantment-anvil:test` 1/1.
- **What shipped**: PR #35 (T-M3-04, cell selector, scope and version families), #36 (T-M3-01, `version-watch` fills
  `versions.yml`), #37 (T-M3-07, CI matrix from `versions.yml`), #38 (T-M3-02, one host digest per Java tag, closes #23,
  ADR-0013), #39 (T-M3-03, ViaProxy bridged topology), #40 (ci: smoke manual, no schedules, to cut billed minutes),
  #41 (T-M3-05, C07 and C08), #42 (T-M3-06, Bukkit agent legacy compatibility, 1.8.8 to 1.12.2), #43 (T-M3-08, version-span
  gotchas). The review itself is this PR.
- **Bugs only real runs found** (each fixed in its card; full list with commits in `docs/platforms/paper.md` and
  `docs/platforms/viaproxy.md`): the itzg digest differs per Java tag (issue #23); `level-type` must be `flat` before 1.18,
  and 1.8.8 silently builds a default world otherwise; `setblock` outside a loaded chunk changes nothing on 1.8.8; Paper
  1.8.8 is PaperSpigot and the agent reported `spigot`; the item name `sugar_cane` resolved to `sugar_cane_block` on 1.18.2
  (C08 DISAGREE) until a legacy material table; legacy block states are data values; Log4j runs async on 1.12.2, so one log
  record arrived twice; a pre-1.17 window locks after a rejected click until the client acks the ping; mending is absent on
  1.8.8 (a wrong test, fixed); closing an anvil on 1.8.8 drops its input slots; Docker answered 500 once (26.1.2, passed on
  rerun). Game rules: the card said 26.1 for snake_case, the run says 1.21.11.
- **Deviations from the cards**:
  - C07 on 1.8.8 and 1.12.2 is a reasoned skip, not a pass.
  - T-M3-02 changed the pin shape: ADR-0013 (one digest per Java tag) supersedes the single `host.digest` of ADR-0008.
  - The `paper@all` verify command was not run as one invocation. Each row ran alone (Docker stability, one XML per row).
    The same cells ran; the card's command was not run literally.
  - The smoke, matrix and version-watch workflows are manual-only since PR #40 (the maintainer cancelled the billed smoke
    workflow). Branch protection requires only the gates and `renovate-validate`.
  - The version-span gotchas link their merged PRs, not CI runs (T-M3-08): CI smoke is manual, so there is no CI run.
  - The branch `card/T-M3-99-milestone-review` was still checked out in an older, locked worktree, so this review is on
    `card/T-M3-99-milestone-review-2`. The row runs were made in that older worktree at `1f84924`, which is `origin/main`.
- **Open items for the maintainer**:
  1. `VERSION_WATCH_TOKEN` secret: PRs opened with the default token do not trigger CI. Without it, version-watch PRs
     need a manual push to run gates.
  2. Per-Java-tag digests are re-pinned by hand; `updateVersions` does not refresh them. The `java25` digest is stale
     against the moving tag.
  3. Resolved after this review (PR #46): the zero-cell decision is covered by `ProofstoneExtensionPlanTest`.
  4. Resolved after this review (PR #46, ADR-0015): the 11 Paper rows are `required` on local per-row evidence.
  5. Folia rows never booted. PDC is not advertised on any version. Paper 1.8.8 reports as `spigot`.
  6. C06 on 1.8.8 failed about 1 run in 4 before PR #39. The one passing run here is not proof it is flake-free.
  7. Anvil block-state rows in `legacy-block-states.csv` come from the wiki and were not checked against a client.
  8. 2026-11-07, independent re-run on `main`: C02 on paper@1.20.4 (bridged) failed once (client join timed out after 30 s)
     and passed on the immediate rerun; all other rows and tests passed first time. A rare join flake: not retried by
     the harness, not diagnosed yet. A repeat run (T-M4-04 flake detector) should pin it down.
  9. Every scenario beyond the anvil was observed on the Paper rows only (no Folia, Purpur or Spigot yet).
- **Re-estimate.** M3 was estimated at 2.5 weeks; it took about one day of agent time plus the maintainer's gating (PRs
  #35 to #43 merged between 09:58 and 16:34 UTC on 2026-11-07). M4 goes from 2 to 2.5 weeks: more Docker runs (repeat=5
  multiplies each test), a new Node driver, and the reference driver's protocol must be checked first. M5 goes from 2 to
  3 weeks: no Folia row has booted, Spigot builds take minutes, and the Folia pack's thread-check patterns are new.
  Total to v0.1 is about 15.5 weeks as a sum of the estimates (was 14).
- **M4 and M5 cards refined** (`tasks/M4-self-verification.md`, `tasks/M5-bukkit-family.md`): bundles name the cell
  selector and are not committed; the real C06 flake is a measurement target, not the acceptance test; the reference
  driver's protocol is checked first; the nightly set and Spigot rows are manual (no schedule); Folia rows boot first;
  every cell runs in its own invocation with an XML check.
- **M4 follow-ups from T-M4-02 (repro bundles, for the T-M4-99 review):**
  1. The agent has no `GET /v1/snapshot` yet, so `snapshot.json` records the error instead of the agent state. Add the
     endpoint to the agent protocol (a card that changes the spec), then the bundle carries real state.
  2. Bridged `viaproxy.log` is unexercised: no bridged row runs in the M4 smoke, so the bundle path for it has no real-server
     evidence. Run a bridged forced failure (for example `paper@1.20.4`) before M4 closes.
  3. Known gap from T-M4-03 (control run): the JUnit extension passes `ConformanceResult.pass(cell)` as an assumption
     (`VerdictGate.judgePass`), so `INVALID` cannot fire from the extension. No runner feeds `ConformanceCache` yet.
     T-M4-07 must wire a conformance runner for its invalid test before M4 closes.
     **Resolved in T-M4-07:** the extension runs a conformance check per cell (`CellConformance`, one scenario so far)
     through `ConformanceCache`. The full C01 to C12 suite is still not wired.

## M4 review (T-M4-99)
- **Exit criterion met.** Control run, differential run, flake detector and repro bundles work, and VACUOUS, DISAGREE and
  FLAKY are each shown on a purpose-built bad test. Evidence (local, real server, EULA in the command environment only,
  every run through `scripts/dockerlock.sh --no-parallel`, Java 21):
  - `:examples:bad-tests:metaTest` BUILD SUCCESSFUL, "every bad test produced its expected verdict (VACUOUS, FLAKY, DISAGREE,
    INVALID, LEAK)", 1:39. Per task XML: `metaVacuous` 1 test, 1 failure (VACUOUS); `metaFlaky` 5 tests, 2 failures (FLAKY on
    run 5, runs 3 and 5 fail on the draw); `metaDisagree` 2 tests, 1 failure (`mcpl` PASS, `mineflayer-ref` DISAGREE on
    paper@26.1.2); `metaInvalid` 1 failure (INVALID); `metaLeak` 1 failure (LEAK). All `skipped="0" errors="0"`.
    Merged report: `examples/bad-tests/build/proofstone/proofstone-report.json` (green=false by design: it holds the expected
    failures).
  - Control run `:examples:bad-tests:test -Dproofstone.controlRun=true`: `VacuousE2E` VACUOUS (FAILED by design); `FlakyE2E`
    also VACUOUS, since its first draw passes with no plugin. 1:09.
  - Repeat run `-Dproofstone.repeat=5`: `FlakyE2E` FLAKY, "verdicts differ across 5 runs: PASS, FAIL". 1:21.
  - Differential run `-Dproofstone.differential=true`: `DriverFaultDisagreeE2E` DISAGREE on paper@26.1.2. 0:58.
  - Bundles: `rerun.txt` names the switches; `viaproxy.log` present in the bridged bundles of `DriverFaultDisagreeE2E`
    and `LeakE2E`. `snapshot.json` still holds `NOT_FOUND` (T-M4-12).
  - `:conformance:smoke` 2/2, `skipped="0" failures="0" errors="0"` (33.6 s test time).
  - `./gradlew check docsCheck` BUILD SUCCESSFUL, `docsCheck: 0 errors, 319 warning(s)` (advisory glossary warnings).
- **What merged**: T-M4-01 verdict engine (PR #49), T-M4-02 repro bundles (commit on `main`), T-M4-03 control run (PR #52),
  T-M4-04 flake detector (PR #54), T-M4-05 and T-M4-05b mineflayer-ref (PRs #50, #53), T-M4-06 differential (PR #55),
  T-M4-07 bad tests and metaTest (PR #56), T-M4-08 self-test review and costs (PR #57), T-M4-09 bundles and report
  (PR #58). Merged PRs #46 to #58 ran from 2026-11-07 21:21 to 2026-11-08 12:23 UTC.
- **Nightly set (new, `.github/workflows/nightly.yml`)**: `workflow_dispatch` only, no `schedule:`. One job runs
  `:examples:bad-tests:metaTest`, which runs the control run, the repeat run, the differential run, INVALID and LEAK, and
  asserts each verdict. Java 21, EULA from the repository variable `PROOFSTONE_ACCEPT_EULA` (same mechanism as the matrix
  job), repro bundles uploaded on every outcome. Pins are the ones `ci.yml` already uses.
  - **Cells, and why.** `metaTest` takes the cells its bad tests pin, so it runs paper@26.3 (native; control, repeat,
    INVALID) and paper@26.1.2 (bridged for `mcpl`; the reference driver's protocol row, so the differential run; LEAK).
    The brief said 26.1.2 is native: that is true only for `mineflayer-ref`. Its `mcpl` driver is bridged.
    No other Paper row is needed: a bridged control or repeat on 1.8.8 would need the cell selector forwarded to the
    `metaTest` tasks, which `badTestSetup` does not do today, plus a local run to prove it. Left as a maintainer decision.
  - **Not verified:** one manual `workflow_dispatch` run was NOT triggered (maintainer rule: no manual workflow
    triggers). So the card's acceptance "one manual run green, linked from this review" is **UNVERIFIED**. No run link
    exists. The YAML was checked with PyYAML (triggers are `workflow_dispatch` only); `actionlint` is not installed here.
    Maintainer: run it once from the Actions tab (or `gh workflow run nightly.yml`), then link the run here. Set the
    repository variable `PROOFSTONE_ACCEPT_EULA` first, or the job fails (it never skips).
- **Costs** (robustness.md section 4): local metaTest 1:39, smoke 0:38, switch runs 0:58 to 1:21. CI nightly is not measured;
  projection 5 to 10 min on a cold runner.
- **Open items (not done here)**:
  1. Gap cards T-M4-10 to T-M4-15 are open (not in T-M4-99's `depends:`): per-cell conformance (R1, R3, R4), differential
     integrity and the stale `mineflayer-ref` `max-native` `"26.1"` (X1, X3, X5), agent snapshot and wider leak detector
     (R6, ROADMAP follow-up 1), harness hardening (R5, R15), assertion lint or retire the R2 claim, per-cell `expect` hook and
     per-pack error patterns (R7, R8, R9).
  2. ROADMAP follow-up 2 (bridged `viaproxy.log`) has evidence now (`DriverFaultDisagreeE2E`, `LeakE2E` bundles).
  3. ROADMAP follow-up 3 is partly open: `INVALID` covers one scenario per cell, not C01 to C12 (T-M4-10).
  4. `ci.yml` `smoke` and the matrix job set up Java 17, but the `conformance` and `mcpl` toolchains need Java 21. The
     nightly job uses 21. The smoke path has not run on Actions (manual only), so this is unverified, not fixed.
  5. The `schedule:` question and the bridged 1.8.8 cell are maintainer decisions (below).
- **Maintainer decisions**:
  1. **Schedule the nightly set or not.** Recommendation: not yet. Keep it manual until the first manual run is green and the
     costs are measured on Actions. A schedule needs the billed-minutes approval (PR #40).
  2. **Do T-M4-10 to T-M4-15 block the next milestone?** Recommendation: T-M4-15 (per-pack error patterns, Folia thread checks,
     R8) blocks M5's Folia cards (T-M5-02, T-M5-03). T-M4-11 is small and its version fix should land before M5. T-M4-10 and
     T-M4-12 block M6 (the release claim depends on conformance per cell and the real snapshot). T-M4-13 and T-M4-14
     can wait. The milestone exit criterion does not need any of them.
  3. **Per-cell conformance cost (T-M4-10).** Not measured yet. Recommendation: run C03 (agent round trip) on every cell and the
     window scenarios C04 to C08 on bridged cells only, as the card says. Measure the added time on one cell first, then record
     it in robustness.md section 4.
  4. **Bridged 1.8.8 control or repeat cell.** Optional. Needs the cell selector forwarded to the `metaTest` tasks first.
- **Re-estimate.** M4 was estimated at 2.5 weeks. Merged PRs #46 to #58 spanned about 15 hours of wall clock (2026-11-07
  21:21 to 2026-11-08 12:23 UTC), with the human gating the Docker runs. M5 stays at 3 weeks: Folia has never booted, and T-M4-15 may come first. M6 stays at 2 weeks,
  and grows if T-M4-10 and T-M4-12 block it. Total to v0.1 is about 15.5 weeks as a sum of the estimates.
- **M5 cards refined** (`tasks/M5-bukkit-family.md`, "What M1 to M4 produced"). The next milestone is M5, not M6, so the
  card's "refine the M6 cards" wording is a typo for M5. The M6 cards are refined in the M5 review (T-M5-99), as the roadmap
  rules say. M6 was not started.

## M5 review (T-M5-99)
- **Exit criterion: met, with one recorded flake.** Folia (4 rows), Purpur (2 rows) and Spigot (3 rows) pass C01 to C08.
  The capability model is done (T-M5-01). Bedrock-prefix simulation works on **native Paper only** (`paper@26.3`). Purpur and
  Spigot 26.3 are native but do not declare `SIMULATED_BEDROCK`, so C-B01 skips there with the reason.
  Evidence (local, real servers, JDK 21, EULA in the command environment only, every Docker run through
  `scripts/dockerlock.sh --no-parallel --max-workers=1`, one invocation per cell). XML read per suite: `skipped` and `failures`
  counted, skip reasons read from the XML.
- **Gates** (all `BUILD SUCCESSFUL`):
  - `./gradlew check docsCheck`: `docsCheck: 0 errors, 356 warning(s)`.
  - `:conformance:smoke`: `ConformanceSmokeTest` 8 tests, 0 skipped, 0 failures, 0 errors.
  - `:examples:bad-tests:metaTest`: "every bad test produced its expected verdict (VACUOUS, FLAKY, DISAGREE, INVALID, LEAK, FAIL,
    SKIPPED)". Its task XML holds 14 tests with 7 failures and 1 skip **by design** (the bad tests fail on purpose; the meta
    assertions pass). Not a red gate.
  - `:platforms:bukkit:agent:integrationTest` 23/23. `:drivers:mcpl:integrationTest` 5/5. `:proofstone-core:integrationTest` 4/4.
  - `:examples:smoke:test` 13/13. `:examples:disenchantment-anvil:test` 1/1.
- **Per-cell conformance** (one `:conformance:run -Dproofstone.cells=<cell>` per row; the run-level XML, all `errors="0"`):

  | Row | Bridged | Tests | Failures | Skips (reason) |
  |---|---|---|---|---|
  | `folia@1.21.11` | yes | 11 | 0 | 1 (C-B01: missing SIMULATED_BEDROCK) |
  | `folia@26.1.2` | yes | 11 | 0 | 1 (C-B01) |
  | `folia@26.2` | yes | 11 | 0 | 1 (C-B01) |
  | `folia@26.3` | no (native) | 11 | 0 | 1 (C-B01) |
  | `purpur@latest` (26.3) | no (native) | 11 | 0 first run, 1 red then green (see flake) | 3 (C-B01, C-F01, C-F02: capability) |
  | `purpur@1.21.11` | yes | 11 | 0 | 3 (capability) |
  | `spigot@1.21.11` | yes | 11 | 0 | 3 (capability) |
  | `spigot@1.8.8` | yes | 11 | 0 | 4 (C07 reasoned: no grindstone before 1.14; 3 capability) |
  | `spigot@26.3` | no (native) | 11 | 0 | 3 (capability) |
  | `paper@26.3` (regression) | no (native) | 11 | 0 | 2 (C-F01, C-F02: FOLIA_REGIONS) |
  | `C-B01` on `paper@26.3` (`--tests`) | no (native) | 1 | 0 | 0 (PASS) |

  The Folia rows ran C-F01 and C-F02 and passed them (no capability skip). Bridged per the card: `folia@1.21.11`,
  `folia@26.1.2`, `folia@26.2`; native: `folia@26.3`.
- **Flake (finding, not a pass)**: `purpur@latest` (resolves to 26.3) first run failed `C06AnvilCombineTest`:
  `ClientDriverException`, `timed out joining ... after PT30S` (`Await`). Rerun, same command, alone: passed (rc 0,
  0 failures). One red, one green on the same row: recorded as flaky. Not retried by the harness. Root cause not diagnosed.
  Same family as the M3 join flake (M3 review, item 8) and the spigot@1.21.11 C06 red in `docs/platforms.md`.
- **Bugs only real runs found** (full list in `docs/platforms/folia.md`, `purpur.md`, `spigot.md`):
  - Folia `TickCounter` used the Bukkit scheduler and had to move to the region scheduler (T-M5-02).
  - On Folia, `PlayerTeleportEvent` is not fired by `tp` or reset, so C-F02 uses a gamemode change instead (T-M5-03).
  - Folia logs `moved too quickly` as WARN in the test window: each pack needs its own narrow allowlist (T-M4-15).
  - Spigot 1.8.8 BuildTools jar is not byte-reproducible: Maven writes a build-time line into `pom.properties`. Fixed with
    `JarDigest` (content digest, minus that line) (T-M5-05).
  - Prefixed Bedrock login stalls at ViaProxy on bridged cells. Cause unknown. Not a claim of Bedrock support.
- **Deviations from the cards**:
  - The Spigot 1.8.8 and 26.3 rows were built from source by BuildTools; first-build time is about 3 to 5 min each (T-M5-05).
  - `SIMULATED_BEDROCK` is declared by the Paper pack only. The T-M5-06 acceptance is met on `paper@26.3`; the card's
    "native cells" reading is therefore Paper only, not every native row.
  - `ConformanceSmokeTest` has 8 test methods in the XML, not the 2 the M4 review listed. Not investigated here.
- **Open items (maintainer decisions)**:
  1. **ADR-0015: promote Folia, Purpur and Spigot rows from `experimental` to `required`?** The rows pass once each here, and
     purpur@26.3 has one red run. The current `docs/platforms.md` status evidence says "not promoted". Recommendation: keep
     `experimental` until purpur@26.3 C06 is diagnosed or shown flake-free over N repeats.
  2. **`Cell.capabilities()` is an abstract method on a frozen interface.** Source-breaking for external implementers. Decide
     whether to make it a default method. Recommendation: default method, in an `api-change` PR, before M6 publishes to Maven Central.
  *Resolved (fix/m5-capability-api): default method, throws unless overridden.*
  3. **Capability tokens NMS and DATAPACKS have no agent token.** They are declared but cannot be reported by the agent.
  *Resolved: removed, re-add with an agent token when a test needs it: M6 NMS uses the per-cell `expect` hook, M7 DATAPACKS_VANILLA.*
  4. **Prefixed Bedrock login stalls at ViaProxy on bridged cells.** Cause unknown. C-B01 runs native only (Paper 26.3).
  5. **Unrun rows**: Purpur 1.14 to 1.20.x and Folia 1.19.4 to 1.21.10. Not run; not claimed.
  6. **Nightly schedule**: still `workflow_dispatch` only. The repository variable `PROOFSTONE_ACCEPT_EULA` and the first manual
     nightly run remain undone by the maintainer (M4 review).
  7. **CI cost (PR #68)**: `gates` and `renovate-validate` are the only PR checks. Option: drop `gates` from required checks
     and rely on local gate evidence. Decision open.
- **Re-estimate.** M5 was estimated at 3 weeks. Merged PRs #62 to #69 spanned about 7 h wall clock (12:21 to 19:10 UTC
  on 2026-11-08), and the review's Docker runs took about 75 min. M6 goes from 2 to 2.5 weeks: T-M4-10 and T-M4-12 block the
  release claim (see M6 refinement below), and the Maven Central and Disenchantment CI steps depend on external accounts. M7
  onward unchanged. Total to v0.1 is about 16 weeks as a sum of the estimates.
- **M6 cards refined** (`tasks/M6-dogfood-release.md`, "What M1 to M5 produced"). T-M4-10, T-M4-12 are open and block the
  0.1 release claim; T-M4-13 and T-M4-14 are open and can wait. M6 was not started.

## M5 follow-up evidence (`fix/m5-evidence-promotion`, 2026-11-08)
Maintainer-approved follow-up to the M5 review. Same local rules: real servers, EULA in the command environment only,
one Docker invocation per row, JUnit XML read per run (`skipped`, `failures`, `errors`), no test weakened, no retry
counted as a pass.

**A. C06 flake measurement** (`:conformance:run -Dproofstone.cells=<row> -Dproofstone.repeat=10 --tests '*C06AnvilCombineTest*'`,
one repeat of 10 per row, boot shared by the repeats):

| Row | Repeats | Result | Earlier red on record |
|---|---|---|---|
| `purpur@26.3` (native) | 10 | 10 passed | M5 review first run: `ClientDriverException: timed out joining ... after PT30S` (the join; `McplDriver.connect`, last state JOINING). Not diagnosed. Not reproduced in 10 repeats. |
| `spigot@1.21.11` (bridged) | 10 | 10 passed | T-M5-05 first run: `DISAGREE: present in the inventory` after 10119 ms. Not diagnosed. Not reproduced in 10 repeats, or in the 6 earlier passes. |
| `folia` | 0 | not run | folia@1.20.6 full runs: join stall (C-F02) once, C07 cursor assertion once. See below. |

**B. Older rows.** `updateVersions` adds only versions newer than a platform's newest row, so both older rows were pinned
by hand with the same checks (md5 from the Purpur API, fill sha256, protocol from the jar's `version.json`, Java from the
java-rules table; `lintVersions validateVersions` clean).
- `purpur@1.20.6` (build 2233, bridged, java21): **added and promoted.** Two full runs, both green.
- `folia@1.20.6` (build 6, channel ALPHA, bridged, java21): **not added.** Two full runs, both red, on different tests:
  run 1 C-F02 join timeout (no login, nothing at ViaProxy); run 2 C07 `Expecting value to be true but was false`
  (the bare sword on the cursor). Recorded in `docs/platforms/folia.md`.

**C. ViaProxy prefixed-login stall.** Time-boxed. `cli --help` on the pinned image has no username or Bedrock option,
and the image bundles ViaBedrock. Not reproduced: a run needs C-B01 on a bridged cell, which the capability model does
not allow. No fix. `SIMULATED_BEDROCK` stays native-only. Recorded in `docs/platforms/viaproxy.md` (Gotchas).

**D. Promotions (ADR-0015).** Rule: full per-row run green with C01 to C08 all run, two independent green runs, no
unexplained flake in the platform's C06 repeat.

| Row | Bridged | Green full runs | C06 repeat | C01 to C08 | Skip reasons | Promoted |
|---|---|---|---|---|---|---|
| purpur 1.20.6 | yes | 2 (this PR) | on purpur@26.3: 10/10 | all pass | C-F01, C-F02, C-B01 (capability) | yes |
| purpur 1.21.11 | yes | 2 (T-M5-04, M5 review) | 10/10 (platform) | all pass | C-F01, C-F02, C-B01 (capability) | yes |
| purpur 26.3 | no | 2 (T-M5-04, this PR) + M5 review red then green | 10/10 | all pass | C-F01, C-F02, C-B01 (capability) | yes (see note) |
| spigot 1.8.8 | yes | 3 (T-M5-05 x2, M5 review) | 10/10 (platform) | all pass | C07 (no grindstone before 1.14), C-F01, C-F02, C-B01 | yes |
| spigot 1.21.11 | yes | 2 (M5 review, this PR) + T-M5-05 red | 10/10 | all pass | C-F01, C-F02, C-B01 (capability) | yes (see note) |
| spigot 26.3 | no | 2 (T-M5-05, M5 review) | 10/10 (platform) | all pass | C-F01, C-F02, C-B01 (capability) | yes |
| folia 1.21.11, 26.1.2, 26.2, 26.3 | 1.21.11, 26.1.2, 26.2 yes; 26.3 no | 1 each (M5 review) | none on folia | all pass | C-B01 (capability) | **no**: no folia C06 repeat, and folia 1.20.6 red twice |
| folia 1.20.6 | yes | 0 (2 red) | — | — | — | not added |

Notes, so the reader can judge the two marked rows:
- purpur 26.3: the M5 review's first run was red (join timeout), and the C06 rerun was green. The promotion relies on the
  10/10 repeat and two green full runs. The original red is not explained.
- spigot 1.21.11: one red full run (T-M5-05) is not explained. The promotion relies on 10/10 repeats and two green full
  runs. A maintainer may read ADR-0015 differently.

**Full-run XML this PR.** `purpur@26.3`: 10 suites, all `skipped` 0 or 1 (reasoned capability skips), `failures=0`,
`errors=0`. `spigot@1.21.11`: same. The new `purpur@1.20.6` runs: the same shape. `folia@1.20.6` run 2: 1 `failures=1`
(C07) and run 1: 1 `failures=1` (C-F02). Bundle paths for the red runs are under `conformance/build/proofstone/`.

**`printMatrix --scope=pr`**: plans **17 cells** (was 11): paper 11 (unchanged), purpur 3 (1.20.6, 1.21.11, 26.3),
spigot 3 (1.8.8, 1.21.11, 26.3), folia 0.

**Open items, after this follow-up** (the M5 review's numbering):
1. ADR-0015 promotion: **partly resolved.** Purpur (3 rows) and Spigot (3 rows) promoted. Folia (4 rows) still
   `experimental` (no Folia C06 repeat, and folia 1.20.6 red twice). Folia needs a repeat run and the 1.20.6 join and
   C07 reds explained or shown to be flake before any Folia promotion.
2. `Cell.capabilities()` default method: resolved in the M5 review (PR #73). No change here.
3. NMS and DATAPACKS tokens: resolved in the M5 review. No change here.
4. Prefixed Bedrock login through ViaProxy: **still open.** Investigated, not reproduced, no fix (see C above).
5. Unrun rows: **partly resolved.** Purpur 1.20.6 added and run. Folia 1.20.6 tried and not added. Still unrun: Purpur
   1.14 to 1.19.x; Folia 1.19.4 to 1.21.10 (1.20.6 included, see above).
6. Nightly schedule: **open**, untouched here.
7. CI cost (PR #68): **open**, untouched here.

## M6 pre-release findings (T-M4-12b verification, 2026-11-09)

Open findings, not passes. Each needs a maintainer call or a diagnosis before M6 release.

1. **T-M4-12 XP-LEAK bad test not added (deviation).** The card's bad test (a test leaves a player's XP changed, expect `LEAK`)
   is not reachable through the public API. Cleanup disconnects the test client before the after-snapshot, and no path changes
   another player, a plugin config or an enabled flag. Options: (a) a persistent fixture player online before the baseline,
   which a test can change; (b) compare the test's own client before cleanup, which false-positives legitimate tests that give
   items. Maintainer call. Recorded in the T-M4-12 card and PR #80.
2. **Intermittent ErrorLogGuard WARN `PSConf moved too quickly!` on c02.** One full 4-platform `@26.3` smoke run failed c02 on
   folia, purpur and spigot with this WARN (`ErrorLogGuard.java:109`). Not reproduced single-cell on spigot@26.3 on main or on the
   PR branch. Not caused by PR #80 (the PR does not touch ErrorLogGuard or the `PSConf` client name). Unexplained. The guard
   allow-list covers `T`-prefixed and `.`-prefixed harness clients, not `PSConf`. Gotcha in `docs/platforms/folia.md`.
3. **Gradle example `paper@26.3` flakes (T-M6-03 selfcheck, PR #85). Unexplained; blocks the "examples are green" release claim.**
   Action path (`scripts/action-selfcheck.sh`): 4 runs, 2 red. Author run: `coalOnTheRightMakesNothing` join timeout. Verifier run 2:
   `ironAndGoldOnTheAnvilMakeOneDiamond` join timeout. Both `ClientDriverException: timed out joining ... after PT30S (no server reply)`.
   Plain gradle control (same cell and flags, no action layer): 5 runs, 1 red. Both tests `java.lang.AssertionError at
   VerdictGate.java:150`, no message printed. The verdict text (INVALID, DISAGREE or LEAK) is not recoverable: the XML was overwritten
   by a later run and the bundle holds no verdict text. The plain path is red too, so the action layer is not the cause.
   **Data points, same family (join timeout `timed out joining ... after PT30S`):**
   - purpur@26.3 C06 join timeout (earlier single red, unexplained; see the M6 status above and `docs/platforms/purpur.md`).
   - Gradle example `paper@26.3`, two runs (action selfcheck, verifier run 2; see above).
   - `metaExpect` on `paper@26.3` (T-M6-09, 2026-11-09, `-Dproofstone.cells=paper@26.3` run): `ExpectFailsE2E` got
     `INVALID` from `cell conformance failed: the conformance client could not join: timed out joining PSConf ... after PT30S`,
     twice in one run, instead of `FAIL`. The red-run XML is kept aside in the T-M6-09 verifier scratchpad. Not a verdict of the test.
   - The same `metaTest` run without a selector (default cells, 2026-11-09, `paper@26.3` and `paper@26.1.2`): 0 join timeouts, all 8
     verdicts as expected. The `paper@26.3` join timeouts did not recur in that run.
   **Hypothesis to test (not confirmed):** other containers on the same Docker Desktop cause resource contention. Checked on
   2026-11-09: `docker ps -a` showed `ohlidame-redis`, `ohlidame-mysql` and `ohlidame-mailpit` running (not Proofstone's, 21 hours up),
   all others exited. A 20-second `docker stats` sample during the default-cells run showed them idle (CPU under 3%, mysql about
   0.6 GiB of an 11.68 GiB limit). The sampler saw no Proofstone container, so contention during the Paper cell is **not measured**.
   **T-M6-10 result (2026-11-09, `scripts/flake-loop.sh`, `:examples:gradle-paper-plugin:e2eTest` on paper@26.3, one run at a
   time under `dockerlock`, no retries). Evidence paths (local, not committed): `build/flake/nostress/`, `build/flake/stress/`,
   `build/flake/smoke/` (in the T-M6-10 worktree).**

   | Condition | Runs | Red | Boot mean (s) | Join mean (s) | Wall mean (s) | Host free RAM mean (GiB) |
   |---|---|---|---|---|---|---|
   | no stress (`ohlidame-*` idle) | 30 (+1 smoke, pass) | 1 (run 29) | 23.4 | 25.6 | 36 | 4.0 |
   | `proofstone-stress`, 10 CPU, 5.84 GiB | 15 | 1 (run 8) | 26.0 | 28.8 | 44 | 2.6 |

   - **Verdict type of each red, from the kept XML and bundle:** both `join-timeout` (`timed out joining ... after PT30S`).
     Run 29 is `INVALID`: the conformance client `PSConf` could not join, `server.log` shows `Done` and then no join.
     Stress run 8 is a plugin test client (`Te...`) that timed out; `server.log` shows `PSConf joined` at +3 s and no join for
     the test client. In both, the client sent `ClientIntentionPacket` and `ServerboundHelloPacket` and got no server reply
     within PT30S. No `DISAGREE`, `LEAK`, `VACUOUS` or `ERRLOG` verdict appeared.
   - **Cause: not shown. Bounded at 2 of 46 runs (1 per condition), cause unknown.**
   - **Contention hypothesis: unresolved.** Stress slowed boot (+2.6 s) and join (+3.2 s) and cut free RAM from 4.0 to 2.6 GiB,
     but the red count (1 of 30 against 1 of 15) cannot separate the two conditions. Boot time does not predict the reds:
     the two red runs booted in 22.9 s and 25.4 s, inside the normal range.
   - **Measurement limits:** boot and join are taken from the first Docker log line of the server container (about 1 s before
     the process starts). Container logs are snapshots taken every 2 s before the harness removes the container. Four pass runs
     (12, 26, 27, 28) have no join time because the container was removed before the snapshot. The watcher race that caused it
     is fixed in the script; the no-stress loop ran before that fix, its verdicts do not depend on it.
   - **Harness gap, closed by T-M6-14 (2026-11-09):** a join whose server never answers the hello waited the full PT30S and
     reported only `timed out`. Now `JoinTimeoutException` names the stalled stage (`stalled at LOGIN_START_SENT_NO_REPLY (...)`),
     the protocol offered and the last packet received, and the harness appends `server login start seen: yes|no`, prints
     `PROOFSTONE JOIN STALL` with the server log tail, and writes `join-stall.txt` into the bundle. The message keeps the
     `timed out joining` prefix, so `scripts/flake-loop.sh` still classifies it as `join-timeout`. No timeout was changed, no
     retry added, and naming the stage is not a cause.
   - **T-M6-14 evidence:** unit tests for stage classification (15 cases) and the server view (4), plus two loopback tests
     with the real MCProtocolLib client: a listener that accepts TCP and goes silent gives `LOGIN_START_SENT_NO_REPLY`, one that
     sends only a compression packet gives `COMPRESSION_SET_NO_LOGIN_SUCCESS`. Real server: `scripts/flake-loop.sh 30`,
     `:examples:gradle-paper-plugin:e2eTest`, paper@26.3, no stress: **30 pass, 0 red** (tests 2, skipped 0, failures 0, errors
     0 in each). So no real-server stall was caught in the new format, and the cause is still unexplained. Evidence:
     `build/flake-t-m6-14/summary.tsv` in the T-M6-14 worktree. Running tally of the plain path: 3 red of 91 runs, all before
     this change.

## M6 review (T-M6-99)

Date 2026-11-09. Ran on `card/T-M6-99-release-review` from origin/main d1b6c02 (PRs #77 to #86 merged). Local runs only.

**Verdict: NO-GO for 0.1.0 as claimed.** Go-able now: dogfood use from `mavenLocal` and Disenchantment work in its own repo.
Not go-able: any public release, any "examples green" claim, any GitHub Action or Maven Central claim.

### Evidence from this run

| Gate | Result |
|---|---|
| `bash scripts/gates.sh --max-workers=1` | OK. `docsCheck` 0 errors, 420 warnings (glossary, not failing). BUILD SUCCESSFUL |
| `:examples:bad-tests:metaTest` on paper@26.3 (selector not forwarded, see finding) | BUILD SUCCESSFUL. All 8 verdicts: VACUOUS, FLAKY, DISAGREE, INVALID, INVALID, LEAK, FAIL, SKIPPED |
| `:conformance:smoke` on paper@26.3 | BUILD SUCCESSFUL. 2 tests, skipped 0, failures 0, errors 0 |
| `scripts/release-mutation-check.sh --allow-missing=maven-paper-plugin` | PASS. Baseline PASS, double-output KILLED, accept-any-right KILLED. Recorded `dirty=true` (uncommitted T-M6-99 docs), so `--publish` would refuse it |
| `scripts/release-mutation-check.sh` (no flag) | INCOMPLETE, as designed. Blocking: `maven-paper-plugin` NOT RUN (no project, no mvn) |
| Gradle example, plain path, paper@26.3, 10 sequential runs | 10 of 10 green (`tests=2 skipped=0 failures=0 errors=0`) |

Finding: the `badRuns` tasks in `examples/bad-tests/build.gradle.kts` do not forward `-Dproofstone.cells` (only `tasks.test` does).
metaTest therefore ran each bad test on its default matrix, not the requested cell. All verdicts were still correct. The
differential run uses paper@26.1.2 by design. Fix in a separate card: forward the selector in `badRun`, and decide what
differential does on a paper@26.3-only run.

### Gradle example flake (ROADMAP "M6 pre-release findings" item 3)

Running total for paper@26.3, plain gradle path: earlier 1 red of 5 (T-M6-03 review), this run 0 red of 10. Total 1 of 15.
Action path (earlier): 2 red of 4. The verdict type of the earlier reds (INVALID/DISAGREE/LEAK/VACUOUS) is **not recoverable**:
the XML was overwritten before anyone read it. Do not claim a root cause.
Next step: keep the XML of every red run (this run's loop copies it aside; reuse that pattern).
T-M6-10 (2026-11-09, ROADMAP "M6 pre-release findings" item 3): 45 more runs, 2 red (both `join-timeout`, verdict types named
from the kept XML and bundles), plus 1 smoke pass. Combined with the 15 plain runs above: 3 red of 61 runs (2 of 46 unstressed, 1 of 15 under the artificial stressor), all join
timeouts. Cause unknown; contention unresolved (see item 3).

### Exit criterion (six items of T-M6-99 / the M6 goal)

| # | Item | Status | Evidence and reason |
|---|---|---|---|
| 1 | Disenchantment's full suite runs in Disenchantment's CI | NOT MET | T-M6-05 is in another repo. No CI exists here. Unverified |
| 2 | Maven and Gradle example projects work | NOT MET | Maven example missing (mvn not installed). Gradle green 10 of 10 this run, but flake 1 red of 15 unexplained |
| 3 | Artefacts published to Maven Central and GHCR | NOT MET | Nothing published, by design (namespace not verified, ADR-0012). Correct state, not a failure |
| 4 | GitHub Action published | NOT MET | Local only (`integrations/github-action`). Action self-check was red earlier |
| 5 | Licence decided | MET | ADR-0011 Accepted 2026-11-09: Apache-2.0, DCO, NOTICE (T-M6-06). Nothing published yet |
| 6 | Mutation check passes | NOT MET | PASS only with `--allow-missing` (Maven). Without it INCOMPLETE. Also needs a clean-tree run at the release HEAD |

"In CI" is impossible: no CI, no workflows (maintainer decision 2026-11-08).

### Merged in M6

- T-M4-10 (per-cell conformance) and T-M4-12 (real snapshot in bundles, wider leak detector): merged in PRs #77 to #86 per the brief.
- T-M6-01 Maven publishing pipeline (dry run only), T-M6-02 GHCR scripts (dry run only; no push), T-M6-03 GitHub Action (local),
  T-M6-04 Gradle example only, T-M6-07 mutation check (local), T-M6-08 quickstart docs.
- T-M6-06 licence: Accepted 2026-11-09 (Apache-2.0, DCO, NOTICE). T-M6-05: other repo.

### Unverified

- Disenchantment CI run (T-M6-05). Maven example (T-M6-04). Maven Central staging and publish. GHCR push and pull by digest (T-M6-02).
- GitHub Action on a hosted runner. Quickstart 15-min target: a fresh agent run took 108 s green on first run, with caveats
  (images cached, JDK 21 not 26 though the doc says 26, clone from a stale local main). Not re-run in this card.
- Mutation check on a clean tree at the release HEAD.

### Decisions needed from the maintainer

1. Licence: **decided** 2026-11-09, Apache-2.0 + DCO + NOTICE (ADR-0011, T-M6-06).
2. Maven: install mvn, or drop the Maven example from 0.1.0.
3. spigot-buildtools image: keep it unpushed (it bundles BuildTools.jar), or decide otherwise.
4. XP-LEAK bad test (T-M4-12 deviation, "M6 pre-release findings" item 1): fixture player, or compare own client.
5. Flakes: Gradle example on paper@26.3 (1 of 15 plain, 2 of 4 action) and `PSConf moved too quickly` (item 2). Accept as known, or fix before 0.1.
6. T-M4-13 and T-M4-14: defer past 0.1 (the M4 review says they can wait). Confirm.
7. Namespace: verify `io.github.h7kz` on the Central Portal (ADR-0012). The agent shades under `dev.proofstone.shaded.jackson`, while ADR-0012 says `io.github.h7kz.proofstone.*` unless a domain is acquired. Decide.
8. Workflow: add one for the release, or keep local scripts only (current decision).
9. metaTest cell selector: approve the forwarding fix card.

### M6 re-estimate

Original estimate 2 weeks. Remaining: Maven example (about 2 days, needs mvn), selector fix and flake diagnosis (about 2 days, needs Docker
runs and a diagnosis), release dry run on a clean tree, Disenchantment CI (external), licence decision (maintainer time).
Re-estimate **3 weeks** from now, most of it waiting on the maintainer and on Docker runs.

### M6 decisions: recommendations and state (2026-11-09 audit)

Audit of origin/main a11ced9 (PRs #77 to #87) found 7 gaps. Fixed in PR #88 (T-M6-09): the publish gate accepted a
mutation result made with `--allow-missing` (now refused), the bad tests ignored `-Dproofstone.cells` (forwarded; pinned
tasks refuse a wrong cell), and wrong doc claims (JDK 17, 21 and 26 roles, bad-test count, bundle path, dead Maven link).
Not fixed, open: the namespace question (item 7).

| # | Decision | State | Recommendation |
|---|---|---|---|
| 1 | Licence | Decided 2026-11-09 | Apache-2.0 + DCO + NOTICE (T-M6-06, ADR-0011 Accepted). |
| 2 | Maven | Done: installed (Apache Maven 3.9.9 at `C:/Users/honzi/tools/apache-maven-3.9.9`), T-M6-11 merged | Install Apache Maven, then run T-M6-11. Maven is a stated target audience (ADR-0001, ADR-0009); dropping it removes half of the release claim. |
| 3 | spigot-buildtools push | Decided | Keep unpushed (bundles BuildTools.jar). Only mineflayer-ref is pushed. |
| 4 | XP-LEAK bad test | Decided | Accept the documented deviation for 0.1; add a persistent fixture player post-0.1 (M7+ gives a proxy-persistent player anyway). |
| 5 | Flakes | Bounded, not explained (T-M6-10: 3 of 61 runs red, all join timeouts: 2 of 46 unstressed, 1 of 15 stressed). Follow-up: T-M6-14 | Diagnose before any 0.1 claim: card T-M6-10. Does not block starting M7 design cards, blocks M7 real-server acceptance claims that depend on join. |
| 6 | T-M4-13, T-M4-14 | Decided | Defer past 0.1. |
| 7 | Namespace | Done (T-M6-12): `io.github.h7kz.proofstone`. Central verification still open (ADR-0012) | If you do not own `proofstone.dev`, rename `dev.proofstone.*` to `io.github.h7kz.proofstone.*` before the first publish (269 files, mechanical, free now, breaking later). If you own it, change ADR-0012 to say so. Decide before M7 adds more packages. |
| 8 | Workflow | Decided | Keep local scripts only. Revisit a tag-triggered release workflow after 0.1. |
| 9 | metaTest selector | Done | PR #88. |

What only you can do: items 1, 2 (install), 7, the Central namespace verification (ADR-0012), and any publish or tag.

## M6 review addendum (2026-11-09, after maintainer decisions)

Run by T-M6-13 (`card/T-M6-13-pre-m7-readiness`, from origin/main 0909947). Code under test: commit `4df12c8` (docs fixes only,
no code change). One Docker run at a time under `dockerlock`, XML and logs read, no retries, no red run.

| Run | Result |
|---|---|
| `bash scripts/gates.sh --max-workers=1` | OK, exit 0 |
| `:conformance:smoke`, paper@26.3 | 2 tests, skipped 0, failures 0, errors 0 |
| `:conformance:run -Dproofstone.cells=paper@26.1.2` | failures 0, errors 0. 3 reasoned skips: missing `FOLIA_REGIONS` (2), missing `SIMULATED_BEDROCK` (1) |
| `:examples:bad-tests:metaTest`, no selector | BUILD SUCCESSFUL, all 8 verdicts as expected |
| Gradle example `e2eTest`, paper@26.3 (quickstart) | tests 2, skipped 0, failures 0, errors 0 |
| Gradle example, `double-output` and `accept-any-right` | both red on assertions (`AnvilMechanicE2E.java:42`, `:60`), not join timeouts |
| Maven example `verify -Pe2e`, paper@26.3 (quickstart) | tests 2, skipped 0, failures 0, errors 0 |
| Maven example, both bugs | both red with the quickstart's messages (`one iron and one gold make one diamond`; `coal is not an accepted right input`) |
| `scripts/release-mutation-check.sh` (no flag), clean tree | **PASS**: head `4df12c8`, dirty false, killed 4, no allowed-missing |
| `scripts/release-maven.sh --dry-run` | DRY RUN OK, nothing uploaded, signatures NOT CHECKED (no key) |
| `release-maven.sh --publish`, env cleared | refused: `PROOFSTONE_RELEASE_CONFIRM` not set |
| `release-images.sh --push`, env cleared | refused: `PROOFSTONE_RELEASE_CONFIRM` not set |
| `release-images.sh --write-digests` | refused: no pushed digest |
| Join timeouts or `PSConf moved too quickly` in this run's logs | 0 |

Flake numbers (unchanged, not re-run here): paper@26.3 Gradle example, all conditions, 3 red of 61 runs (2 of 46 unstressed, 1 of 15 under the stressor; the unstressed runs are 1 of 15 earlier plus 1 of 31 in T-M6-10),
all `join-timeout`. Cause unknown; bounded, not explained. Action path 2 of 4 earlier. Follow-up card **T-M6-14** logs the
server's handshake view at join and names the stalled stage in INVALID, so the next red can be explained.

### Exit criterion, re-scored

| # | Item | Status | Evidence and reason |
|---|---|---|---|
| 1 | Disenchantment's full suite runs in Disenchantment's CI | NOT MET | T-M6-05 is in another repo. No CI here, by decision (2026-11-08). Unverified |
| 2 | Maven and Gradle example projects work | MET (local) | Both e2e green on paper@26.3 and both bugs killed, at `4df12c8`. Flake bounded (see above); Maven flake rate not measured |
| 3 | Artefacts published to Maven Central and GHCR | NOT MET | Nothing published, by design. Central namespace verification (ADR-0012) and GHCR push not done. Correct state, not a failure |
| 4 | GitHub Action published | NOT MET | Local only (`integrations/github-action`). Hosted runner not verified |
| 5 | Licence decided | MET | ADR-0011 Accepted 2026-11-09: Apache-2.0, DCO, NOTICE |
| 6 | Mutation check passes | MET (local, at `4df12c8`) | Complete PASS on a clean tree. Publish requires a PASS for the tagged HEAD: re-run before any tag |

### Verdict

**Still NO-GO for a public 0.1.0 claim.** Blocking: item 1 (other repo), item 3 (Central namespace verification, then publish, both
the maintainer's), item 4 (Action not published). Nothing here publishes or tags anything.

**Go-able now, locally:** dogfood use from `mavenLocal`, and a tag-ready state for the code at `4df12c8`. Tagging is the
maintainer's decision. After any further commit, re-run `bash scripts/release-mutation-check.sh` at the tag HEAD first:
`release-maven.sh --publish` refuses otherwise.

### M7 next-session prompt

The full, current prompt is `docs/plan/NEXT-SESSION-M7-PROMPT.md` (updated after the 2026-11-09 audit). The earlier short prompt
that stood here is superseded.

## M7 findings

Reds and surprises from M7 real-server runs, per the M7 Flake rule. Each names its verdict type and, for a join
timeout, the stalled stage and hop.

- **T-M7-01 spike (2026-11-09), no join timeout.** Velocity 4.3.1 build 45: 15 of 15 client joins, 41 backend logins.
  BungeeCord build 2111: 10 of 10 client joins, 30 backend logins. Join timeouts on either hop: 0. Details in
  `docs/platforms/velocity.md` and `docs/platforms/bungeecord.md`.
- **Switch red, not a join timeout: Velocity sent `/server survival` to the backend** (2 of 48 switch attempts, the
  first client of runs 3 and 5). The backend answered `command.unknown.command`. Likely Velocity's
  `command-rate-limit = 50` with `forward-commands-if-rate-limited = true` (the two commands were 25 ms apart); with the
  limit at 0, 10 of 10 switched (one run). Open for T-M7-09.
- **Setup red: copying `config/` into a Paper backend's `/data` crashed both backends at world load** (root-owned
  directory). Fixed in the spike script by copying into `/config`. Recorded in `docs/platforms/velocity.md` Gotchas.
- **Protocol gaps:** Velocity 3.5.1 build 615 and Waterfall 1.21 build 615 reject the protocol 777 client (one probe
  each). Neither is proposed as a row.
- **T-M7-07 topology host (2026-11-10), no join timeout.** `:proofstone-core:topologyTest`, three invocations per proxy, one
  proxy per invocation, each with two `paper@26.3` backends and one client join through the proxy: Velocity 3 of 3 green,
  BungeeCord 3 of 3 green, 6 of 6 joins. One forced-fault invocation (expected outcomes, not flakes): a backend whose agent
  never answers fails the start naming `server-survival` with every container stopped; a paused backend stalls the join
  at `CONFIGURATION_STALLED` on the proxy-to-backend hop.
- **T-M7-09 driver survives a server switch (2026-11-10), no join timeout, no red.** `:proofstone-core:topologyTest --tests '*<Proxy>SwitchIT'`, one proxy per invocation, three separate invocations per proxy, each with two `paper@26.3` backends: Velocity 3 of 3 green, BungeeCord 3 of 3 green. Each run: one client, `/server survival` and back, twice (4 switches, 5 logins, 4 start-configurations; BungeeCord also 4 respawns, Velocity 0), views the new server's each time. `:conformance:run -Dproofstone.cells=paper@26.3`: 0 failures, 0 errors, 2 reasoned skips (Folia).
- **Latent config error, not a red run: `VelocityAgentIT` sets `command-rate-limit` at the top level**, where Velocity 4.3.1 ignores it
  (it is an `[advanced]` key). No player commands run in that test, so no result changed. Recorded in
  `docs/platforms/velocity.md` Gotchas; the topology config renders it under `[advanced]`.
- **T-M7-10 public topology API (2026-11-13), no join timeout; one red (log guard), BungeeCord.**
  `:proofstone-core:topologyTest --tests '*<Proxy>TopologyApiE2E'`, one proxy per invocation, three invocations each, two
  tests per run (one client join through the proxy, `transferTo("survival")` and back): Velocity 3 of 3 green, BungeeCord
  2 of 3 green. The red (run 2, `FAIL`, not a join timeout, not a switch timeout): the `lobby` backend logged an `ERROR`
  and a `WARN` from authlib, a `sessionserver.mojang.com` profile lookup answered `HTTP 429` during
  `ServerLoginPacketListenerImpl.callPlayerPreLoginEvents`. A Paper backend behind BungeeCord legacy forwarding completes
  the forwarded profile online at each login, so the run depends on Mojang's session server and its rate limit. The log
  guard did its job; no allowlist was added and nothing was retried. Evidence kept locally (XML, bundle with a log per
  container). **Decision (maintainer, delegated): no allowlist, no Mojang dependency.** Cause: Paper's
  `proxies.bungee-cord.online-mode` defaults to true, so every login fetched profile properties from Mojang (also on the
  green runs, silently). Fix: BungeeCord backends get `proxies.bungee-cord.online-mode: false`, and `TopologyHost` checks the
  written config on every backend (Velocity backends already had it off). The run 2 red stays counted as a red; the
  BungeeCord acceptance is judged on three fresh invocations after the fix (PR #108).
- **T-M7-11 conformance P01 and P02 (2026-11-13), no join timeout; one harness red before the final runs.**
  `:conformance:run -Dproofstone.cells=<topology cell>`, one proxy per invocation. The first Velocity run was red (`INVALID`,
  `P02`, not a join timeout and not a flake): the direct join to the backend stalled at `UNKNOWN` with `last packet received:
  ClientboundCustomQueryPacket`, because the mcpl driver never answered Paper's `velocity:player_info` login query, so the
  backend's refusal was never reached. Fix: the driver answers a login query with an empty answer, as a vanilla client does
  (`McplLoginQueryTest`). The three Velocity invocations and the three BungeeCord invocations after the fix are all green
  (6 of 6, 1 test each, skipped 0 failures 0 errors 0). P01 reads `events` and `players` on a real proxy, so both agents now
  report `events`. Backend game ports had been published on all addresses against ADR-0016 section 8; the maintainer's decision: a test-only
  exception bound to `127.0.0.1` (ADR-0016 section 8 amended, `GamePortBindingTest`).
