# M8 follow-ups

Written 2026-11-15 from the M8 review (T-M8-99, ROADMAP "M8 review"). Gaps the review found between the M8 exit criterion,
ADR-0017 and the merged code, plus two findings from runs. **Not approved; none started.** They do not block the M9 spikes
(T-M9-01, T-M9-02), but T-M8-13 is needed before the M8 exit item "a mod under test through the public API" can be ticked.

IDs continue [M8-mods.md](M8-mods.md) (T-M8-01 to T-M8-12, T-M8-99), so the next free ID was T-M8-13.

**Flake rule.** The **Flake rule** in [M8-mods.md](M8-mods.md) applies unchanged: one cell per invocation under
`scripts/dockerlock.sh`, `--no-parallel --max-workers=1`, XML read, three separate green invocations per acceptance, a red is a
finding copied aside (XML, bundle, container logs) before the next run and never replaced by a later green. Loader join
timeouts, boot timeouts and log-guard reds are counted apart from the Paper and topology numbers.

### T-M8-13 · `@Proofstone(mods)` and `Capability.MODS`: a mod under test through the public API
- tier: sonnet
- depends: T-M8-99
- modules: proofstone-api, proofstone-core, examples, docs/specs/test-api.md, docs/user
- adrs: 0001, 0017
- specs: specs/test-api.md §13
- test-first: yes

**Goal.** A test declares the mod it tests, as ADR-0017 section 4 and test-api.md §13 already say, and the M8 exit item
"a test can put a mod under test (`/mods`) into a cell through the public API" can be shown by a run.

**Evidence.** `Proofstone` has `plugins` and `datapacks` but no `mods` (api-baseline at `d89cb4e`); `Capability` has no `MODS`,
although ADR-0017 section 8 names it and both mod agents report the token `mods`. `CellInvocation.bootLoader` passes
`withMods(..., List.of())`, so no user mod ever reaches `/mods`; T-M8-11 reaches the example mod only through the test-only
switch `-Dproofstone.injectModFault`. test-api.md §13 documents `mods = {}` as if it existed.

**Do.**
- Add `String[] mods() default {}` to `@Proofstone` and `Capability.MODS` (additive, `api-change`), resolved as `plugins` and
  `datapacks` are (globs, exactly one match each, failing loudly naming the glob).
- In `bootLoader`, copy the declared mods to `/mods` and run the existing loaded-or-loud check (`ModLoad`) on each declared id.
  A declared mod with id `fabric-api` is `INVALID test declaration` (ADR-0017 section 4). `mods` on a cell without `MODS` is
  `SKIPPED` with `CAPABILITY_MISSING`. A control run installs no declared mod (self-tests §2).
- Turn `metaModCrash` and `metaModCrashNeoForge` into tests that declare the crashing example mod through `mods`, and keep
  the fault switch only if a test still needs it (say which).
- Update test-api.md §13 and the user guide so they match the code, and the M8 example if it gains a mod.

**Don't.**
- Change an existing signature or the meaning of `plugins` or `datapacks`. Let a mod that did not load pass.

**Acceptance.**
- [x] `api-baseline.txt` diff shows additions only (`mods`, `MODS`).
- [x] Unit tests for the glob resolution, the skip, the `fabric-api` refusal and the control run.
- [x] A test that declares a server-only mod through `mods` passes on `fabric@26.3` and on `neoforge@26.3`, three invocations
  each, and the crash-at-load bad test (declared through `mods`) is `INVALID` naming the mod on both rows.

**Verify.**
`./gradlew :proofstone-api:test :proofstone-core:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaModTest --no-parallel --max-workers=1`

### T-M8-14 · Read `proofstone.agentJar` only on the Bukkit-family path in `CellInvocation.boot`
- tier: haiku
- depends: T-M8-99
- modules: proofstone-core, examples
- adrs: 0017
- specs: specs/test-api.md
- test-first: yes

**Goal.** A loader run no longer has to set the Bukkit agent's jar property it never uses (ROADMAP "M8 findings", T-M8-12 run 1:
`ParameterResolutionException` before any server started).

