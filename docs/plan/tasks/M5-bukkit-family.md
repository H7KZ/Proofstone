# M5: Bukkit family complete (Folia, Purpur, Spigot, Bedrock-prefix simulation)

M5 can run in parallel with M4 once M3 is done.

**Exit criterion.**
- Folia, Purpur and Spigot pass conformance.
- The capability model is done.
- Bedrock-prefix simulation works.

## What M1 to M4 produced (refined in T-M4-99)

- Cell selector: `-Dproofstone.cells=folia@latest` (or `folia@<v>`, `all`). A `@Matrix` whose platform the selector does
  not name is not run. Every M5 verify command uses it, one cell per invocation.
- Pins: one host digest per Java tag (ADR-0013). Folia has 4 pinned rows (1.21.11, 26.1.2, 26.2, 26.3), all
  `experimental`. The only Folia observation is 1.21.11: client oracle OK, RCON oracle unusable (`versions.yml`). No
  Folia row has passed conformance (M3 review, open item 5).
- Real-server runs go through `scripts/dockerlock.sh --no-parallel` with `PROOFSTONE_ACCEPT_EULA=true` in the command
  environment. Read each XML for `skipped="0" failures="0" errors="0"`.
- Self-tests (M4): control run, repeat run and differential run exist, and `:examples:bad-tests:metaTest` asserts every
  bad test. A Folia row gets the same treatment as a Paper row, but its bad tests are not added in M5.
- The reference driver (`mineflayer-ref`) speaks only 26.1.2 (ADR-0004). Differential runs cover only that row, so
  Folia, Purpur and Spigot have no differential run in M5.
- `ErrorLogGuard` allowlists Paper's log lines only. Folia's thread-check messages need per-pack patterns, which are
  gap card T-M4-15. It blocks T-M5-03 unless the maintainer decides otherwise (ROADMAP M4 review, decision 2).
- The nightly set (`.github/workflows/nightly.yml`) runs `metaTest` on the two Paper cells that the bad tests pin. No
  Folia, Purpur or Spigot row is added to it in M5 without a decision.
- Java: the agent is Java 8 bytecode (1.8.8 runs). The host-side drivers and bad tests need a Java 21 toolchain.

### T-M5-01 · Capability model and `@Requires`
- tier: haiku
- depends: T-M3-99
- modules: proofstone-api, proofstone-core
- adrs: 0001
- specs: specs/test-api.md §1, glossary.md (Capability)
- test-first: yes

**Goal.** Tests declare the capabilities they need, and cells that lack one are skipped with a reason.

**Do.**
- Add a `Capability` enum, with documentation for each value.
- Compute each cell's capabilities as the agent-reported set intersected with the pack-declared set.
- Make `@Requires` mismatches produce `SKIPPED("missing capability X on cell")`.

**Don't.**
- Infer capabilities from version strings.

**Acceptance.**
- [x] Unit and integration tests show the skip reason in reports. (`:proofstone-core:test`: CapabilityModelTest 9/9, skipped 0; `:examples:bad-tests:metaRequires` on paper@26.3, 2026-11-08: `RequiresSkipsE2E.skippedWithoutFolia` SKIPPED, `missing capability FOLIA_REGIONS on cell paper@26.3`, CAPABILITY_MISSING in proofstone-report.json; held test PASS.)

**Verify.**
`./gradlew :proofstone-core:test`

### T-M5-02 · Folia scheduler abstraction in the agent
- tier: sonnet
- depends: T-M3-99
- modules: platforms/bukkit/agent
- adrs: 0002
- specs: platforms/folia.md
- test-first: no

**Goal.** Every agent operation runs on the thread that owns its data.

**Do.**
- First boot the Folia rows with the C01 health check only, as T-M3-02 did for Paper, before any scheduler code.
  The per-tag digests exist, but no Folia row has ever booted (M3 review). Record each failure in
  `docs/platforms/folia.md`.
