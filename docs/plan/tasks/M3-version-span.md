# M3: Version span (1.8.8 → newest, ViaProxy bridging)

Exit: ViaProxy bridging works. Every Paper row from 1.8.8 to the newest passes C01–C08. `version-watch`
opens PRs automatically.
These cards were refined at T-M2-99. Keep the format.

## What M1 and M2 produced (read before picking a card)

Real names and paths. Cards below use them. The M1 list is in `M2-windows-dual-oracle.md` and still holds.

- **Core test lifecycle** (`proofstone-core`, `dev.proofstone.core.junit`): `ProofstoneExtension` plans cells with
  `CellPlanner.plan(platform, versions)` from the `@Matrix` annotations, starts one `PaperHost` per cell, and injects
  `Cell` (`CellView`), `Agent` (`HostAgent`, `HostPlayer`, `HostPlugin`, `HostEvents`) and `Client` (`SessionClient`,
  `SessionWindow`). Per test it joins a fresh client, resets the player, checks the log (`ErrorLogGuard`, WARN and worse,
  with an allowlist) and restores patched configs (`TestState`). Waits are `Await` and `AwaitOracleWaiter`.
- **There is no run-time cell selector yet.** `@Matrix(versions = {"latest"})` is hard-coded in each test, and
  `-Dproofstone.cells` appears only in the docs. `CellView.bridged()` is always false. T-M3-04 creates the selector and
  T-M3-03 the bridging, so cards that use `-Dproofstone.cells` depend on T-M3-04.
- **Driver** (`drivers:mcpl`): `McplDriver`, `McplSession`, `WindowModel` (a model per window; server-authoritative
  clicks), `ItemParser` and `ItemRegistries` (item and enchantment ids come from the registries the server sends).
  The driver speaks the newest protocol (26.3); older rows need ViaProxy (T-M3-03), and the driver then sees
  newest-format data components for items from an old server.
- **Conformance** (`conformance`): C01 and C02 in `ConformanceSmokeTest`, C03 to C06 in `C03GiveRoundTripTest` to
  `C06AnvilCombineTest`, with the shared `ConformanceFixture` (an anvil and a chest on solid ground). `:conformance:run`
  runs all of them; `:conformance:smoke` runs C01 and C02. The fixture's commands (`setblock`, `tp`, `forceload`) and the
  tests' vanilla expectations (anvil costs, the 255 enchant cap) were observed on 26.3 only; expect version differences
  and record each one as a gotcha.
- **API v0 is frozen** (`proofstone-api/api-baseline.txt`, `specs/test-api.md` section 8). A card that changes a public
  type needs the label `api-change`. The post-v0 backlog (`Agent.world`, `awaitTicks`, `openWindow`, `Clients`,
  `Topology`, fixture reset) is in section 8.4 of that spec.
- **Agent** has Java 8 bytecode and runs through `Game` / `BukkitGame` / `FakeGame`. Newer Bukkit APIs are read by
  reflection (`getOnlinePlayers`, `Enchantment.getKey`). Capability names are in `ProofstoneAgentPlugin`.
- **Known limits that touch M3**:
  - **GitHub issue #23 (open): the itzg host digest is per Java tag.** `versions.yml` has one `host.digest`, pinned to
    `java25`, so only the Paper 26.x rows can run; `PaperHost` fails loudly on the rest. T-M3-02 fixes it first.
  - **Only the Paper 26.3 row is pinned** (`build`, `sha256`). Every other row, and the Folia host digest, is `TODO`.
    T-M3-01 fills in the builds and hashes; a row must be pinned before a cell can run on it.
  - Vanilla commands run as the console and their output is captured from the log on the calling thread only. Do not
    assert on `command` output.
  - Game rule names are snake_case from 26.1. `GameRules` picks the set by major version; older rows use the old names.

Lessons for M3 cards:

- **A real run finds the bugs.** M1 found four, M2 found more (ROADMAP, "M2 review"). Every card that adds a version
  needs a real-server run on that version, one suite at a time (Docker Desktop breaks under parallel Testcontainers).
