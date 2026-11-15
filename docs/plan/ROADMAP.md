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
| **M7** | Proxies (exit met locally, reviewed in T-M7-99) | Velocity and BungeeCord topologies, with proxy agents, pass conformance. | 15 ([M7-proxies.md](tasks/M7-proxies.md)) | 1 / 9 / 5 | 3 (was 2; honest range 1.5 to 3). Actual: about 4 days of agent wall clock for PRs #98 to #112 (see the M7 review) |
| **M8** | Mods and data (exit partly met, reviewed in T-M8-99; follow-ups T-M8-13 to 18 open) | Fabric and NeoForge agents (newest native row only; Stonecutter out, ADR-0017), plus data pack and resource-pack tests. | 13 ([M8-mods.md](tasks/M8-mods.md)) + 6 follow-ups ([M8-followups.md](tasks/M8-followups.md)) | 1 / 6 / 6 | 4 (range 2 to 5). Actual: about 17 h of agent wall clock for PRs #115 to #126 (see the M8 review) |
| **M9** | Bedrock | Spikes (offline Bedrock through Geyser; a BDS script agent). Then a Bedrock driver, the Geyser topology and, if feasible, the BDS Script-API agent. | 13 (refined 2026-11-15, [M9-bedrock.md](tasks/M9-bedrock.md), awaiting approval) | 0 / 6 / 7 | 4 after the spikes (range 2 to 6) |
| **M10** | Ecosystem | Community pack template, a TS front end (optional), more forks, a docs site, Sponge, PocketMine and others. | epic | refine | ongoing |

**Cards M0–M6:** 66 in total. 40 are Haiku cards (61%), 24 Sonnet and 2 Opus (T-M3-02 moved from Haiku to Sonnet at T-M2-99).

**Total to v0.1 (M0–M6): about 16 focused weeks** (sum of the estimates, M5 run after M4; was 15.5 before the M5 review, see the M5 review). Full ecosystem (M7–M10): about 10+ more weeks (M7 done; M8 to M10 about 8+ weeks, see the M7 review; after M8, about 4 to 6 weeks plus ongoing M10, see the M8 review).
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
- **T-M7-12, P04 red (velocity@4.3.1+paper@26.3, first run), verdict INVALID, not a join timeout.** The fixture recorded `GetServer lobby`
  but the proxy event probe on `PluginMessageEvent` saw no channel. Velocity answers `BungeeCord` messages itself and fires no event
  for them. P04 on Velocity now requires the reply only (reason printed and in `velocity.md`). The probe still is required on BungeeCord.
- **T-M7-12, P02 red (bungeecord@2111+paper@26.3, first run), verdict INVALID.** BungeeCord's `has connected` line names the player
  (`[T7e5c0f01,/172.26.0.1:47956]`), which the address parser did not accept. Parser fixed, `bungeecord.md` updated.
- **T-M7-12, a log-guard red in two velocity runs after P04 was fixed.** The lobby WARNed `PSConf moved too quickly!` on the switch back;
  the allowlist only knows `T<hex>` names, so the topology conformance client was renamed to that pattern (no allowlist entry added).
- **T-M7-13 negative controls (2026-11-13), run by hand, not in `scripts/release-mutation-check.sh` (maintainer decision).**
  `-Dproofstone.injectTopologyFault=<fault>` on `examples/bad-tests` `topology/TopologyFaultE2E`, one cell per run, each fault once
  per proxy, every run `tests=1 skipped=0 failures=1 errors=0` and `INVALID` by design (a red-by-design run, not a flake). XML
  read; the message names the reason, the container and the hop, and none is a join timeout (`metaTopologyTest` fails on
  `timed out joining`). Velocity 4.3.1: `wrong-secret` P01, `Unable to verify player details`, hop proxy to backend lobby;
  `forwarding-off` P01, `This server requires you to connect with Velocity`, same hop; `backend-forwarding-off` P01, `Your server did
  not send a forwarding request to the proxy`, same hop; `unreachable-backend` T00, `backend survival is offline at the proxy
  (container server-survival, hop: proxy to backend survival)`. BungeeCord 2111 (no secret, so no `wrong-secret`, refused loudly):
  `forwarding-off` P01, `If you wish to use IP forwarding, please enable it in your BungeeCord config as well!`;
  `backend-forwarding-off` P01, `Unknown data in login hostname, did you forget to enable BungeeCord in spigot.yml?`;
  `unreachable-backend` T00, as Velocity. Both proxies kick at the proxy-to-backend hop, so the hop comes from the logs, not from
  the kick text. Clean runs, `:conformance:run -Dproofstone.cells=<cell>`, three separate `dockerlock.sh` invocations per cell:
  Velocity 3 of 3 and BungeeCord 3 of 3 green (`tests=2 skipped=0 failures=0 errors=0`). Mutation check of the meta test itself: a
  fault run with no failure reads `no test produced INVALID`; a message holding `timed out joining` or lacking the reason is red.
  Observation, not examined: the `server-lobby` log tail of the `wrong-secret` run ends in a `FileOutputStream.open` stack trace
  from `CraftServer.saveConfig` during server start, before any join.
  **Maintainer question (mutation check):** the seven runs cost about 7 minutes in total (3 containers each). Say if they should
  join `scripts/release-mutation-check.sh`; until then they are a by-hand pre-release step (`:examples:bad-tests:metaTopologyTest`).
- **T-M7-13, existing `metaTest` run once: red, infrastructure, not the product (2026-11-13).** Six of eight bad tests gave their
  verdict (VACUOUS, FLAKY, DISAGREE, INVALID, FAIL, SKIPPED). `metaInvalidWindow` and `metaLeak` (both pinned `paper@26.1.2`, bridged)
  never booted: itzg `install-paper` failed with `SslHandshakeTimeoutException: handshake timed out after 30000ms` downloading
  Paper, the container exited, `PaperHostException ... timed out waiting for "Done ("` after 180 s. No single-server code changed in
  T-M7-13. Not retried (Flake rule); the card's "all 8 verdicts" acceptance stays open until a later run, kept XML in the
  PR evidence.

## M7 review (T-M7-99)

