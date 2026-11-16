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
- [x] Per-row red counts over at least 5 runs each, XML read, with the seed per run. (11 Fabric and 10 NeoForge invocations, 0 red by
  a WARN; seeds in ROADMAP "M8 findings", "T-M8-16 loader WARN diagnosis".)
- [x] The cause named with log lines, or "unexplained" with the counts and what was ruled out. (Peaceful difficulty plus the zombie
  village variant, reproduced by hand; and the second WARN family, `handleDisconnection() called twice`, a vanilla race.)
- [x] A fix card proposed, or the reason none is needed. (T-M8-21 and T-M8-22 below.)

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=fabric@26.3 --no-parallel --max-workers=1`

**Result (2026-11-15).** Scope extended by the orchestrator to the second loader WARN family. Both causes are named in ROADMAP
"M8 findings"; no harness code changed.

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
- [x] The amendment lists each change with the run or file that shows it. (ADR-0017 "Amendments", 2026-11-16.)
- [x] `./gradlew docsCheck` passes. (Through `bash scripts/gates.sh --max-workers=1`, in the PR.)

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
- [x] The bad test gives `LEAK` with the rule named, three invocations; the clean runs of the task stay green.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaModFabricTest --no-parallel --max-workers=1`

### T-M8-20 · Fix the red loader host ITs and audit every module's integration tests
- tier: sonnet
- depends: T-M8-99
- modules: platforms/fabric/agent, platforms/neoforge/agent, docs/ai
- adrs: 0017
- specs: specs/self-tests.md
- test-first: no

**Goal.** `aFailedStartFailsAtOnceWithTheLogTailAndLeavesNoContainer` (`FabricLoaderHostIT`, `NeoForgeLoaderHostIT`) is red on `main`
since T-M8-11, which made a mod that crashes at load fail the start as `INVALID test declaration` naming the mod, not `failed to
boot`. The IT asserts what a failed start now is, and every module's `integrationTest` is run once so no other stale expectation hides.

**Evidence.** The red IT fails identically on the T-M8-11 merge: the orchestrator merged T-M8-11 without running these module ITs,
because the verifier checklist did not say to.

**Do.**
- Reproduce the red on `main`, copy the XML aside.
- Update the two ITs to the current shape: still `PaperHostException`, message `INVALID test declaration` naming the mod and the
  cell, the loader's give-up line, `--- log tail ---`, no `BOOT TIMEOUT`, failure well under half the boot timeout, no container left.
- Audit: run `integrationTest` once for every module that has it (and the `topologyTest` ITs of `proofstone-core`) on `main` plus the fix, under the Docker lock,
  one module per invocation. Fix a clearly stale expectation in its own commit with the reason; record any other red in the ROADMAP
  "M8 findings" and propose a card.
- Add to `docs/ai/workflow.md`: the verifier runs the `integrationTest` of every module a PR touches or depends on.

**Don't.**
- Weaken an assertion, raise a timeout, add a retry, a sleep or an allowlist.

**Acceptance.**
- [ ] Both `*LoaderHostIT*` classes (all tests) are green in three separate invocations each.
- [ ] The audit table (module, invocation, XML counts, result) is in the PR and the ROADMAP.
- [ ] `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :platforms:fabric:agent:integrationTest --tests '*LoaderHostIT*' --no-parallel --max-workers=1`

### T-M8-21 · Pin the world seed on loader cells (the `zombie_villager` WARN)
- tier: haiku
- depends: T-M8-16
- modules: proofstone-core, docs/platforms
- adrs: 0017
- specs: specs/self-tests.md §1
- test-first: yes

**Goal.** A loader cell generates the same world every boot, so a zombie village near spawn cannot turn a test red through the log
guard (ROADMAP "M8 findings", T-M8-16: `DIFFICULTY=peaceful` makes `EntityType.create` return null for the zombie villager of a
zombie village template, 1 in about 430 boots, and only a village in the generated area can do it).

**Do.**
- Test first: `LoaderEnvironmentTest` asserts that `LoaderEnvironment.env` sets `SEED` for a Fabric cell and a NeoForge cell, and that
  it equals the value `PaperHost` pins. It fails on main (no `SEED`).
- Make `PaperHost`'s `SEED` one package-visible constant and put it into `LoaderEnvironment.env`. Same value, `1234567`: it was
  checked on Fabric (`locate structure #minecraft:village`: nearest village 353 blocks from spawn, the generated area is about
  64 blocks) and Paper never logged the WARN with it.
- Documentation, in the same PR: the Fabric and NeoForge Gotchas (mechanism, the seed and its nearest village), the troubleshooting
  entry (cause known, fixed), `docs/robustness.md` R9 and `docs/platforms.md` (no "open" for this WARN).

**Don't.**
- Change `DIFFICULTY` (a hostile mob would then spawn in tests), allowlist the line, raise a timeout, add a retry or a sleep.
- Set `GENERATE_STRUCTURES=false` in this card. It would also remove the whole family regardless of seed, but it makes the cell's
  world differ from Paper's; it stays the fallback if a mod under test (T-M8-13) moves worldgen and brings a village near spawn.

