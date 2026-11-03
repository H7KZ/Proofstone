# M3: Version span (1.8.8 → newest, ViaProxy bridging)

Exit: ViaProxy bridging works. Every Paper row from 1.8.8 to the newest passes C01–C08. `version-watch`
opens PRs automatically.
These cards are refined at T-M2-99. Keep the format.

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
- [ ] A dry run against a recorded API fixture produces the expected YAML diff.

**Verify.**
`./gradlew updateVersions --dry-run`, then `./gradlew :build-logic:test`

### T-M3-02 · Verify the Java rule table on every Paper version
- tier: haiku
- depends: T-M3-01
- modules: versions.yml, docs/versioning.md
- adrs: 0008
- specs: versioning.md §3
- test-first: no

**Goal.** Replace the assumed Java rules with observed ones.

**Do.**
- Boot every Paper row with its rule-assigned Java tag, using the C01 health check only, and record any
  failures.
- Fix `java-rules`, checking whether the `java16` tag exists, and update the table in
  docs/versioning.md.

**Don't.**
- Hard-code per-row Java tags unless a rule can't express the case.

**Acceptance.**
- [ ] Every Paper row boots. The table is updated and marked "observed".

**Verify.**
`./gradlew :conformance:run -Dtestificate.cells=paper@all --tests C01`

### T-M3-03 · ViaProxy bridged topology
- tier: sonnet
- depends: T-M2-99
- modules: testificate-core, images/viaproxy
- adrs: 0004, 0005
- specs: specs/client-driver.md §5
- test-first: no

**Goal.** Clients that speak the newest protocol can reach any older server.

**Do.**
- Pin `ghcr.io/viaversion/viaproxy` by digest.
- Find and document the CLI flags for the target address, target version, offline auth and bind port.
- When `driver.protocol != row.protocol`, core starts ViaProxy on the cell network and points the driver
  at it.
- `Cell.bridged()` returns true, and reports show "bridged".
- Write `docs/platforms/viaproxy.md`.

**Don't.**
- Install Via plugins inside the server under test.

**Acceptance.**
- [ ] C01–C06 pass, bridged, on 1.8.8, 1.12.2, 1.16.5, 1.18.2 and 1.20.4.

**Verify.**
`./gradlew :conformance:run -Dtestificate.cells=paper@1.8.8,paper@1.12.2,paper@1.16.5,paper@1.18.2,paper@1.20.4`

### T-M3-04 · Planner: `all`, selectors, status filtering
- tier: haiku
- depends: T-M2-99
- modules: testificate-core
- adrs: 0008
- specs: specs/test-api.md §1, versioning.md §4
- test-first: yes

**Goal.** Full matrix selection.

**Do.**
- Support `versions = {"all"}`.
- Support `-Dtestificate.cells` and `TESTIFICATE_CELLS` selectors, with the syntax `platform@version`,
  `platform@latest` and `platform@all`.
- Add a status filter. PRs run `required` plus selected `experimental` rows. Nightly runs everything that
  isn't retired.
- Read `testificate.yml` version families from the user project.

**Don't.**
- Allow unknown selectors. They are errors.

**Acceptance.**
- [ ] Unit tests cover every selector form and its error messages.

**Verify.**
`./gradlew :testificate-core:test`

### T-M3-05 · Conformance C07 (grindstone) and C08 (shift-click crafting result)
- tier: haiku
- depends: T-M3-03
- modules: conformance
- adrs: 0003
- specs: specs/self-tests.md §1
- test-first: no

**Goal.** Cover more window types and click modes.

**Do.**
- Implement both scenarios as dual-oracle assertions.

**Don't.**
- Use `serverOnly`.

**Acceptance.**
- [ ] Both pass natively on the newest row and bridged on 1.12.2 and 1.18.2.

**Verify.**
`./gradlew :conformance:run --tests 'C07|C08'`

### T-M3-06 · Bukkit agent: legacy compatibility, 1.8.8 → 1.12.2
- tier: sonnet
- depends: T-M3-03
- modules: platforms/bukkit/agent
- adrs: 0002
- specs: specs/agent-protocol.md §3
- test-first: yes

**Goal.** One agent jar that behaves correctly on servers from before the 1.13 flattening.

**Do.**
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
`./gradlew :platforms:bukkit:agent:integrationTest -Dtestificate.cells=paper@1.8.8,paper@1.12.2,paper@latest`

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
- [ ] Adding a row to `versions.yml` adds a CI job with no workflow edit.

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
`./gradlew :conformance:run -Dtestificate.cells=paper@all`
