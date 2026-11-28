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
| **M9** | Bedrock (exit met for the Geyser scope, reviewed in T-M9-99; BDS deferred to M10 by the maintainer) | Spikes (offline Bedrock through Geyser; a BDS script agent). Then a Bedrock driver, the Geyser topology and, if feasible, the BDS Script-API agent. | 13 (refined 2026-11-15, [M9-bedrock.md](tasks/M9-bedrock.md), approved 2026-11-17 without BDS; 3 moved to M10) | 0 / 6 / 7 | 4 after the spikes (range 2 to 6). Actual: about 8 h of agent wall clock for PRs #151 to #159 (see the M9 review) |
| **M10** | Ecosystem (refined in T-M9-99, approved 2026-11-17; reviewed in T-M10-99: Part A partly met, Part B not started) | Bedrock leftovers (BDS, a release driver pin, the refused-protocol fault, review findings), then community pack template, a TS front end (optional), more forks, a docs site, Sponge, PocketMine and others. | 19 ([M10-ecosystem.md](tasks/M10-ecosystem.md)) | 1 / 12 / 6 | Part A about 1 week (3 days to 3 weeks); Part B ongoing. Actual so far: about 1 h of agent wall clock for 5 Part A cards, PRs #161 to #165 (see the M10 review) |

**Cards M0–M6:** 66 in total. 40 are Haiku cards (61%), 24 Sonnet and 2 Opus (T-M3-02 moved from Haiku to Sonnet at T-M2-99).

**Total to v0.1 (M0–M6): about 16 focused weeks** (sum of the estimates, M5 run after M4; was 15.5 before the M5 review, see the M5 review). Full ecosystem (M7–M10): about 10+ more weeks (M7 done; M8 to M10 about 8+ weeks, see the M7 review; after M8, about 4 to 6 weeks plus ongoing M10, see the M8 review; after M9, about 1 week of Bedrock leftovers plus ongoing M10, see the M9 review).
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
  `Agent.world` (the block read `blockType` and `Client.placeBlock`/`breakBlock` arrived in T-M11-04), `awaitTicks`, `logLines`, `bootLogLines`, an explicit `assertNoErrorsLogged`, `gamemode`, `PlayerState.location` and `openWindow`,
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
  5. Folia rows never booted. PDC was not advertised on any version (served from 1.14 since T-M11-05). Paper 1.8.8 reports as `spigot`.
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
Update (T-M6-109, 2026-11-19): a fourth join-timeout shape was kept with full evidence, a bridged `CONFIGURATION_STALLED` on paper@26.1.2. It is a different stage from the 3 native reds. See "T-M6-109 join-stall analysis" below; card T-M6-109 proposes the fix work.

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

1. **spigot@26.3 C04 timeout seen once by an agent.** **Not reproduced: 1 run, C04 green** (row above). The earlier red left no kept XML, bundle or stage message in the repository or the evidence folders, so its verdict type and stage cannot be named. Verdict: **unexplained, unverified, kept open**, counted as 1 red of 2 known spigot@26.3 C04 runs since then. A repeat count (for example 10 C04 runs, as the M5 follow-up did for C06) is a maintainer call; not run here. **Update (T-M7-16, 2026-11-19): 10 separate runs, C04 green 10 of 10, not reproduced.** Counted with the earlier two: 1 red of 12 known spigot@26.3 C04 runs. Still unexplained, kept open (section "T-M7-16 result" below).
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