**Acceptance.**
- [ ] The new `LoaderEnvironmentTest` cases fail before the change and pass after.
- [ ] By hand, on both loaders, the console `seed` answers `Seed: [1234567]` and `locate structure #minecraft:village` is at least
  250 blocks away (one line in the PR for each).
- [ ] `:conformance:run` on `fabric@26.3` and `neoforge@26.3`, three invocations each, XML read: no WARN-guard red, and 0 lines
  `Skipping Entity with id` in every container log (a read-only `docker logs -f` watcher, as T-M8-16). Reds of the known
  `no matching item among []` DISAGREE family are counted apart.
- [ ] `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`./gradlew :proofstone-core:test --tests '*LoaderEnvironmentTest*'`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=neoforge@26.3 --no-parallel --max-workers=1`

### T-M8-22 · A client's own leave no longer reds a test (`handleDisconnection() called twice`)
- tier: sonnet
- depends: T-M8-16
- modules: proofstone-core, docs/platforms
- adrs: 0003, 0017
- specs: specs/self-tests.md §1
- test-first: yes

**Goal.** The vanilla race in `ServerConnectionListener.tick` and `Connection.tick` (T-M8-16: the second `handleDisconnection()` runs
one tick after the first, about 0.4% of client closes, both loaders, not in Paper-family logs) cannot fail a test. It reds one only
when the closing client is `PSConf`, because the conformance runs inside the first test's guard window.

**Decision for the maintainer first.** (A) A pack-scoped allowlist entry on the Fabric and NeoForge packs, anchored to the exact
line, with the reason (vanilla noise; the guard cannot tell it from a real double handling). Smallest, but it hides that line
for good. (B) This card as written: no allowlist, the guard window moves. Pick one before the card starts; the steps below are B.

**Do.**
- Test first, no Docker, with a fake host log: (1) a `handleDisconnection() called twice` record after the conformance client's
  `left the game` does not fail the first test; (2) the same record inside the conformance window **before** the client closes
  fails the conformance, naming the line; (3) any other WARN after the new cursor fails the test as before; (4) a Paper cell
  takes the same path and its tests are unchanged.
- In `CellConformance.scenarios`, check the log (`ErrorLogGuard`, the cell's pack) from the conformance start up to just before
  `client.close()`, and report a hit as the conformance's own failure.
- After `client.close()`: await (the `Await` helper, no sleep) the host log line `<name> left the game`, then await two more global
  ticks (`GET /v1/ticks/await`, agent-core `Game.awaitTicks`; the first handling is on the tick that sees the close, the second on
  the next). Only then return, and take the first test's log cursor after that, not before the conformance
  (`CellInvocation.beforeEach`, `new TestState(host, ErrorLogGuard.cursor(host))`).
- Fail loudly if the line or the ticks do not arrive in the existing await budget: a conformance that cannot confirm its own leave
  is a failed conformance, never a pass.

**Don't.**
- Allowlist the line (that is option A), add a sleep, a retry, or raise a timeout; guard less than today (every record outside the
  two-tick gap is still checked).
- Change the driver's close (`network.disconnect`): every real client closes the same way and the race is the server's.

**Acceptance.**
- [ ] The four unit cases fail before and pass after (cases 2 and 3 are the ones that keep the guard honest).
- [ ] `:conformance:run` on `fabric@26.3` and `neoforge@26.3`, three invocations each, XML read: no WARN-guard red. A run cannot
  prove the fix (the race is about 1 in 100 invocations); the unit cases do. By hand, `scripts/spike/DisconnectRace.java` through
  `scripts/spike/loader-hand.sh` (`RACE="600 0 help"`) shows how often the line still appears in the server log (expected: as before,
  about 0.4%), with every hit outside a failed test.
- [ ] `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`./gradlew :proofstone-core:test --tests '*ErrorLogGuardTest*'`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=fabric@26.3 --no-parallel --max-workers=1`

**Result (2026-11-16).** Option B, picked by the maintainer's delegate. `CellConformance.session` (the conformance client's whole
life, package-private so a fake host can drive it): the log window opens before the join, is checked up to just before the close
(a hit fails the conformance, `the server logged errors during the conformance`, naming the line), then after `client.close()` it
awaits `PSConf left the game` in the host log (`LogBuffer.since` from a mark taken before the close) and
`POST /v1/ticks/await {n: 2}` (the agent route is a POST, not the GET the card text says), each in the 10 s budget used by every
other client wait; a failure of either is a failed conformance. `CellInvocation.conformance` then sets `TestState.logCursor` to
the log's end, only in the invocation that ran the conformance (a cached result leaves the cursor where `beforeEach` put it).
No allowlist, sleep, retry or timeout change; the driver's close is untouched.
- Unit cases (`ConformanceLeaveTest`, a fake host whose ticks run on their own thread): before the fix cases 1 and 2 and the two
  fail-loud cases (no leave line, no ticks) are red, cases 3 and 4 are green (they are the controls that the guard still checks
  every other record and that a Paper-pack cell takes the same path); after, all 6 pass, 4 more reruns green. The card says all
  four fail before: 3 and 4 cannot, since the guard they test is unchanged, and they are not claimed to.