Date 2026-11-14. Ran on `card/T-M7-99-review` from origin/main `9192c60` (PRs #98 to #112 merged). Local runs only, Windows 11,
Docker Desktop 29.9.2, JDK 21 for Gradle, JDK 26 for Maven. One Docker run at a time under `scripts/dockerlock.sh`,
`--no-parallel --max-workers=1`, EULA in the command environment only. Every XML read; every red kept, none retried. Judged from
code and XML, not from PR text.

**Verdict: M7 exit criterion MET (local evidence).** Both topologies pass P01 to P05 on `paper@26.3` backends, the topology API
is additive, misconfigurations are loud reds, and the flake rule's three invocations were met again here (6 of 6). Not met
elsewhere in this review: the full four-cell `:conformance:smoke` is red on the known `PSConf moved too quickly!` log-guard
family (3 of 8 tests, folia, purpur and spigot @26.3), a single-server finding older than M7, unexplained. Both topology rows
stay `experimental` (promotion is a maintainer decision below). Nothing was published or tagged.

### Evidence from this run

| Run | Result |
|---|---|
| `bash scripts/gates.sh --max-workers=1` at `9192c60` | `gates: OK`, exit 0 (release-gate-test 9/0, bad-tests-pin-check 0 failed, renovate unchanged) |
| `:conformance:smoke`, no selector (paper, folia, purpur, spigot @26.3) | **RED**: tests 8, failures 3, errors 0, skipped 0. `c02` failed on folia, purpur and spigot @26.3: `ErrorLogGuard` WARN `PSConf moved too quickly! 0.5,64.0,0.5`. paper@26.3 c01 and c02 green. Not a join timeout. XML kept; the bundles were deleted by the next `:conformance:run` before they were copied (lesson in the M8 prompt) |
| `:conformance:run -Dproofstone.cells=velocity@4.3.1+paper@26.3`, 3 separate invocations | 3 of 3 green, each tests 2, skipped 0, failures 0, errors 0; T00 and P01 to P05 passed each time; 75 to 78 s each |
| `:conformance:run -Dproofstone.cells=bungeecord@2111+paper@26.3`, 3 separate invocations | 3 of 3 green, each tests 2, skipped 0, failures 0, errors 0; T00 and P01 to P05 passed each time; 75 to 77 s each |
| `:examples:bad-tests:metaTest` | BUILD SUCCESSFUL, "every bad test produced its expected verdict (VACUOUS, FLAKY, DISAGREE, INVALID, INVALID, LEAK, FAIL, SKIPPED)", 4 m 43 s. The bad tests fail by design; `metaRequires` skips by design |
| `:examples:bad-tests:metaTopologyTest` (7 faults, one cell each) | BUILD SUCCESSFUL, "every bad test produced its expected verdict": 7 of 7 `INVALID`, each `tests=1 failures=1` by design, 0 `timed out joining`. Velocity: `wrong-secret` (`Unable to verify player details`), `forwarding-off` (`requires you to connect with Velocity`), `backend-forwarding-off` (`did not send a forwarding request`), all `hop: proxy to backend lobby`; `unreachable-backend` (`is offline at the proxy`, `hop: proxy to backend survival`). BungeeCord: `forwarding-off`, `backend-forwarding-off`, `unreachable-backend`, same hops. 7 m 32 s |
| `:conformance:run -Dproofstone.cells=spigot@26.3` (C04 reproduction attempt) | Green: 12 tests, failures 0, errors 0, 3 reasoned skips (missing `SIMULATED_BEDROCK`, `FOLIA_REGIONS` x2). C03 to C08 passed, C04 included; 0 join timeouts. 5 m 38 s |
| `bash scripts/release-mutation-check.sh` (no flag), clean tree | **PASS**: head `9192c60`, dirty false, cell paper@26.3, killed 4 of 4, no allowed-missing. Gradle and Maven baselines PASS (each example's e2e suite once, green) |
| Containers of ours left running after the runs | none (checked after every run; only Testcontainers' own reaper, which exits by itself). Other projects' containers untouched |

The Gradle example and the Maven example ran once each as the mutation check's baselines (row above), not as separate runs.

### Topology flake numbers (ADR-0016 section 9, counted apart from M6)

Topology joins through the proxy, all M7 real-server runs with the XML read:

| Source | Invocations | Red | Join timeouts (first join / switch) |
|---|---|---|---|
| T-M7-01 spike (by hand) | 25 client joins, 71 backend logins | 2 switch reds (Velocity rate limit, explained, fixed by ADR-0016 section 5) | 0 / 0 |
| T-M7-07 `topologyTest` | 6 | 0 | 0 / 0 |
| T-M7-09 switch ITs | 6 | 0 | 0 / 0 |
| T-M7-10 topology API | 3 Velocity + 3 BungeeCord + 3 BungeeCord after fix + 1 | 1 (log guard, Mojang HTTP 429, explained, fixed) | 0 / 0 |
| T-M7-11 P01, P02 | 6 after fix, 1 before | 1 (driver did not answer the login query, explained, fixed) | 0 / 0 |
| T-M7-12 P03 to P05 | 6 final, plus reds before | 4 (P04 Velocity probe, P02 Bungee parser, 2 `PSConf moved too quickly!`, all explained, fixed) | 0 / 0 |
| T-M7-13 clean runs | 6 | 0 | 0 / 0 |
| T-M7-14 example | 3 | 0 | 0 / 0 |
| **T-M7-99 (this run)** | **6** | **0** | **0 / 0** |

**Topology join timeouts: 0 observed** on either hop, first join or switch, across every counted invocation above (at least
51 `dockerlock` invocations plus the spike's 25 joins). Every topology red in M7 has a named cause and a fix; **no topology red
is unexplained.** The single-server family (3 of 61 Gradle-example runs, M6) is not re-measured here.

### Exit criterion, re-scored

| # | Item | Status | Evidence |
|---|---|---|---|
| 1 | Velocity topology passes P01 to P05 on `paper@26.3` backends | MET | This run: 3 of 3 green (above). Earlier: T-M7-12 3 of 3, T-M7-13 clean 3 of 3. P04 on Velocity is judged on the fixture's `GetServer` reply only: Velocity answers `BungeeCord`-channel messages itself and fires no `PluginMessageEvent` (`velocity.md`), so the proxy-side event oracle is absent there (recorded, not hidden) |
| 2 | BungeeCord topology passes the same, legacy forwarding | MET | This run: 3 of 3 green, direct join refused with BungeeCord's forwarding message. Earlier: T-M7-12 and T-M7-13 3 of 3 each |
| 3 | `Topology.server("lobby")` and `client.transferTo("survival")`, additive to API v0 | MET | `api-baseline.txt` diff from `88ecb71`: 11 lines added, 0 removed (`Topology`, `Topology.Backends`, `Client.transferTo` default method, `Platform.VELOCITY`/`BUNGEECORD`, `Capability.PROXY`/`TOPOLOGY`). Source diff of `proofstone-api` removes only two enum trailing lines (comma added). Real server: T-M7-10 3 of 3 per proxy, T-M7-14 example 3 of 3. Caveat: new enum constants can break a user's exhaustive `switch` without `default` at compile time |
| 4 | A forwarding misconfiguration is a loud red | MET | This run's `metaTopologyTest`: 7 of 7 faults `INVALID` with the reason, the container and the hop in the message, none a join timeout (row above); the clean runs of the same cells are green. T-M7-13 had the same 7 of 7. Every fault is a test-only system property (`-Dproofstone.injectTopologyFault`), and the bad tests are pinned to their cells (`bad-tests-pin-check`) |
| 5 | Flake rule evidence for every acceptance | MET | Three green invocations per cell for every real-server acceptance in the card file (ROADMAP "M7 findings", PRs #101 to #112) and again here. No red was replaced by a green; reds are listed with causes above |

### Code review (diff `88ecb71..9192c60`, 208 files, +14416 / -246)

- **Weakened tests: none found.** Changed test files: `ErrorLogGuardTest` swaps the "unknown platform fails loudly" example
  from `velocity` to `waterfall` (velocity now has its own list) and adds identity checks for the two new lists; the assertion is
  kept, not weakened. `SchemaValidationTest` moves the version-mismatch case from `1.1` to `1.2` (the bump) and adds 7 invalid
  cases. `AgentContractIT` lists the six proxy goldens as pending with the reason; the Velocity and BungeeCord contract tests
  replay them. Other changes are additions or import moves (T-M7-04 renames, R100).
- **New runtime dependencies: none.** Velocity and BungeeCord APIs and their POM types are `compileOnly` or test-only
  (`gradle/libs.versions.toml`, pinned versions with reasons). The agents shade only `agent-core` and jackson, relocated.
- **Workflows: none.** `.github/` holds only the issue and PR templates.
- **Secrets: none committed.** No key, secret or jar file in the diff; the EULA appears only in command lines in docs.
- **Sleeps and retries: none in harness or test code.** The only `sleep` added is a 1 s poll in `scripts/spike/proxy-spike.sh`
  (a spike script, condition-polled).
- **ADR-0016 conformance:** identity strings, schema 3 `proxies`, `experimental` rows, Waterfall out, native only, the
  client-side `/server` switch with harness rate limits off, the dual oracle and `DISAGREE` across agents, per-container log
  guard and bundle, leak detector on every backend, per-hop join messages: all present in code. Deviation: the loopback-only
  game-port binding (section 8 exception) is applied by `PaperHost` to every server with a network, which includes bridged
  single-server cells (`CellInvocation` puts them on the ViaProxy network). Harmless (metaTest's bridged tasks gave their
  verdicts) but it contradicts ADR-0016 section 1 ("single-server code is unchanged"); recorded, not fixed here.
- **Observe-only (both proxy agents):** handlers at the last priority, no command registered, no setter called, reflective
  getters only; `ObserveOnlyTest` in each agent asserts it, including a scan of compiled classes for state-changing API names.
- **Secret redaction:** the forwarding secret is generated per cell, copied from memory, redacted from every bundle file and the
  bundle re-scanned (`BundleWriterTest`). The register review is in `docs/robustness.md` section 5, "Topology review".

### Known open items: verdicts

1. **spigot@26.3 C04 timeout seen once by an agent.** **Not reproduced: 1 run, C04 green** (row above). The earlier red left no kept XML, bundle or stage message in the repository or the evidence folders, so its verdict type and stage cannot be named. Verdict: **unexplained, unverified, kept open**, counted as 1 red of 2 known spigot@26.3 C04 runs since then. A repeat count (for example 10 C04 runs, as the M5 follow-up did for C06) is a maintainer call; not run here.
2. **`FileOutputStream.open` stack trace in the wrong-secret `server-lobby` log: baseline, not caused by the fault.** It is
   `ERROR Could not save bukkit.yml ... (Permission denied)` from `CraftServer.saveConfig` at start, on **every** Paper boot in
   this review, single-server too (12 of 12 `server.log` files in this run's metaTest bundles, paper@26.3 and @26.1.2), because
   `PaperHost` copies `/data/bukkit.yml` as root (T-M1-10). BungeeCord backends also log `Could not save spigot.yml` (their
   copied `/data/spigot.yml`, T-M7-07). The settings are read, only the re-save fails, and the log guard does not see it (outside
   the test window). A defect, not a flake: card **T-M8-02**, fixed there (copies owned by uid/gid 1000, start check).
3. **metaTest SSL download failure (T-M7-13): environment, closed.** This run: all 8 verdicts, both `paper@26.1.2` tasks booted.
   The T-M7-13 acceptance box "existing metaTest still reports all 8 verdicts" is now ticked on this evidence.
4. **VelocityAgentIT rate limit and the Mojang rate limit: closed.** `VelocityAgentIT` now renders `login-ratelimit` and
   `command-rate-limit` under `[advanced]`, as `TopologyConfig` does. The Mojang HTTP 429 (T-M7-10) cannot recur on a topology:
   both backend kinds set proxy `online-mode: false` and `TopologyHost` checks the written config on every backend. Not re-run
   here (`VelocityAgentIT` is not in this review's run list).
5. **`ErrorLogGuardTest` edit: not a weakening** (see Code review).
6. **Velocity P04, `PluginMessageEvent` not fired: accepted platform behaviour, documented.** Velocity answers `BungeeCord`
   channel messages in its backend session and fires no event, so the probe cannot see them (`velocity.md`, Gotchas). P04 on
   Velocity prints the reason and judges the fixture's reply only. The gap is one oracle on one scenario on one proxy; it stays
   recorded, not papered over.
7. **New: the full smoke `PSConf moved too quickly!` family recurred** (3 of 8 c02 tests: folia, purpur, spigot @26.3). Second
   full four-cell red with the same three cells (first: "M6 pre-release findings" item 2). The M5 review's full smoke was green.
   So far 2 red of 3 recorded full four-cell smoke runs. Paper passes c02 each time. Cause unknown; **unexplained, open.** The
   allowlist covers `T<hex>` names; the single-server conformance client is `PSConf` (T-M7-12 renamed only the topology one).
   Not allowlisted here.

### Decisions needed from the maintainer

1. **Promote the two topology rows to `required`?** ADR-0015 with P01 to P05 is met for each pairing (three clean
   invocations three times over). ADR-0016 section 3 also requires the proxy row to be `required`. Recommendation: promote
   both pairings and both proxy rows in one `versions.yml` PR with this table.
2. **`PSConf moved too quickly!` (item 7).** Recommendation: a diagnosis card before 0.1 (name the client `T<hex>` like the
   topology client only if the diagnosis shows it is the same reset-teleport race; never a blanket allowlist).
3. **Approve `docs/plan/tasks/M8-mods.md`** (13 cards). Nothing in M8 is started.
4. Unchanged from M6: Central namespace verification, Disenchantment CI, publish, tag, 0.1.0 go/no-go.

### Re-estimate

M7 was estimated at 3 focused weeks (range 1.5 to 3). Actual: the first M7 PR merged 2026-11-09 23:39 and the last 2026-11-13
22:32 (+01:00), about 4 days of agent wall clock for 15 cards and 23 non-merge commits, plus this review (about 30 minutes of
Docker runs). Remaining:

| M | Estimate | Reason |
|---|---|---|
| M8 Mods and data | **4 weeks, range 2 to 5** (unchanged centre) | Two loaders and build tools, a driver change for mod channels and resource packs, and the open question whether the vanilla client joins a modded server at all (T-M8-01). If it cannot, add a modded-driver ADR and about 2 weeks |
| M9 Bedrock | 4 weeks after the spike (unchanged) | The spike decides; no new evidence |
| M10 Ecosystem | ongoing | Unchanged |

Total M7 to M10 is about 8+ weeks from here (was 10+ before M7).

### M8 refined

`docs/plan/tasks/M8-mods.md`: exit criterion, context (what M7 left, the copy-ownership defect, external services, the driver
question), Flake rule, 13 cards (T-M8-01 to T-M8-12 plus T-M8-99), honest estimate. **Not approved; nothing started.** Next
session prompt: `docs/plan/NEXT-SESSION-M8-PROMPT.md`.

### M7 follow-ups (2026-11-14, branch card/M7-followups)

- **Decision 1 done:** `velocity@4.3.1` and `bungeecord@2111` proxy rows and their `paper@26.3` pairings are `required` in `versions.yml`
  (ADR-0015 and ADR-0016 section 3). Evidence: the "Evidence from this run" table above, P01 to P05 green 3 of 3 per cell, each
  tests 2, skipped 0, failures 0, errors 0. `printMatrix --scope=pr`: 21 cells before, **23 after** (adds `velocity@4.3.1+paper@26.3`
  and `bungeecord@2111+paper@26.3`; the 21 single-server cells are identical). Post-promotion real runs are in the PR.
- **Decision 2 carded, not started:** T-M7-15 (`PSConf moved too quickly!`) and T-M7-16 (spigot@26.3 C04), [M7-followups.md](tasks/M7-followups.md).
  The root-owned `bukkit.yml` copy stays with T-M8-02.
- **ADR-0016 amended** (Amendments, 2026-11-14): the loopback-only game-port binding covers bridged single-server cells too.

## M8 findings

Reds and surprises from M8 real-server runs, per the M8 Flake rule. Mod-cell join timeouts are counted apart from the
Paper and topology numbers.

- **T-M8-12 docs and example (2026-11-15).** `examples/loader-datapack` (`DataPackFunctionE2E`, C-D01 cut down) on
  `fabric@26.3`: `:examples:loader-datapack:loaderExample -Dproofstone.cells=fabric@26.3`, one invocation each under the Docker
  lock, warm loader cache. Run 1 **RED before any server started** (`ParameterResolutionException`: `CellInvocation.boot` reads
  `proofstone.agentJar`, the Bukkit agent, before it branches to the loader host, so a loader run must set it though a loader cell
  never loads it; the example's build now sets it; a finding about the host, not changed here). Then 3 of 3 green, 1 test each,
  `skipped="0" failures="0" errors="0"`, loader boot 15.0, 14.5, 14.0 s, 0 join timeouts, no `moved too quickly`. User guide
  [mods-and-packs](../user/mods-and-packs.md) and troubleshooting entries added; `docs/platforms.md` keeps Fabric, NeoForge, data
  packs and resource packs `experimental`. Evidence: local copies of the XML and logs (not committed).
- **T-M8-11 negative controls (2026-11-15), run by hand, not in `scripts/release-mutation-check.sh` (asked in the PR).** Four
  red-by-design runs in `metaModTest` (`examples/bad-tests`), each judged by its own reason, none a boot or join timeout, and the
  report's verdict equal to the XML's: `fabric@26.3` and `neoforge@26.3`, a mod that throws at init: `INVALID`, naming
  `proofstone_example (proofstone-example-mod.jar)` and quoting the loader's line (Fabric `Could not execute entrypoint stage
  'server'`, host fails at once on `Failed to start the minecraft server`; NeoForge `FMLModContainer ... Failed to create mod
  instance`, the container exits, no `Failed to start FML` line seen, host fails on the exit); `paper@26.3`, a data pack whose
  function does not parse: `INVALID`, naming `file/broken-function.zip` and quoting `Failed to load function
  proofstone_broken:hello` and the parse error; `paper@26.3`, a required pack pushed with sha1 forty zeros while the test expects it
  loaded: `FAIL`, `statuses=[ACCEPTED, FAILED_DOWNLOAD]` and `hash mismatch: expected 000..., got <sha1>`, no kick (T-M8-10).
  Counted runs, one cell per invocation under the Docker lock, XML read (`tests=1 skipped=0 errors=0`, `failures=1` by design for
  every fault): `metaModFabricTest` 3 of 3 green, `metaModNeoForgeTest` 3 of 3, `metaModPaperTest` 3 of 3 (two faults each run).
  0 join timeouts, 0 boot timeouts, wall time per invocation 17 to 18 s (Fabric), 20 to 23 s (NeoForge), 59 s (Paper), all warm
  (loader downloads cached, agents built); `metaModTest` all four in one go 1 min 40 s warm. Seen in the pack-hash runs: the
  allowlisted harness-client `moved too quickly` WARN (not `PSConf`, not a failure). Mutation check of the meta test itself, one
  run each: a data pack whose function parses reads `no test produced INVALID` with the body's own failure; a pack pushed with its
  true hash reads `no test produced FAIL` (the test passes); the mod's config `fail=false` reads `no test produced INVALID`.
  Findings: (1) **a data pack with a broken function stays enabled**, so the declared-pack check passed it; the server logs the
  error at boot, before the agent's log window, so ADR-0017 section 4's "caught by the log guard" did not hold. The host's log of
  the whole boot is now read for WARN or worse lines that name a namespace of the pack (`DataPackLoad.errorsLogged`). (2) A failure
  before conformance (a mod crash, an unloaded or noisy data pack) was `FAIL` in `proofstone-report.json` while JUnit said `INVALID`;
  the report now says `INVALID`. (3) `@Proofstone` has no `mods` element (test-api.md section 13 lists it; T-M8-07 built the host
  side only), so the mod fault is a test-only switch, `-Dproofstone.injectModFault=crash-at-load`, which installs the example mod
  with `fail=true`; the card kept the API unchanged. The existing controls after the change, one run each, XML read: `metaTest`
  (288 s) all 8 verdicts (VACUOUS, FLAKY, DISAGREE, INVALID, INVALID, LEAK, FAIL, SKIPPED, the one skip reasoned), `metaTopologyTest`
  (454 s) 7 of 7 `INVALID`; 0 timeouts in either. Evidence: local copies of the XML, bundles and logs (not committed).
- **T-M8-10 resource packs (2026-11-15).** `:conformance:run -Dproofstone.cells=paper@26.3`, three separate invocations under the
  Docker lock, all green: 17 tests, 2 skipped (`FOLIA_REGIONS`, reasoned), 0 failures, 0 errors each, C-R01 and C-R02 included;
  0 join timeouts, no `moved too quickly`. Three development runs of C-R01/C-R02 alone came first. Dev run 1: accept, decline
  and wrong hash RED (`AwaitTimeoutException`, probe empty): **Paper 26.3 fires no `PlayerResourcePackStatusEvent` for the
  configuration-phase push from `server.properties`** (paper.md Gotchas), against ADR-0017 §7's assumption that the probe reads
  it; the scenarios now read the server's view from a play-phase push by the fixture plugin. Dev run 2: RED, the probe's events
  carried `getHash` = null and `CapturedEvent` (`Map.copyOf`) refused them with a NullPointerException, an existing bug for any
  event with a null getter; fixed in its own commit. Dev run 3 green. The driver side (record, answers, sha1 check, decline kick
  `multiplayer.requiredTexturePrompt.disconnect`, no answer refused at once) passed from dev run 1. Evidence: local copies of the
  XML, bundles and logs (not committed).
- **T-M8-09 data packs (2026-11-15).** C-D01 and C-D02, `:conformance:run` per cell, one cell per invocation under the Docker
  lock. First set: `paper@26.3` 3 of 3 green; `fabric@26.3` 1 of 3 green, **C-D02 RED in runs 2 and 3** (`DISAGREE`: the agent
  reads the diamond, the client's inventory is empty). **Cause (driver transcript of the red bundle):** the scenario sent the
  window close right after the result slot emptied; the server's update for the player's slot 45 (the diamond) reached the
  driver 1 ms after the close, and the driver ignores a closed window's packets as the vanilla client does. Not a driver bug:
  the scenario closed too early. C08 (same flow) can race the same way and has stayed green; not changed here. **Fix** (own
  commit): C-D02 awaits the diamond in the window's own player slots before closing; the dual-oracle check is unchanged.
  Fresh set after the fix: `paper@26.3` 3 of 3 green (13 tests, 2 skipped `FOLIA_REGIONS`, 0 failures, 0 errors),
  `fabric@26.3` 3 of 3 green (11 tests, 0 skipped, 0 failures, 0 errors). `neoforge@26.3`: C-D01 and C-D02 skipped with
  `missing capability DATAPACKS_VANILLA` (one invocation). Evidence: local copies of the XML, bundles and logs (not committed).
- **T-M8-01 spike (2026-11-14).** Fabric 0.20.1 and NeoForge 26.3.0.103 on 26.3 take the `mcpl` 777 client natively with
  no mod and with a server-only mod (Fabric also with Fabric API). Driver joins: Fabric 28 counted, 20 ok, 3 join
  timeouts, 5 refused; NeoForge 13 counted, 13 ok. Details in [fabric.md](../platforms/fabric.md) and
  [neoforge.md](../platforms/neoforge.md).
- **Join timeout, expected, stage `CONFIGURATION_STALLED` (3 of 3 on a required resource pack, Fabric).** The server
  pushes the pack in configuration and waits; the driver does not answer. No timeout on any mod set. For T-M8-10.
- **Refusal, not a timeout: a Fabric content mod shuts out the vanilla client.** With Fabric API and a mod that registers
  one item, 3 of 3 joins were kicked: `This server requires Fabric Loader and Fabric API installed on your client!`. M8
  on Fabric is limited to server-only mods and data packs unless a modded-client driver gets its own ADR. NeoForge
  admitted the client with one added item (3 of 3); content testing through it is unverified.
- **Refusal: Fabric on 1.21.11 rejects 777** (`multiplayer.disconnect.incompatible 1.21.11`, 2 of 2). Stonecutter is out
  for M8 (newest native row only).
- **Host finding, boot: a cold start always reaches Mojang** (Fabric launcher: `launchermeta.mojang.com`; NeoForge
  installer: `piston-meta.mojang.com`; both fail with `--network none`). A restart of an installed container needs no
  network (12 s Fabric, 11 s NeoForge). For T-M8-07.
- **Host finding, boot: a failed Fabric start hung the container** for the 600 s spike timeout, because the spike mod's
  non-daemon HTTP thread kept the JVM alive after `Failed to start the minecraft server`. For T-M8-05/06/07.
- **T-M8-06 NeoForge agent (2026-11-14).** Two dev runs before the counted ones: the first was a red failed boot, FML
  refused the shaded agent jar (`InvalidModuleDescriptorException`, a Jackson service file left unrelocated; fixed with
  `mergeServiceFiles()`, [neoforge.md](../platforms/neoforge.md) Gotchas); the test waited out the 5 min boot timeout, so
  a host should treat `Failed to start FML` as a failed boot at once (T-M8-07). Then 3 counted `NeoForgeAgentIT` runs, 3
  green, 3 of 3 driver joins, 0 join timeouts.
- **Log guard:** every NeoForge boot logs `Appender DebugFile` ERROR (`io.netty.channel.kqueue.Native`); every loader
  boot logs `No key layers in MapLike[{}]`. `PSConf moved too quickly!` was not seen.
- **T-M8-07 loader host (2026-11-14).** `LoaderHost` booted both rows with the agent and an example mod, three separate
  green cold starts each, 0 join timeouts (no join is made). Host boot times, container start to `Done (`, install
  included: Fabric 16.3, 16.5, 16.3 s; NeoForge 19.7, 19.5, 18.9 s. A failed start (the example mod throws) fails the cell
  after 15 s (Fabric, read `Failed to start the minecraft server`) and 17 to 18 s (NeoForge, the container exit), never the
  5 min boot timeout. Install hosts measured with a logging proxy as the only route out: see
  [fabric.md](../platforms/fabric.md) and [neoforge.md](../platforms/neoforge.md) "The loader host". `vanilla-sha256` is
  filled from the run (both loaders' vanilla jar is the same bytes, sha1 equal to Mojang's manifest).
- **T-M8-08 conformance on loader rows (2026-11-14).** `:conformance:run -Dproofstone.cells=<row>` through `LoaderHost`, three
  separate counted invocations per row, all 6 green: each 9 tests, `skipped="0" failures="0" errors="0"`. Join timeouts 0 of
  8 joins per run on both rows (loader count, apart from Paper and topology); boot timeouts 0 (Fabric boots 14.3 to 15.6 s,
  NeoForge 16.7 to 18.1 s, 24 each); `PSConf moved too quickly` 0. One dev run before the counted ones was a red: fabric C02
  failed because vanilla's `/help` has no `Help` header (a finding about the test's expectation; asserted as it is now,
  [fabric.md](../platforms/fabric.md)). Also asserted: the window-open player read is `501 NOT_SUPPORTED` on both agents.
  Rows stay `experimental`. ADR-0015 evidence (XML read, bridged = no, all native 777):

  | row | bridged | C01 | C02 | C03 | C04 | C05 | C06 | C07 | C08 | skips | runs |
  |---|---|---|---|---|---|---|---|---|---|---|---|
  | fabric@26.3 | no | pass | pass (vanilla `/help`) | pass | pass | pass | pass | pass | pass | none | 3 of 3 green |
  | neoforge@26.3 | no | pass | pass (vanilla `/help`) | pass | pass | pass | pass | pass | pass | none | 3 of 3 green |

- **Verifier run, T-M8-09 (2026-11-15): 1 red in 6 conformance invocations, new log-guard family on Fabric.** Paper 3 of 3
  green. Fabric `:conformance:run -Dproofstone.cells=fabric@26.3`: runs 1 and 2 green, **run 3 red** in
  `ConformanceSmokeTest` (C02, verdict: log guard, not a join or boot timeout, not C-D01 or C-D02):
  `WARN net.minecraft.world.entity.EntityType: Skipping Entity with id minecraft:zombie_villager`, logged by `Worker-Main-3`
  right after the join (random world, so a village zombie in a generated chunk near spawn; vanilla, not caused by the
  data-pack code). Seen once; not allowlisted, no card yet. XML and bundle kept aside (`C:\pw\t809-ev`, local only). The
  T-M8-09 Fabric acceptance is therefore **not ticked as 3 of 3**: the agent's fresh set was 3 of 3 green, the verifier set
  was 2 green and 1 red from this family. For the T-M8-99 review to decide: a card for the family, or a counted rate.

## M8 review (T-M8-99)

Date 2026-11-15. Ran on `card/T-M8-99-review` from origin/main `d89cb4e` (PRs #115 to #126 merged; M8 diff `18432ae..d89cb4e`).
Local runs only, Windows 11, Docker Desktop 29.9.2, JDK 21 for Gradle, JDK 26 for Maven. One Docker run at a time under
`scripts/dockerlock.sh`, `--no-parallel --max-workers=1`, EULA in the command environment only. Every run's XML, bundles and log
copied aside before the next run (local only, not committed). Every XML read; no red retried. Judged from code and XML, not
from PR text.

**Verdict: M8 exit criterion PARTLY MET.** Met from runs: C01 to C08 on `fabric@26.3` and `neoforge@26.3`, the data-pack scenario
on Paper and Fabric (and its reasoned skip on NeoForge), the resource-pack prompt, answer and status on Paper, the three negative
controls, and the flake rule for those. **Not met: item 2**, because a test cannot declare a mod under test through the public API
(`@Proofstone(mods)` does not exist; card T-M8-13). The scope is also narrower than the epic: server-only mods only (a Fabric
content mod kicks the vanilla client), NeoForge content unverified, newest row only (no Stonecutter). The full four-cell smoke is
red again on the `PSConf moved too quickly!` family (older than M8, T-M7-15). Both loader rows stay `experimental`. Nothing was
published or tagged.

### Evidence from this run

| Run | Result |
|---|---|
| `bash scripts/gates.sh --max-workers=1` at `d89cb4e` | `gates: OK`, exit 0 (release-gate-test 9/0, bad-tests-pin-check 0 failed, renovate unchanged) |
| `:conformance:smoke`, no selector: cells paper, folia, purpur, spigot @26.3 | **RED**: tests 8, failures 3, errors 0, skipped 0. `c02` failed on folia, purpur and spigot @26.3: `ErrorLogGuard` WARN `PSConf moved too quickly! 0.5,64.0,0.5`. paper@26.3 c01, c02 green; c01 green on all four. Not a join timeout. Bundles kept (lead for T-M7-15 below) |
| `:conformance:run -Dproofstone.cells=fabric@26.3`, 3 separate invocations | 3 of 3 green, each tests 11, skipped 0, failures 0, errors 0 (C01 to C08, C-D01, C-D02, loader window test); 183 to 188 s each |
| `:conformance:run -Dproofstone.cells=neoforge@26.3`, 3 separate invocations | 3 of 3 green, each tests 11, skipped 2 (C-D01, C-D02: `missing capability DATAPACKS_VANILLA on cell neoforge@26.3`), failures 0, errors 0; 207 to 212 s each |
| `:conformance:run -Dproofstone.cells=velocity@4.3.1+paper@26.3`, 3 separate invocations | 3 of 3 green, each tests 2, skipped 0, failures 0, errors 0 (T00, P01 to P05); 79 to 80 s each |
| `:conformance:run -Dproofstone.cells=bungeecord@2111+paper@26.3`, 3 separate invocations | 3 of 3 green, each tests 2, skipped 0, failures 0, errors 0; 76 to 77 s each |
| `:conformance:run -Dproofstone.cells=paper@26.3`, 3 separate invocations | 3 of 3 green, each tests 17, skipped 2 (`FOLIA_REGIONS`, reasoned), failures 0, errors 0, C-D01, C-D02, C-R01, C-R02 included; 382 to 383 s each |
| `:examples:bad-tests:metaTest` | BUILD SUCCESSFUL, "every bad test produced its expected verdict (VACUOUS, FLAKY, DISAGREE, INVALID, INVALID, LEAK, FAIL, SKIPPED)", 4 m 47 s; tests 17, failures 9 and skipped 2 by design |
| `:examples:bad-tests:metaTopologyTest` (7 faults, one cell each) | BUILD SUCCESSFUL, "every bad test produced its expected verdict": 7 of 7 `INVALID`, each `tests=1 failures=1` by design, 0 `timed out joining`; 7 m 33 s |
| `:examples:bad-tests:metaModFabricTest` | BUILD SUCCESSFUL, expected verdict `INVALID`: `mod proofstone_example (proofstone-example-mod.jar) crashed the server at load on fabric@26.3`, quoting `Could not execute entrypoint stage 'server'`; tests 1, failures 1 by design, skipped 0, errors 0; 20 s |
| `:examples:bad-tests:metaModNeoForgeTest` | BUILD SUCCESSFUL, expected verdict `INVALID`: the same mod named on neoforge@26.3, quoting `FMLModContainer`; tests 1, failures 1 by design; 23 s |
| `:examples:bad-tests:metaModPaperTest` | BUILD SUCCESSFUL, expected verdicts `INVALID` (`data pack file/broken-function.zip logged an error while the server loaded it: ... Failed to load function proofstone_broken:hello`) and `FAIL` (pack pushed with hash forty zeros, the driver's record quoted); tests 2, failures 2 by design; 1 m |
| `:examples:loader-datapack:loaderExample -Dproofstone.cells=fabric@26.3` | Green: tests 1, skipped 0, failures 0, errors 0; loader boot 14.9 s; the allowlisted harness-client `Tc133d056 moved too quickly!` seen, not `PSConf`; 26 s |
| `bash scripts/release-mutation-check.sh` (no flag), first run | PASS, head `d89cb4e`, cell paper@26.3, killed 4 of 4, but **`dirty=true`**: this review's doc edits were in the worktree while it ran (a process error, docs only). Not counted as the release PASS |
| `bash scripts/release-mutation-check.sh` (no flag), clean detached worktree at `d89cb4e` | **PASS**: head `d89cb4e`, dirty false, cell paper@26.3, killed 4 of 4 (Gradle and Maven: `double-output`, `accept-any-right`), no allowed-missing. Both baselines PASS (each example's e2e suite once, green) |
| Containers of ours left running after the runs | none (checked after every run; only Testcontainers' reaper). Other projects' containers untouched |

The runs used a fresh worktree, so the first loader run filled the harness download cache (`build/loader-cache`, launcher,
installer and Fabric API by sha256). Each loader container installs inside itself on every boot (ADR-0017 §6), so by design every
loader boot here fetched from Mojang and the loader mavens; the outbound hosts were not re-measured in this review (T-M8-07 did).

### Loader flake numbers (counted apart from Paper and topology, M8 flake rule 4)

Each loader `:conformance:run` boots one container per test class (10 per invocation).

| Source | Invocations | Red | Loader boots | Boot timeouts | Join timeouts | Log-guard reds (family) |
|---|---|---|---|---|---|---|
| T-M8-01 spike (by hand) | 41 driver joins | 3 join timeouts, explained (required pack, no answer: `CONFIGURATION_STALLED`), 5 refusals by design | n/a | 0 | 3 (all the resource-pack stall, before T-M8-10) | 0 |
| T-M8-05/06 ITs (counted) | 6 | 0 (1 dev red before: FML refused the shaded jar, fixed) | not recorded | 0 counted (the dev red waited out 5 min) | 0 | 0 |
| T-M8-07 host | 6 | 0 | 6 | 0 | n/a (no join) | 0 |
| T-M8-08 conformance | 6 | 0 (1 dev red before: vanilla `/help`, test expectation) | 48 | 0 | 0 | 0 |
| T-M8-09 data packs (loader runs) | Fabric 3 first set + 3 fresh (agent) + 3 (verifier), NeoForge 1 | 2 C-D02 `DISAGREE` in the first set (scenario closed the window too early, fixed); **1 verifier red: `zombie_villager` WARN** | not recorded | 0 | 0 | **1 `zombie_villager`** |
| T-M8-11 meta mod tasks | 6 loader (3 Fabric, 3 NeoForge) | 0 unexpected (INVALID by design) | 6 | 0 | 0 | 0 |
| T-M8-12 example | 4 Fabric | 1 before any server (`proofstone.agentJar` read on the loader path) | 3 | 0 | 0 | 0 |
| **T-M8-99 (this run)** | **6 conformance + 2 meta + 1 example** | **0** (the 2 meta reds are the expected `INVALID`) | **63** (60 conformance, 1 example, 2 meta starts that crash at load by design) | **0** | **0** | **0** |

- **Loader join timeouts: 0** in every counted M8 harness invocation; the only loader join timeouts ever seen are the 3 spike
  joins against a required resource pack the driver did not answer (fixed by T-M8-10: no declared answer ends the join at once).
- **Loader boot timeouts: 0** counted. This run's boot times, container start to `Done (`, install included: Fabric 14.1 to 15.1 s
  (30 boots), NeoForge 16.9 to 18.1 s (30 boots).
- **Loader log-guard reds: 1 in 22 recorded loader `:conformance:run` invocations since T-M8-08** (T-M8-08 6, T-M8-09 10, this
  review 6), the `Skipping Entity with id
  minecraft:zombie_villager` worldgen WARN (T-M8-09 verifier, Fabric C02). 0 in this review (6 loader conformance invocations, 60
  boots). Lead: loader cells set no `SEED` (`LoaderEnvironment.env`), Paper pins `SEED=1234567` (`PaperHost`), so loader worlds
  differ per boot. Card T-M8-16 (diagnose first). `PSConf moved too quickly!` on loader cells: **0** in every M8 run.

**Paper and topology numbers (apart):** topology 6 of 6 green, 0 join timeouts (first join or switch); `paper@26.3` 3 of 3 green, 0
join timeouts. Single-server smoke: `PSConf moved too quickly!` 3 of 8 smoke tests red (folia, purpur, spigot @26.3), the same three
cells as the M7 review and "M6 pre-release findings" item 2: **3 red of 4 recorded full four-cell smoke runs**. **Lead for T-M7-15
(not a cause yet):** in the folia bundle, the first `PSConf` join of the class spawns at the world spawn (`-9.5, -60.0, 6.5`), the
fixture teleports it to `0.5, 64.0, 0.5`; the class's next `PSConf` join logs in **at the saved position `0.5, 64.0, 0.5`** (the
player file persisted between tests) and the WARN fires on that login, before the test body. The topology client got a new
`T<hex>` name per test (T-M7-12), so it never logs in at a saved position.

### Exit criterion, re-scored

| # | Item | Status | Evidence |
|---|---|---|---|
| 1 | A Fabric cell and a NeoForge cell, newest row the `mcpl` client joins natively, boot with their agent and pass C01 to C08, skips reasoned | **MET** (rows `fabric@26.3`, `neoforge@26.3`, native 777, not bridged) | This run: 3 of 3 green per row, C01 to C08 passed every time, 0 skips in C01 to C08. Earlier T-M8-08 3 of 3 per row. C02 on a loader asserts vanilla `/help` (`/help [<command>]`), not the Bukkit `Help` header: a different expectation per family, documented, not weaker. The window-open player read is `501 NOT_SUPPORTED` on both agents (asserted) |
| 2 | A test can put a mod under test (`/mods`), a data pack and a resource pack into a cell through the public API, additively to API v0 | **NOT MET** (2 of 3 artefacts) | Data packs: `@Proofstone(datapacks)`. Resource packs: `@ServerResourcePack`, `@ResourcePackAnswer`, `Client.resourcePacks()`. **Mods: no `@Proofstone(mods)` and no `Capability.MODS`** (api-baseline at `d89cb4e`); `CellInvocation.bootLoader` passes an empty mod list; the only mod that ever reaches `/mods` besides the agent and Fabric API is the example mod behind the test-only switch `-Dproofstone.injectModFault`. test-api.md §13 documents `mods` as if it existed; the user guide says plainly it does not. Additive: `api-baseline.txt` diff 33 lines added, 0 removed (`DATAPACKS_VANILLA`, `RESOURCE_PACKS`, `Platform.FABRIC`/`NEOFORGE`, `Proofstone.datapacks`, `ResourcePackAnswer`, `ResourcePackPush`, `ServerResourcePack`, `Client.resourcePacks` default). Card T-M8-13 |
| 3 | A data-pack scenario (function result and recipe) passes on Paper and on Fabric, skipped with the capability named where `DATAPACKS_VANILLA` is missing | **MET, with a recorded red** | This run: C-D01 and C-D02 green 3 of 3 on `paper@26.3` and 3 of 3 on `fabric@26.3`; skipped 3 of 3 on `neoforge@26.3` naming `DATAPACKS_VANILLA`. Counted across M8 after the scenario fix: Fabric 8 of 9 invocations green (agent 3/3, verifier 2/3, this review 3/3), the one red the `zombie_villager` log-guard family on C02 in the same invocation, not C-D01 or C-D02. Paper 9 of 9. The T-M8-09 acceptance box stays unticked for that red; this row counts it, not rounds it up |
| 4 | The driver reports a resource-pack prompt (URL, hash, required flag), answers as the test says, and the server-side status is read by the agent; no rendering | **MET on Paper only, with a changed mechanism** | This run: C-R01 and C-R02 green 3 of 3 on `paper@26.3`; T-M8-10 3 of 3; `metaModPaperTest` wrong-hash `FAIL` (`statuses=[ACCEPTED, FAILED_DOWNLOAD]`). **ADR-0017 §7's assumption failed:** Paper 26.3 fires no `PlayerResourcePackStatusEvent` for the configuration-phase push from `server.properties`, so the server-side status is read from a **play-phase push by the fixture plugin**; the configuration-phase push is checked on the driver side only. Loader cells do not hold `RESOURCE_PACKS` (no read-only status hook). ADR amendment: T-M8-17 |
| 5 | A broken mod, a data pack that fails to load and a wrong-hash resource pack are each a loud red | **MET** (the broken mod enters only through the test-only fault switch) | This run, one invocation each, XML read: crash-at-load mod `INVALID` naming the mod on `fabric@26.3` and `neoforge@26.3`; broken-function data pack `INVALID` naming the pack and quoting the server; wrong-hash pack `FAIL` with the driver's statuses; none a boot or join timeout. Earlier T-M8-11 3 of 3 per task. The broken data pack is caught by the host reading its own boot log, not by the log guard (ADR-0017 §4 assumption failed; T-M8-17). Because `mods` does not exist, a user's broken mod cannot be declared at all (item 2) |
| 6 | Flake rule evidence for every acceptance above | **MET for items 1, 3 (with the red counted), 4, 5; not applicable to item 2's missing part** | Three separate green invocations per row per acceptance in the cards and again here; no red replaced by a green; reds listed above with causes, one (zombie_villager) without a cause yet |

### Known gaps, stated plainly

- **Server-only mods only.** A Fabric mod that adds a synced registry entry gets the vanilla `mcpl` client kicked at join (T-M8-01,
  3 of 3). NeoForge admitted the client with one added item in the spike, but **NeoForge content mods are unverified**: no run read
  a mod-added item through both oracles.
- **`@Proofstone(mods)` does not exist** (item 2). M8 proves the loader host can run mods; it does not let a user test theirs.
- **T-M8-09 Fabric acceptance had a red** from the `zombie_villager` family; it is counted, not hidden (item 3).
- **Paper fires no configuration-phase resource-pack status event** (ADR-0017 §7 assumption failed; item 4).
- **The data-pack log-guard assumption failed** (ADR-0017 §4): the host's boot log is read instead (`DataPackLoad.errorsLogged`).
- **Mojang at cold start (ADR-0017 §6)** is a listed, content-pinned exception to "no Mojang dependency in runs": every loader boot
  installs inside its container and fetches Mojang's metadata, the vanilla jar and loader libraries. **This is a maintainer veto
  point that was never answered** (decision 2 below). Library jars fetched by the installers are not pinned one by one.
- **Loom and ModDevGradle are not used** (ADR-0017 §5 named them). Both agents compile with plain `javac` against pinned jars:
  the vanilla 26.3 server jar, Fabric loader and Fabric API, and six NeoForge-side jars (NeoForge universal, FML loader, bus,
  mergetool api, maven-artifact, typetools for tests), each checked by sha256 at build time. The fallback §5 allows; the ADR text
  still names the plugins (T-M8-17).
- **Diff sizes over the 400-line guide** (non-test, non-doc lines per PR): #116 682 (spike scripts), #118 617, #119 1452, #120 1422,
  #121 972, #123 493, #124 838, #125 438. #120, #121 and #124 said so in the PR; #118, #119, #123 and #125 did not. No split was made.
- **About 950 duplicated lines** between the Fabric and NeoForge agents (`MinecraftView`, `ItemJson`, `TickCounter`, `ServerThread`,
  `Values`, `GameView`, test `MockView`, `AgentClient`; 2 to 44 changed lines per file). Card T-M8-15.
- **`proofstone.agentJar` is required on the loader path** though no loader loads it (T-M8-12 run 1). Card T-M8-14.
- **No leak bad test on a loader cell** (robustness R6). Card T-M8-18.
- **Card boxes:** T-M8-01, 08, 09, 10 and 11 acceptance boxes are still unticked in `M8-mods.md` though merged. Left as they are;
  the evidence above is what counts.

### What is NOT verified

- A user-declared mod in `/mods` through the API (does not exist).
- Any content mod on NeoForge reaching the client and both oracles; any content mod on Fabric (refused by design).
- Any loader row other than 26.3; any bridged loader row (refused by the planner).
- Data packs on NeoForge (the agent serves no `/v1/datapacks`); resource packs on any loader cell.
- A `LEAK` verdict on a loader cell; a differential run on a loader cell (refused: only `mcpl` joins).
- A cold loader start without network (fails by design, ADR-0017 §6); a Mojang or maven outage (never seen).
- The cause of the `zombie_villager` WARN and of `PSConf moved too quickly!` (leads only).
- The Gradle and Maven examples were run only as the mutation check's baselines.

### Code review (diff `18432ae..d89cb4e`, 223 files, +13863 / -147)

- **Weakened tests: none found.** Changed test files: C03 to C08 and `ConformanceSmokeTest` add the loader `@Matrix` rows; C02 gains a
  per-family expectation (vanilla `/help [<command>]` on loaders, `Help` on Bukkit), the assertion kept. `AgentContractIT` lists
  `mods/list` as pending (Bukkit agents never serve it, reasoned) and replays `datapacks/list` in its own case, which asserts a 404,
  not an empty list, where the server has no data-pack API. `TopologyApiTest` extends its "added after the existing ones" check to
  the new constants. `CapturedEvent` (API, main code): a getter that returned `null` is now a captured value (`field` returns null)
  instead of being reported as "no getter"; a missing getter still fails loudly (T-M8-10, own commit). Behaviour change for a
  caller that relied on the old exception; signature unchanged.
- **New runtime dependencies: none.** Fabric loader, Fabric API, NeoForge, FML, bus, mergetool, maven-artifact and the vanilla
  server jar are compile-only, pinned by version and sha256 in `gradle/libs.versions.toml`; typetools is test-only. The agents shade
  only `agent-core` and Jackson, relocated (NeoForge also merges service files). Repositories are `exclusiveContent` per group.
- **Workflows: none.** `.github/` holds only the issue and PR templates.
- **Secrets: none committed.** No key, token, jar or Mojang binary in the diff; the vanilla jar is fetched by sha1 content address at
  build time into `build/` and never shaded or published.
- **Sleeps and retries:** none in harness or test code.
- **API v0 additive only:** yes (item 2 row).
- **Observe-only, log guard per loader, leak detector:** `docs/robustness.md` §5 "Loader review (T-M8-99)".

### Decisions needed from the maintainer

1. **Accept item 2 as not met and approve T-M8-13** (`@Proofstone(mods)`, `Capability.MODS`) before M8 is called done, or accept M8
   closed with the gap. Recommendation: approve T-M8-13; it is small, the host side exists.
2. **ADR-0017 §6, Mojang at cold start:** the veto point flagged in T-M8-03 was never answered. Keep the content-pinned exception, or
   ask for an install cache card. Recommendation: keep it until a CDN failure is seen; every boot here passed.
3. **Approve `docs/plan/tasks/M8-followups.md`** (T-M8-13 to T-M8-18) and the T-M7-15 lead above.
4. **Do not promote the loader rows yet.** ADR-0015's C01 to C08 three-green evidence is met for both rows, but the `zombie_villager`
   family is open and the row cannot take a user mod. Promotion after T-M8-13 and T-M8-16.
5. **Approve `docs/plan/tasks/M9-bedrock.md`** (13 cards, two spikes first). Nothing in M9 is started.
6. Unchanged from M7: Central namespace verification, Disenchantment CI, publish, tag, 0.1.0 go/no-go.

### Re-estimate

M8 was estimated at 4 focused weeks (range 2 to 5). Actual: first M8 merge 2026-11-14 13:42, last 2026-11-15 06:58 (+01:00), about
17 hours of agent wall clock for 12 cards and 32 non-merge commits, plus this review (about 70 minutes of Docker runs, plus the mutation re-run). The spike's
"the vanilla client joins" answer removed the modded-driver branch that the range allowed for. Remaining:

| M | Estimate | Reason |
|---|---|---|
| M8 follow-ups | **about 2 days** | 6 cards, 3 diagnosis or docs; T-M8-13 is the only one that closes an exit item |
| M9 Bedrock | **4 weeks after the spikes, range 2 to 6** (centre unchanged) | Offline Bedrock auth, a second protocol family over UDP, a beta Script API, a possible new image. If the Geyser spike shows no offline route, M9 shrinks to about 1 week |
| M10 Ecosystem | ongoing | Unchanged; refined by T-M9-99 |

Total M8 follow-ups to M10 is about 4 to 6 weeks plus ongoing ecosystem work (was 8+ before M8).

### M9 refined

`docs/plan/tasks/M9-bedrock.md`: exit criterion, context (what M8 left, the simulated tier, offline auth, UDP, BDS licensing), Flake
rule, 13 cards (T-M9-01 Geyser spike and T-M9-02 BDS spike first, ADR-0018, schema and SPI, driver, Geyser host, API, conformance, BDS
host and conformance only if the BDS spike says feasible, negative controls, docs, review). **Not approved; nothing started.**

## M7 follow-up findings (T-M7-15)

Date 2026-11-15. Ran on `card/T-M7-15-psconf-diagnosis` from origin/main `704839a`. Local only, Windows 11, Docker Desktop
29.9.2, JDK 21 for Gradle. One cell selector per invocation under `scripts/dockerlock.sh`, `--no-parallel --max-workers=1`, EULA
in the command environment only. Every run's XML, bundles and log copied aside before the next run (local only, not committed).
No red retried. No code changed.

**Cause (named, from log lines): the four "cells" of the full smoke run on one Paper server.** `CellInvocation.resource` keys the
class-shared host (`PER_CLASS`) by `"host:" + cell.mc()`. All four smoke rows are `@26.3`, so the first cell (`paper@26.3`) boots
Paper and `folia@26.3`, `purpur@26.3` and `spigot@26.3` reuse that server (`TopologyInvocation` keys by `cell.identity()` and is
not affected). The per-cell conformance check (`CellConformance`, cached per cell id) then joins `PSConf` once per cell on that
same server: the first join spawns at world spawn, C03's fixture teleports it to `0.5 64 0.5`, the player file stays in the
container's `/data/world`, and the next cell's `PSConf` logs in there and Paper WARNs. The WARN fires inside the next cell's c02
test window, so the log guard fails c02 on cells 2 to 4. The red cells are fixed by order (`@Matrix` order paper, folia, purpur,
spigot), not by platform. Evidence:

- Every full-smoke bundle (`folia`, `purpur`, `spigot`) holds the same `server.log`, starting `[init] Resolving type given PAPER`
  and `Downloaded /data/paper-26.3-244.jar`; each `agent-transcript.jsonl` answers `/v1/health` with `"platform":"paper"` though
  `cell.json` says `folia`, `purpur` or `spigot`. One server boot per run (40 to 55 s for 8 tests; one cell alone 29 to 37 s).
  `PROOFSTONE CONFORMANCE folia@26.3: passed` is printed for a check that ran on Paper.
- Log (full-1, spigot bundle): `PSConf[...] logged in with entity id 1 at ([minecraft:overworld]10.5, -60.0, 2.5)` (no WARN), then
  `PSConf[...] logged in with entity id 3 at ([minecraft:overworld]0.5, 64.0, 0.5)` followed by
  `WARN]: PSConf moved too quickly! 0.5,64.0,0.5`, and the same at entity ids 5 and 7. 4 `PSConf` joins per run, the first never
  WARNs (0 of 5), every later one does (15 of 15).
- Direct test without code change (throwaway, labelled, not counted below): `-Dproofstone.cells=purpur@26.3,spigot@26.3`, 2 runs,
  with `docker logs` saved by a read-only watcher: one container, `Resolving type given PURPUR`, `spigot@26.3` c02 red on
  `PSConf moved too quickly! 0.5,64.0,0.5` both times (tests 4, failures 1, errors 0, skipped 0). `folia@26.3,spigot@26.3`, 3 runs:
  one container, `Resolving type given FOLIA`, the second `PSConf` logs in at `0.5, 64.0, 0.5` and Folia logs no WARN (3 of 3
  green, tests 4, failures 0). So the second login at the saved spot WARNs on a Paper or Purpur server and not on Folia; the
  shared server is what makes a second `PSConf` login happen at all.
- Ruled out: a Paper-only `moved-too-quickly` threshold (the harness writes no `spigot.yml` or `paper-global.yml` for single-server
  cells; every red ran on the same Paper server anyway), the reset teleport of a test client (the WARN is on the conformance
  client's login, before the test body), and a platform defect in Folia, Purpur or Spigot (each alone: 0 of 5 red).
- Why the WARN fires on the login at a saved spot but not on the first, cold join is not proven here (the first join is the
  server's first; later joins are on a warm server). It does not matter for the fix: one server per cell means one `PSConf` login
  per server. The topology client of T-M7-12 hit the same "second login of one name on one server" shape (lobby, switch back).

**Consequence beyond the WARN.** The full smoke never booted Folia, Purpur or Spigot: their c01 and c02 "passes" in every recorded
full smoke (M5 review green, and the green c01s since) ran on Paper. Any user class with two `@Matrix` platforms of one mc version
run together is affected the same way. Per-cell `:conformance:run` invocations (one selector each, as the reviews ran them) are not
affected. Earlier notes that harness `T<hex>` clients log `moved too quickly!` on Folia came from smoke logs of this shared Paper
server; in the folia-first experiment above no client WARNed.

| Invocation | Runs | Red | XML per run | Red tests |
|---|---|---|---|---|
| `:conformance:smoke`, no selector (paper, folia, purpur, spigot @26.3) | 5 | **5** | tests 8, skipped 0, failures 3, errors 0 | c02 on folia, purpur, spigot, every run: `PSConf moved too quickly! 0.5,64.0,0.5`; paper c01, c02 and all c01 green |
| `-Dproofstone.cells=folia@26.3` | 5 | 0 | tests 2, skipped 0, failures 0, errors 0 | none |
| `-Dproofstone.cells=purpur@26.3` | 5 | 0 | tests 2, skipped 0, failures 0, errors 0 | none |
| `-Dproofstone.cells=spigot@26.3` | 5 | 0 | tests 2, skipped 0, failures 0, errors 0 | none |
| `-Dproofstone.cells=paper@26.3` | 5 | 0 | tests 2, skipped 0, failures 0, errors 0 | none |

Per cell over the 5 full runs: paper 0 of 5 red, folia 5 of 5, purpur 5 of 5, spigot 5 of 5. With the earlier records the full
four-cell smoke is now 8 red of 9 (M5 review green; the shared-server cause was already in the code then, `"host:" + cell.mc()` at
`577480f`, so that green was a login that did not WARN, not a different setup). Containers left after each run: none.

**Fix card proposed:** T-M7-17 in [M7-followups.md](tasks/M7-followups.md) (key the shared host by the full cell, test-first with a
unit test and a C01 platform check; no allowlist, sleep, retry or timeout). **Done in T-M7-17, next section.** Side note, not this
card: the itzg image downloads `paper-global.yml`, `paper-world-defaults.yml` and `spigot.yml` at boot from an unpinned GitHub
branch (`Downloaded /data/config/paper-global.yml from https://raw.githubusercontent.com/Shonz1/minecraft-default-configs/main/...`).

## T-M7-17 host key per cell

Date 2026-11-15. Branch `card/T-M7-17-host-key-per-cell` from origin/main `d49c1da`. Local only, Windows 11, JDK 21 for Gradle, one
invocation at a time under `scripts/dockerlock.sh`, `--no-parallel --max-workers=1`, EULA in the command environment only. Every
run's XML, bundle and `docker logs` copied aside before the next run (local only, not committed).

**Change.** `CellInvocation.hostKey(cell)` keys the class-shared host by platform, mc, build, Java tag and jar sha256 (was
`"host:" + cell.mc()`); `differentialKey` is keyed by platform and mc (was mc only). `TopologyInvocation` already keyed by
`cell.identity()` and `LoaderHost` cells go through the same `hostKey` (platform is the loader id), so no change there. C01 platform
check: the harness reads `/v1/health` at readiness already, so `CellInvocation.resource` now compares `health.platform()` with the
cell's platform on every use of the shared host and throws `INVALID: cell folia@26.3 is running on a paper server ...`. Chosen over
asserting the `version` command reply because `Agent.command` replies are best effort and never asserted (ADR-0002), and `Agent` has
no platform getter (no API change). It fails every test of the mismatched cell, c01 included.

**Before (main plus the unit test and the check, no key fix, commit `6185e6c`).** `HostKeyTest`: tests 5, failures 3. Full
`:conformance:smoke`: tests 8, skipped 0, failures 6, errors 0 (c01 and c02 on folia, purpur, spigot, all `INVALID: cell
<platform>@26.3 is running on a paper server`; paper green).

**After (fix).** `HostKeyTest`: tests 6, failures 0.

| Invocation | Result | XML | Server types in container logs |
|---|---|---|---|
| `:conformance:smoke` full, run 1 | green | tests 8, skipped 0, failures 0, errors 0 | not captured (green run writes no bundle, no watcher yet) |
| full, run 2 | **red** | tests 8, skipped 0, failures 1, errors 0 | bundle: purpur, `Resolving type given PURPUR`; c02 purpur red: `PSConf moved too quickly! -4.5,-60.0,4.5` (see finding) |
| full, run 3 | green | tests 8, skipped 0, failures 0, errors 0 | not captured (no watcher yet) |
| full, runs 4, 5, 6, 7 (watcher added) | 4 green | tests 8, skipped 0, failures 0, errors 0 each | runs 4 and 7: PAPER, FOLIA, PURPUR and CUSTOM (Spigot jar) all four; run 5: three of four; run 6: none (the 2 s `docker logs` poll missed short-lived containers) |
| each cell alone (`paper`, `folia`, `purpur`, `spigot` @26.3) | 4 green | tests 2, skipped 0, failures 0, errors 0 each | paper PAPER, purpur PURPUR, spigot CUSTOM; folia not captured |
| `:conformance:run` `fabric@26.3` | green | 10 suites, failures 0, errors 0, skipped 0 | n/a |
| `:conformance:run` `neoforge@26.3` | green | failures 0, errors 0, 2 skipped (CD01, CD02: `missing capability DATAPACKS_VANILLA`, reasoned) | n/a |
| `:conformance:run` `velocity@4.3.1+paper@26.3` | green | tests 2 and 2, skipped 0, failures 0, errors 0; T00, P01 to P05 passed | n/a |

The card's "5 separate invocations, each green" is **not met as stated**: 7 full invocations, 6 green, 1 red (a finding below). 0
`PSConf moved too quickly!` on a shared Paper server; 0 in the 6 green runs. Server types were not captured in runs 1 and 3 (a green run writes
no bundle); in runs 4 and 7 all four server types booted, one container each.

**New finding (open, not fixed here): `PSConf moved too quickly!` on the first login of a fresh Purpur server.** Full run 2, purpur
bundle: `Done (5.912s)`, then `PSConf ... logged in with entity id 1 at ([minecraft:overworld]-4.5, -60.0, 4.5)` and, in the same
millisecond, `WARN: PSConf moved too quickly! -4.5,-60.0,4.5`, one login on a server that is Purpur's own (`Resolving type given
PURPUR`, no shared server, no saved player file). So the T-M7-15 note that the first `PSConf` join never WARNs was Paper-only. Count after
the fix: Purpur c02 red 1 of 8 real runs (7 full, 1 alone); Folia, Spigot and Paper 0 of 8 each (Folia and Spigot c02 now really run). The
mechanism is not diagnosed: the log shows the player logged in at `-4.5 -60.0 4.5` (not the `0, 64, 0` spawn set at boot) and the
WARN on that login, before the fixture teleport. Not allowlisted, not worked around. Decision needed: a card to diagnose it (counted Purpur-only runs) before any harness-side change.
**Diagnosed in T-M7-18, next section.**

## M7 follow-up findings (T-M7-18)

Date 2026-11-15. Branch `card/T-M7-18-purpur-psconf-diagnosis` from origin/main `ddb8442`. Local only, Windows 11, Docker Desktop
29.9.2, JDK 21 for Gradle. One invocation at a time under `scripts/dockerlock.sh`, `--no-parallel --max-workers=1`, EULA in the
command environment only. Per run: XML, bundle and `docker logs -t` of every container copied aside before the next run (local
only, not committed). No red retried. No harness code changed. Spike: `scripts/spike/AckRace.java` and `ack-race.sh` (labelled).

**Cause (named, from bytecode and log lines): the client's join-teleport acknowledgement is handled before the player's first
server tick, so the movement check measures the move from `0, 0, 0`.**

- Paper and Purpur 26.3 (`javap` of `ServerGamePacketListenerImpl` in the pinned jars): `firstGoodX/Y/Z` are written only by
  `resetPosition()`, which runs at the start of `tickPlayer()` (the player's tick), in `handleMovePlayer` when `tickCount == 0`,
  and on respawn. In 26.3 the accept-teleport packet carries a position, and `handleAcceptTeleportPacket` passes it straight to
  `handlePlayerPositionChange`, which computes the move as `position - firstGood` **without** the `tickCount == 0` reset that
  `handleMovePlayer` has. Before the first tick `firstGood` is still `0, 0, 0`, so the move is the whole spawn position (at least
  60 blocks: the flat world's surface is `y -60`), and the server logs `moved too quickly!` with the spawn coordinates and
  teleports the player back.
- Every WARN in this card's harness logs has numbers equal to that player's login position (21 of 21 WARN lines), for example
  `PSConf ... logged in ... at ([minecraft:overworld]-8.5, -60.0, -5.5)` then `PSConf moved too quickly! -8.5,-60.0,-5.5` (full
  run 3). The T-M7-15 WARN `0.5,64.0,0.5` was the same at a saved spot.
- The mcpl driver sends `ServerboundAcceptTeleportationPacket` at once on the join `ClientboundPlayerPositionPacket`
  (`McplSession.applyPosition`). The T-M7-17 red bundle's driver transcript (the `T` client) shows the order: accept at
  `.966997`, a second `ClientboundPlayerPositionPacket` (the server's correction) at `.968629`, and the first-tick packets
  (`ClientboundSetHealthPacket`, `ClientboundSetExperiencePacket`) only at `45.008`, 40 ms later.
- Spike, direct test (`ack-race.sh`: one fresh server, 20 joins, modes alternating; I = acknowledge at once, as the driver does;
  T = acknowledge only after the first `ClientboundSetHealthPacket`, which the player's first tick sends):

  | Server | I joins with WARN | WARN lines (I) | T joins with WARN |
  |---|---|---|---|
  | purpur@26.3 (build 2657) | 9 of 10 (all but the first join) | 54 | **0 of 10** |
  | paper@26.3 (build 244) | 9 of 10 (all but the first join) | 54 | **0 of 10** |

  The first join of each server (cold server and cold client: 17 to 20 ms from the login packet to the position packet, against
  about 1 ms later) did not WARN, as `PSConf`, the first login of every cell, usually does not.
- Why only Purpur in the harness. Folia and Spigot did not WARN in any run (Folia: 0 of 5 `PSConf`, 0 of 5 `T` logins; it has
  the same accept code, so its scheduling differs, not proven how. Spigot's check is CraftBukkit's wall-clock
  `allowedPlayerTicks`, whose `lastTick` starts at the server tick count, not milliseconds / 50, so the first move gets a huge
  allowance). Paper and Purpur share the code. The full smoke always runs paper, folia, purpur, spigot, so Paper's `PSConf` is the
  test JVM's first client and Purpur's comes on a warm JVM: Purpur's `PSConf` logged in 1.35 to 1.66 s after `Done` in the full
  runs and 2.11 to 2.52 s alone. Reds follow that: Purpur c02 red in 2 of 12 full runs (T-M7-17 and here), 0 of 11 alone. That
  is a correlation with small counts; which warm-up (client or server) usually closes the race on a first login is not proven.
- The harness `T<hex>` clients hit the same race on every Paper and Purpur login (20 of 20 here); the guard allows them
  (`^T[0-9a-f]{8} moved too quickly! `). The allowlist reason "the reset teleport races the client's move" is wrong: the WARN is
  on the join, before any reset.
- Ruled out: the world seed (Paper and Purpur cells already pin `SEED=1234567`; the spawn still moves by up to about 15 blocks per
  boot through the spawn radius, and any spot on the flat world is at least 60 blocks from `0, 0, 0`); the level type as a cause
  (flat only makes the move large); port reuse (every server is a new container on a new port, and `T` clients WARN on every
  one); a Purpur-only setting (Paper behaves the same in the spike; the harness writes no `spigot.yml` or `purpur.yml`). T-M8-16
  lead (loader cells set no seed): the seed does not decide this WARN, so it is no lead for it.

| Invocation | Runs | Red | XML per run | Red tests |
|---|---|---|---|---|
| `:conformance:smoke -Dproofstone.cells=purpur@26.3` | 10 | 0 | tests 2, skipped 0, failures 0, errors 0 | none; `T8257c763` WARN 10 of 10 (allowlisted), `PSConf` 0 of 10 |
| `:conformance:smoke`, no selector (paper, folia, purpur, spigot @26.3) | 5 | **1** (run 3) | runs 1, 2, 4, 5: tests 8, skipped 0, failures 0, errors 0; run 3: tests 8, skipped 0, failures 1, errors 0 | run 3, purpur c02: `PSConf moved too quickly! -8.5,-60.0,-5.5` |

Per cell over the 5 full runs: purpur 1 of 5 red; paper, folia, spigot 0 of 5. `PSConf` WARN: purpur 1 of 15 logins, paper 0 of
5, folia 0 of 5. Spigot container logs were not captured (the watcher attached before the container started); its c02 XML is
green each time. Containers left after each run: none of ours (Ryuk exits on its own).

**Fix card proposed:** T-M7-19 in [M7-followups.md](tasks/M7-followups.md): the mcpl driver acknowledges the join teleport after
the server's first player tick (the first `ClientboundSetHealthPacket`), an ordering await on a packet, with no sleep, retry,
allowlist or rename. Decision needed: this differs from the vanilla client, which acknowledges at once.


## T-M7-19 acknowledge the join teleport after the first tick

Date 2026-11-15. Branch `card/T-M7-19-ack-after-first-tick` from origin/main `b8d27da`. Local only, Windows 11, Docker Desktop, JDK 21,
one invocation at a time under `scripts/dockerlock.sh`, `--no-parallel --max-workers=1`, EULA in the environment only. Evidence copied
aside per run (XML, container logs captured by a read-only `docker logs -f` watcher from container start, so green runs are
covered too). No sleep, timer, retry or timeout raise.

**Change.** `McplSession` holds the join teleport's `ServerboundAcceptTeleportationPacket` and `ServerboundPlayerLoadedPacket` until
the first `ClientboundSetHealthPacket` of a login, then sends the newest held position's acknowledgement; later positions are
acknowledged at once; a new `ClientboundLoginPacket` starts the hold again. `McplDriver.connect` waits (same connect timeout) for that
release, so a server without a first `SetHealth` is a `JoinTimeoutException` naming stage `JOIN_TELEPORT_HELD_NO_HEALTH` (or
`JOIN_NO_FIRST_HEALTH` with no position either). Spec: client-driver.md section 3a. Then the `^T[0-9a-f]{8} moved too quickly! `
entries were dropped from the Paper, Folia, Purpur and Spigot packs and the three `pack.yml` mirrors (separate commit); the
`.`-prefixed Clients entry stays (not this card's WARN). Bridged joins do not hold (see "Bridged rows" below).

**Test first.** `McplJoinAckTest` (7 tests, no Docker). With only method and enum stubs on main's behaviour, 4 of the first 5 tests
failed (the hold, the newest position, the new login, the stage name); the connect-wait tests were added with the later change.
`McplSwitchTest` scripts now send a `SetHealth` after each join position (the real servers do); without it its "player loaded
again" assertion waits on a packet that is always there. `:drivers:mcpl:test` green (68 tests).

### Runs on the final code (every run after the last code change; XML read, container logs counted for `moved too quickly!`)

| Invocation | Runs | Result | XML per run | `moved too quickly!` in container logs |
|---|---|---|---|---|
| `:conformance:smoke -Dproofstone.cells=purpur@26.3` | 10 | 10 green | tests 2, skipped 0, failures 0, errors 0 | 0 (10 of 10 logs) |
| `:conformance:smoke`, no selector (paper, folia, purpur, spigot @26.3) | 5 | 5 green | tests 8, skipped 0, failures 0, errors 0 | 0 (4 server logs per run, all four types seen) |
| `:conformance:smoke` alone: paper, folia, purpur, spigot @26.3 | 1 each | 4 green | tests 2, skipped 0, failures 0, errors 0 | 0 |
| `:conformance:run velocity@4.3.1+paper@26.3` | 3 | 3 green | tests 2, skipped 0, failures 0, errors 0 | 0 (3 logs) |
| `:conformance:run bungeecord@2111+paper@26.3` | 3 | 3 green | tests 2, skipped 0, failures 0, errors 0 | 0 |
| `:conformance:run fabric@26.3` | 1 | green | every suite skipped 0, failures 0, errors 0 | 0 |
| `:conformance:run neoforge@26.3` | 4 | **1 red (C08)**, 3 green | red: `DISAGREE: present in the inventory ... no matching item among []` | 0 |

Before, on main: T-M7-18 recorded purpur alone 0 of 10 red with the WARN fired and allowlisted 10 of 10, and full smoke 1 of 5 red
(purpur c02). After: 0 WARN lines in every run above, entries dropped. The earlier iteration of this change (the hold only, before the
connect wait) ran purpur alone 10 of 10 and full smoke 5 of 5 green with 0 WARNs, and the dropped-entry `:conformance:run` for paper
(green), folia, purpur and spigot (red only by "has no cell to run" for six paper-only classes, the known single-cell selection
gotcha in purpur.md; no real test failed) and velocity, bungeecord, fabric, neoforge (green).

**The neoforge red is not this change.** The same C08 `DISAGREE` came on main (`a322fdb` code) in 1 of 3 runs, and the 3 later runs
on the final code were green. It is the same family as the spigot@1.21.11 `DISAGREE: present in the inventory` (T-M5-05), not
diagnosed here. Kept as a finding, counted separately from the WARN.

**Rows that send the first `SetHealth`** (the join completes): paper, folia, purpur, spigot @26.3; the velocity and bungeecord
topologies with paper@26.3 backends; fabric@26.3 and neoforge@26.3; bridged paper@1.21.11. A row without it can no longer finish a join.

**Bridged rows (scope change, orchestrator 2026-11-15).** The first iteration held the acknowledgement on every row. Bridged
`paper@1.8.8` and `spigot@1.8.8` then failed the join (`JOIN_TELEPORT_HELD_NO_HEALTH` after 30 s, protocol offered 777), because
ViaProxy resends the 1.8 server's position and no `SetHealth` arrives before the client acknowledges (seen in a debug run with the
transcript tail widened to 400 entries, not kept). Both were green on main and never logged the WARN (0 in container logs). So the
hold is now native only: `ConnectSpec` gained a `bridged` flag (default false, set from `Cell.bridged()` by `CellInvocation`; small
core change approved for this card), and `McplDriver.newSession` passes `!bridged` as the hold. A native row that never sends the
first `SetHealth` still fails loudly. The topology joins (`TopologyInvocation`) stay native: the driver talks to the proxy, whose
backends are native paper@26.3 rows. Unit tests: `McplJoinAckTest.aBridgedJoinAcknowledgesTheJoinTeleportAtOnce` failed before the
flag (the session still held) and `aNativeJoinStillHoldsTheJoinTeleport` passes.

The native-path evidence above (the table, runs 1 to 5 of the final iteration) was taken before the flag. The flag changes no native
code path (`holdJoinAcknowledgement` is true for every native join) but I re-ran, after the flag:

| Invocation (after the flag) | Runs | Result | XML / note |
|---|---|---|---|
| `:conformance:smoke -Dproofstone.cells=purpur@26.3` | 3 | 3 green | tests 2, 0/0/0; 0 WARN |
| `:conformance:smoke` full 4-cell | 3 | 3 green | tests 8, 0/0/0; 0 WARN, all four server types in the logs |
| `:conformance:run velocity@4.3.1+paper@26.3` | 1 | green | tests 2, 0/0/0 |
| `:conformance:run fabric@26.3` | 1 | green | 0/0/0 per suite |
| `:conformance:run neoforge@26.3` | 1 | green | 0/0/0 per suite (2 suites skipped with a reason) |
| `:conformance:run paper@1.8.8` (bridged) | 6 | **3 red**, 3 green | red: `C04ChestMoveTest` `cursor after picking up slot A ==> expected: <true> but was: <false>` (run 1 also failed C03, C05 to C08 and the smoke class through the shared cell, 8 failures; runs 5 and 6 1 failure each) |
| `:conformance:run paper@1.8.8` on main (`a322fdb`) | 6 | 6 green | same selector |
| `:conformance:smoke paper@1.8.8` after the flag / on main, alternating | 10 / 10 | 10 green / 10 green | tests 2, 0/0/0 |
| `:conformance:run spigot@1.8.8` (bridged) | 3 | 3 with only `has no cell to run` (CD01, CD02, CR01 x3, CR02), C01 to C08 green | the known single-cell selection gotcha; no other failure |
| `:conformance:run paper@1.21.11` (bridged) | 1 | green | 0 WARN |
| `:conformance:run paper@1.12.2` / `paper@1.16.5` | 1 each | red as on main | `list` gave a blank reply (unchanged; 1.16.5 also `GET /v1/datapacks` ClassNotFoundException from the agent in CD01 and CD02, new in `run`, not compared on main) |

**Open finding, unexplained.** `:conformance:run paper@1.8.8` failed C04 in 3 of 6 runs after the flag and in 0 of 6 on main, while
the 10 vs 10 alternating `smoke` runs were all green. The bridged code path is meant to be identical to main (immediate
acknowledgement, no wait in `connect`: `joinFinished()` is true when not holding), so a driver cause is not found, and 3 of 6
against 0 of 6 is not proof of a regression (p about 0.1) but is not dismissed either. Evidence is kept in `C:\pw\t719-ev` (`b-paper@1.8.8-1`
to `-6`) and `C:\pw\t719-base-ev`. Decision for the orchestrator: merge as is, or repeat on a quiet machine first.

Known families, counted separately: single-server join timeouts and the zombie_villager worldgen WARN were not seen in these runs.