**Do.**
- Move the `proofstone.agentJar` null check in `CellInvocation.boot` after the `loader != null` branch, so only `bootPaper`
  requires it; the loader branch already requires `proofstone.<loader>.agentJar`.
- Remove the workaround from `examples/loader-datapack/build.gradle.kts` (it sets the Bukkit agent jar) and say why in the commit.
- A unit test: a loader cell boots with only `proofstone.fabric.agentJar` set, and a Paper cell without `proofstone.agentJar`
  still fails loudly with the property named.

**Don't.**
- Drop the check for Paper cells, or change any message other than where it is raised.

**Acceptance.**
- [ ] The unit tests pass.
- [ ] `:examples:loader-datapack:loaderExample -Dproofstone.cells=fabric@26.3` green three invocations without the workaround.

**Verify.**
`./gradlew :proofstone-core:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:loader-datapack:loaderExample -Dproofstone.cells=fabric@26.3 --no-parallel --max-workers=1`

### T-M8-15 · Share the vanilla game-view code between the Fabric and NeoForge agents
- tier: sonnet
- depends: T-M8-99
- modules: platforms/fabric/agent, platforms/neoforge/agent, platforms
- adrs: 0002, 0017
- specs: specs/agent-protocol.md §2
- test-first: yes

**Goal.** One copy of the code that reads the vanilla server (players, inventories, windows, world rules, item JSON, ticks), so a
fix lands in both agents at once.

**Evidence.** `MinecraftView`, `ItemJson`, `TickCounter`, `ServerThread`, `Values` and `GameView` exist in both agents with 2 to
44 changed lines each (746 main lines in the Fabric copies), plus the test helpers `MockView` and `AgentClient` (235 lines): about
950 duplicated lines. 26.x is unobfuscated, so the vanilla calls are the same bytes on both loaders.

**Do.**
- Move the shared classes into one internal module (for example `platforms/vanilla-view`, Java 25, compiled with plain `javac`
  against the same pinned vanilla jar), shaded into both agent jars; loader-specific code (events, readiness, mod list, data
  packs) stays in each agent.