- **The server's value is the truth.** When a golden file or an expectation disagrees with an old version, find out which
  is wrong and fix it in its own commit with the reason. Do not add a per-version special case without a gotcha entry.
- **Await state, never assume it** (anvil result slot, window close, experience sync). Older versions are slower and
  ordered differently, so no fixed delay can be right.

### T-M3-01 · `version-watch` workflow
- tier: haiku
- depends: T-M2-99
- modules: .github/workflows, build-logic
- adrs: 0006, 0008
- specs: versioning.md §2
- test-first: yes

**Goal.** New Paper and Folia builds or versions arrive as PRs that fill in `versions.yml`.

**Do.**
- Add a Gradle task, `updateVersions`, that queries `fill.papermc.io/v3` for Paper and Folia and fills
  in, for each row:
  - the build number;
  - the jar SHA-256 (download and hash);
  - `protocol`, from the Mojang version manifest or the server jar's `version.json`.
- Write a scheduled workflow that runs every 6 hours and opens a PR (via `peter-evans/create-pull-request`,
  pinned by SHA) with the label `versions`. New rows get `status: experimental`.

**Don't.**
- Promote any row to `required`.

**Acceptance.**
- [x] A dry run against a recorded API fixture produces the expected YAML diff.

**Verify.**
`./gradlew updateVersions --dry-run`, then `./gradlew :build-logic:test`

### T-M3-02 · Per-Java-tag host digests, and verify the Java rule table on every Paper version
- tier: sonnet
- depends: T-M3-01, T-M3-04
- modules: versions.yml, docs/versioning.md
- adrs: 0008
- specs: versioning.md §3
- test-first: no

**Goal.** Replace the assumed Java rules with observed ones.