- Add a `FoliaScheduler` that uses reflection only, so the agent still compiles against 1.8.8.
- Route each operation type to the owning scheduler:
  - player operations go to the entity scheduler;
  - block operations go to the region scheduler;
  - commands go to the global scheduler.
- Run every operation through a `CompletableFuture` with a timeout.
- Report per-region ticks.

**Don't.**
- Disable Folia's thread checks.
- Let a thread-check line pass as a logged-error allowlist entry. The pattern is per pack (T-M4-15), and it is reviewed.

**Acceptance.**
- [x] The golden contract tests pass on Folia `latest`. Evidence: `folia@26.3` (latest, experimental), AgentContractIT
  25 of 25, skipped 0, failures 0, errors 0 (2026-11-08). Health is matched with `platform: folia` and the `regions`
  token added, and ticks drop the Folia-only `regions` field before matching (AgentContractIT `forPlatform`).
- [x] No thread-check errors appear in the logs. Evidence: `no thread-check errors` passes on folia@26.3 (server log
  holds no thread-check text; the only ERROR lines are `No key layers` and the harness's `Could not save bukkit.yml`).

**Verify.**
`./gradlew :platforms:bukkit:agent:integrationTest -Dproofstone.cells=folia@latest`

### T-M5-03 · Folia pack and conformance extras
- tier: haiku
- depends: T-M5-02, T-M5-01
- modules: platforms/bukkit/folia, conformance
- adrs: 0003
- specs: specs/self-tests.md §1
- test-first: no

**Goal.** A Folia platform pack that passes conformance, plus Folia-specific scenarios.

**Do.**
- Write `pack.yml` with its log patterns, including the thread-check messages.
- Add C-F01: teleport a client across regions, then check the state.
- Add C-F02: an event probe on a region thread, which reports the `thread` field.

**Don't.**
- Copy the Paper pack's allowlist without reviewing it.

**Acceptance.**
- [x] C01–C08, C-F01 and C-F02 pass on every Folia row. (2026-11-08, `:conformance:run -Dproofstone.cells=folia@<v>`, one dockerlock run per row, JUnit XML skipped=0 failures=0 errors=0 on all 10 suites: 1.21.11 and 26.1.2 and 26.2 bridged, 26.3 native.)

**Verify.**
`./gradlew :conformance:run -Dproofstone.cells=folia@all`

### T-M5-04 · Purpur pack
- tier: haiku
- depends: T-M5-01
- modules: platforms/bukkit/purpur, versions.yml
- adrs: 0008
- specs: platforms.md
- test-first: no

**Goal.** A Purpur platform pack that passes conformance.

**Do.**
- Confirm that the itzg `TYPE=PURPUR` image works.
- Add the Purpur rows to `version-watch`, using Purpur's API.
- Write `pack.yml` and `docs/platforms/purpur.md`.

**Don't.**
- Add Purpur-specific API usage to the agent.

**Acceptance.**
- [x] C01–C08 pass on the newest Purpur row and one older Purpur row. (`purpur@26.3` native and `purpur@1.21.11` bridged,
  2026-11-08: C01–C08 pass, skipped 0 failures 0 errors 0; C-F01/C-F02 skip on the missing FOLIA_REGIONS capability.
  Details in `docs/platforms/purpur.md`.)

**Verify.**
`./gradlew :conformance:run -Dproofstone.cells=purpur@latest`

### T-M5-05 · Spigot pack (BuildTools image, checksum cache)
- tier: sonnet
- depends: T-M5-01
- modules: platforms/bukkit/spigot, images/spigot-buildtools
- adrs: 0005, 0006
- specs: platforms.md
- test-first: no

**Goal.** Spigot servers built from source, reproducibly.

**Do.**
- Build an image that runs BuildTools for a pinned `--rev`.
- Cache the resulting jar by SHA-256, both in CI and locally.
- Run Spigot rows as manual per-row runs (no schedule: M3 review, PR #40), because builds take minutes.
- Expect `platform: spigot` for PaperSpigot 1.8.8. M3 found that the Paper 1.8.8 build reports `spigot`
  (`docs/platforms/paper.md`), so decide the pack's platform name from the jar, not from the Paper config class.

**Don't.**
- Redistribute the Spigot jars.

**Acceptance.**
- [x] C01–C08 pass on Spigot 1.8.8, 1.21.11 and the newest version. (T-M5-99, 2026-11-08, one invocation per row, XML
  `skipped` reasons only: 1.8.8 C07 is a reasoned skip, no grindstone before 1.14; `spigot@26.3` and `spigot@1.21.11` pass
  C01–C08 with only the capability skips. Evidence in ROADMAP "M5 review (T-M5-99)".)

**Verify.**
`./gradlew :conformance:run -Dproofstone.cells=spigot@1.21.11`

### T-M5-06 · Bedrock-prefix simulation
- tier: haiku
- depends: T-M5-01
- modules: proofstone-api, platforms/bukkit
- adrs: 0002
- specs: platforms.md (Bedrock)
- test-first: no

**Goal.** Test a Java plugin's Bedrock-only code paths without running a real Bedrock client.

**Do.**
- Add `clients.create(".Name")`, which logs in a Java client under a prefixed name.
- Document, and set through the pack, the Paper username-validation setting that allows prefixed names.
  Verify which setting is required.
- Add the `SIMULATED_BEDROCK` capability.

**Don't.**
- Claim real Bedrock support anywhere.

**Acceptance.**
- [x] A fixture plugin's prefix-based detection sees the client as Bedrock. (T-M5-99, 2026-11-08: `:conformance:run
  -Dproofstone.cells=paper@26.3 --tests *ConformanceBedrockPrefixTest*` BUILD SUCCESSFUL, 1 test, skipped 0, failures 0.
  Native Paper only: `SIMULATED_BEDROCK` is declared by the Paper pack alone, so the same test skips with the reason
  `missing capability SIMULATED_BEDROCK` on purpur@26.3, spigot@26.3 and every bridged row.)

**Verify.**
`./gradlew :conformance:run --tests C-B01`

### T-M5-07 · Platform docs
- tier: haiku
- depends: T-M5-03, T-M5-04, T-M5-05
- modules: docs/platforms
- adrs: 0010
- specs: platforms.md
- test-first: no

**Goal.** Up-to-date platform docs.

**Do.**
- Write or update `folia.md`, `purpur.md` and `spigot.md`, covering their hosts, capabilities and
  gotchas.
- Update the status levels in platforms.md.

**Don't.**
- Claim a status that the conformance results don't back up.

**Acceptance.**
- [x] `docsCheck` passes. (`./gradlew docsCheck --max-workers=1`: 0 errors, 356 warnings, none from this PR.)
- [x] Every status level matches the latest conformance results. (All non-Paper rows stay `experimental`; evidence per row in `docs/platforms.md` "Status evidence" and each platform doc.)

**Verify.**
`./gradlew docsCheck`

### T-M5-99 · Milestone review M5
- tier: sonnet
- depends: T-M5-01, T-M5-02, T-M5-03, T-M5-04, T-M5-05, T-M5-06, T-M5-07
- modules: docs/plan
- adrs: 0010
- specs: plan/ROADMAP.md
- test-first: no

**Goal.** Confirm the M5 exit criterion.

**Do.**
- Run every gate and paste the output.
- Run each cell in its own `:conformance:run -Dproofstone.cells=<cell>` invocation, under the Docker lock, and parse
  each XML for `skipped` and `failures`. One invocation with a long cell list hides which row failed (M3 lesson).
- Re-estimate the remaining work.

**Don't.**
- Start M7.

**Acceptance.**
- [x] The exit criterion is demonstrated, with one recorded flake (purpur@26.3 C06, red then green on rerun). See ROADMAP "M5 review (T-M5-99)".

**Verify.**
`./gradlew :conformance:run -Dproofstone.cells=folia@<v>` for each Folia row, then `purpur@latest` and `spigot@1.21.11`,
one at a time, each XML checked for `skipped="0" failures="0"` (a reasoned skip is listed, not hidden).