- Behaviour unchanged: both golden contract tests replay as before, both observe-only tests pass (extended to scan the shared
  module's classes).

**Don't.**
- Change the wire protocol, a golden file, or what either agent reports. Add a runtime dependency (the module is ours and shaded).

**Acceptance.**
- [ ] `./gradlew :platforms:fabric:agent:test :platforms:neoforge:agent:test` passes; duplicated lines reported before and after.
- [ ] `:conformance:run` on `fabric@26.3` and `neoforge@26.3` green, three invocations each.

**Verify.**
`./gradlew :platforms:fabric:agent:test :platforms:neoforge:agent:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=fabric@26.3 --no-parallel --max-workers=1`

### T-M8-16 · Diagnose the `Skipping Entity with id minecraft:zombie_villager` WARN on loader cells
- tier: sonnet
- depends: T-M8-99
- modules: proofstone-core, conformance, docs/platforms
- adrs: 0003, 0007, 0017
- specs: specs/self-tests.md §1
- test-first: no

**Goal.** Name the cause of the worldgen WARN that turned a `fabric@26.3` C02 red (ROADMAP "M8 findings", T-M8-09 verifier run 3;
the review's own counts are in ROADMAP "M8 review"), so a fix card can remove it without an allowlist.

**Evidence so far.** `WARN net.minecraft.world.entity.EntityType: Skipping Entity with id minecraft:zombie_villager`, from a
worldgen worker thread right after the join. **Lead found by the review (not yet a cause):** `LoaderEnvironment.env` sets
`LEVEL_TYPE` flat but **no `SEED`**, while `PaperHost` pins `SEED=1234567`, so every loader cell generates a different world and
a superflat village (with its zombie villager) lands near spawn in some runs and not others. Paper cells, with a fixed seed, have
never logged it.

**Do.**
- Copy `build/` results aside before every run. Run `:conformance:run -Dproofstone.cells=fabric@26.3` and `neoforge@26.3` at least
  5 times each; record red or green and, for every run, whether the WARN appears anywhere in the server log (also outside the
  test window), with the world seed from the log.
- Reproduce on purpose: start the loader host by hand with the seed of a red run, and with `SEED=1234567`; state whether the
  WARN follows the seed. Read why the entity is skipped (a data fix, a missing type in a structure template, a 26.3 vanilla bug).
- Write the finding into ROADMAP and propose the fix card (pin the seed in `LoaderEnvironment`, or a documented vanilla gotcha
  with a reasoned log-guard entry, or a spawn-area setting).

**Don't.**
- Allowlist the line, raise a timeout, add a retry or a sleep, or change harness code in this card.

**Acceptance.**
- [ ] Per-row red counts over at least 5 runs each, XML read, with the seed per run.
- [ ] The cause named with log lines, or "unexplained" with the counts and what was ruled out.
- [ ] A fix card proposed, or the reason none is needed.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=fabric@26.3 --no-parallel --max-workers=1`

### T-M8-17 · Amend ADR-0017 to what ran (§4 data-pack check, §5 build, §7 resource-pack status, §8 `MODS`)
- tier: sonnet
- depends: T-M8-99
- modules: docs/adr, docs/specs, docs/platforms
- adrs: 0017
- specs: specs/test-api.md, specs/client-driver.md
- test-first: no

**Goal.** ADR-0017 states what the code does where M8 runs showed its assumptions were wrong, as ADR-0016 was amended after M7.

**Do.**
- Add an "Amendments" section (dated) to ADR-0017, without re-deciding it:
  - §4: a data pack with a broken function stays enabled and the server logs the error at boot, before the agent's log window,
    so the log guard does not see it; the host reads its own log of the whole boot for WARN or worse lines naming a namespace of
    the pack (`DataPackLoad.errorsLogged`, T-M8-11), and a failure there is `INVALID`, not `FAIL`.
  - §5: Loom and ModDevGradle are not used. Both agents compile with plain `javac` against pinned jars, the fallback §5 allows:
    the vanilla 26.3 server jar, Fabric loader and Fabric API (Fabric), and six NeoForge-side jars (NeoForge universal, FML loader,
    bus, mergetool api, maven-artifact, plus typetools for tests), each checked by sha256 at build time (`gradle/libs.versions.toml`).
  - §7: Paper 26.3 fires no `PlayerResourcePackStatusEvent` for the configuration-phase push from `server.properties`; the
    server-side status is read from a play-phase push by the fixture plugin (T-M8-10, paper.md Gotchas). The driver side holds.
  - §8: `MODS` is not in the API yet (T-M8-13).
- Mark §6 (Mojang at cold start) as still awaiting the maintainer's veto decision, with the host list T-M8-07 measured.

**Don't.**
- Change code, a schema, `versions.yml`, or any decision the runs did not contradict.

**Acceptance.**
- [ ] The amendment lists each change with the run or file that shows it.
- [ ] `./gradlew docsCheck` passes.

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M8-18 · A leak bad test on a loader cell
- tier: sonnet
- depends: T-M8-99
- modules: examples/bad-tests, scripts
- adrs: 0003, 0017
- specs: specs/self-tests.md §3
- test-first: yes

**Goal.** Show the leak detector catches a leak on a loader cell, as `metaLeak` does on Paper (robustness R6).

**Evidence.** `LeakDetector.snapshot` takes any `ServerHost`, so `LoaderHost` cells are snapshotted before and compared after,
and both mod agents serve `snapshot` and the world rules. No bad test exercises it: every `metaLeak` run is on `paper@26.1.2`.

**Do.**
- A bad test pinned to `fabric@26.3` that leaves a world rule changed (as `LeakE2E` does), expected `LEAK`, refused on other cells
  (`bad-tests-pin-check`). Add it to `metaModFabricTest` or a new task, and ask in the PR whether it joins the mutation check.

**Don't.**
- Change the leak detector, or count a boot or join timeout as the expected verdict.

**Acceptance.**
- [ ] The bad test gives `LEAK` with the rule named, three invocations; the clean runs of the task stay green.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaModFabricTest --no-parallel --max-workers=1`