**Known limit from M1 (GitHub issue #23, an ADR-0008 challenge).** `versions.yml` carries one `host.digest` per platform, but the `itzg/minecraft-server` digest
differs per Java tag, so today only the rows that use the pinned tag (Java 25: the 26.x rows) can run; `PaperHost`
fails loudly for the others. This card (or a new first card in M3) must change the host pin to one digest per Java
tag in the spec, the schema (`proofstone-protocol/schemas/versions.schema.json`), the validator, `VersionsCatalog`
and `PaperHost`. This card is tier sonnet for that reason, and it resolves issue #23. Treat it as a spec change: update `docs/specs/versions-yml.md` and write an ADR if it changes
ADR-0008. Also pin `sha256` and `build` for every row you want to run.

**Do.**
- Pin one digest per Java tag (`java8`, `java17`, `java21`, `java25`, and `java16` if it exists) and the `build` and
  `sha256` of every row that should run.
- Boot every Paper row with its rule-assigned Java tag, using the C01 health check only, and record any
  failures. `PaperHost` already fails loudly when the container's Java major does not match the cell; keep that check.
- Fix `java-rules`, checking whether the `java16` tag exists, and update the table in
  docs/versioning.md.

**Don't.**
- Hard-code per-row Java tags unless a rule can't express the case.

**Acceptance.**
- [x] Every Paper row boots. The table is updated and marked "observed". Evidence: C01 passed on all 11 Paper rows
  (1.8.8, 1.12.2, 1.16.5, 1.17.1, 1.18.2, 1.20.4, 1.20.6, 1.21.11, 26.1.2, 26.2, 26.3) with their rule-assigned tag;
  1.21.11 needed a `GameRules` fix first, 26.1.2 passed on a rerun after a Docker 500. `docs/versioning.md` §3.
  Folia rows have per-tag digests but were not booted.

**Verify.**
`./gradlew :conformance:run -Dproofstone.cells=paper@all --tests '*ConformanceSmokeTest*'`

### T-M3-03 · ViaProxy bridged topology
- tier: sonnet
- depends: T-M2-99, T-M3-01, T-M3-02, T-M3-04
- modules: proofstone-core, images/viaproxy
- adrs: 0004, 0005
- specs: specs/client-driver.md §5
- test-first: no

**Goal.** Clients that speak the newest protocol can reach any older server.

**Do.**
- Pin `ghcr.io/viaversion/viaproxy` by digest.
- Find and document the CLI flags for the target address, target version, offline auth and bind port.
- A row must be pinned (T-M3-01) and runnable with its Java tag (T-M3-02) before it can be bridged.
- When `driver.protocol != row.protocol`, core starts ViaProxy on the cell network and points the driver
  at it.
- `Cell.bridged()` returns true (`CellView.bridged()` is hard-wired false today), and reports show "bridged".
- Write `docs/platforms/viaproxy.md`.

**Don't.**
- Install Via plugins inside the server under test.

**Acceptance.**
- [x] C01–C06 pass, bridged, on 1.8.8, 1.12.2, 1.16.5, 1.18.2 and 1.20.4. (30 of 30, skipped 0, failures 0: `:conformance:run -Dproofstone.cells=paper@1.8.8,paper@1.12.2,paper@1.16.5,paper@1.18.2,paper@1.20.4`, 2026-11-07, BUILD SUCCESSFUL.)

**Verify.**
`./gradlew :conformance:run -Dproofstone.cells=paper@1.8.8,paper@1.12.2,paper@1.16.5,paper@1.18.2,paper@1.20.4`

### T-M3-04 · Planner: `all`, selectors, status filtering
- tier: haiku
- depends: T-M2-99
- modules: proofstone-core (`CellPlanner`, `ProofstoneExtension`)
- adrs: 0008
- specs: specs/test-api.md §1, versioning.md §4
- test-first: yes

**Goal.** Full matrix selection. Today `CellPlanner.plan` takes the versions of the `@Matrix` annotation and nothing
overrides them; this card adds the run-time selector that the other M3 cards use.

**Do.**
- Support `versions = {"all"}`.
- Support `-Dproofstone.cells` and `PROOFSTONE_CELLS` selectors, with the syntax `platform@version`,
  `platform@latest` and `platform@all`.
- Add a status filter. PRs run `required` plus selected `experimental` rows. Nightly runs everything that
  isn't retired.
- Read `proofstone.yml` version families from the user project.

**Don't.**
- Allow unknown selectors. They are errors.

**Acceptance.**
- [x] Unit tests cover every selector form and its error messages. Evidence: `CellSelectorTest` (16), `ScopeTest` (3),
  `VersionFamiliesTest` (10) and new `CellPlannerTest` cases for `all`, scope and families; `:proofstone-core:test`
  green (71 tests, 0 failures, 0 skipped).

**Verify.**
`./gradlew :proofstone-core:test`

### T-M3-05 · Conformance C07 (grindstone) and C08 (shift-click crafting result)
- tier: haiku
- depends: T-M3-03, T-M3-04
- modules: conformance
- adrs: 0003
- specs: specs/self-tests.md §1
- test-first: no

**Goal.** Cover more window types and click modes.

**Do.**
- Implement both scenarios as dual-oracle assertions, in the style of `C05AnvilRenameCostTest` (reuse
  `ConformanceFixture`; add a grindstone and a crafting table there).
- Open windows with `Client.openBlock` and click with `Window.click(slot, Click.SHIFT)`. If a result slot or shift mode
  is missing in the driver's `WindowModel`, fix it there in a separate commit.

**Don't.**
- Use `serverOnly`.

**Acceptance.**
- [ ] Both pass natively on the newest row and bridged on 1.12.2 and 1.18.2. Not met, evidence 2026-11-07
  (`:conformance:run -Dproofstone.cells=paper@latest,paper@1.12.2,paper@1.18.2 --tests '*C07*' --tests '*C08*'`, 6 tests,
  1 failed, 1 skipped): C07 PASSED on 26.3 and 1.18.2 (bridged), SKIPPED on 1.12.2 (no grindstone before 1.14, by
  design). C08 PASSED on 26.3 and 1.12.2 (bridged), FAILED on 1.18.2 (bridged): DISAGREE at the give check, agent
  resolves `sugar_cane` to `sugar_cane_block` (docs/platforms/paper.md; needs the T-M3-06 legacy material table).

**Verify.**
`./gradlew :conformance:run --tests '*C07*' --tests '*C08*'`

### T-M3-06 · Bukkit agent: legacy compatibility, 1.8.8 → 1.12.2
- tier: sonnet
- depends: T-M3-03, T-M3-04
- modules: platforms/bukkit/agent
- adrs: 0002
- specs: specs/agent-protocol.md §3
- test-first: yes

**Goal.** One agent jar that behaves correctly on servers from before the 1.13 flattening.

**Do.**
- Start from the failures of the golden replay (`AgentContractIT`) on 1.8.8 and 1.12.2, and from the reflection already
  in `BukkitGame` (`onlinePlayers`, `Enchantment.getKey`).
- Add a legacy material and enchantment name table, mapping namespaced keys to the pre-1.13 enum names.
  Generate it from data and store it as a resource.
- Item serialisation without `NamespacedKey` and without PDC (below 1.14, `pdc` is reported as an
  unsupported capability).
- Handle event getter differences safely.

**Don't.**
- Ship separate agent jars per version.

**Acceptance.**
- [ ] The golden contract tests pass on 1.8.8, 1.12.2 and the newest row.

**Verify.**
`./gradlew :platforms:bukkit:agent:integrationTest -Dproofstone.cells=paper@1.8.8,paper@1.12.2,paper@latest`
(`AgentContractIT` runs on one fixed row today, so this card also makes it read the selected cells.)

### T-M3-07 · CI matrix generated from versions.yml
- tier: haiku
- depends: T-M3-04
- modules: .github/workflows, build-logic
- adrs: 0008
- specs: versioning.md §4
- test-first: no

**Goal.** CI jobs come from data, with nothing hard-coded.

**Do.**
- Add a Gradle task, `printMatrix --scope=pr|nightly`, that prints GitHub Actions matrix JSON.
- Use it from the workflows through `fromJSON`, with one job per cell and the repro bundle uploaded as an
  artefact.

**Don't.**
- Duplicate version lists in YAML.

**Acceptance.**
- [x] Adding a row to `versions.yml` adds a CI job with no workflow edit. Evidence: `CiMatrixTest.addingARowAddsACellWithoutAnyOtherChange` (matrix from the planner, no YAML list); `printMatrix --scope=nightly` lists the four Paper rows from the seed.

**Verify.**
`./gradlew printMatrix --scope=nightly`

### T-M3-08 · Write down the version-span gotchas
- tier: haiku
- depends: T-M3-05, T-M3-06
- modules: docs/platforms
- adrs: 0010
- specs: platforms.md
- test-first: no

**Goal.** Capture what the span runs taught us.

**Do.**
- Add a version-span part to the Gotchas of `docs/platforms/paper.md`, which holds only 26.3 observations today.
- Collect the anomalies from the M3 runs into `docs/platforms/paper.md` and `viaproxy.md` Gotchas, each
  with its date and version.

**Don't.**
- Speculate. Record only what was observed.

**Acceptance.**
- [ ] Each gotcha links to a CI run or a repro bundle.

**Verify.**
`./gradlew docsCheck`

### T-M3-99 · Milestone review M3
- tier: sonnet
- depends: T-M3-01, T-M3-02, T-M3-03, T-M3-04, T-M3-05, T-M3-06, T-M3-07, T-M3-08
- modules: docs/plan
- adrs: 0010
- specs: plan/ROADMAP.md
- test-first: no

**Goal.** Verify the exit criterion and refine the M4 and M5 cards.

**Do.**
- Run every gate and paste the output.
- Refine the M4 and M5 cards, and re-estimate the work.

**Don't.**
- Start M4.

**Acceptance.**
- [ ] The exit criterion is shown green across the Paper span.

**Verify.**
`./gradlew :conformance:run -Dproofstone.cells=paper@all`