- **T-M8-16 loader WARN diagnosis (2026-11-15).** Two vanilla WARN families on loader cells, each with a mechanism. Diagnosis only:
  no harness, guard, allowlist or `versions.yml` change. Fix cards T-M8-21 and T-M8-22. Local, Windows 11, Docker Desktop, JDK 21,
  one invocation at a time under `scripts/dockerlock.sh`, `--no-parallel --max-workers=1`, EULA in the command environment only.
  Every run was `:conformance:run -Dproofstone.cells=<row>` with `conformance/build/{proofstone,test-results}` removed before and
  copied aside after, XML read. A read-only watcher followed `docker logs -f` of every server container (so green runs keep their
  whole log) and a second one took one tar of the small world files after `Done (` to read the seed (`world_gen_settings.dat`, tag
  `seed`). Evidence: `C:\pw\t816-ev`.

  **Runs (21 invocations, 210 boots; XML read; the WARN columns count every container log, also outside the test window):**

  | Run | Result | tests / failures / errors / skipped | Boots | `Skipping Entity` lines | `handleDisconnection` lines | Seeds read |
  |---|---|---|---|---|---|---|
  | `fabric-pre1` | green | 11/0/0/0 | 10 | 0 | 0 | none (before the seed watcher) |
  | `fabric-1` | **red**: C06 (known `DISAGREE`, below) | 11/1/0/0 | 10 | 0 | 0 | 8 |
  | `fabric-2` to `-4`, `-7` to `-10` | green | 11/0/0/0 | 10 each | 0 | 0 | 10 each |
  | `fabric-5` | green | 11/0/0/0 | 10 | 0 | **1** | 10 |
  | `fabric-6` | green | 11/0/0/0 | 10 | 0 | **1** | 10 |
  | `neoforge-1` to `-8`, `-10` | green | 11/0/0/2 | 10 each | 0 | 0 | 8 each |
  | `neoforge-9` | **red**: C08 (known `DISAGREE`, below) | 11/1/0/2 | 10 | 0 | 0 | 8 |

  The 2 skips per NeoForge run are `CD01` and `CD02` with `missing capability DATAPACKS_VANILLA` (a skip with its reason, by design,
  neoforge.md). Seeds: 178 read, **all different**; the ones missing (2 per NeoForge run, 2 in `fabric-1`) are containers that were
  short-lived or booted before the watcher started, so no tar was taken. Every loader boot gets its own random world.
  **Per-row red counts:** fabric 1 of 11 invocations red, NeoForge 1 of 10, **0 of 21 by a log-guard WARN**. Both reds are the
  known `DISAGREE: present in the inventory ... no matching item among []` family: the client closed the window while the
  server's slot packets for it were still in flight (`ContainerSetContent id=1 (no such window, ignored)` or `ContainerSetSlot id=1
  (not applied: unknown window or slot)` right after `ServerboundContainerClosePacket` in both driver transcripts). It is the
  family of T-M7-19 (NeoForge C08) and T-M5-05, not diagnosed here and counted apart. Every other `WARN` or `ERROR` line in the 210
  logs is known boot noise (offline mode, `No key layers`, NeoForge `Appender DebugFile`, `Advanced terminal`).

  **1. `Skipping Entity with id minecraft:zombie_villager` (T-M8-09 verifier, Fabric C02): mechanism found.**
  Not seen in 210 boots (0 in 21 invocations). Counting the earlier M8 runs (T-M8-08, T-M8-09, the review: about 220 boots) the
  rate is **1 in about 430 boots**. The one hit on record is the `t809` verifier bundle: `[02:52:45] [Worker-Main-3/WARN]: Skipping
  Entity with id minecraft:zombie_villager`, in the second of the client's join. Cause:
  - Bytecode of the pinned `server-26.3.jar` (`javap -c`; 26.x is unobfuscated): the WARN is in `EntityType.create(ValueInput,
    Level, EntitySpawnRequest)`, logged when `EntityType.create(Level, EntitySpawnRequest)` returns null, and that returns null
    when `canSpawn(level)` is false: the type is not enabled by the feature flags, or **`!isAllowedInPeaceful()` and
    `level.getDifficulty() == PEACEFUL`**. The harness sets `DIFFICULTY=peaceful` on every cell (`LoaderEnvironment.env`,
    `PaperHost`). A structure template that holds a zombie villager therefore logs the WARN once per entity, and a hostile mob
    template is skipped the same way.
  - The template: `village/plains/zombie/villagers/unemployed` (`zombie_villager` is in its NBT; there are 10 such templates over
    the five biomes). It belongs to the **zombie variant of a village**: `village/plains/town_centers` holds 4 zombie town
    centres at weight 1 and 4 normal ones at weight 50, so 4 of 204, about 2%. Villages are placed every 34 chunks (separation 8),
    so a village start inside the generated area is a minority of worlds and a zombie one about 2% of those: the order of 1 in
    several hundred boots, as seen. Paper cells never logged it because `PaperHost` pins `SEED=1234567`.
  - **Reproduced on purpose**, by hand (`scripts/spike/loader-hand.sh`: the environment of `LoaderEnvironment.env`, no agent),
    with `place template minecraft:village/plains/zombie/villagers/unemployed` on a force-loaded chunk. `DIFFICULTY=peaceful`: one
    `WARN Skipping Entity with id minecraft:zombie_villager` per placement (Fabric 2 of 2; NeoForge with `SEED=1234567` 1 of 1, the
    same logger `net.minecraft.world.entity.EntityType`). `DIFFICULTY=easy`, same commands: **0**. 150 `place
    structure minecraft:village_plains` on peaceful (72 answered "not loaded", about 78 placed): 15 WARN lines. So the WARN follows
    **difficulty and the zombie village variant, not the seed**; the seed decides only whether a village is generated where the
    server generates chunks.
  - **Seed:** `locate structure #minecraft:village` puts the nearest village **353 blocks** from spawn with `SEED=1234567`
    (Fabric hand run), 272 with `SEED=1` and 362 with `SEED=2` (all `village_plains`). The generated area is the spawn chunks plus
    the join's view distance 4 (about 64 blocks), so the pinned seed cannot produce the WARN and is deterministic. The seed of the
    red `t809` run is unknown (a red run keeps no world), so "the WARN follows the red run's seed" was **not** tested; the
    mechanism above was.
  - Fix: T-M8-21 (pin the seed on loader cells, one constant shared with Paper). Not an allowlist: it is real output of a world
    nobody tests.

  **2. `WARN net.minecraft.network.Connection: handleDisconnection() called twice` (NeoForge C03, T-M8-15 verifier run 3):
  mechanism found, a vanilla race, not a double close by the harness.** The `t815v-ev` bundle shows `PSConf lost connection:
  Disconnected`, `PSConf left the game`, then the WARN, then the test client's login. `PSConf` is the conformance client. Its
  close is one call: `CellConformance.scenarios` calls `client.close()` once, and `McplSession.close` is
  `network.disconnect("proofstone closing")`. The server's own code calls the method twice:
  - `javap -c` on `server-26.3.jar`: `Connection.tick()` ends with `if (!isConnected() && !disconnectionHandled)
    handleDisconnection()`; `ServerConnectionListener.tick()` calls `connection.tick()` while `isConnected()` and, for a connection
    that is not connected, `iterator.remove(); connection.handleDisconnection()` **without** the `disconnectionHandled` check;
    `handleDisconnection()` logs the WARN when the flag is already set. So when the client's close reaches the netty thread between
    `ServerConnectionListener`'s `isConnected()` check and the tail of `Connection.tick()` (the player's packet-listener tick runs
    in between), the tail handles the disconnect (`lost connection`, `left the game`) and the **next tick** removes and handles it
    again.
  - The timestamps agree: in the 4 older container logs that carry docker timestamps the WARN follows `left the game` by **52, 59,
    52 and 54 ms**, one 50 ms tick (`t719-ev2`, `t719-base-ev`).
  - **Reproduced on purpose**, by hand (`scripts/spike/loader-hand.sh` and `DisconnectRace.java`: the real mcpl driver's `connect`
    and `close`, in a loop on one server: join, `/help`, wait for the reply, close at once, as `ConformanceSmokeTest` C02 does).
    In that mode: Fabric 4 hits in 1800 cycles, NeoForge 8 in 1200, with and without the agent alike: **12 in 3000, 0.4% of
    closes**, both loaders. A jitter-only close (join, wait 0 to 100 ms, close): 0 in 500 (Fabric). Hits come early in a run (the
    server is cold): NeoForge cycles 14, 15, 43, 76, 85, 87, 228, 483.
  - In the harness, with a cold server per test, 2 of the 210 boots show it, **both green**: `fabric-5` and `fabric-6`, each
    straight after `left the game` of the **test** client of the 9th and 10th container of the run (by test order
    `ConformanceLoaderWindowTest` and `ConformanceSmokeTest` C02; the log does not name the test). The guard
    (`CellInvocation.afterEach`) has already read the log then, so such a hit is invisible to it. It is red only when the close
    that races is `PSConf`'s, because the conformance runs inside the first test's guard window (the cursor is taken before it,
    `CellInvocation.beforeEach`): that is what turned `neoforge@26.3` C03 red once. Container logs of earlier evidence
    directories (not this card's runs): 4 of 104 loader logs with a leave carry it, **0 of 324 Paper-family logs**. Why Paper
    differs is **not shown** (its jar was not decompiled here).
  - A red needs the `PSConf` close to race: once per cell run, at the harness rate (2 in about 210) about 1 in 100 invocations.
    The T-M8-15 verifier saw it once in 3 invocations; this card saw it 0 times in 21.
  - Fix: T-M8-22, **closed 2026-11-16 as option B** (no allowlist; the maintainer's delegate picked it): the conformance checks
    its own window up to just before `PSConf` closes, then waits for `PSConf left the game` in the host log and two server ticks,
    and `CellInvocation` moves the first test's cursor past that. Details and runs in the card's Result
    (`docs/plan/tasks/M8-followups.md`).

  **Not done by this card:** any harness or guard change, a run on Paper or another row, a test of the red run's own seed, a look at
  Paper's jar, a diagnosis of the `no matching item among []` DISAGREE (2 reds in 21 invocations, counted apart).

- **T-M8-20 host IT audit (2026-11-15).** `aFailedStartFailsAtOnceWithTheLogTailAndLeavesNoContainer` (`FabricLoaderHostIT`,
  `NeoForgeLoaderHostIT`) was **red on `main` since T-M8-11**: a mod that throws at init now fails the start as `INVALID test
  declaration` naming the mod (`ModLoad`), and the IT still expected `failed to boot`. T-M8-11 was merged without running these
  module ITs. Fixed in the IT (same `PaperHostException`, fast-failure bound, log tail and no-container checks kept); the verifier
  checklist now says to run the `integrationTest` of every module a PR touches or depends on. Audit, each module once on
  `main` plus the fix under the Docker lock, `--no-parallel --max-workers=1`, XML read, no other red found:

  | Module | Invocation | tests / skipped / failures / errors | Result |
  |---|---|---|---|
  | fabric agent, `FabricLoaderHostIT` (before the fix) | `:platforms:fabric:agent:integrationTest --tests '*FabricLoaderHostIT*'` | 2 / 0 / 1 / 0 on main | RED |
  | fabric agent, `FabricLoaderHostIT`, 3 invocations | same | 2 / 0 / 0 / 0 each | green |
  | neoforge agent, `NeoForgeLoaderHostIT`, 3 invocations | `:platforms:neoforge:agent:integrationTest --tests '*NeoForgeLoaderHostIT*'` | 2 / 0 / 0 / 0 each | green |
  | fabric agent, `FabricAgentIT` | same task, `--tests '*FabricAgentIT*'` | 11 / 0 / 0 / 0 | green |
  | neoforge agent, `NeoForgeAgentIT` | same task, `--tests '*NeoForgeAgentIT*'` | 11 / 0 / 0 / 0 | green |
  | bukkit agent | `:platforms:bukkit:agent:integrationTest` | 25 / 0 / 0 / 0 | green |
  | velocity agent | `:platforms:velocity:agent:integrationTest` | 10 / 0 / 0 / 0 | green |
  | bungee agent | `:platforms:bungee:agent:integrationTest` | 12 / 0 / 0 / 0 | green |
  | drivers/mcpl | `:drivers:mcpl:integrationTest` | 5 / 0 / 0 / 0 | green |
  | drivers/mineflayer-ref | `:drivers:mineflayer-ref:integrationTest` | 2 / 0 / 0 / 0 | green |
  | proofstone-core | `:proofstone-core:integrationTest` | 4 / 0 / 0 / 0 | green |
  | proofstone-core topology | `:proofstone-core:topologyTest` | 14 / 0 / 0 / 0 | green |

  `conformance` and `examples` have no `integrationTest` task (their `smoke`, `run`, `loaderExample` and meta tasks are run by
  their own cards). Evidence: local copies of the XML and logs (not committed).
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

- **T-M8-23 `no matching item among []` (C06, C08) on loader cells (2026-11-16).** Mechanism named from ordering evidence; the
  DISAGREE itself did **not** reproduce. Local, one invocation at a time under `scripts/dockerlock.sh`, EULA in the command
  environment only, `conformance/build/{test-results,proofstone}` copied aside after every run (`C:\pw\ev823`, not committed).
  - **Runs before the fix** (`:conformance:run --tests C04..C08`, one cell per invocation): `fabric@26.3` 10 of 10 green and
    `neoforge@26.3` 10 of 10 green, 5 tests each (50 + 50 scenario runs, 0 DISAGREE); then 6 `neoforge@26.3` and 6 `fabric@26.3`
    with 20 busy-loop processes on the 20-core host: 11 green, **1 red** (neoforge run 3, C08, **not this family**:
    `WARN net.minecraft.network.Connection: handleDisconnection() called twice`, logged at 04:12:00 before the test client's login,
    that is the T-M8-22 race of the conformance client; kept aside, counted apart; T-M8-22's wait did not cover it, a finding for
    that card, not looked at further here). So the family is rarer than the earlier counts (2 in 21, then 1 in 3 on #137) on an idle host.
  - **Mechanism (ordering, from the driver transcript of that red run, which is the only transcript written, a green run keeps
    none):** the server answers the C08 shift-click in two flushes 2 ms apart: `ContainerSetSlot` for slots 0, 0, 0, 7, 8, 9 (the
    result slot and the grid), three `RecipeBookAdd`, then `ContainerSetSlot id=1 slot=45` (the paper, the player's own slot) and
    `SetCursorItem`. The scenario's `click` returns on the first stateId change, so it can return between the two flushes, and C08
    then asserted an empty result slot and closed. `McplSession.target` ignores the packets of a closed window and `retire` copies
    the window's player slots into the player inventory at close, so a slot update after the close is lost
    (`ContainerSetSlot id=1 (not applied: unknown window or slot)` right after `ServerboundContainerClosePacket`, the lines of
    T-M8-16's two reds). The same shape in C06 (and C05, C07): `placeCursorInInventory` clicks an empty player slot and the click
    can return on a late packet of the click before, before its own slot update. The poll interval of `Await` (25 ms first
    pause) makes the window 2 ms of about 25: a few percent per run, more when the server's flushes are further apart (load).
  - **Not shown:** a live DISAGREE with its transcript (0 in 32 invocations here); that the C06 variant is the late-packet-of-the-
    click-before one (inferred from `McplSession.click`, not seen); why neoforge had "several" and fabric once.
  - **Fix** (own commit, test first): C08 awaits the 3 paper in the window's own player slots before it closes, and
    `ConformanceFixture.placeCursorInInventory` (C05, C06, C07) awaits the stack in the slot it clicked, both with the `Await`
    helper and the 10 s budget used by every client wait; no driver change, no sleep, retry, allowlist or weakened assertion.
    `ConformanceCloseRaceTest` (5 cases, no Docker, a fake window whose slot update arrives 250 ms after the click and is dropped if
    the window closed): the C08 case and the two `placeCursorInInventory` cases are red before and green after.
  - **After the fix:** `:conformance:run` `fabric@26.3` 3 of 3 green (11 tests, 0 skipped) and `neoforge@26.3` 3 of 3 green (11 tests,
    2 skipped: CD01, CD02 `DATAPACKS_VANILLA`, by design). A run cannot prove the fix (the race did not show in 32 runs before it);
    the unit cases do.

- **T-M8-25 one boot clock (2026-11-17).** `PaperHost`, `ProxyHost` and `ViaProxyHost` no longer use Testcontainers' default 60 s
  port wait; they share `LoaderHost`'s no-op wait (now one package-private class, `NoPortWait`). `awaitDone`, `awaitAgent`,
  `awaitListening` and the proxy's ready-line await are unchanged, with their own budgets. Option B (cut the per-boot jar fetch)
  was not approved and is proposed as T-M8-26 only. Local, one invocation at a time under `scripts/dockerlock.sh`, EULA in the
  command environment only, evidence copied aside (`C:\pw\t825-ev`, not committed).
  - **Test first:** `BootClockTest` (3 cases, no Docker, reads the configured wait strategy of each container): 3 of 3 failed
    before the change, 3 of 3 pass after; `:proofstone-core:test` green.
  - **Loaded arm** (20 busy `while :; do :; done` loops on the host, `paper@1.21.11` bridged, `:conformance:run`, 10 invocations):
    **10 of 10 green**, 0 `Container startup failed` (checked in the 10 logs and the XML), 170 tests, 0 failures, 0 errors, 70
    skipped (7 per run, each with a reason: missing capability `SIMULATED_BEDROCK`, `FOLIA_REGIONS`, `RESOURCE_PACKS` on a bridged
    Paper cell). T-M8-24's same arm was 7 green, 3 red. Runs took 16.4 to 25.7 minutes (T-M8-24: 16.5 to 17.4).
  - **Idle:** `:conformance:smoke` 3 of 3 green (8 tests each, 0 skipped); `:conformance:run -Dproofstone.cells=paper@1.21.11` 3 of 3
    green (17 tests each, 7 skipped by capability as above); `:proofstone-core:integrationTest` green (4 tests);
    `topologyTest --tests '*TopologyFaultIT*'` green (2 tests); the whole `topologyTest` green (14 tests, 7 suites). Every suite
    `failures="0" errors="0"`. `bash scripts/gates.sh --max-workers=1` passed.
  - **Not shown:** that a boot over 60 s was green live. The server logs were not kept, so the loaded boot times of this arm were not
    measured (the unit case shows the strategy, the 0 of 10 reds shows the outcome); and the "a boot over 180 s fails as `timed out
    waiting for server log shows "Done ("`" case was not forced on a real server. The load here was not compared against the
    before-fix code in the same session.

- **T-M8-24 intermittent `Container startup failed` (2026-11-16).** Diagnosis only, no code change; mechanism named, fix proposed as
  T-M8-25 (not approved). Local, one invocation at a time under `scripts/dockerlock.sh`, EULA in the command environment only,
  evidence copied aside (`C:\pw\t824-ev`, not committed).
  - **Runs:** idle `paper@1.21.11` bridged 10 of 10 green (140 server boots), idle `paper@26.3` 10 of 10 green (140), idle
    `TopologyFaultIT` 10 of 10 green (30); `paper@1.21.11` bridged with 20 busy shell loops on the host **7 green, 3 red** (10 of
    140 server boots failed).
  - **Mechanism:** `PaperHost` keeps Testcontainers' default `HostPortWaitStrategy` and its 60 s `startupTimeout`; Paper binds the
    game port late in a boot of 23 s idle and 55 to 61 s loaded, and the container was killed at 61 s while still booting (event
    times and log tails in the card). The harness's own `bootTimeout` is 3 minutes and was not the clock that fired.
  - **Not shown:** that the #138 and `TopologyFaultIT` reds were this; they did not reproduce idle and no log of them exists.

### T-M8-18: a leak bad test on a loader cell

- **Added:** `leak/loader/LoaderLeakE2E`, pinned to `fabric@26.3`, sets `random_tick_speed` to 7 and leaves it. Task `metaLeakFabric`,
  part of `metaModTest` and `metaModFabricTest`; the pin refusal is in `scripts/bad-tests-pin-check.sh` (red before the task existed,
  green after). `metaLeak` now includes `**/leak/LeakE2E*`, so it never picks up the Fabric test. The detector is unchanged.
- **First run was red, a finding:** with `gamerule random_tick_speed 3` (the Paper test's shape, adapted) the test **passed** and
  `metaLeakFabric` said "no test produced LEAK". A diagnosis run read the rule at the start of the test body: already `3`.
  `PaperHost` runs `GameRules.commands` (rule 0) at boot; `LoaderHost` does not, so a loader cell holds the default and setting 3
  changed nothing. The detector was right. The test now sets 7. Not fixed here: whether loader cells should set the deterministic-world
  rules is a harness question (the loader cells' world differs from Paper's: ticks, spawns, weather, daylight).
- **After:** `metaModFabricTest` 3 of 3 green, each XML read: `LoaderLeakE2E` tests 1 failures 1 skipped 0 errors 0, message `LEAK
  LoaderLeakE2E.leavesTheRandomTickSpeedChangedBehind on fabric@26.3: state left behind by the test: random_tick_speed: Game rule
  random_tick_speed is currently set to 3 -> random_tick_speed: Game rule random_tick_speed is currently set to 7`; the report says
  `FAIL` (a leak is a FAIL starting `LEAK`); `BrokenFabricModE2E` still `INVALID` in the same runs; no timeout.
- **Not shown:** a NeoForge leak test (the NeoForge agent serves the same rules, not run); a leak other than a world rule on a loader
  cell (plugin or player state).

### T-M8-28: does `metaLeakFabric` join the release mutation check (finding, no code change)

- **Answer: no, not as a list entry.** Read `scripts/release-mutation-check.sh`, `scripts/lib/mutation-gate.sh`,
  `scripts/release-gate-test.sh`, `examples/bad-tests/README.md`. The set is `EXAMPLES=(gradle-paper-plugin maven-paper-plugin)`:
  example projects, not bad tests. Each runs once with no bug (must pass), then once per toggle read from its `config.yml` `bugs:`
  block (`toggles()`), with `-Dproofstone.bug=<toggle>` and one `--cell` for every run (default `paper@26.3`); a mutation counts as
  KILLED when the suite is red. `metaLeakFabric` has no toggle or baseline, runs the `metaModFabricTest` Gradle task, and is pinned
  to `fabric@26.3` (refused elsewhere by `bad-tests-pin-check.sh`), so it fits neither the unit nor the single cell. The gate
  (`mutation_gate_check`) only reads the result file's keys (status, head, dirty, allowed_missing, age); it knows no test names, so
  `release-gate-test.sh` has nothing to add. Same stance as the M7 topology faults and the T-M8-11 controls: by hand.
- **Not decided here:** whether the maintainer wants a second mutation mechanism for harness detectors (new code in the check, with
  per-entry cells and a task name). Cost for one: a Fabric boot, `metaModFabricTest` took 40 to 47 s in T-M8-27 (warm).
- **Run:** `bash scripts/release-gate-test.sh` only (9 ok, 0 failed, see PR). `release-mutation-check.sh`, `release-maven.sh` and
  `release-images.sh` were not run; `PROOFSTONE_RELEASE_CONFIRM` not set.

### T-M8-27: loader cells set the deterministic-world rules

- **Change:** `LoaderHost` runs `GameRules.apply` (one entry point, also used by `PaperHost`) after its readiness checks: the four rules
  `PaperHost` sets. Spawn and seed untouched. Local, one invocation at a time under `scripts/dockerlock.sh`, EULA in the command
  environment only, evidence copied aside (`C:/pw/t827-ev`, not committed).
- **Test first:** `GameRulesTest` `apply` cases: compile-red before (no `apply`), 6 of 6 green after. Host ITs (`FabricLoaderHostIT`,
  `NeoForgeLoaderHostIT`) read the four rules after start: red before on both rows (1 run each: `random_tick_speed` 3, `advance_time`,
  `advance_weather`, `spawn_mobs` all `true`), green after, 3 of 3 each, every boot reading 0, false, false, false (the snapshot is the
  same in all six boots).
- **Leak test:** `metaModFabricTest` 3 of 3: `LoaderLeakE2E` `LEAK`, `random_tick_speed ... currently set to 0 -> ... currently set to 7`
  (was 3 -> 7), `BrokenFabricModE2E` `INVALID` in the same runs, no timeout. README and test comment updated. The seed (T-M8-21) is a
  separate env var and unchanged.
- **Other gates:** `:proofstone-core:integrationTest` (PaperHostIT, 4 tests), `:platforms:fabric:agent:integrationTest` (11 + 2),
  `:platforms:neoforge:agent:integrationTest` (11 + 2): `skipped=0 failures=0 errors=0` each. `:conformance:run` `fabric@26.3` 3 of 3
  green (11 tests, 0 skipped), `neoforge@26.3` 3 of 3 green (11 tests, 2 skipped: C-D01, C-D02 by capability, as before).
- **Not shown:** a NeoForge leak test; a Paper cell is unchanged in behaviour (its `PaperHostIT` is green) but the full Paper conformance
  was not re-run.

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
  differ per boot. Card T-M8-16 found the cause since (peaceful difficulty plus the 2% zombie village variant, not the seed itself; see "M8 findings", fix T-M8-21). `PSConf moved too quickly!` on loader cells: **0** in every M8 run.

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
  `Values`, `GameView`, test `MockView`, `AgentClient`; 2 to 44 changed lines per file). Card T-M8-15. Done 2026-11-15 (PR #134, merged): `platforms:vanilla-view` holds one copy (709 main lines, 227 fixture lines);
  both agents shade it. Known leftovers: `FabricLoaderHostIT`/`NeoForgeLoaderHostIT.aFailedStart...` red on main too (T-M8-11 message), `FabricGame`/`NeoForgeGame` and `FabricAgent`/`NeoForgeAgent` are still near-copies (about 250 lines, 24 differing).
- **`proofstone.agentJar` was required on the loader path** though no loader loads it (T-M8-12 run 1). Card T-M8-14, done
  2026-11-15: `CellInvocation.agentJar(loaderId)` reads only the property the cell uses (the Bukkit one for `bootPaper`, the
  loader's own for `bootLoader`), the example's workaround is removed, and `AgentJarPropertyTest` (4 tests) covers both paths.
  `:examples:loader-datapack:loaderExample -Dproofstone.cells=fabric@26.3` 3 of 3 green without the workaround, 1 test each,
  `skipped="0" failures="0" errors="0"`, loader boot 14.7, 14.3, 14.6 s; `:conformance:smoke` on `paper@26.3` (2 tests) and
  `:conformance:run` on `fabric@26.3` (11 tests) green, 0 skipped; no `zombie_villager` WARN seen in those logs;
  `printMatrix --scope=pr` identical before and after.
- **T-M8-13 `@Proofstone(mods)` and `Capability.MODS` (2026-11-15, in review).** `api-baseline.txt` diff: 2 lines added, 0 removed
  (`Proofstone.mods`, `Capability.MODS`, last constant). `CellInvocation.bootLoader` passes the declared globs (none on a control
  run) through `Mods.resolve` (one jar per glob, `fabric-api` refused, all before Docker) and `ModsLoaded` checks each declared
  id against `GET /v1/mods` after the capability check. The fault switch `-Dproofstone.injectModFault` (`ModFault`) is removed:
  no test needs it, because `metaModCrash` and `metaModCrashNeoForge` now declare the crashing example mod through `mods`
  (`mod/BrokenFabricModE2E`, `BrokenNeoForgeModE2E`; the jar is the agent's `exampleCrashModJar`, the example mod plus one marker
  resource). The example mod no longer fails on a missing config file (a declared mod has none). New example
  `examples/loader-mod` (`ServerOnlyModE2E`). Unit tests (`ModsTest`, `ModsLoadedTest`, `CapabilityModelTest`): red on main (test
  sources did not compile), green after; `:proofstone-api:test :proofstone-core:test` 484 tests, 0 skipped.
  `:examples:loader-mod:loaderExample` 3 of 3 green on `fabric@26.3` (boot 14.6, 14.7, 14.5 s) and 3 of 3 on `neoforge@26.3`
  (boot 18.0, 17.3, 17.7 s), 1 test each, `skipped="0" failures="0" errors="0"`. `metaModFabricTest` 3 of 3 and
  `metaModNeoForgeTest` 3 of 3 give `INVALID` naming `proofstone_example` and `proofstone-crashing-mod.jar`, no timeout;
  `metaModPaperTest` (INVALID, FAIL), `metaTest` and `metaTopologyTest` once each as before. `:conformance:run` `fabric@26.3`
  11 tests 0 skipped, `neoforge@26.3` 11 tests with the two reasoned C-D01/C-D02 skips; no `zombie_villager` WARN. Not run on a
  real server: a control run on a loader cell, and a declared mod the agent does not list (unit-tested only).
- **No leak bad test on a loader cell** (robustness R6). Card T-M8-18: done, see the T-M8-18 findings below.
- **Card boxes:** T-M8-01, 08, 09, 10 and 11 acceptance boxes are still unticked in `M8-mods.md` though merged. Left as they are;
  the evidence above is what counts.

### What is NOT verified

- A user-declared mod other than the repository's example mod, and what a mod does at runtime through the API (T-M8-13 ran the
  example mod, which registers nothing).
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
5. **(Done 2026-11-17, without BDS) Approve `docs/plan/tasks/M9-bedrock.md`** (13 cards, two spikes first). T-M9-01 (Geyser spike) is done (PR pending), result: feasible with a stand-in for two Microsoft discovery names, `validate-bedrock-login: false` and `transport: both` ([geyser.md](../platforms/geyser.md)); T-M9-02 (BDS) is deferred to M10. **ADR-0018 (T-M9-03, Accepted 2026-11-17) decided the design:** CloudburstMC Protocol (Java) as a driver host on the cell network, a harness-owned discovery stand-in for the Microsoft names (no new image), Floodgate route with `validate-bedrock-login: false` (an *offline-stub* cell), identity `geyser@2.12.1+paper@26.3`, `versions.yml` schema 5. Cards T-M9-04 to T-M9-12 updated to match.
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

**Progress note (T-M9-12, 2026-11-17).** The Bedrock user guide ([bedrock.md](../user/bedrock.md)), the `examples/geyser-bedrock` example (3 of 3 green on `geyser@2.12.1+paper@26.3`) and the `docs/platforms.md` status (experimental, driver on a snapshot) are done. Cards 01, 03 to 08, 11 and 12 have their work in; BDS cards 02, 09 and 10 are M10. T-M9-99 (the review) is next and decides the exit criterion; this note scores nothing.

## M9 review (T-M9-99)

Date 2026-11-17. Ran on `card/T-M9-99-m9-review` from origin/main `be123b2` (PRs #151 to #159 merged; M9 diff `3effa9d..be123b2`,
136 files, +10934 / -152). Local runs only, Windows 11, Docker Desktop 29.9.3, JDK 21 for Gradle, JDK 26 for Maven. One Docker run at a
time under `scripts/dockerlock.sh`, `--no-parallel --max-workers=1`, EULA in the command environment only. Every run's XML (and the
report and bundles, where written) copied aside before the next run (local only, not committed). Every XML read; no red retried.
Judged from code and XML, not from PR text. The mutation check ran first, on the clean tree (`dirty=false`); this review's doc edits
started after it ended.

**Verdict: M9 exit criterion MET for the Geyser scope**, with BDS out by the maintainer's limit (deferred to M10, not scored) and with
gaps stated below, none rounded up. Met from runs: a real Bedrock client joins `geyser@2.12.1+paper@26.3` offline through Geyser and
Floodgate (stand-in answered exactly the two discovery names, name guard clean), the agent sees `.Name`, the public API declares it
additively, C-G01 to C-G04 pass with the three skips reasoned, and the two reachable faults are loud `INVALID`s. **Not met and not
claimed:** the third fault of the original list (a Bedrock protocol the Geyser build refuses) was dropped by T-M9-11, so Geyser's
refusal is unverified; the *offline-stub* label is in `cell.json` and the boot line but not in the report or the JUnit display name
that ADR-0018 section 4 names; the driver is on a pinned **snapshot**, so the row cannot be promoted. The row stays `experimental`.
Nothing was published or tagged.

### Evidence from this run

| Run | Result |
|---|---|
| `bash scripts/release-mutation-check.sh` (no flag), clean worktree at `be123b2` | **PASS**: head `be123b2`, `dirty=false`, cell paper@26.3, killed 4 of 4 (Gradle and Maven: `double-output`, `accept-any-right`), no allowed-missing; both baselines PASS. 240 s. Script header read first: local only, writes `build/release/mutation-check.env`, publishes nothing (only `release-maven.sh --publish` reads the file, not run) |
| `:conformance:smoke`, no selector: paper, folia, purpur, spigot @26.3 | Green: tests 8, skipped 0, failures 0, errors 0 (C01, C02 on four cells); 253 s. The `PSConf moved too quickly!` family the M8 review saw (3 of 8 red) did not appear |
| `:conformance:run -Dproofstone.cells=geyser@2.12.1+paper@26.3`, 3 separate invocations | 3 of 3 green, each `ConformanceGeyserTest` tests 7, skipped 3, failures 0, errors 0 (C-G01 to C-G04 passed; skipped with reasons in the XML: window clicks and `useBlock`, forms, resource packs, each `TestAbortedException` naming ADR-0018 section 2); 47 to 51 s per suite; cell boot 34.7, 31.0, 31.5 s (`PROOFSTONE GEYSER BOOT ... (offline-stub)`) |
| `:conformance:run -Dproofstone.cells=fabric@26.3` (newest native loader row), 3 separate invocations | 3 of 3 green, each tests 11, skipped 0, failures 0, errors 0 (C01 to C08, C-D01, C-D02, loader window test); 180 to 195 s |
| `:conformance:run -Dproofstone.cells=velocity@4.3.1+paper@26.3` (newest topology row), 3 separate invocations | 3 of 3 green, each tests 2, skipped 0, failures 0, errors 0 (T00, P01 to P05); 69 to 73 s |
| `:examples:bad-tests:metaTest` | BUILD SUCCESSFUL, "every bad test produced its expected verdict (VACUOUS, FLAKY, DISAGREE, INVALID, INVALID, LEAK, FAIL, SKIPPED)"; tests 17, failures 9 and skipped 2 by design, errors 0; 4 m 24 s |
| `:examples:bad-tests:metaTopologyTest` (7 faults, one cell each) | BUILD SUCCESSFUL, 7 of 7 `INVALID`, each `tests=1 failures=1` by design, errors 0, 0 `timed out joining`; 6 m 51 s |
| `:examples:bad-tests:metaModTest` | BUILD SUCCESSFUL, "every bad test produced its expected verdict (INVALID, LEAK, INVALID, INVALID, FAIL)"; 5 suites, each `tests=1 failures=1` by design, skipped 0, errors 0 (NeoForge and Fabric crash mods, Fabric leak, broken data pack, wrong-hash pack); 1 m 56 s |
| `:examples:bad-tests:metaBedrockTest` | BUILD SUCCESSFUL, "every bad test produced its expected verdict (INVALID, INVALID)"; 2 suites `tests=1 skipped=0 failures=1 errors=0` by design. `key-mismatch`: `cell conformance failed: Floodgate refused the data Geyser sent`, `hop: Geyser to Floodgate on the server`, quoting `Please connect through the official Geyser`. `stand-in-absent`: `the discovery stand-in answered none of the two expected names`, `hop: server and driver host to the stand-in`, quoting `Unable to fetch discovery data`. Neither a join timeout. Cell boot 30.6, 30.9 s; 1 m 31 s. The bundle (`server/`, `stand-in/`, `driver-host/`, `cell.json` with `offline-stub`) has no `plugins/floodgate` path and no 32-hex-digit or padded 22-char base64 string |
| `:examples:geyser-bedrock:geyserExample -Dproofstone.cells=geyser@2.12.1+paper@26.3` | Green: `BedrockBranchE2E` tests 2, skipped 0, failures 0, errors 0; cell boot 31.2 s; 47 s |
| `:proofstone-core:geyserTest -Dproofstone.cells=geyser@2.12.1+paper@26.3` (the T-M9-06/07 host ITs, workflow.md verifier rule) | Green: `GeyserCellIT` 1 and `GeyserApiContract` 2, skipped 0, failures 0, errors 0; cell boot 30.8 s; 81 s |
| Containers of ours left running after the runs | none (checked after every run) |

### Bedrock flake numbers (counted apart from Paper, topology and loader, M9 Flake rule)

Bedrock joins per invocation: `:conformance:run` on the Geyser row 7 (the cell check plus `PsG01` to `PsG06`; `PsG05` and `PsG06` join,
show the refusal and abort), the example 3, `metaBedrockTest` 2 (each faulted by design), `geyserTest` 4 (`GeyserCellIT` 1, the API contract's cell check and its 2 tests).

| Source | Invocations | Bedrock joins | Join timeouts (stage) | Log-guard reds | Other reds |
|---|---|---|---|---|---|
| T-M9-01 spike, by hand (secondary, geyser.md "Run log") | 26 runs + 1 hand run | 59, 48 spawned | 7: `raknet-connect` 4 (Node `jsp-raknet`), `login-sent` 2 and `login` 1 (the Xbox refusal as the client saw it, with validation on) | n/a | 4 server disconnects with a message, each a removed condition. **42 of 42 green with all conditions in place** |
| T-M9-06 `geyserTest` (secondary, recorded) | 3 counted + 2 diagnosis | 3 counted | 0 | 0 | 2 diagnosis reds before the counted runs: join failed at `SPAWN_STATUS` with driver decode errors (fixed in the driver) |
| T-M9-07 API contract (secondary) | 3 + 3 | not recorded | 0 | 0 | 1 run of the first 3 **skipped** both tests (`missing capability BEDROCK_CLIENT`: health read before the `floodgate` token; fixed, then 3 of 3 green) |
| T-M9-08 conformance (secondary) | 3 counted + 3 dev | 21 counted | 0 | 0 | 3 dev reds, wrong expectations (`§r` prefix, `/list`, `/plugins` split), kept as findings |
| T-M9-11 `metaBedrockTest` (secondary) | 3 | 6 (faulted by design) | 0 (the stand-in-absent fault is a server `disconnect.timeout` at `LOGIN_SENT`, not a driver timeout) | 0 | 0 unexpected |
| T-M9-12 example (secondary) | 3 | 9 | 0 | 0 | 0 |
| **T-M9-99 (this run)** | **3 conformance + 1 meta + 1 example + 1 `geyserTest`** | 30 (28 clean, 2 faulted by design) | **0** | **0** | **0** |

- **Bedrock join timeouts: 0** in every counted harness invocation of M9 (agent cards and this review). The only Bedrock join
  timeouts ever seen are the spike's 7, each explained (a RakNet library that cannot join Geyser, or the Xbox refusal with validation on).
- **Bedrock log-guard reds: 0** in every counted invocation (`ErrorLogGuard.Patterns.GEYSER`, five allowlisted lines with reasons). The
  guard reads the **server only**; the stand-in and driver-host logs are not guarded (finding, T-M10-06).
- **Stand-in names:** the cell's first check passed in every Geyser invocation of this run, which requires the stand-in to have
  answered exactly the two discovery names and the name guard to be clean; a red there is `INVALID` for every test on the cell.
- **Paper, topology and loader numbers (apart):** smoke 8 of 8 green on four cells, `fabric@26.3` 3 of 3, `velocity@4.3.1+paper@26.3`
  3 of 3; 0 single-server or topology join timeouts, 0 container-start failures, 0 NeoForge installer read timeouts (one NeoForge
  boot, in `metaModTest`), no NeoForge conformance run (so the declared CD01/CD02 skips did not occur), 0 bridged `paper@1.8.8` runs
  (not in this review's set, so the C03 to C05 bridged family was not sampled).

### Exit criterion, re-scored

| # | Item | Status | Evidence |
|---|---|---|---|
| 1 | A Bedrock client driver joins a Java server through Geyser and Floodgate offline (no Xbox account, no Microsoft service call: the two discovery names go to the stand-in and the name guard is clean), on the newest native `paper` row, and the Bukkit agent sees the player as Floodgate names it (`.Name`). The cell is labelled *offline-stub* | **MET, with two stated limits** | This run: 3 of 3 Geyser invocations, C-G01 green each time (`agent.player` of the Bedrock client is `.PsG01`), after the cell's first check (exactly the two names answered by the stand-in, guard clean) passed. `paper@26.3` is the newest `paper` row (protocol 777, native to `mcpl`). No account, no token in the code or config (`validate-bedrock-login: false`, Floodgate key from `SecureRandom`). **Limit 1:** "no Microsoft service call" is checked as *names looked up*, once per cell (at the first check), and egress is open by design (ADR-0018 section 4); a later lookup in the same cell is not guarded (T-M10-06). **Limit 2:** the *offline-stub* label is in `cell.json` (bundles) and the `PROOFSTONE GEYSER BOOT ... (offline-stub)` line; the JUnit display name and `proofstone-report.json` show only `geyser@2.12.1+paper@26.3` (this run's XML and report), although ADR-0018 section 4 and `GeyserCell` Javadoc say they carry it (T-M10-06) |
| 2 | A test declares a Bedrock client through the public API, additively to API v0 (label `api-change`, no existing signature changed), and the Geyser topology through `versions.yml` | **MET** | `api-baseline.txt` `3effa9d..be123b2`: 3 lines added (`Capability.BEDROCK_CLIENT`, `Clients.bedrock(String)` default, `Platform.GEYSER`), 0 removed; the default throws `UnsupportedOperationException` naming the capability. PR #156 carries `api-change`. `versions.yml` schema 5 key `geyser` (row `2.12.1`, build 1303, Floodgate 2.2.5 build 141, both sha256-pinned, `bedrock-protocol: 2225`, `experimental`), `drivers.bedrock`. `@Matrix(platform = Platform.GEYSER)` with `-Dproofstone.cells=geyser@2.12.1+paper@26.3` planned the cell in every run here |
| 3 | Conformance scenarios on the Geyser topology (join, command reply, inventory read with the dual oracle, a Bedrock-only plugin branch) pass, every skip reasoned in the XML | **MET** | This run: C-G01 (join, Floodgate name), C-G02 (`/plugins` reply, two messages), C-G03 (`stick x3` and `diamond_sword x1`, `DualAssert` id and count on both oracles), C-G04 (the plugin's Bedrock branch for the Bedrock client, not for the Java one) green 3 of 3; 3 skips per run, each with its reason in the XML. Earlier T-M9-08 3 of 3. The dual oracle compares id and count only (no item data components in driver v1): narrower than on Java rows, stated in geyser.md |
| 4 | A misconfigured Geyser cell (stand-in absent, and the faults T-M9-11 proves reachable: a Floodgate key mismatch or a Bedrock protocol the Geyser build refuses) is a loud red, never a pass or a join timeout | **MET as worded (2 faults); the refused-protocol fault is not built** | This run, `metaBedrockTest` once, XML read: `stand-in-absent` and `key-mismatch` each `INVALID` with the specific reason and hop (Evidence table), no timeout. Earlier T-M9-11 3 of 3. The key mismatch is made on Floodgate's side (a decoy `key-file-name`), because Geyser on one server ignores its own key setting. **The refused protocol was dropped** (needs a switch in `drivers/bedrock`, outside T-M9-11's modules); the item's own wording admits only reachable faults, so it is not a failure of the item, but Geyser's refusal text is unverified (T-M10-05). The stand-in-absent fault reaches the client as a server `disconnect.timeout` at `LOGIN_SENT`; the verdict is `INVALID` with the hop named from the server log, not a join timeout |
| 5 | BDS | **Not scored: deferred to M10** by the maintainer's limit (ADR-0018 section 1) | T-M9-02, 09 and 10 are T-M10-01 to T-M10-03. No BDS code, image or zip exists in the repository |
| 6 | Flake-rule evidence for every acceptance above | **MET for items 1 to 4** | Three separate green invocations of the Geyser conformance here and in T-M9-08; `metaBedrockTest` 3 of 3 in T-M9-11 and once here; the example 3 of 3 in T-M9-12 and once here; `geyserTest` 3 of 3 in T-M9-06 and once here. No red was replaced by a green; the agent-card reds are listed above with causes |

### ADR-0018 against the build (deviations, for an amendment: T-M10-08)

- **Three snapshot groups, not one.** The catalog pins `org.cloudburstmc.protocol` `3.0.0.Beta15-20261116.161745-6` as the ADR says,
  and also `org.cloudburstmc.netty` `1.1.0.CR4-20261103.123906-3` and `org.cloudburstmc.math` `2.0-20220327.162752-1` (the Beta15 POM
  names floating snapshots of both). `exclusiveContent` matches only timestamped versions of those three groups; 32 artefacts are
  checked by sha256 (`drivers/bedrock/cloudburst-artefacts.sha256`), including releases the library pulls (netty 4.1.101, jose4j,
  expiringmap, aircompressor, fastutil, `com.nukkitx:natives` 1.0.3, slf4j-api), all shaded and relocated into the driver-host jar and
  never on a user's or the harness's classpath. No release of CloudburstMC Protocol exists (T-M9-05, checked 2026-11-17).
- **Refused-protocol fault dropped** (T-M9-11): no Geyser setting narrows the accepted protocols; making it needs a test-only switch in the
  driver (T-M10-05, a maintainer decision).
- **Key mismatch on Floodgate's side**, not Geyser's (Geyser on one server reads Floodgate's key whatever its own setting says).
- **Fault injection is a test-only system property in production code** (`-Dproofstone.injectGeyserFault`, `GeyserFault`, and the
  stand-in's `--refuse-names`); M8 removed its own such switch (`injectModFault`) once a declared input could make the fault.
- **Transport to the driver host** is `docker exec curl` inside the driver-host container (`ExecCurlTransport`), with a per-cell bearer
  token, not a published HTTP port: "the way the harness reaches an agent" in spirit, nothing published.
- **The `floodgate` token** is added by `FloodgateProbe` at the agent's first global tick (Floodgate enables after the agent), and
  `GeyserHost` re-reads the health after awaiting it (the T-M9-07 skip). No protocol bump (the health schema does not enumerate tokens).
- **Log guard on the server only**, not "per container" (section 6); **name guard once per cell** at the first check (section 4 says the
  host fails the cell "if a name on a deny list was ever asked").
- **`offline-stub` not in the report or display name** (section 4), see item 1.
- **A new skip reason `TEST_ABORTED`** (an `Assumptions.abort` was `SKIPPED` in JUnit and `FAIL` in the report before; now `SKIPPED`
  in both, own commit, unit test). A behaviour change in core reporting, consistent with "a skip is reported as a skip".
- Held as decided: pre-rendered Geyser config accepted (no first-start fallback), pre-seeded key, no UDP published, stand-in and driver
  host on the pinned `java25` digest (no new image), identity `geyser@2.12.1+paper@26.3`, differential and flake-detector runs refused.

### Code review (diff `3effa9d..be123b2`, 136 files, +10934 / -152)

- **Weakened tests: none found.** Modified existing test files: only `TopologyApiTest` (the "added after the existing ones" check
  extended to `GEYSER` and `BEDROCK_CLIENT`). Every other test file is new (29 files, +3038). Main-code changes to existing classes:
  `ErrorLogGuard` made public with a new `GEYSER` list (5 allowlisted lines, each with a reason; Paper's list is not inherited, so the
  Geyser server guard is stricter, not weaker); `SkipReason.TEST_ABORTED` (above); `McplDriver` and `MineflayerRefDriver` report
  `Edition.JAVA`.
- **New runtime dependencies:** the CloudburstMC Protocol snapshot named by ADR-0018, with its transitive closure (above), **only inside
  the shaded driver-host jar**, which runs in a container. `proofstone-core`, `proofstone-api` and the agents gained no dependency; the
  Bukkit agent's `FloodgateProbe` looks Floodgate up by plugin name, no Floodgate class.
- **Workflows: none.** `.github/` holds only the issue and PR templates.
- **Secrets: none committed.** No PEM block, key, keystore or certificate in the repository (`git grep` for `BEGIN ... PRIVATE KEY` and
  `BEGIN CERTIFICATE`, `git ls-files` for `.pem`, `.key`, `.p12`, `.jks`, `.crt`: none). The added lines hold no 32-hex-digit or 22-char
  base64 string (a 16-byte key in hex or base64 form). The Floodgate key (and the fault's decoy key) is 16 `SecureRandom` bytes per cell,
  copied from memory, redacted in hex (both cases), base64 (standard and URL, padded or not) and raw form, `plugins/floodgate` dropped
  from every bundle, files read back (`GeyserBundleTest`). The stand-in's keystore and certificate are generated per run in a temp
  directory. Weak spot: the raw form is matched as ISO-8859-1 text; a raw key in a UTF-8-decoded log would not match (low risk: the
  key file is never logged, and the directory is never bundled).
- **Mojang binaries: none.** No jar besides the Gradle wrapper; Geyser and Floodgate are downloaded by sha256 at run time.
- **Sleeps and retries: none in harness or test code.** The driver host waits on a `Condition` with a deadline (ADR-0007); `sleep 0.5`
  polling exists only in `scripts/spike/geyser/*.sh` (spike helpers, not harness code).
- **API v0 additive only:** yes (item 2).
- **Diff size over the 400-line guide** (non-test, non-doc lines): #151 932 (spike scripts, not said), #153 687, #154 2058 (includes the
  31-line pin file), #155 1988 (includes the 350-line Geyser config template), #156 484. #153 to #156 said so in the PR with a reason.
- **R13 and the other safeguards:** `docs/robustness.md` section 5 "Bedrock review (T-M9-99)".

### What is NOT verified

- BDS in any form; whether BDS accepts an unsigned login (T-M10-01).
- Geyser's refusal of an unsupported Bedrock protocol (T-M10-05); any Bedrock protocol other than 2225.
- Two Bedrock clients at once (T-M10-07); window clicks, `useBlock`, forms and resource packs for a Bedrock client (driver v1).
- Vanilla `/list` for a Bedrock client vs a Java client (T-M10-10).
- Geyser on Folia, Purpur or Spigot, on any `paper` row but 26.3, behind a proxy, or with Geyser and Floodgate on different servers.
- The JWKS document of the stand-in (never fetched with validation off); a Java 8 image for the trust store.
- That no packet reaches Microsoft: only names looked up are checked (egress open by design).

### Decisions needed from the maintainer

1. **Accept M9 as done for the Geyser scope** with the gaps above (refused-protocol fault unverified, label gap, snapshot driver), or ask
   for T-M10-05 and T-M10-06 first. Recommendation: accept; neither gap turns a red into a green, and both are carded.
2. **BDS (T-M10-01):** approve a BDS source (image by digest or zip by sha256, its EULA, its size) for the spike, or keep BDS out.
3. **The refused-protocol fault (T-M10-05):** allow a default-off test-only protocol switch in `drivers/bedrock`, or accept the fault as
   unverified.
4. **Approve `docs/plan/tasks/M10-ecosystem.md`** (19 cards; 11 marked "Needs maintainer decision": images, servers to download, a
   template repository, hosting, promotion).
5. **Do not promote the Geyser row** until the driver is on a release (ADR-0018 section 8; T-M10-04 is blocked upstream).
6. Unchanged from M7 and M8: Central namespace verification, Disenchantment CI, publish, tag, 0.1.0 go/no-go (maintainer only).

### Re-estimate

M9 was estimated at 4 focused weeks after the spikes (range 2 to 6). Actual: first M9 commit 2026-11-17 12:31, last merge (#159) 20:15
(+01:00), **about 8 hours of agent wall clock** for 9 cards (01, 03 to 08, 11, 12) and 21 non-merge commits, plus this review (about
41 minutes of Docker runs). The spike's working offline route removed the "no route" branch, and BDS left the milestone.

| M | Estimate | Reason |
|---|---|---|
| M10 Part A (Bedrock leftovers) | **about 1 week, range 3 days to 3 weeks** | BDS spike 2 days; if feasible, a host, a script agent and a conformance subset (the range); the Geyser cards 05 to 10 are small. T-M10-04 waits on an upstream release |
| M10 Part B (ecosystem) | **ongoing** | Spikes of 2 days each; the template and guide about 1 week; the docs site after a hosting decision |

### M10 refined

`docs/plan/tasks/M10-ecosystem.md`: exit criterion per part, context, Flake rule, 19 cards (Part A: BDS spike, host and conformance
moved from M9; release pin; refused-protocol fault; guard and label gaps; two clients; ADR-0018 amendment; promotion; `/list`. Part B:
Sponge, Gate, Nukkit-family and PocketMine/Endstone spikes, pack template and guide, docs site, the two optional items, Paper forks;
review). **Not approved; nothing started.**

### Leftover items (M8 and M9 follow-up files)

- `M8-followups.md`: every card merged except T-M8-26 (declined); its header now says so. Boxes of 14, 15, 20, 21, 22 unticked by their
  PRs, left as they are.
- `M9-bedrock.md`: header now states the merged cards and the move of 02, 09, 10 to M10. Boxes of 04, 05, 06 unticked by their PRs,
  left as they are; this review is their evidence.
- No `M9-followups.md` is written: every M9 leftover is an M10 Part A card.

## M10 review (T-M10-99)

Date 2026-11-17/18. Ran on `card/T-M10-99-m10-review` from origin/main `a2f9cb3` (M10 merges #161 to #165; M10 diff `d5ebd52..a2f9cb3`,
29 files, +764 / -46). Local runs only, Windows 11, Docker Desktop 29.9.3, JDK 21 for Gradle, JDK 26 for Maven. One Docker run at a time
under `scripts/dockerlock.sh`, `--no-parallel --max-workers=1`, EULA in the command environment only, one cell per invocation. Every
run's XML (and the report, bundles and `build/release`, where written) copied aside before the next run (local only, not committed).
Every XML read; no red seen, none retried. Judged from code and XML, not from PR text. The mutation check ran first, on the clean tree
(`dirty=false`); this review's doc edits started after the last Docker run.

**Maintainer decisions this session (2026-11-17), stated as given:**
- The M10 plan (`M10-ecosystem.md`) is **approved**.
- **BDS spike (T-M10-01) deferred.** Desk research behind it, unverified by any run: BDS has no supported offline (unsigned) remote
  join; NetherNet is BDS's default transport from 26.50 while our driver speaks RakNet only; the Mojang EULA was not read for this use;
  Mojang publishes no sha256 for the BDS zip. Recommendation recorded: only if the maintainer approves a source, use the `itzg` BDS image
  with `DIRECT_DOWNLOAD_URL` and our own sha256, `transport=raknet`, BDS 26.50 or older. Endstone is a follow-up question after that.
- **The refused-protocol switch is approved** (T-M10-05, merged #163).
- **T-M10-04 stays blocked:** no release of `org.cloudburstmc.protocol` on Maven Central or `repo.opencollab.dev/maven-releases` on
  2026-11-17. Re-checked by this review on 2026-11-18: `bedrock-connection/maven-metadata.xml` is 404 on both. T-M10-09 waits on 04.
- **Part B (cards 11 to 18) needs maintainer decisions and is not started.** (On the card list, 14 and 18 carry no "Needs maintainer
  decision" mark; the maintainer held them with the rest.)

**Verdict: M10 exit criterion NOT MET.** Part A is **partly met**: the Geyser row's M9 findings 05, 06, 07, 08 and 10 are closed with
runs, the row stays `experimental` with its blocking reason named, but BDS has no verdict (deferred, nothing ran) and 04 and 09 are open
(blocked upstream). Part B is **not met**: no spike ran, no template or docs-site decision. M10 stays open ("ongoing" in this file).
Nothing was published or tagged.

### Evidence from this run

| Run | Result |
|---|---|
| `bash scripts/release-mutation-check.sh` (no flag), clean worktree at `a2f9cb3` | **PASS**: head `a2f9cb38b6d5`, `dirty=false`, cell paper@26.3, killed 4 of 4 (Gradle and Maven: `double-output`, `accept-any-right`), both baselines PASS, no allowed-missing. 242 s. Local only; publishes nothing |
| `bash scripts/gates.sh --max-workers=1` at `a2f9cb3` | `gates: OK` (check, docsCheck, release-gate test, `bad-tests-pin-check: 0 failed`, renovate unchanged); 134 s. Re-run on this branch after the doc edits: see the PR |
| `:conformance:smoke`, no selector: paper, folia, purpur, spigot @26.3 | Green: tests 8, skipped 0, failures 0, errors 0; 264 s; 0 `moved too quickly!` |
| `:conformance:run -Dproofstone.cells=geyser@2.12.1+paper@26.3`, 3 separate invocations | 3 of 3 green, each `ConformanceGeyserTest` tests 8, skipped 3, failures 0, errors 0 (cell check, C-G01 to C-G05; skipped with reasons in the XML: window clicks and `useBlock`, forms, resource packs, each `TestAbortedException` naming ADR-0018 section 2); suites 50.8 to 52.2 s; invocations 55 to 60 s; cell boot 34.0, 32.3, 33.1 s. Every test case's display name is `geyser@2.12.1+paper@26.3 (offline-stub)`; `proofstone-report.json` has `"bedrockAuth": "offline-stub"` on all 8 rows |
| `:conformance:run -Dproofstone.cells=fabric@26.3` (newest native loader row), 3 separate invocations | 3 of 3 green, each tests 11, skipped 0, failures 0, errors 0 (C01 to C08, CD01, CD02, loader window test); 188 to 197 s |
| `:conformance:run -Dproofstone.cells=velocity@4.3.1+paper@26.3` (newest topology row), 3 separate invocations | 3 of 3 green, each tests 2, skipped 0, failures 0, errors 0 (T00, P01 to P05); 70 to 76 s |
| `:examples:bad-tests:metaTest` | BUILD SUCCESSFUL, "every bad test produced its expected verdict (VACUOUS, FLAKY, DISAGREE, INVALID, INVALID, LEAK, FAIL, SKIPPED)"; failures and 2 skips by design, errors 0; 4 m 33 s |
| `:examples:bad-tests:metaTopologyTest` | BUILD SUCCESSFUL, 7 of 7 `INVALID`, each `tests=1 failures=1` by design, errors 0, 0 `timed out joining`; 6 m 57 s |
| `:examples:bad-tests:metaModTest` | BUILD SUCCESSFUL, "(INVALID, LEAK, INVALID, INVALID, FAIL)"; 5 suites `tests=1 failures=1` by design, errors 0; 1 m 56 s |
| `:examples:bad-tests:metaBedrockTest` | BUILD SUCCESSFUL, "every bad test produced its expected verdict (INVALID, INVALID, INVALID)"; 3 suites `tests=1 skipped=0 failures=1 errors=0` by design. `key-mismatch`: `Floodgate refused the data Geyser sent`, hop Geyser to Floodgate. `refused-protocol`: `Geyser refused the Bedrock protocol the driver sent`, hop driver host to Geyser (protocol number). `stand-in-absent`: `the discovery stand-in answered none of the two expected names`. None a join timeout. Cell boot 32.5, 31.5, 32.0 s; 2 m 14 s |
| `:proofstone-core:geyserTest -Dproofstone.cells=geyser@2.12.1+paper@26.3` (host ITs, workflow.md verifier rule; T-M10-07 added `GeyserTwoClientsIT`) | Green: `GeyserApiContract` 2, `GeyserCellIT` 1, `GeyserTwoClientsIT` 1, skipped 0, failures 0, errors 0; 120 s |
| Upstream check for T-M10-04 (2026-11-18) | `org/cloudburstmc/protocol/bedrock-connection/maven-metadata.xml`: 404 on `repo1.maven.org` and on `repo.opencollab.dev/maven-releases` |
| Containers of ours left running after the runs | none (checked after every run; only the Testcontainers `ryuk` reaper, which exits by itself) |

Not re-run: the example `examples/geyser-bedrock` (no M10 change touches it) and T-M10-10's four `/list` transcripts (a temporary
scenario, not kept; geyser.md is the only record).

### Bedrock flake numbers (counted apart from Paper, topology and loader, M10 Flake rule)

Bedrock joins per invocation: `:conformance:run` on the Geyser row 9 (the cell check, `PsG01` to `PsG04`, `PsG05A` and `PsG05B`, and
`PsG05`, `PsG06` in the skip tests, which join, show the refusal and abort); `metaBedrockTest` 3 (one per fault, each faulted by design).

| Source | Invocations | Bedrock joins | Join timeouts (stage) | Log-guard reds | Other reds |
|---|---|---|---|---|---|
| T-M10-05 `metaBedrockTest` (secondary, geyser.md) | 3 counted + 2 probes | 9 counted (faulted by design) | 0 | 0 | 0 unexpected; the 2 probes were `INVALID` as expected |
| T-M10-06 conformance (secondary, geyser.md) | 3 | not recorded | 0 | 0 | 0 |
| T-M10-07 conformance and IT (secondary, geyser.md) | 3 conformance + IT | not recorded | 0 | 0 | 1 dev red (`/say` needs op; C-G05 moved to `/me`), kept as a gotcha |
| **T-M10-99 (this run)** | **3 conformance + 1 meta + 1 `geyserTest`** | 30 + the host ITs' joins (27 clean, 3 faulted by design) | **0** | **0** | **0** |

- **Bedrock join timeouts: 0** (`timed out joining` and `JoinTimeoutException` appear in no run output of this review).
- **Bedrock log-guard reds: 0.** Since T-M10-06 the guard covers the server (`ErrorLogGuard.Patterns.GEYSER`) and, per test and at
  cell close, the stand-in and the driver host (`AuxLogGuard`, both allowlists empty). Limit: no real run has shown the new guard fire
  on a real line; its red path is proven by unit tests (`AuxLogGuardTest`, `CellGuardsTest`) and by the stand-in-absent fault only for
  the expected `STUB refused` exemption.
- **Paper, topology and loader numbers (apart):** smoke 8 of 8, `fabric@26.3` 3 of 3, `velocity@4.3.1+paper@26.3` 3 of 3, the three
  Java meta tasks once each with every expected verdict; 0 single-server or topology join timeouts, 0 container-start failures, 0
  `moved too quickly!`. No bridged `paper@1.8.8` and no NeoForge conformance run (not in this review's set).

### Exit criterion, re-scored

| # | Item | Status | Evidence |
|---|---|---|---|
| A1 | Either a BDS cell passes the conformance subset its protocol subset allows (T-M10-03), or the BDS spike says "not feasible" with evidence and the BDS row in `platforms.md` says so | **NOT MET (deferred by the maintainer)** | Nothing about BDS ran: no image, zip, code or spike output in the repository. The research above is desk work, not a spike verdict, so "not feasible" is not claimed. `platforms.md` BDS row now says "deferred, nothing ran" with the research marked unverified (this PR). T-M10-02 and 03 wait on 01 |
| A2 | The Geyser row has the M9 review's open items closed or re-carded (T-M10-04 to T-M10-09) | **PARTLY MET: 05, 06, 07, 08, 10 closed; 04 and 09 open** | **05** (#163): `metaBedrockTest` here gives `INVALID` for all three faults, the refused one quoting Geyser's refusal and the hop; `ProtocolOverride` reads the switch only with `injectGeyserFault=refused-protocol` beside it (code read, `ProtocolOverrideTest`); T-M10-05 recorded 3 of 3. **06** (#162): display name and `bedrockAuth` seen in this run's XML and report; `CellInvocation` calls `requireGuardsClean` after every test and `GeyserHost.close` once more (code read); 0 guard reds here. **07** (#164): C-G05 green 3 of 3 here, `GeyserTwoClientsIT` green once. **08** (#161): ADR-0018 "Amendments, 2026-11-17" lists the M9 deviations (doc read; gates green). **10** (#165): geyser.md states the cause (Paper's op-only `minecraft.command.list`) with the four transcripts; one run, not re-run here. **04**: blocked, no upstream release (re-checked 2026-11-18). **09**: waits on 04. Both are carded and open, not closed |
| A3 | The Geyser row is promoted or stays `experimental` with the blocking reason named | **MET (stays experimental)** | `versions.yml` row `status: experimental`. `platforms.md` names the reason: the driver is on a pinned snapshot (ADR-0018 section 8), no release exists (T-M10-04). Promotion is the maintainer's call (ADR-0015) and is not prepared (T-M10-09 waits) |
| B1 | Each platform spike has a verdict; a platform that gets cards gets its own milestone file | **NOT MET (not started)** | T-M10-11 to 14 have not run; no `sponge.md`, `gate.md`, `nukkit.md`; no new milestone file |
| B2 | The pack template and the docs site are built or declined by the maintainer | **NOT MET (no decision)** | T-M10-15 and 16 wait on the maintainer (template location, hosting). T-M10-17 (TS front end, Fabric gametest) and 18 (Paper forks) not started either |
| F | Every real-server acceptance has the Flake-rule evidence | **MET for the merged cards 05, 06, 07; 10 is a one-run diagnosis** | 05: `metaBedrockTest` 3 of 3 (card) and once here. 06 and 07: Geyser conformance 3 of 3 in each card and 3 of 3 here; `metaBedrockTest` once here; the IT once here. 10's acceptance is a written cause from one run (no green to repeat). No red was replaced by a green |

### Code review (diff `d5ebd52..a2f9cb3`, 29 files, +764 / -46)

- **Weakened tests: none.** Changed test files gain lines only, except `GeyserFaultTest`, whose removed assertion ("`refused-protocol`
  is not a fault") became false by design in T-M10-05 and is replaced by tests for the new fault. `ConformanceGeyserTest` gained C-G05;
  C-G01 to C-G04 and the skip tests are untouched.
- **The test-only switch** (`-Dproofstone.bedrock.protocolOverride`) is read once in `HostSession.join`, refused without the fault
  property beside it, refused when equal to the codec's own number, and set only by `GeyserFault.driverHostOptions()` through the
  driver host's `JAVA_TOOL_OPTIONS`. As approved; it is production code that exists for a test (ADR-0018 amendment records the kind).
- **Report change:** `bedrockAuth` is added only when the cell id starts with `geyser@`; other rows are unchanged (`ReportWriterTest`).
  The cell id used by the selector is unchanged.
- **No new runtime dependency, no catalog or `versions.yml` change, no API change** (`api-baseline.txt` untouched), **no workflow**
  (`.github/` holds only templates), **no secret** (no PEM block, no 32-hex-digit string in the added lines), **no sleep** in harness or
  test code.
- **Diff size** (non-test, non-doc lines): #162 167, #163 104, #164 2; all under the 400-line guide.
- **Stale docs fixed here:** `robustness.md` R5 and R13 still said two clients and the refused protocol were unrun; `M10-ecosystem.md`
  still said "Not approved; no card is started".

### What is NOT verified

- BDS in any form, including the desk research's four claims (offline join, NetherNet default, EULA, sha256).
- That the stand-in and driver-host log guard fires on a real unexpected line (unit tests only; allowlists empty).
- Any Bedrock protocol other than 2225 joining; the refused-protocol control uses 9999 (a 2224 probe was also refused, geyser.md).
- Window clicks, `useBlock`, forms, resource packs for a Bedrock client (driver v1); Geyser on other servers, rows or behind a proxy.
- That no packet reaches Microsoft: only names looked up are checked (egress open by design, ADR-0018 section 4).
- Every Part B platform.

### Decisions needed from the maintainer

1. **Accept this review's scoring:** M10 open; Part A partly met (05 to 08 and 10 done; BDS deferred; 04 and 09 blocked upstream).
2. **BDS (T-M10-01):** approve a source (recommendation above) or keep BDS out. If kept out for good, say so, and T-M10-01 to 03 can be
   closed with the BDS row saying "not pursued" (not "not feasible": nothing ran).
3. **Part B:** which of 11 to 18 to start, and the decisions they name (images, server jars, Gate binary, template location, hosting).
4. **T-M10-04:** keep waiting, or ask upstream for a release. Do not promote the Geyser row before it (ADR-0018 section 8).
5. Unchanged from M7 to M9: Central namespace verification, Disenchantment CI, publish, tag, 0.1.0 go/no-go (maintainer only).

### Re-estimate

Part A so far: first M10 commit 2026-11-17 22:24, last merge (#165) 23:16 (+01:00), **about 1 hour of agent wall clock** for 5 cards
(the small Geyser cards the M9 review predicted), plus this review (about 45 minutes of Docker runs).

| M | Estimate | Reason |
|---|---|---|
| M10 Part A (rest) | **about 3 days if BDS is approved (range 2 days to 3 weeks); otherwise 0 plus waiting on upstream** | BDS spike 2 days, host and conformance only if feasible (the range). 04 and 09 are about half a day once a release exists, and cannot be scheduled |
| M10 Part B | **ongoing** | Unchanged: spikes of 2 days each after their decisions; template and guide about 1 week; docs site after a hosting decision |

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

**Verifier run, T-M7-19 (2026-11-15): bridged `paper@1.8.8` `:conformance:run`, PR against main, alternating, 5 each.** PR red 3 of 5
(runs 2, 3, 4; C04 "cursor after picking up slot A", run 4 also C03 and C05 INVALID from the C04 failure), main red 2 of 5 (run 1
C05 "the renamed sword on the cursor" expected `Blade` but was null, run 5 one failure). With the agent's counts (PR 3 of 6, main
0 of 6) the totals are PR 6 of 11 and main 2 of 11. The same family (window and cursor state on the bridged 1.8.8 row) fails on
main, so the PR is not shown to be worse, and the bridged path is unchanged by construction (`ConnectSpec.bridged` skips the hold).
Cause unexplained. Two PR full 4-cell smokes were green (tests 8, skipped 0, failures 0, errors 0). Card T-M7-20 diagnoses the flake.


## T-M7-20 the bridged paper@1.8.8 cursor flake

Date 2026-11-16. Diagnosis only, no code changed. Branch `card/T-M7-20-bridged-flake`. Local, Windows 11, Docker Desktop, JDK 21, one
invocation per run under `scripts/dockerlock.sh`, `--no-parallel --max-workers=1`, EULA in the command environment only. Evidence
(XML, `build/proofstone` bundles, Gradle log, container logs captured by a read-only `docker logs -f` watcher) copied aside after every
run (`C:\pw\ev`, local, not committed). No sleep, retry, timeout raise or harness change.

**Counts** (`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=paper@1.8.8
--no-parallel --max-workers=1`, 10 separate invocations per arm, XML read; a run is red when any C03 to C06, C08 or smoke test is not
PASS; C07 is a known Assumption skip on 1.8.8):

| Arm | Red | Where |
|---|---|---|
| `3cde4e8` (main, includes T-M7-19 and T-M8-23) | **3 of 10** | run 7: C04 in the cell conformance, "cursor after picking up slot A" `expected: <true> but was: <false>`, so C03, C04, C05, C06, C08 and both smoke tests INVALID (8 failures); runs 9 and 10: C05 "the renamed sword on the cursor" `expected: <Blade> but was: <null>` |
| `b8d27da` (parent of the T-M7-19 merge, before it) | **2 of 10** | runs 4 and 9: C05 "the renamed sword on the cursor" `expected: <Blade> but was: <null>` |

3 of 10 against 2 of 10 is no difference. The flake **predates T-M7-19** (red 2 of 10 without it, and 2 of 5 on main in the verifier
run) and **survives T-M8-23**. It is not shown that T-M7-19 changed the rate.

Extra, counted apart (not in the table): `:conformance:run ... -Dproofstone.repeat=10 --tests '*C05AnvilRenameCostTest*'` on main:
FLAKY, runs 1 and 7 red with the same C05 assertion, run 9 a bridged join `CONFIGURATION_STALLED` (login acknowledged, configuration
never finished; the known bridged login stall of viaproxy.md, not this family), 7 green. Same with `--tests '*C04ChestMoveTest*'`: 10 of
10 green. The repeat mode keeps every run's driver transcript, so it is the only source of green packets: a green run of the plain
invocation keeps no bundle.

**What the evidence does not have.** The red in run 7 is the *conformance* client `PSConf`. A conformance failure writes the bundles of
the tests it made INVALID, whose client never ran a scenario (0 lines in `driver-transcript.jsonl`), so there is no packet record of
that red. The mechanism is read from the 4 red C05 test transcripts (main runs 9 and 10, before runs 4 and 9), 2 repeat reds and the
click answers of the repeat runs; C04's red is attributed to it by the identical assertion shape, not by a packet record.

**Mechanism: the driver releases `click()` on the full resync, and on 1.8.8 the cursor arrives in a separate packet after it.**
- Answer to the third click of main run 9 C05 (ms after the send): `ContainerSetSlot id=1 slot=0` 91.3, `slot=2` 91.3,
  `ClientboundPingPacket` 93.3 (pong sent at once), `ContainerSetContentPacket id=1 slots=39` 93.3, **`ClientboundSetCursorItemPacket`
  96.3**, then the failing test's `ServerboundContainerClosePacket` at 96.3. The ping turns the server's rejection into a resync
  (viaproxy.md, "Version span, pre-1.17"). The 1.8 window items have no carried stack, so `ContainerSetContent` carries an empty
  cursor and the real cursor follows as its own packet, 0 to 9 ms later. Over the 72 transcripted answers of the repeat runs the cursor
  came with the content (same timestamp) 63 times and after it 9 times (0.4, 0.7, 1.0, 1.0, 2.0 x4, 8.8 ms); never before it.
- `McplSession.click` waits on `model.stateId() != sent || model.syncs() != sentSyncs` (`syncs` counts `ContainerSetContent`), so it
  returns on the content packet. `applyContent` sets the player cursor from the content's empty carried item, so a read between the
  content and the cursor packet sees an **empty cursor**, `ItemView.name()` null: exactly "was: <null>". The same read in C04 is
  `cursor().is("diamond_sword")` false.
- Only **pick-up** reads are exposed: C04 after `click(SLOT_A)` and C05 after `click(2)`. Placing clicks expect an empty cursor, which
  the content already says, so "cursor after placing" cannot go red this way. This matches every red seen.
- Why a few percent: the test thread polls with `Await` (25 ms, then 50, 100, 200). In the repeat runs the click returned at 93 to 96 ms
  after the send (probe 3) or about 205 ms (probe 4) in nearly every click, and the server's answer arrives at 78 to 98 ms, so the probe
  lands inside the answer far more often than a uniform draw would. In every red the close is 0 to 3 ms after the `SetCursorItem`
  record (main run 10: content 92.3, cursor 93.3, close 96.0; before run 4: content 91.2, cursor 94.6, close 95.6), so the read came
  before the cursor was applied and after the content released the click.
- Per exposed read (4 per run: conformance C04, C05 and the two tests): main 3 of 40, before 2 of 40.
- It is a race in the driver's release condition (answer = the content packet) meeting a ViaProxy 1.8 translation that splits the
  answer. Not ViaProxy dropping the cursor: it arrived in all 6 reds.
- **Same shape as T-M8-23, a different packet.** T-M8-23: `click` returns on the first stateId change and a close beats a later slot
  update (loader rows), fixed in the scenarios by awaiting the client's own slot. Here `click` returns on the content and the cursor
  update is later. T-M8-23's `placeCursorInInventory` and `awaitInPlayerSlots` do not cover a cursor read, so this red survives it.
  The close-before-slot-update shape was not seen on this row: no `(no such window, ignored)` or `(not applied` line followed a close
  in the 6 red transcripts.

**Ruled out.** The cursor never arriving; the cursor arriving before the content; T-M7-19's hold (bridged joins skip it, and the
before arm is red 2 of 10); a rejected-click lock (every answer was the normal ping plus resync).

**Not shown.** A direct measurement of the release instant (the transcript records packets, not the test thread's read; the order is
inferred from the null value and the close time); the C04 red's packet record; why the content and cursor split varies up to 9 ms
(not attributed to Paper or to ViaProxy).

**Fix card proposed:** T-M7-21 in [M7-followups.md](tasks/M7-followups.md): C04 and C05 await the cursor before asserting it (the
T-M8-23 style, `Await`, no sleep), same assertion text, and a second commit keeps the conformance client's transcript when the cell
conformance fails.

## T-M7-21 await the cursor after a pick-up click (C04, C05)

Date 2026-11-16. Branch `card/T-M7-21-await-cursor`. Local, Windows 11, Docker Desktop, JDK 21. Every real-server run is its own invocation
under `scripts/dockerlock.sh`, EULA in the command environment only, `--no-parallel --max-workers=1`; evidence (XML, bundles, Gradle logs)
copied aside after each run (`C:/pw/t721-ev`, local, not committed).

**Change.** C04 and C05 call `ConformanceFixture.awaitCursor` (`Await`, the 10 s client budget) after the pick-up click, then assert with the
same message and expectation as before. No sleep, retry, timeout raise or looser assertion; a cursor that never shows fails with
`AwaitTimeoutException` at the timeout. Second commit: a failed cell conformance keeps the conformance client's driver transcript in the bundle
of every test it makes INVALID (`conformance-driver-transcript.jsonl`; before, the INVALID tests' bundles held 0 transcript lines). That commit
has a `BundleWriterTest` case; it was not seen red first, and no real-server run produced a failed conformance, so the file is not seen on a real bundle.

**Driver-side alternative, not taken.** `McplSession.click` on pre-1.17 bridged rows could also wait for the cursor packet. The cursor is the
last packet of the answer and has no marker (T-M7-20: it follows the resync 0 to 9 ms later, and over 72 answers it came with it 63 times), so the
driver cannot know that a cursor is coming without a timeout of its own, which would be a wait with no end condition. The scenario await names
its end condition (the expected cursor), so it is the default. Other scenarios that read a cursor right after a pick-up click would need the same
await; none of C03 to C08 does besides C04 and C05 (T-M7-20).

**Counts.**

| Run | Result |
|---|---|
| `ConformanceCursorRaceTest` before the fix | 3 of 4 red (C04, C05 on the cursor assertion; never-shows with `AssertionError`, not `AwaitTimeoutException`) |
| `ConformanceCursorRaceTest` after | 4 of 4 green |
| `bash scripts/gates.sh --max-workers=1` | OK |
| `:conformance:run -Dproofstone.cells=paper@1.8.8`, 10 invocations | 10 of 10 green: tests 17, skipped 10, failures 0, errors 0 each; no join stall |
| `:conformance:smoke -Dproofstone.cells=paper@1.8.8`, 3 invocations | 3 of 3 green: tests 2, skipped 0, failures 0, errors 0 |
| `:proofstone-core:integrationTest`, 3 invocations | **2 of 3 green**; run 2 red: `PaperHostIT.serverWithoutTheAgentFailsReadinessWithTheLogTail`, `paper@26.3 failed to start: Container startup failed ...` (same message start as the intermittent startup failure T-M8-24 diagnoses; not investigated here, this PR does not touch `PaperHost`) |

10 greens do not prove the fix: at the earlier 20 to 30 percent rate, 10 greens happen about 3 to 6 percent of the time. The mechanism (T-M7-20) and
the unit case are what carry it.

## M6 release status refresh (T-M6-100, 2026-11-18)

Full local re-run at HEAD `e77149b`, clean tree, one Docker run at a time under `dockerlock`, XML and logs read, no retries.
Maven 3.9.9 was installed user-locally (zip SHA-512 checked against Apache's published value); `MAVEN_HOME` points at it.

| Run | Result |
|---|---|
| `bash scripts/gates.sh` | OK (three orphan copies in `.claude/worktrees` had to be moved out first: `docsCheck` scanned them) |
| `:conformance:smoke` | 8 tests, skipped 0, failures 0, errors 0 |
| `:conformance:run`, default cells | 50 tests, failures 0, errors 0, 9 reasoned skips (`FOLIA_REGIONS`, `SIMULATED_BEDROCK`) |
| `:conformance:run` on `fabric@26.3`, `neoforge@26.3`, `geyser@2.12.1+paper@26.3`, `velocity@4.3.1+paper@26.3`, `bungeecord@2111+paper@26.3` | BUILD SUCCESSFUL each |
| Examples: gradle-paper-plugin, geyser-bedrock, loader-datapack, loader-mod (fabric, neoforge), smoke topology (velocity) | green |
| `:examples:bad-tests:metaTest` | BUILD SUCCESSFUL, every bad test gave its expected verdict |
| `integrationTest` of core, both drivers and the five platform agents | BUILD SUCCESSFUL |
| `bash scripts/release-mutation-check.sh` (no flag) | **PASS**: head `e77149b`, dirty false, killed 4 (both examples, both bugs) |

Not a defect: `:examples:smoke:topologyExample` on `bungeecord@...` fails with "has no cell to run", because that example is Velocity-only by design.

**Verdict unchanged: NO-GO for a public 0.1.0 claim.** Blocking, all the maintainer's or external: Disenchantment CI (another repo),
Central namespace verification and publish, GHCR push, the GitHub Action on a hosted runner, the tag. Code and local gates are
tag-ready at `e77149b`; re-run the mutation check at the tag HEAD first.

## T-M7-16 result (2026-11-19, card T-M6-109 branch from origin/develop `0eb960c`)

`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=spigot@26.3 --no-parallel --max-workers=1`,
10 separate invocations, one at a time, no retry, evidence copied aside after each (`/c/pw/evidence-T-M7-16/run-NN`, local, not
committed). The pinned jar was in the Spigot cache (`build/proofstone/spigot-cache/22b5f5b3...jar`, copied from an earlier worktree);
no BuildTools line in any log, so no rebuild was part of any run. Windows 11, Docker Desktop 29.9.3, JDK 25 toolchain.

| Run | Wall | tests | skipped | failures | errors | C04 | Join timeouts | `moved too quickly` |
|---|---|---|---|---|---|---|---|---|
| 1 | 7 m 50 s (first run, cold Gradle home) | 17 | 3 | 6 | 0 | `skipped=0 failures=0 errors=0` | 0 | 0 |
| 2 to 10 | 3 m 14 s to 3 m 17 s each | 17 | 3 | 6 | 0 | `skipped=0 failures=0 errors=0` | 0 | 0 |

- **C04 red: 0 of 10. Not reproduced.** C03 to C08 and both smoke tests passed in all 10. Join timeouts on spigot@26.3: 0 of 10
  runs (no `JOIN STALL` or `timed out joining` line in any gradle log). Counted apart from M6's flake (which is paper@26.3
  Gradle example, 3 of 61): none of those numbers move. Item stays **open, unexplained**: 1 red of 12 known spigot@26.3 C04 runs.
- **The card's Verify command is itself red, 10 of 10, for a reason that is not spigot.** Gradle exit 1 each time: 6 failures, all
  `initializationError` from `ProofstoneExtension.plan` ("has no cell to run: -Dproofstone.cells names none of its @Matrix
  platforms") in `CD01DataPackFunctionTest`, `CD02DataPackRecipeTest`, `CR01ResourcePackTest` (3) and
  `CR02ResourcePackWrongHashTest`. Those classes declare paper, fabric and neoforge, not spigot, and fail loudly instead of
  skipping when the selector names a cell none of their platforms contain. The 3 skips are reasoned (`SIMULATED_BEDROCK` once,
  `FOLIA_REGIONS` twice). So `:conformance:run -Dproofstone.cells=<spigot or purpur row>` cannot be green until those classes
  skip or are excluded; the other 10 classes were green. Not fixed here (no code changes); not carded yet, maintainer call
  (the loud failure follows the "fail loudly on unknowns" rule, so this may be intended and the Verify lines should add `--tests`).
- **Verify corrected (T-M7-22, 2026-11-20).** The loud failure is intended, so the selection changes, not the guard. Corrected
  command (the 10 spigot-capable classes, by `--tests`; the four paper-only classes are not selected):
  `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=spigot@26.3 --tests '*C03GiveRoundTripTest' --tests '*C04ChestMoveTest' --tests '*C05AnvilRenameCostTest' --tests '*C06AnvilCombineTest' --tests '*C07GrindstoneTest' --tests '*C08ShiftClickCraftTest' --tests '*ConformanceSmokeTest' --tests '*ConformanceFoliaEventTest' --tests '*ConformanceFoliaTeleportTest' --tests '*ConformanceBedrockPrefixTest' --no-parallel --max-workers=1`.
  The old command (no `--tests`) is **red by design**, 10 of 10.
- The root-owned `bukkit.yml` line (T-M8-02) is not in any gradle log; a green run keeps no server log, so it could not be
  checked per run. No red, so no log to read.
- Cleanup: no Proofstone container left, Gradle daemon of this worktree stopped.

## T-M7-22 result (2026-11-20, card T-M7-17 branch `card/T-M7-17-verify-selector`)

Corrected Verify (ROADMAP "T-M7-16 result", card T-M7-16 Verify): `:conformance:run -Dproofstone.cells=spigot@26.3` with ten
`--tests` class filters (C03 to C08, `ConformanceSmokeTest`, `ConformanceFoliaEventTest`, `ConformanceFoliaTeleportTest`,
`ConformanceBedrockPrefixTest`), `--no-parallel --max-workers=1`, under `scripts/dockerlock.sh`. One run, no retry. Gradle
`BUILD SUCCESSFUL in 11m 22s`, exit 0. Windows 11, Docker Desktop, JDK 25 toolchain.

| Suite (XML) | tests | skipped | failures | errors | Skip reason |
|---|---|---|---|---|---|
| C03, C04, C05, C06, C07, C08 | 1 each | 0 | 0 | 0 | none |
| ConformanceSmokeTest | 2 | 0 | 0 | 0 | none |
| ConformanceFoliaEventTest | 1 | 1 | 0 | 0 | missing `FOLIA_REGIONS` on spigot@26.3 |
| ConformanceFoliaTeleportTest | 1 | 1 | 0 | 0 | missing `FOLIA_REGIONS` on spigot@26.3 |
| ConformanceBedrockPrefixTest | 1 | 1 | 0 | 0 | missing `SIMULATED_BEDROCK` on spigot@26.3 |
| **Total** | **11** | **3** | **0** | **0** | all 3 reasoned (capability absent on Spigot) |

- Green: 0 failures, 0 errors. The 3 skips are the reasoned capability skips; the paper-only classes are not selected, so no
  `initializationError`. Counted apart from the T-M7-16 numbers (17 tests there included the 6 initialization errors).
- Single run only: C04 green (C04 result in its XML: `skipped="0" failures="0" errors="0"`). Item stays as in T-M7-16.

## T-M6-109 join-stall analysis (2026-11-19, no code changed)

Data points read (all local evidence, kept outside the repo):

| # | Where | Path | Stalled stage | Server saw login? | Source |
|---|---|---|---|---|---|
| A | `:examples:bad-tests:metaTest`, paper@26.1.2, `LeakE2E` conformance client `PSConf`, protocol 777 | bridged (ViaProxy) | `CONFIGURATION_STALLED`, last packet `ClientboundLoginFinishedPacket` | yes: `UUID of player PSConf`, no `logged in with entity id`, then `lost connection: Disconnected` at +30 s | `/c/pw/evidence-T-M4-13/dev2/` (`join-stall.txt`, `server.log`, `viaproxy.log`) |
| B | C05 repeat run 9, paper@1.8.8 | bridged | `CONFIGURATION_STALLED`, last packet `ClientboundLoginFinishedPacket` | yes: `logged in with entity id 52` (1.8.8 has no configuration phase) | `/c/pw/ev/rep/c05/bundles/` |
| C | paper@26.3 Gradle example and plugin tests, 3 of 61 (T-M6-10 and earlier) | native | client sent `ClientIntentionPacket` and `ServerboundHelloPacket`, no reply (`LOGIN_START_SENT_NO_REPLY` shape; recorded before T-M6-14, stage not logged) | not recorded | ROADMAP "M6 pre-release findings" item 3 |
| D | smoke c02 on paper@1.8.8 and spigot@1.8.8 | bridged | `JOIN_TELEPORT_HELD_NO_HEALTH` (different stage, fixed by T-M7-19) | yes | `/c/pw/t719-ev2/` |

**Shown.**
- In A the client received `ClientboundLoginFinishedPacket` and then nothing for 30 s: no registry data, known packs or finish
  configuration packet reached the client. ViaProxy logged `Connected successfully! Switching to CONFIGURATION state` and, unlike
  every passing connection in the same evidence (3 other joins, same ViaProxy and server image), never logged `Configuration finished!
  Switching to PLAY state`. The Paper log has no error, no kick and no `logged in` line; it ends with the player's UUID line.
- The stalled stage common to A and B is **configuration, after login finished**, on a **ViaProxy-bridged** cell, with ViaProxy
  logging the switch to CONFIGURATION and never the finish. A and B are on different servers (26.1.2 and 1.8.8), so the stall is not
  one Paper version.
- C is a **different stage** (no reply to the hello, before login finished), on native cells. So there is **no single stage common
  to all of the join timeouts on record**. Calling them one flake would be a guess. Do not pool A with C.
- The ViaVersion mapping loader finishing in the same second as the join (A) is not a discriminator: it happens in the passing runs
  too (evidence dev2, other ViaProxy logs).
- The `driver-transcript.jsonl` of A holds 0 lines (conformance client; `driver-transcript` is written by the scenario), so the
  packet record comes only from `join-stall.txt`.

**Guessed, not shown.**
- Whether the configuration packets were never sent by Paper, or were sent and lost or held inside ViaProxy's 26.3 to 26.1
  translation (registry data, tags, known packs). The logs of both sides are silent at the stall.
- Whether 2 data points (A, B) are one cause. Same stage and same ViaProxy log shape, but B's server was already in play.
- Whether any of it relates to the 3 native reds (C). Nothing observed ties them.
- Contention (other containers on the host). Never measured at a stall.

Next step: card **T-M6-109** in [M6-dogfood-release.md](tasks/M6-dogfood-release.md) (proposed, not started).

## T-M6-109 differential runs (2026-11-19, no repo code changed)

`:conformance:run -Dproofstone.cells=<cell> -Dproofstone.repeat=N --tests '*ConformanceSmokeTest.c02*'` (one cell boot, N joins through
one ViaProxy), one invocation at a time under dockerlock, evidence in `/c/pw/evidence-T-M6-109/<run>` (local). Windows, Docker Desktop.

| Run | Cell | Joins | Stall shape (no `Configuration finished`) | Other reds |
|---|---|---|---|---|
| b1 | paper@1.8.8 | 30 | 1 (repeat 4), reported as `could not join ... disconnect.endOfStream`, not a join timeout | 0 |
| e1 | paper@1.8.8 | 100 | 0 | 1 (repeat 42, ViaProxy kick, below) |
| e2 | paper@1.8.8, 16 host CPU burners | 100 | 1 (repeat 62, same report) | 0 |
| w1 | paper@1.8.8 (+ read-only socket queue watcher) | 100 | 0 | 1 (repeat 43, ViaProxy kick) |
| w2, w3 | paper@1.8.8 (+ watcher) | 100 each | 0 | 0 |
| b2, b4 | paper@26.1.2 | 30, 100 | 0 | 0 |
| b3 | paper@26.3 native (control) | 30 | 0 | 0 |

Totals: paper@1.8.8 530 joins, stall shape 2 (0.4 percent), ViaProxy kick 2; paper@26.1.2 130 joins, 0; native 30, 0. A
`CONFIGURATION_STALLED` `JoinTimeoutException` was 0 of 6 runs here (point B's shape occurred twice but both were classified as
`could not join`). Point A (26.1.2) was not reproduced: 0 of 130. e1 ran with an unused env-gated diagnostic compiled in (never set).

**Shown.**
- A stalled join is the same on the wire as points A and B: `LoginFinished`, client `LoginAcknowledged` 1 ms later, nothing more
  (the `driver-transcript` of the failed repeat is empty; the packet order is in the timeout message when there is one). ViaProxy log
  ends at `Connected successfully! Switching to CONFIGURATION state`, the 1.8.8 server logged `logged in with entity id`. The packets
  that ViaProxy should send to the client (`UpdateEnabledFeatures`, tags, known packs, registry data, 4 ms after the ack in every
  passing join) never leave ViaProxy. So the holder is **ViaProxy** (or the link to it), not Paper and not the driver, for the
  1.8.8 shape.
- The close at +30 s races the client's own PT30S: 2 of 2 stalls here came back as `ClientDriverException: could not join ...
  disconnect.endOfStream`, with no stage and no `join-stall.txt`. That is an evidence gap in the harness, not yet fixed.
- Not load: 16 CPU burners gave 1 of 100, the idle runs 1 of 330.
- A second ViaProxy-side failure on the same cell: `S2CLoginCompressionPacket.read` `IndexOutOfBoundsException` (ridx 1, widx 1) in
  the proxy to server decoder, with a Via remap error of `DISGUISED_CHAT (0x1C)`, 2 of 530 joins, at repeat 42 and 43.

**Guessed, not shown.**
- Mechanism: ViaProxy `ChannelUtil.disableAutoRead` at login success and `restoreAutoRead` only in `ConfigurationPacketHandler` on the
  client's ack (source read on GitHub main, not the pinned 3.4.15 build; the helpers are not thread-safe). A lost restore would leave
  the server socket unread, which fits both A and B. The socket watcher (`/proc/net/tcp` rx queue of the ViaProxy container, 1 s
  samples, read-only) was running in w1 to w3 but no stall happened in them, so it never had the chance to show unread bytes.
- Whether the decode error is the same race (a state switch applied late to the proxy to server decoder) or not.
- Whether A (26.1.2) and B share the cause; whether C-F02 (folia 1.20.6) and the prefixed-Bedrock stall are this stage:
  folia 1.20.6 stalled before login (`Hello`, no reply, no ViaProxy line), a different stage; the Bedrock-prefixed one has no `[AUTH]` line
  either, so it is not this stage as observed. Not pooled.

**Fix:** none in this repo. No test could be written first (the cause is in ViaProxy). The ack-delay experiment (hold
`LoginAcknowledged`) was built and compiled but not run: at 0.4 percent a run of 300 joins per arm is the minimum, and any fix that
delays the ack is a sleep. **Next experiment:** run `w`-style loops until a stall is caught with the socket watcher on (expect rx queue
above 0 on the proxy to server socket if the restore was lost); then try ViaProxy 3.4.16+ (pin change: needs maintainer approval).
Proposed (not carded): make a +30 s disconnect during join report the stage (small, test-first, `McplDriver`).



## Final ledger (2026-11-19, orchestrator session)

**Release state.** `v0.1.0` is tagged on `main`. All four artefacts (`proofstone-api`, `-core`, `-protocol`, `-drivers-mcpl` at `0.1.0`) are on Maven Central
(POM, jar, sources, javadoc and `.asc` return HTTP 200, checked 2026-11-19; deployment `c9a579d2-231a-448a-bb0f-fc9c400716b3` PUBLISHING then synced). Namespace
verification is recorded in ADR-0012. A clean-room consumer build of both examples against Central is green three times each on paper@26.3 (T-M6-105, #174; T-M6-107, #179).
The GitHub Release `v0.1.0` is still a draft (maintainer action). No release PR for a next version is prepared: the post-0.1.0 changes are tooling, docs and test-harness only,
and the maintainer picks `0.1.1` or `0.2.0`.

**Known problems.**
- Join stalls: T-M7-16 not reproduced (0 of 10); T-M6-109 names ViaProxy for the 1.8.8 bridged stall, mechanism unproven (#182). Native paper@26.3 flake (3 of 61) is a different stage, cause unexplained.
- Prefixed-Bedrock stall through ViaProxy and Folia 1.20.6: not shown to be the same family; no card of their own beyond T-M6-109 notes.
- GitHub Action: documented (T-M6-108, #178), still not published or verified on a hosted runner.
- Hosted `gates` fails intermittently on the first run when `repo.opencollab.dev` does not serve `text-serializer-nbt-0.2.1.jar` (4 of 5 first runs this session; reruns passed). Needs a maintainer decision (mirror or pin change).
- The T-M7-16 Verify command is red because six conformance classes (`CD01`, `CD02`, `CR01`, `CR02`) declare no spigot cell: needs a maintainer call.

**Cards.** Every card ID in `docs/plan/tasks/`: merged cards with their PR, open cards with the reason. Nothing is silently open. Checkbox state in the card files is not reliable for older cards.

| Card | Status | PR or reason |
|---|---|---|
| T-M0-01 | merged | #1 |
| T-M0-02 | merged | #3 |
| T-M0-03 | merged | #4 |
| T-M0-04 | merged | #5 |
| T-M0-05 | merged | #6 |
| T-M0-06 | merged | #2 |
| T-M0-07 | merged | #7 |
| T-M0-99 | merged | #8 |
| T-M1-01 | merged | #10 |
| T-M1-02 | merged | #12 |
| T-M1-03 | merged | #13 |
| T-M1-04 | merged | #15, #16, #18 |
| T-M1-05 | merged | #20 |
| T-M1-06 | merged | #9 |
| T-M1-07 | merged | #14 |
| T-M1-08 | merged | #17 |
| T-M1-09 | merged | #11 |
| T-M1-10 | merged | #19 |
| T-M1-11 | merged | #21 |
| T-M1-99 | merged | #22 |
| T-M2-01 | merged | #24 |
| T-M2-02 | merged | #27 |
| T-M2-03 | merged | #26 |
| T-M2-04 | merged | #25 |
| T-M2-05 | merged | #29 |
| T-M2-06 | merged | #28 |
| T-M2-07 | merged | #30 |
| T-M2-08 | merged | #31 |
| T-M2-09 | merged | #32 |
| T-M2-10 | merged | #33 |
| T-M2-99 | merged | #34 |
| T-M3-01 | merged | #36 |
| T-M3-02 | merged | #38 |
| T-M3-03 | merged | #39 |
| T-M3-04 | merged | #35 |
| T-M3-05 | merged | #41 |
| T-M3-06 | merged | #42 |
| T-M3-07 | merged | #37 |
| T-M3-08 | merged | #43 |
| T-M3-99 | merged | #44 |
| T-M4-01 | merged | #49 |
| T-M4-02 | merged | #51 |
| T-M4-03 | merged | #52 |
| T-M4-04 | merged | #54 |
| T-M4-05 | merged | #50, #53 |
| T-M4-06 | merged | #55 |
| T-M4-07 | merged | #56 |
| T-M4-08 | merged | #57 |
| T-M4-09 | merged | #58 |
| T-M4-10 | merged | #77, #78 |
| T-M4-11 | merged | #60 |
| T-M4-12 | merged | #79, #80 |
| T-M4-13 | merged | #180 |
| T-M4-14 | merged | #177 |
| T-M4-15 | merged | #61 |
| T-M4-99 | merged | #59 |
| T-M5-01 | merged | #62 |
| T-M5-02 | merged | #63 |
| T-M5-03 | merged | #64 |
| T-M5-04 | merged | #65 |
| T-M5-05 | merged | #66 |
| T-M5-06 | merged | #67 |
| T-M5-07 | merged | #69 |
| T-M5-99 | merged | #71 |
| T-M6-01 | merged | #82 |
| T-M6-02 | merged | #83 |
| T-M6-03 | merged | #85 |
| T-M6-04 | merged | #81 |
| T-M6-05 | open | waiting: maintainer OK to open a PR in the Disenchantment repo (master) |
| T-M6-06 | merged | #90 |
| T-M6-07 | merged | #84 |
| T-M6-08 | merged | #86 |
| T-M6-09 | merged | #88 |
| T-M6-10 | merged | #89, #93 |
| T-M6-11 | merged | #92 |
| T-M6-12 | merged | #91 |
| T-M6-13 | merged | #94 |
| T-M6-14 | merged | #96 |
| T-M6-99 | merged | #87 |
| T-M6-100 | merged | #167 |
| T-M6-101 | merged | #168 |
| T-M6-105 | merged | #174 |
| T-M6-107 | merged | #179 |
| T-M6-108 | merged | #178 |
| T-M6-109 | merged | #181, #182 |
| T-M7-01 | merged | #98 |
| T-M7-02 | merged | #99 |
| T-M7-03 | merged | #100, #102 |
| T-M7-04 | merged | #101 |
| T-M7-05 | merged | #103 |
| T-M7-06 | merged | #104 |
| T-M7-07 | merged | #105 |
| T-M7-08 | merged | #106 |
| T-M7-09 | merged | #107 |
| T-M7-10 | merged | #108 |
| T-M7-11 | merged | #109 |
| T-M7-12 | merged | #110 |
| T-M7-13 | merged | #111 |
| T-M7-14 | merged | #112 |
| T-M7-15 | merged | #128 |
| T-M7-16 | merged | #181 |
| T-M7-17 | merged | #128, #129 |
| T-M7-18 | merged | #130 |
| T-M7-19 | merged | #131 |
| T-M7-20 | merged | #143 |
| T-M7-21 | merged | #145 |
| T-M7-99 | merged | #113 |
| T-M8-01 | merged | #116 |
| T-M8-02 | merged | #115 |
| T-M8-03 | merged | #117 |
| T-M8-04 | merged | #118 |
| T-M8-05 | merged | #119 |
| T-M8-06 | merged | #120 |
| T-M8-07 | merged | #121 |
| T-M8-08 | merged | #122 |
| T-M8-09 | merged | #123 |
| T-M8-10 | merged | #124 |
| T-M8-11 | merged | #125 |
| T-M8-12 | merged | #126 |
| T-M8-13 | merged | #133 |
| T-M8-14 | merged | #132 |
| T-M8-15 | merged | #134, #147 |
| T-M8-16 | merged | #136 |
| T-M8-17 | merged | #140 |
| T-M8-18 | merged | #141 |
| T-M8-19 | merged | #142 |
| T-M8-20 | merged | #135 |
| T-M8-21 | merged | #137 |
| T-M8-22 | merged | #138 |
| T-M8-23 | merged | #139 |
| T-M8-24 | merged | #144 |
| T-M8-25 | merged | #146 |
| T-M8-26 | open | proposed, not approved: per-boot Paper jar work (opus) |
| T-M8-27 | merged | #148 |
| T-M8-28 | merged | #149 |
| T-M8-99 | merged | #127 |
| T-M9-01 | merged | #151 |
| T-M9-02 | open | moved to M10 as T-M10-01 |
| T-M9-03 | merged | #152 |
| T-M9-04 | merged | #153 |
| T-M9-05 | merged | #154 |
| T-M9-06 | merged | #155 |
| T-M9-07 | merged | #156 |
| T-M9-08 | merged | #157 |
| T-M9-09 | open | moved to M10 as T-M10-02 |
| T-M9-10 | open | moved to M10 as T-M10-03 |
| T-M9-11 | merged | #158 |
| T-M9-12 | merged | #159 |
| T-M9-99 | merged | #160 |
| T-M10-01 | open | waiting: maintainer approval of the BDS spike (image pull); was T-M9-02 |
| T-M10-02 | open | waiting: follows T-M10-01; was T-M9-09 |
| T-M10-03 | open | waiting: follows T-M10-01; was T-M9-10 |
| T-M10-04 | open | waiting: upstream CloudburstMC release (not checked as none was approved) |
| T-M10-05 | merged | #163 |
| T-M10-06 | merged | #162 |
| T-M10-07 | merged | #164 |
| T-M10-08 | merged | #161 |
| T-M10-09 | open | waiting: follows ADR-0015 (Geyser row promotion) |
| T-M10-10 | merged | #165 |
| T-M10-11 | open | waiting: maintainer approval of the Sponge spike |
| T-M10-12 | open | waiting: maintainer approval of the Gate spike |
| T-M10-13 | open | waiting: maintainer approval of the Nukkit spike |
| T-M10-14 | open | waiting: maintainer approval of the PocketMine and Endstone scope spike |
| T-M10-15 | open | waiting: depends on spike decisions |
| T-M10-16 | open | waiting: maintainer decision on the docs site (GitHub Pages or not) |
| T-M10-17 | open | waiting: maintainer approval of the TypeScript front end and Fabric client gametest scope spike |
| T-M10-18 | open | waiting: depends on spike decisions |
| T-M10-99 | merged | #166 |

Also this session: T-M7-16 diagnosed: not reproduced in 10 runs, kept open as unexplained (#181); T-M6-109 partly done: ViaProxy named for the 1.8.8 stall, mechanism not proven, no code fix (#182).

## Ledger update (2026-11-20, second orchestrator session)

The maintainer approved the remaining work on 2026-11-19 ("do it all"): the Phase C spikes, the Disenchantment PR, GitHub Pages and the ViaProxy experiment. Status of each, replacing the "open" rows above:

| Card | Status | Result |
|---|---|---|
| T-M10-01 (BDS add-on spike) | done: not feasible | #188. Pack side works; the unsigned join is refused (MALFORMED_PACKET, 5 of 5), NetherNet is BDS's only player transport. T-M10-02 and T-M10-03 closed by this verdict. |
| T-M10-11 (Sponge spike) | done: feasible | #191. SpongeVanilla 26.3-21.0.0-RC2792, 23 of 23 joins, plugin agent read `health` and `players`. RC build only; no promotion. |
| T-M10-12 (Gate spike) | done: fits ADR-0016, agent-less | #189. Oracle gap: no proxy-side players, events or log. |
| T-M10-13 (Nukkit spike) | done: not feasible today | #194. PowerNukkitX 3.0.6 is NetherNet-only; 3.0.5 is protocol 2193. |
| T-M10-14 (PocketMine, Endstone scope) | done: not now | #193. |
| T-M10-17 (TypeScript, Fabric client gametest scope) | done: drop TypeScript, defer Fabric client gametest | #192. |
| T-M10-16 (docs site) | merged | #186. GitHub Pages enabled; `docs.yml` workflow; ADR-0019 and ADR-0014 amended (three workflows). Site: https://h7kz.github.io/Proofstone/ |
| T-M9-14 (ViaProxy bump experiment) | done: nothing to bump | #185. No stable release newer than the pin; 3.5.0-SNAPSHOT has a rewritten CLI and no login fix. |
| T-M6-111 (EventProbes class loading) | merged | #190. Third-party event classes load without the cross-plugin WARN. |
| T-M6-05 (Disenchantment suite) | draft PR, not merged | H7KZ/Disenchantment#80. paper@26.3 and paper@1.21.11 green 3 of 3; paper@1.18.2 red (deterministic, was the EventProbes bug fixed by #190: re-run needed); grindstone, erase mode and special anvils are skips (not on master). |
| T-M10-02, T-M10-03 | closed by the BDS verdict | see T-M10-01 |
| T-M10-04, T-M10-09, T-M10-15, T-M10-18 | open | depend on upstream releases, ADR-0015 and the spike decisions |
| T-M8-26 | open | opus work, not started in either session |

**Open follow-ups, not yet carded:**
- `datapacks/list` fails `500 zip file closed` on paper@1.18.2 after the reload cases (DataPackList's `Class.forName` reaches the disabled fixture's closed loader); red under the old code too (T-M6-111 finding).
- The Bedrock driver speaks RakNet only; BDS, PowerNukkitX 3.0.6, Nukkit master and Geyser's default transport need NetherNet (T-M10-01, T-M10-13): a maintainer decision.
- The T-M7-16 Verify command is red because six conformance classes declare no spigot cell (T-M7-16 note).
- Hosted `gates` failed on the first run when `repo.opencollab.dev` did not serve `text-serializer-nbt-0.2.1.jar` (4 of 5 first runs); reruns passed.
- `patchConfig` restore builds a config object the target plugin never sees (T-M6-111 note).
- A fast client disconnect hides the join-stall stage in McplDriver (T-M6-109 note); the ViaProxy mechanism for the 1.8.8 stall is unproven.
- Not done by choice: the `v1` tag of the GitHub Action (a `v*` tag triggers the release workflow) and a Marketplace listing (the action is not at the repository root).

## Ledger update (2026-11-21)

Merged into `develop` since the 2026-11-20 update:

| Card | Status | PR | Result |
|---|---|---|---|
| T-M10-19 (NetherNet spike) | done: Go behind an ADR | #198 | Verdict Go. ADR not yet filed. Effort 1-2 weeks for the driver host plus Geyser. PNX is a separate track. BDS acceptance needs a maintainer yes for the binary. |
| T-M11-01 (`Agent.logLines`) | merged | #199 | |
| T-M11-02 (`PlayerState.openWindow`, `ServerWindow`) | merged | #200 | |
| T-M11-03 (`Agent.bootLogLines`) | merged | #201 | Agent attaches at plugin enable. Not covered: the banner, library loading, the plugin loading list and the agent's own Enabling line. |
| T-M11-04 (`Client.placeBlock`/`breakBlock`, `Agent.blockType`) | merged | #202 | Non-sneaking place against an anvil or chest opens it. Survival break completes only with zero break time. Open follow-ups: exception type differs across drivers (Mineflayer ref throws `ClientDriverException`, others `UnsupportedOperationException`); `McplBlockActionTest` lacks timeout, disconnect and lower-seq-ack cases; survival dig never aborts. |
| T-M11-05 (`ItemView.pdc`) | merged | #203 | Filled by the Bukkit agent. Verified on paper 1.18.2 and 26.3. `getKeys` availability on 1.14-1.17 unverified. |
| T-M11-06 (mcpl `ClientboundSetPlayerInventoryPacket`) | merged | #204 | Applied. Harmless, but not the cause of the folia flake. Card text corrected to say so. |
| T-M11-07 (mcpl late window packet after close) | merged | #205 | Real cause of the folia client-oracle DISAGREE flake. Routes the late packet to window 0. Disenchantment e2e folia@26.3: 5 of 5 green after, 1 of 3 before. |

**Open Proofstone items:**
- Window drag API (`Window.drag`, T-M11-09): done in PR (see the ledger once merged).
- Window cursor wait (`Window.awaitCursor`, T-M11-11): done in PR (see the ledger once merged). Replaces the racy one-shot `cursor()` read after a click in plugin suites.
- Fault-injection hook (`Agent.failNextInventoryWrite`): spike T-M11-10 verdict No-go for an agent API (ADR-0002; not generic, not robust). [fault-injection.md](../platforms/fault-injection.md); ADR-0020 Proposed awaits a maintainer decision. Suites use their own test support plugin meanwhile.
- Follow-up card for the T-M11-04 minor findings: not yet carded.
- NetherNet ADR: to file.
- ViaProxy 1.8.8 stall: mechanism unproven.
- `datapacks/list` `zip file closed` follow-up (2026-11-20 update): still open.

## Ledger update (2026-11-23)

**0.2.0 is on Maven Central.** Verified by HTTP: pom, jar, sources and javadoc return 200 for `proofstone-api`, `proofstone-core`,
`proofstone-protocol` and `proofstone-drivers-mcpl` (16 of 16). GitHub Release `v0.2.0` is still a draft (maintainer publishes it).

| Check | Result |
|---|---|
| `scripts/consumer-cleanroom.sh all 1` against Central | GREEN: gradle-run-1 and maven-run-1, each 2 tests, 0 skipped, 0 failures, 0 errors; every `io.github.h7kz.proofstone` file (api, core, protocol, drivers-mcpl: jar, module, pom) resolved from `repo.maven.apache.org`; one run per suite |

**Decisions taken (maintainer delegated them: "decide yourself", 2026-11-23):**
- ADR-0020 **Accepted**: ADR-0002 stays, no agent-side fault injection; suites use a test-owned support plugin.
- ADR-0021 **Accepted**: NetherNet transport for the Bedrock driver (driver host and Geyser cell; BDS and PNX not in scope, BDS still needs
  the maintainer's yes for the binary). Cards are not started.
- Sponge stays unpromoted (RC only). No `v1` tag and no Marketplace listing before the GitHub Action moves to its own repository.

**Still open:** McplDriver fast disconnect hides the join-stall stage; ViaProxy 1.8.8 stall mechanism unproven; `datapacks/list`
`zip file closed` on paper@1.18.2; `patchConfig` restore; T-M8-26; join-stall flake class T-M6-109 (one `CONFIGURATION_STALLED` on
paper@1.20.6, not reproduced in 2 reruns); `release.yml` dependency prefetch; `bootLogLines` coverage gap; `getKeys` on Bukkit 1.14-1.17;
NetherNet cards; the T-M11-04 minor findings. Maintainer-only: publish the GitHub Release, delete scratch folders, set
`PROOFSTONE_ACCEPT_EULA` on Disenchantment.

## Ledger update (2026-11-24)

**Proofstone:** #214 added experimental rows `paper@1.21.4` (build 232, protocol 769) and `paper@1.21.7` (build 32, protocol 772). Both stay
`experimental`: ADR-0015 needs a per-row conformance run. #215 moved the `gates` job to GitHub-hosted runners (the 2 vCPU self-hosted runner
cancelled it after about 19 minutes) and added JDK 25 to its setup. GitHub Release `v0.2.0` is published.

**Disenchantment (consumer evidence):**
- paper@1.20.6 red on `SpecialAnvilPlacementE2E` was a test race (the result slot was read before its packet arrived; the window had closed).
  Fixed in Disenchantment#93 (`awaitSlot` for the expected book); unfixed local runs were 3 of 3 green, so the race needs host load.
  All six hosted cells green after the fix.
- paper@1.21.4: 3 of 3 clean (52 tests, 1 reasoned skip). paper@1.21.7: 2 of 3 clean; run 2 had one `CONFIGURATION_STALLED` join stall in
  `RestrictionsE2E` (join-stall flake class, T-M6-109; a finding, not retried). Disenchantment#95 adds both cells and pins `7a684e9`.
- Mutation check on Disenchantment (`mutation-check.py all`): M1 to M11 all RED.
- Self-hosted Hetzner runners (2 vCPU) have no Maven and no `gh`, and the e2e cells hung to the 45 minute timeout on them; the e2e cells now
  run hosted, Build and Verify stay self-hosted with a pinned Maven install.

**Still open:** counted repeat runs (k of n) on all Disenchantment cells before the next release; McplDriver fast disconnect hides the join-stall
stage; ViaProxy 1.8.8 stall mechanism; `datapacks/list` `zip file closed` on paper@1.18.2; `patchConfig` restore; T-M8-26; T-M6-109;
`release.yml` dependency prefetch; `bootLogLines` coverage gap; `getKeys` on Bukkit 1.14-1.17; NetherNet cards (ADR-0021); T-M11-04 minor
findings. None of these is carded yet. Maintainer-only: delete the scratch folders.

## Ledger update (2026-11-24, later): counted repeat runs and follow-up cards

Disenchantment master `e2e.yml` flake report, `-Dproofstone.repeat=5` per cell, hosted runners, one cell at a time, pin `7a684e9`
(Disenchantment#96 and #97 merged first: Verify and the e2e Build job run hosted; master runs write the Gradle cache). Logs are local in
`C:\pw\dis-ev-repeat`. Every cell is red; reds are findings, nothing was retried or weakened.

| cell | run | result (5 repeats of the 51-test suite) |
|---|---|---|
| paper@1.18.2 | 45422784274 | 252 of 256 green; `DisabledWorldE2E` red in repeats 2 to 5; 6 reasoned skips |
| paper@1.20.4 | 45452573168 | same; 6 reasoned skips |
| paper@1.20.6 | 45411790684 | 252 of 256; repeat 1 `CONFIGURATION_STALLED` (join stall), `DisabledWorldE2E` red in repeats 3 to 5 |
| paper@1.21.4 | 45411809166 | 252 of 256; `DisabledWorldE2E` red in repeats 2 to 5; no stall |
| paper@1.21.7 | 45444989586 | `DisabledWorldE2E` red in repeats 2 to 5; `BedrockMenuE2E` repeat 3 join stall (`CONFIGURATION_STALLED`), repeat 5 red (cause not read) |
| paper@1.21.11 | 45428008518 | 252 of 256; `DisabledWorldE2E` red in repeats 2 to 5 |
| paper@26.3 | 45458720890 | 252 of 256; `DisabledWorldE2E` red in repeats 2 to 5 |
| folia@26.3 | 45436347893 | 252 of 256; `DisabledWorldE2E` red in repeats 2 to 5 |

- Reading: `DisabledWorldE2E` is green in single invocations and in repeat 1, and red from repeat 2 on, on all 8 cells. Not a version issue; a
  repeat-mode leak, or a negative read that cannot be awaited. Hypothesis only; carded as T-M12-04 with this evidence. No repeat count
  can be called clean until it is understood.
- Join stalls (T-M6-109 class): paper@1.20.6 (repeat 1) and paper@1.21.7 (repeat 3) in this series, paper@1.18.2 and 1.20.6 once each on the
  #96 PR run. Stage `CONFIGURATION_STALLED` every time.
- Infra: repo.opencollab.dev did not serve `text-serializer-nbt-0.2.1.jar` to hosted runners in 5 of 12 builds (runs 45407182143 e2e Build and
  1.20.4 cell, 45417893251, 45427392783, 45435524730). Not a test result; those cells were re-run after the Gradle cache write.

Follow-up cards: [M12-followups.md](tasks/M12-followups.md), T-M12-01 to T-M12-11 (McplDriver stall stage, ViaProxy 1.8.8 mechanism,
`datapacks/list`, `patchConfig` restore, `release.yml` prefetch, `bootLogLines`, `ItemView.pdc` `getKeys`, five NetherNet cards). T-M8-26,
T-M6-109 and T-M11-13 already exist. None started.

Maintainer-only: delete the scratch folders and worktrees (`C:\pw\t05`, `t06`, `t07`, `t99`, `.claude\worktrees\agent-a73c3f12ea3a78e02`, old
`C:\pw\dis-ev-*`, `T-M11-*`, `gradle-home-*`, `m2-dis-*`, `cleanroom-*`, and this session's `dis-mut`, `trash`, `old-jars`, `m12-cards`, `led`).

## Ledger update (2026-11-25): repeat runs after the T-M12-04 fix

T-M12-04 merged as #218 (`677b6d2`): Bukkit agent restore loads the snapshot into the plugin's existing config object. C-C03
(`ConformanceConfigRestoreTest`) was red before (held object kept `one` after the restore) and is green after, 3 of 3 separate invocations on
paper@26.3 and 3 of 3 on paper@1.18.2, XML `skipped="0" failures="0" errors="0"`; an independent sonnet review ran, and its test findings were
fixed. Disenchantment#98 pins `677b6d2`; all 8 cells green (paper@1.21.4 after one re-run of a failed dependency download).

Hosted flake report on master, `-Dproofstone.repeat=5`, one cell at a time:

| cell | run | result |
|---|---|---|
| paper@26.3 | 45486841310 | 5 of 5 clean |
| paper@1.21.4 | 45492940403 | 5 of 5 clean |
| paper@1.20.6 | 45496607795 | 5 of 5 clean |
| paper@1.18.2 | 45500309177 | RED: repeat 1 `CONFIGURATION_STALLED` join stall in `EconomyE2E.grindstoneExtractChargesItsOwnPrice`; the other 4 repeats passed; the flake gate then reported the differing verdicts (FAIL, PASS) |
| paper@1.20.4 | 45505160327 | 5 of 5 clean |
| paper@1.21.7 | 45508521243 | 5 of 5 clean |
| paper@1.21.11 | 45514106025 | 5 of 5 clean |
| folia@26.3 | 45519342244 | 5 of 5 clean |

So 7 of 8 cells are 5 of 5 clean and one is a join stall of the T-M6-109 class (cards T-M12-01 and T-M12-02). `DisabledWorldE2E` is green in every
repeat on every cell. The earlier series (all 8 red) is in the 2026-11-24 addendum above.

**Still open:** T-M12-01 to T-M12-03 and T-M12-05 to T-M12-11 (none started); T-M8-26; T-M6-109. The repeat count on paper@1.18.2 stays red until a
clean 5 of 5; it is a flake finding, not a test defect. Rows paper@1.21.4 and 1.21.7 stay `experimental` until their per-row conformance
evidence (ADR-0015). Maintainer-only: delete the scratch folders (list in the 2026-11-24 addendum, plus `C:\pw\led2`, `C:\pw\ev-m1204`,
`C:\pw\dis-ev-repeat2`).

## Ledger update (2026-11-25, later): T-M12 cards merged, final repeat series

Merged on develop (all `gates` green, real-server evidence in each PR): T-M12-04 #218 (patchConfig restore keeps the config object), T-M12-03 #220
(`datapacks/list` on paper@1.18.2), T-M12-07 #221 (`ItemView.pdc` verified on paper@1.17.1 and, after T-M12-12, paper@1.16.5), T-M12-06 #222
(bootLogLines boundary pinned), T-M12-01 #223 (a disconnect names the join stage), T-M12-13 #225 (spigot@26.3 re-pinned: three fresh builds
agreed, the old digest no longer built), T-M12-12 #224 (a console reply is captured on async loggers: Paper 1.16.5 on Java 8 logs through
Log4j `AsyncLogger`, and the capture detached before the reply arrived, so the bridged 1.16.5 cell was INVALID in conformance 3 of 3). Four
independent sonnet reviews ran (T-M12-04, -03, -01, -12); their findings were applied. Two fixes started from a wrong first guess and were
corrected by evidence (T-M12-12: not a wrong logger context; T-M12-03: a second missing class on 1.18.2).

Disenchantment pins Proofstone `d073d4c` (#99); all 8 e2e cells green. Hosted flake report, `-Dproofstone.repeat=5`, one cell at a time:

| cell | run | result |
|---|---|---|
| paper@26.3 | 45612788429 | 5 of 5 clean |
| paper@1.21.4 | 45618026411 | 5 of 5 clean |
| paper@1.18.2 | 45628661557 | 5 of 5 clean |
| paper@1.20.4 | 45632844467 | 5 of 5 clean |
| paper@1.21.7 | 45636578418 | 5 of 5 clean |
| folia@26.3 | 45649997660 | 5 of 5 clean |
| paper@1.21.11 | 45643810824 | RED: one `CONFIGURATION_STALLED` join stall (`EraseModeE2E`, repeat 3); the other 4 repeats passed |
| paper@1.20.6 | 45624862028 | INVALID: one test class's server never booted (`Network is unreachable` while Paper downloaded `mojang_1.20.6.jar`); not a plugin or harness result |
| paper@1.20.6 (re-run) | 45656217998 | RED: one `CONFIGURATION_STALLED` join stall (`SpecialAnvilE2E`, repeat 1); the other 4 repeats passed |

Reading: 6 of 8 cells are 5 of 5 clean. The only reds left are single join stalls, `CONFIGURATION_STALLED` (login acknowledged, configuration never
finished), the T-M6-109 class, on 2 of 8 cells; no repeat failed for any other reason. That flake class is still unexplained, and a release
claim cannot pass 5 of 5 on every cell while it exists. T-M12-01 now makes a disconnect-shaped stall name its stage, which narrows the next
look. Disenchantment's `DisabledWorldE2E` has been green in every repeat on every cell since T-M12-04.

Infra seen today, not results: repo.opencollab.dev did not serve `text-serializer-nbt-0.2.1.jar` to hosted runners (worked around with a Gradle
cache write on master runs, Disenchantment#97); GitHub's workflow-dispatch API answered HTTP 500 during an Actions degradation.

**Still open:** T-M12-02 (ViaProxy 1.8.8 stall mechanism, a counted-loop spike); T-M6-109 (the native `CONFIGURATION_STALLED` class); T-M12-05
(`release.yml` dependency prefetch: needs the maintainer's approval to change a workflow, AGENTS.md section 4); T-M12-08 to T-M12-11 (NetherNet: the ADR
needs the maintainer's yes, then driver, Geyser cell, BDS spike needs a yes for the BDS binary); T-M8-26. Rows paper@1.21.4 and 1.21.7 stay
`experimental` until their per-row conformance runs (ADR-0015). Maintainer-only as before: tags, Central, release approvals, deleting the
scratch folders and worktrees (`C:\pw\T-M12-*`, `m12-cards`, `led`, `led2`, `led3`, `ev-m12*`, `dis-ev-repeat*` and the earlier list).

## Ledger update (2026-11-26): the T-M12 follow-ups, the join stall read from both sides, the final series

**Merged (Proofstone develop `42c0732` plus the correction #233):** T-M12-05 `release.yml` dependency prefetch (#227); T-M12-08 the MPL-2.0 amendment to ADR-0011 (#228; ADR-0021 was already
accepted); T-M12-09 the NetherNet transport in the Bedrock driver host (#229); T-M12-10 the Geyser cell over NetherNet, `-Dproofstone.geyser.transport=nethernet` (#230);
T-M12-11 the BDS spike (#231); T-M12-02 the join-stall analysis (#232, corrected in #233). Disenchantment #100 pins `42c0732` in both places and sets `proofstone.version` to `0.2.0`.

| Card | Result |
|---|---|
| T-M12-09 | PowerNukkitX 3.0.6 joined through the driver host RPC with `"transport":"nethernet"`, 3 of 3 separate invocations on the final code (a1 to a3, about 2.6 s each). Four reds on the way, each a named cause (see [nethernet.md](../platforms/nethernet.md)) |
| T-M12-10 | Geyser cell over NetherNet: `ConformanceGeyserTest` 8 tests, 3 reasoned skips, 0 failures, 3 of 3 invocations (acc 1 to 3). Run 1 red: Geyser binds the offer identity's key to the login key. The default stays `both` (RakNet); the choice is a run option, not a `versions.yml` field (the row schema forbids extra keys and belongs to `proofstone-protocol`) |
| T-M12-11 | BDS 1.26.66.1 accepts the NetherNet transport and a self-made offer identity and still refuses the unsigned login: `MALFORMED_PACKET`, `Connection Request invalid.` (2 of 2). T-M10-02 stays closed |
| T-M12-02 | Reproduced and read from both sides; see below |
| T-M12-05, -08 | as above |

**The join stall (`CONFIGURATION_STALLED`).** It is a stall of the **ViaProxy-bridged** cells: every cell below 26.3 is bridged (an earlier note of mine called paper@1.21.11 native; it is not). Counted loops, 2,900 joins on paper@1.8.8 and paper@1.21.11 (500 and 2,400, the latter with the acknowledgement-delay and 50-join control loops): 7 of 500 on 1.8.8
(4 quiet, where ViaProxy's receive queue from Paper grows unread, 2 of 2 measured; 3 with a ViaProxy `IndexOutOfBoundsException` on the proxy to server leg) and 13 of 1400 on 1.21.11, all of them at join 61 to 98 of a 100-join server,
none in 500 joins of 50-join loops. On 1.21.11 the client reads exactly the 50 login bytes and flushes its acknowledgement, Paper sends about 800 B and then nothing, every Paper thread is idle. Holding the acknowledgement 10 ms did not prevent it (1 of 500).
Not shown: why the ViaProxy hop stops. Details and counts: [viaproxy.md](../platforms/viaproxy.md).

**Disenchantment on the final pin.** PR run on `42c0732`: the first run failed to compile on the self-hosted runner (the pom asked for `proofstone` `0.1.0`, the source build publishes `0.2.0`; the previous hosted run's artifact held both versions, so hosted runners were apparently compiling against a cached
older 0.1.0), fixed in #100; then 7 of 8 cells green and `paper@1.20.6` RED with a `CONFIGURATION_STALLED` stall (`CostFormulaE2E`); its job re-run was green and #100 was merged on that.

| Cell (final series, repeat=5, `42c0732`) | Run | Result |
|---|---|---|
| paper@26.3 | 45751664778 | **5 of 5 clean** (256 tests, 0 failures, 1 reasoned skip) |
| folia@26.3 | 45772976805 | RED: `BedrockMenuE2E.aPrefixDetectedPlayerGetsTheChestMenuAndOthersDoNot` failed in 2 of 5 repeats: `WARN ServerCommonPacketListenerImpl: <player> was kicked for floating too long!` (vanilla anti-fly kick; the error-log guard turns it into a failure). Nothing in Proofstone sets `allow-flight`; not a stall |
| paper@1.21.11, 1.21.7, 1.21.4, 1.20.6, 1.20.4, 1.18.2 | 45755417541, 45758203083, 45761194803, 45764183745, 45767465556, 45770079495 | **INVALID, no result**: 255 of 256 tests errored in setup, `ViaProxy ... failed to start: Can't get Docker image ghcr.io/viaversion/viaproxy@sha256:46eef05d...: Status 403: error from registry: denied` on the self-hosted runner (`nbg1-cx23-1-Disenchantment-1`). A re-dispatch of paper@1.21.11 (45781335879) gave the same 403. The same cells pulled the image in the PR run an hour earlier |

**Can the project claim "every cell 5 of 5 clean"? No.** Of 8 cells only paper@26.3 is clean in the final series; folia@26.3 has a new, separate red (the floating kick); six bridged cells have no result at all because the runner cannot pull
the ViaProxy image; and the join-stall class (bridged cells, about 1 per 100 joins late in a long server) is explained only to the ViaProxy hop, not fixed.

**For the maintainer.**
- The runner's pull of `ghcr.io/viaversion/viaproxy` is refused (403 `denied`): look at its Docker credentials for `ghcr.io` (a stale login makes a public image fail); then re-dispatch the six bridged cells one at a time.
- `_runner.yml`, `release.yml`, `docs.yml` and `gates.yml` now choose `runs-on` from a runner lookup with `secrets.RUNNER_TOKEN`; an automated security review flagged that the secrets-bearing release jobs (`maven`, `images`, `github-release`) can land on a shared persistent self-hosted
  runner, that `docs.yml`'s Pages deploy has `id-token: write` there, and that the token lookup is reachable from `pull_request` workflows (`gates.yml`). Not changed by me (AGENTS.md section 4); the review's fixes are hosted `ubuntu-latest` for those jobs or ephemeral
  runners in a restricted group.
- Candidate fixes that need their own card: `allow-flight=true` in the cells (floating kick); ViaProxy debug logging and a fresh ViaProxy every 40 joins to tell a per-proxy count from a per-server one.

**Scratch for manual deletion** (outside any repository): `C:\pw\ev-m1209`, `ev-m1209-*.out`, `ev-m1210`, `ev-m1211`, `ev-m1202`, `dis-ev-pin42`, `dis-ev-series`, `m1209-scratch`, `nukkit-cache` and `bds-cache` (the two Mojang/PowerNukkitX downloads, never committed),
`gradle-home-T-M12-*`, the worktrees `T-M12-02`, `-05`, `-08`, `-09`, `-10`, `-11`, `led-1126`, the scripts `m12*.sh`, `m12*.py`, `viawatch.sh`, `dis-series.sh`, and `.claude\worktrees\agent-a73c3f12ea3a78e02` in the main checkout.

## Ledger update (2026-11-28): final hosted repeat series, the join-stall mechanism

**Merged:** Proofstone #235 (T-M12-14, cells allow flight: the folia@26.3 "kicked for floating too long!" red is gone), #236 (T-M12-15, the ViaProxy stall mechanism), #237 (callers read from stack frames; a mitigation rejected). Disenchantment #101 pins develop `adc5532` in both places (`proofstone.version` stays 0.2.0).

**Pin-bump evidence.** The PR's own e2e run landed on the self-hosted runner, whose pull of the ViaProxy image from ghcr.io is refused (`403 denied`), so its bridged cells are INVALID, not results. Hosted run 45800502383 (evidence branch, hosted-only runner pick, never merged): all 8 cells green on attempt 2. Attempt 1: paper@1.21.7 a Maven Central transfer error (infra); paper@1.18.2 `StatsE2E.anAnvilDisenchantDoesNotTouchTheGrindstoneCounters` red once (expected `disenchants=1`, was 0), which is a finding: the counters are read right after the post-event, and a later 5-repeat series on that cell was clean. Both jobs were re-run as failed jobs only (the re-run also repeated the 1.18.2 red). folia@26.3 green on the first attempt.

**Repeat series, `-Dproofstone.repeat=5`, hosted runners, one cell at a time, evidence branches (never merged):**

| cell | pin | run | result |
|---|---|---|---|
| paper@26.3 | 42c0732 | 45789459751 | 5 of 5 clean |
| paper@1.21.11 | 42c0732 | 45794683733 | RED: 1 `CONFIGURATION_STALLED` join stall (the flake gate then reported the differing verdicts) |
| paper@1.21.7 | 42c0732 | 45800130919 | 5 of 5 clean |
| paper@1.21.4 | adc5532 | 45809125324 | RED: 1 `CONFIGURATION_STALLED` join stall |
| paper@1.20.6 | adc5532 | 45813438154 | 5 of 5 clean |
| paper@1.20.4 | adc5532 | 45817843881 | 5 of 5 clean (6 reasoned skips) |
| paper@1.18.2 | adc5532 | 45821842312 | 5 of 5 clean |
| folia@26.3 | adc5532 | 46111043236 | 5 of 5 clean |

The first three ran on the older pin because the series was moved to `adc5532` after paper@1.21.7; the pin differs only by the allow-flight change. Earlier on the self-hosted runner: paper@26.3 5 of 5 clean, folia@26.3 red (the floating kick, fixed), the six bridged cells INVALID (image pull 403). Master's scheduled e2e on 2026-11-26 (run 45819726516) had a join stall on paper@1.21.4; the one on 2026-11-27 (run 45996689457) is INVALID (self-hosted 403 on six bridged cells).

**Is "every cell 5 of 5 clean" met? No.** 6 of 8 cells were clean in their series and 2 (paper@1.21.11, paper@1.21.4) were red with one join stall each. Both are bridged cells, and the stall is a known defect of the pinned ViaProxy, not of a test.

**The join stall (T-M12-15).** A data race in ViaProxy's `ChannelUtil` (pinned `3e81750`, same on upstream master): the compression pair (`CompressionPacketHandler.handleP2S`, restored from a write listener on the client channel's loop) and the login pair (`LoginPacketHandler.handleP2S`, restored by the acknowledgement's listener) disable and restore auto-read on the server channel without a lock; one interleaving leaves auto-read off, ViaProxy stops reading Paper, and the client times out at `CONFIGURATION_STALLED`. Shown: the call order of a caught stall, 14 harmless occurrences of the race in 700 joins, the callers by stack frame (200 joins), and a stress test of the unmodified class (8 of 600000 left auto-read off). Not shown: the flag value of the stalled join after its last restore. Rejected mitigation: `NETWORK_COMPRESSION_THRESHOLD=-1` (the compression handler still runs in 101 of 101 joins and a stall still came). No retry, pin change or patched ViaProxy was done; that is the maintainer's decision.

**Still open (maintainer):**
- Report the race upstream (ViaVersion/ViaProxy; text in T-M12-15 of [M12-followups.md](tasks/M12-followups.md)); decide on a newer ViaProxy pin once fixed.
- The self-hosted runner's ghcr.io pull is refused (403): look at its Docker credentials. Until then the PR e2e gate of Disenchantment cannot be green on bridged cells when that runner is picked.
- An automated security review flagged the runner selection (`_runner.yml`, `RUNNER_TOKEN`, secrets-bearing release jobs and the Pages deploy on a shared persistent self-hosted runner, token lookup reachable from `pull_request` in `gates.yml`). Not changed.
- Rows paper@1.21.4 and 1.21.7 stay `experimental` until per-row conformance evidence (ADR-0015). T-M8-26 not started. Tags, Central and image publishing are untouched.
- Delete scratch (not inside a repo): in `C:\pw`: `T-M12-*`, `led*`, `dis-ev-*`, `gradle-home-*`, `vp-image`, `vp-stress`, `viaproxy-build`, `viaproxy-src`, `m1209-scratch`, `nukkit-cache`, `bds-cache`, `ev-m12*`, `t1215-*`, `t1216*`, `dis-series*`; and `C:\Users\honzi\Documents\GitHub\Proofstone\.claude\worktrees\agent-a73c3f12ea3a78e02`, the untracked `CONTINUATION-PROMPT.md`, and the evidence branches `test/series-hosted`, `test/series-hosted2`, `test/pin-hosted` in Disenchantment.

## Ledger update (2026-11-28, final): the join stall is fixed; every cell 5 of 5 clean

**This supersedes the "is every cell 5 of 5 clean? No" and "no mitigation" statements of the update above.**

**Merged:** Proofstone #239 (T-M12-17: ViaProxy starts with `--compression-threshold -1`; `ViaProxyFlagsTest`); Disenchantment #102 pins `f5e8f76` in both places (`PROOFSTONE_REF`, `proofstone.ref`; `proofstone.version` stays 0.2.0).

**Fix.** The race in ViaProxy's `ChannelUtil` needs two overlapping auto-read pairs on the server channel. The compression pair runs only when ViaProxy's own `--compression-threshold` is above -1 (default 256). With `-1` the pair does not exist (diagnostic proxy: no `CompressionPacketHandler` call in 101 joins). The first attempt, the server's `NETWORK_COMPRESSION_THRESHOLD=-1`, was the wrong knob and did not help (T-M12-16). Local evidence on c02, `-Dproofstone.repeat=100`: **0 stalls in 1000 bridged joins** (paper@1.21.11 500, paper@1.8.8 500; baseline 13 in 1400 and 7 in 500). `:conformance:smoke` green in 3 separate invocations (paper@1.8.8 and paper@1.21.11), XML read.

**Repeat series on the fixed pin `f5e8f76`, `-Dproofstone.repeat=5`, hosted runners, one cell per run (evidence branch `test/series-hosted3`, never merged):**

| cell | run | k of n clean | tests run | skipped |
|---|---|---|---|---|
| paper@26.3 | 46120320657 | 5 of 5 | 256 | 1 |
| paper@1.21.11 | 46122757843 | 5 of 5 | 256 | 1 |
| paper@1.21.7 | 46125750276 | 5 of 5 | 256 | 1 |
| paper@1.21.4 | 46128642811 | 5 of 5 | 256 | 1 |
| paper@1.20.6 | 46130732948 | 5 of 5 | 256 | 1 |
| paper@1.20.4 | 46132647175 | 5 of 5 | 256 | 6 (reasoned) |
| paper@1.18.2 | 46134355369 | 5 of 5 | 256 | 6 (reasoned) |
| folia@26.3 | 46136687798 | 5 of 5 | 256 | 1 |

All 8 runs: failures 0, errors 0, 0 join-stall reports. Skips carry reasons and are skips, not passes.

**Is "every cell 5 of 5 clean" met? Yes**, on `f5e8f76`: 8 of 8 cells, 5 of 5 repeats each, on hosted runners. Limits: n is 5 per cell (about 1 in 100 stalls before the fix, so 40 bridged-or-not repeats is modest evidence on its own; the 1000 local joins carry the statistical weight). Disenchantment's own PR e2e run on #102 landed on the self-hosted runner and is INVALID (ghcr.io pull 403), so its red bridged cells are not results.

**Still open (maintainer):**
- File the race upstream (ViaVersion/ViaProxy; text in T-M12-15 of [M12-followups.md](tasks/M12-followups.md)). The race is still in ViaProxy; the cells no longer reach it.
- Self-hosted runner: ghcr.io pull refused (403); until fixed, PR e2e on Disenchantment is invalid for bridged cells whenever that runner is picked.
- Runner-selection security review flag (`_runner.yml`, `RUNNER_TOKEN`, secrets-bearing jobs on a shared persistent runner). Not changed.
- Rows paper@1.21.4 and 1.21.7 stay `experimental` until per-row conformance evidence (ADR-0015). T-M8-26 not started. Tags, Central and image publishing untouched.
- Delete scratch (outside any repo): in `C:\pw`: `T-M12-*`, `led*`, `dis-ev-*`, `gradle-home-*`, `vp-image`, `vp-stress`, `viaproxy-build`, `viaproxy-src`, `m1209-scratch`, `nukkit-cache`, `bds-cache`, `ev-m12*`, `t121*`, `dis-series*`; the worktree `.claude/worktrees/agent-a73c3f12ea3a78e02`; the untracked `CONTINUATION-PROMPT.md`; Disenchantment evidence branches `test/series-hosted`, `test/series-hosted2`, `test/series-hosted3`, `test/pin-hosted`.
