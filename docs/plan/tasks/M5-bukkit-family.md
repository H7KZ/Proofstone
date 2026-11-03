# M5: Bukkit family complete (Folia, Purpur, Spigot, Bedrock-prefix simulation)

M5 can run in parallel with M4 once M3 is done.

**Exit criterion.**
- Folia, Purpur and Spigot pass conformance.
- The capability model is done.
- Bedrock-prefix simulation works.

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
- [ ] Unit and integration tests show the skip reason in reports.

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
- Add a `FoliaScheduler` that uses reflection only, so the agent still compiles against 1.8.8.
- Route each operation type to the owning scheduler:
  - player operations go to the entity scheduler;
  - block operations go to the region scheduler;
  - commands go to the global scheduler.
- Run every operation through a `CompletableFuture` with a timeout.
- Report per-region ticks.

**Don't.**
- Disable Folia's thread checks.

**Acceptance.**
- [ ] The golden contract tests pass on Folia `latest`.
- [ ] No thread-check errors appear in the logs.

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
- [ ] C01–C08, C-F01 and C-F02 pass on every Folia row.

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
- [ ] C01–C08 pass on the newest Purpur row and one older Purpur row.

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
- Run Spigot rows only in the nightly suite, because builds take minutes.

**Don't.**
- Redistribute the Spigot jars.

**Acceptance.**
- [ ] C01–C08 pass on Spigot 1.8.8, 1.21.11 and the newest version.

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
- [ ] A fixture plugin's prefix-based detection sees the client as Bedrock.

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
- [ ] `docsCheck` passes.
- [ ] Every status level matches the latest conformance results.

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
- Re-estimate the remaining work.

**Don't.**
- Start M7.

**Acceptance.**
- [ ] The exit criterion is demonstrated.

**Verify.**
`./gradlew :conformance:run -Dproofstone.cells=folia@all,purpur@latest,spigot@1.21.11`
