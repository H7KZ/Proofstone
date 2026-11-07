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
| **M4** | Self-verification | Control run, differential run (mineflayer-ref), flake detector and repro bundles all work. Verdicts `VACUOUS`, `DISAGREE` and `FLAKY` are demonstrated on purpose-built bad tests. | 9 | 4 / 4 / 1 | 2.5 (was 2; more Docker runs and a new Node driver, see the M3 review) |
| **M5** | Bukkit family complete | Folia (regions), Purpur and Spigot (BuildTools cache) pass conformance. Capability model done. Bedrock-prefix simulation works. | 8 | 5 / 3 / 0 | 3 (was 2; no Folia row has booted yet, see the M3 review) |
| **M6** | Dogfood and 0.1 release | Disenchantment's full suite runs on Proofstone in its CI. Maven and Gradle example projects work. Published to Maven Central and GHCR. GitHub Action published. Licence decided. Mutation check passes. | 9 | 6 / 2 / 1 | 2 |
| **M7** | Proxies | Velocity and BungeeCord topologies, with proxy agents, pass conformance. | epic | refine first (Sonnet) | 2 |
| **M8** | Mods and data | Fabric and NeoForge agents, multi-version via Stonecutter, plus data pack and resource-pack tests. | epic | refine first (Sonnet/Opus) | 4 |
| **M9** | Bedrock | Spike (offline Bedrock through Geyser). Then a Bedrock driver, the Geyser topology and the BDS Script-API agent. | epic | spike = Opus, then refine | 4 |
| **M10** | Ecosystem | Community pack template, a TS front end (optional), more forks, a docs site, Sponge, PocketMine and others. | epic | refine | ongoing |

**Cards M0–M6:** 66 in total. 40 are Haiku cards (61%), 24 Sonnet and 2 Opus (T-M3-02 moved from Haiku to Sonnet at T-M2-99).

**Total to v0.1 (M0–M6): about 15.5 focused weeks** (sum of the estimates, M5 run after M4; was 14). Full ecosystem (M7–M10): about 10+ more weeks.
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
- **CI**: `gates`, `smoke` and `renovate-validate` run on every PR and are required checks on `main`. The `smoke`
  job accepts the Minecraft EULA by the maintainer's instruction and fails (never skips) without it.
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