- Cost: 155 to 215 ms per conformance (median 166 ms, 26 conformances in the runs below), printed as
  `PROOFSTONE CONFORMANCE <cell> leave confirmed in N ms`.
- Real servers: `:proofstone-core:test` (52 suites, 0 skipped, 0 failures), `:proofstone-core:integrationTest` (1 suite) and
  `topologyTest` (7 suites), `:conformance:smoke` 3 invocations (4 cells each, 8 tests, all green), `:conformance:run`
  `fabric@26.3` 3 of 3 green; `neoforge@26.3` 4 invocations: 1 green, 1 red (CD02, the container exited during the NeoForge
  installer's library download: `SocketTimeoutException: Read timed out`, before any test, not this change), 1 red (C08, the known
  `no matching item among []` DISAGREE family, T-M8-23, counted apart), then one more run green; CD01 and CD02 skip with
  DATAPACKS_VANILLA expected. `velocity@4.3.1+paper@26.3` and `bungeecord@2111+paper@26.3` green, bridged `paper@1.21.11` green,
  `metaTest` ("every bad test produced its expected verdict", the leak and conformance-window verdicts unchanged),
  `metaTopologyTest` (7 of 7 `INVALID`), `FabricLoaderHostIT` green. No WARN-guard red in any of them; a run cannot show that the
  race is fixed (about 1 in 100 invocations), the unit cases do.
- By hand (`loader-hand.sh`, `RACE="600 0 help"`, the harness not involved): `handleDisconnection() called twice` appeared 1 time in
  600 closes on Fabric and 1 in 600 on NeoForge, so the line is still in the server log at the old rate (small sample).

### T-M8-23 · Diagnose and fix the `no matching item among []` DISAGREE family (C06, C08) on loader cells
- tier: sonnet
- depends: T-M8-22
- modules: proofstone-core, conformance, docs/platforms
- adrs: 0003, 0017
- specs: specs/self-tests.md §1
- test-first: yes

**Goal.** C06 and C08 stop going red on `fabric@26.3` and `neoforge@26.3` with `DISAGREE: present in the inventory ... no matching
item among []`, with the mechanism shown by run evidence and the fix an await on the client's own slot state.

**Evidence so far.** Seen as fabric C08 (1 of 3 on #137), neoforge C08 (several), fabric C06 (once), and in ROADMAP "M8 findings"
(T-M8-16). Lead: the same race T-M8-09 found in C-D02: the scenario closes the window before the server's slot update for the
player's own slot reached the client, and the driver (like the vanilla client) ignores the packets of a closed window.

**Do.**
- Copy the XML, the bundle and the container log aside after every run. At least 10 `:conformance:run` invocations per loader row
  (`fabric@26.3`, `neoforge@26.3`), one cell per invocation. Record per run red or green and, for a red, the driver transcript lines
  around `ServerboundContainerClosePacket`.
- Name the mechanism with those lines, or say "unexplained" with the counts.
- Test first: a unit test with a fake window whose server update for the player's slot only arrives after `close()`; it must be
  red against the scenario as it is. Then make C08 and C06 await the result in the window's own player slots before closing, as
  C-D02 does (own commit), and check C04, C05 and C07 for the same shape.

**Don't.**
- Add a sleep, a retry, raise a timeout, weaken or drop an assertion, or allowlist anything. Change the driver.

**Acceptance.**
- [x] The mechanism is shown by transcript lines of a red run, or named unexplained. (Ordering shown from a transcript, the DISAGREE
  not reproduced in 32 invocations: stated as such, ROADMAP "M8 findings" T-M8-23.)
- [x] The unit test is red before and green after. (`ConformanceCloseRaceTest`, 3 of 5 red before.)
- [x] `:conformance:run` on `fabric@26.3` and `neoforge@26.3`, three separate invocations each after the fix; a red is a finding,
  never replaced by a later green. (6 of 6 green.)
- [x] `bash scripts/gates.sh --max-workers=1` passes; the `integrationTest` of every module touched is run. (Below.)

**Verify.**
`./gradlew :proofstone-core:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=neoforge@26.3 --no-parallel --max-workers=1`

**Result (2026-11-16).** Diagnosed and fixed as the lead said, with the limit that no live DISAGREE was caught. Numbers and the
transcript ordering are in ROADMAP "M8 findings" (T-M8-23). C08 and `ConformanceFixture.placeCursorInInventory` (C05, C06, C07) now
await the result in the client's own slot state before the window closes. Before: 20 idle invocations (10 per row) and 12 under
CPU load, 0 DISAGREE, 1 unrelated red (`handleDisconnection() called twice` on a conformance client, T-M8-22's family, kept aside).
After: 6 of 6 green. `:proofstone-core:test` and `docsCheck` through `bash scripts/gates.sh --max-workers=1`: OK. `:proofstone-core:integrationTest`
(`PaperHostIT`, 4 tests, 0 skipped, 0 failures, 0 errors) green under the Docker lock. Not done: the red above is not diagnosed here.
