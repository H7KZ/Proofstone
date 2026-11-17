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

### T-M8-19 · Stop the image fetching Paper's default configs from an unpinned GitHub branch
- tier: sonnet
- depends: T-M8-23
- modules: proofstone-core, docs/platforms
- adrs: 0006, 0008, 0013
- specs: specs/versions-yml.md
- test-first: yes

**Goal.** A Paper or Folia cell boot makes no network fetch of `paper-global.yml`, `paper-world-defaults.yml`, `bukkit.yml` or
`spigot.yml` (or `paper.yml` below 1.19), with the mechanism shown by the image's own scripts and a container log, and the
cells still green. "Pin everything" (AGENTS.md section 3) has no exception for config files.

**Diagnosis (read from the pinned images, 2026-11-16).** In every one of the five pinned `itzg/minecraft-server` digests,
`start-deployPaper` (also used by `TYPE=FOLIA`) sets `PAPER_CONFIG_DEFAULTS_REPO` to
`https://raw.githubusercontent.com/Shonz1/minecraft-default-configs/main` (branch `main`, not a commit), builds
`<repo>/<VERSION>/paper-global.yml,paper-world-defaults.yml` into `DOWNLOAD_DEFAULT_CONFIGS` (1.19 and later) and
`<repo>/<VERSION>/bukkit.yml,spigot.yml[,paper.yml]` into `DOWNLOAD_DEFAULTS`. `start-setupEnvVariables` then runs
`mc-image-helper mcopy --skip-existing` for both lists unless `SKIP_DOWNLOAD_DEFAULTS` is true. A failed fetch is only
`logWarning "One or more default ... files were not available"` (and `--ignore-missing-sources`), so offline the boot still
succeeds, with the server's own generated defaults, and the files differ per day. `TYPE=PURPUR` fetches only when
`PURPUR_CONFIG_REPO` is set (it is not); `TYPE=CUSTOM` (Spigot) and Fabric/NeoForge fetch nothing. `ProxyHost` and the two proxy
ITs already set `SKIP_DOWNLOAD_DEFAULTS=true` (ADR-0016); `PaperHost.configure` does not.

**Do.**
- Test first: a unit test builds a `PaperHost` for a Paper, a Folia and a Spigot-jar cell without Docker and asserts the container
  env has `SKIP_DOWNLOAD_DEFAULTS=true` and no `PAPER_CONFIG_*` override. Red before.
- Set `SKIP_DOWNLOAD_DEFAULTS=true` in `PaperHost.configure`, with the why in a comment. No image change.
- Show by container log that the fetch no longer happens: the same boot with `raw.githubusercontent.com` resolving to 127.0.0.1
  (`--add-host`) logs the "not available" warning before and not after.
- Docs: paper.md Gotchas and versioning say what ran.

**Don't.**
- Change an image digest, build or rebuild an image, add a workflow, a dependency, a sleep, a retry, a timeout raise or an
  allowlist, weaken a test, edit the wire protocol. If SKIP turns out to need a new image, stop and report.

**Acceptance.**
- [x] The unit test is red before and green after. (`PaperHostEnvTest`, 3 of 3 red, then 3 of 3 green.)
- [x] Container log evidence of the fetch before and none after. (Below.)
- [x] `:conformance:smoke` three separate invocations and `:conformance:run -Dproofstone.cells=paper@26.3` three separate
  invocations, XML read, a red is a finding. (6 of 6 green, below.)
- [x] The `integrationTest` of every module touched is run; `bash scripts/gates.sh --max-workers=1` passes. (See Result; one pre-existing red, not caused by this card.)

**Verify.**
`./gradlew :proofstone-core:test --tests '*PaperHostEnvTest*'`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:smoke --no-parallel --max-workers=1`

**Result (2026-11-16).** Fixed with one env variable on the existing pinned image, no digest change: `PaperHost.configure` sets
`SKIP_DOWNLOAD_DEFAULTS=true` (`PaperHostEnvTest`: Paper, Folia and a built Spigot jar).
- Fetch evidence: `docker run` of the java25 digest (`TYPE=PAPER VERSION=26.3`) with `--add-host raw.githubusercontent.com:127.0.0.1`.
  Without the variable: two `mc-image-helper mcopy` ERRORs (`Connection refused: raw.githubusercontent.com/127.0.0.1:443`) and the
  WARNs `One or more default config files were not available from .../main/26.3/paper-global.yml,...paper-world-defaults.yml` and
  `... default files ... bukkit.yml,...spigot.yml`; the server still reached `Done`. With it: no such line, `Done (6.1s)`.
  One boot each, java25 image only; the other four digests carry the same script text (grepped), not booted.
- `:conformance:smoke` 3 separate invocations: 8 tests each, 0 skipped, 0 failures, 0 errors.
  `:conformance:run -Dproofstone.cells=paper@26.3` 3 separate invocations: all green; the only skips (2 per run) are the Folia-only
  tests, `missing capability FOLIA_REGIONS on cell paper@26.3`, with their reason.
- `:proofstone-core:integrationTest` (`PaperHostIT`, 4 tests) green; `topologyTest` (Velocity and BungeeCord suites) green except
  `TopologyFaultIT`, red in the full run (1 of 2 tests: `aBackendThatNeverBecomesReady...`, `container server-lobby did not become
  ready: ... Container startup failed`). Finding, not fixed here: with the fix, 3 reruns of `TopologyFaultIT` alone gave 2 green and 1
  red (same message); with the fix removed (baseline), 4 reruns gave 2 red (both tests failed, same `server-lobby ... Container
  startup failed`) and 2 green. So it exists without this change; cause not diagnosed (the lobby log tail ends at the known
  `No key layers in MapLike[{}]` ERROR a few seconds after start). Needs its own card.

### T-M8-24 · Diagnose the intermittent `Container startup failed` / `HostPortWaitStrategy` on Paper cells
- tier: sonnet
- depends: none
- modules: proofstone-core, docs/platforms
- adrs: 0005, 0016
- specs: specs/self-tests.md
- test-first: no

**Goal.** Name the mechanism of the red that went away on a rerun (`paper@1.21.11` bridged x5 and `metaTest` `paper@26.1.2` once
during the #138 verification; `TopologyFaultIT` red in 2 of 4 reruns on the baseline of T-M8-19 and 1 of 3 with its fix, `container
server-lobby did not become ready ... Container startup failed`), with counts per arm and log lines, and propose the fix card.
Diagnosis only: no code change.

**Evidence (2026-11-16).** Local, Windows 11, Docker Desktop (server 29.9.2, 20 cores, WSL2 `memory=16GB`), JDK 21, `origin/main` at
`3cde4e8`. One Gradle invocation per run under `scripts/dockerlock.sh`, `--no-parallel --max-workers=1`, EULA in the command
environment only. Per run: `docker events`, a read-only `docker logs -f -t` of every container of ours, a Windows CPU sample every
5 s, the JUnit XML and bundle copied aside (`C:\pw\t824-ev`, local, not committed). Reds are counted, never replaced.

| Arm | Task | Runs | Red runs | Red server starts |
|---|---|---|---|---|
| idle, bridged | `:conformance:run -Dproofstone.cells=paper@1.21.11` | 10 | **0** | 0 of 140 |
| idle, single server | `:conformance:run -Dproofstone.cells=paper@26.3` | 10 | **0** | 0 of 140 |
| idle, topology | `:proofstone-core:topologyTest --tests '*TopologyFaultIT*'` | 10 | **0** | 0 of 30 |
| loaded, bridged | the bridged task, with 20 busy shell loops running on the host | 10 | **3** (runs 3, 7, 9) | **10 of 140** |

The load is 20 `while :; do :; done` loops, started before the run and killed after it (run 1 of the loaded arm used 20 `yes`
processes instead; green). Each loop got about 0.3 of a core. The Windows `LoadPercentage` sample read lower in the loaded arm (mean
24%) than in the idle one (mean 42%), so it does not measure what the Docker VM got; the run time does: a bridged run took 7.7 to 9.2
minutes idle and 16.5 to 17.4 minutes loaded.

**Mechanism.** `PaperHost` sets no wait strategy, so Testcontainers (2.0.5) uses its default `HostPortWaitStrategy` with the
default `startupTimeout` of **60 s** (the `AbstractWaitStrategy` constructor, read with `javap`). On the exposed game port it runs in
the container, in a loop, `grep -i ':0*63dd' /proc/net/tcp* || nc ...` (the `exec_create` event right after every `start`; 63dd is
25565). Paper binds 25565 late in its boot, 1 to 3 s before `Done (`, so the 60 s is the whole boot: the image downloads the pinned
Paper jar, Paperclip downloads the Mojang jar and patches it, Paper remaps, loads the world. Offsets from the container's first log
line, medians over every server container:

| Stage | idle bridged (140) | idle single (140) | loaded bridged (140) |
|---|---|---|---|
| Paper jar downloaded | 5.3 s | 5.4 s | 21.8 s |
| `Starting org.bukkit.craftbukkit.Main` (after Paperclip) | 14.2 s | 14.8 s | 48.2 s |
| `Done remapping server` | 18.2 s | not logged | 52.0 s |
| `Starting Minecraft server on *:25565` (the bind) | 23.1 s (max 30.5) | 19.5 s (max 42.0) | 56.7 s (max 60.9) |

The two stages that fetch and patch (jar download 4x, Paperclip 3x) grow; the stages after them do not (remap 4.0 s idle, 3.8 s
loaded; remap to bind 4.9 s and 4.7 s). Idle, the worst bind of 280 server containers was 42 s (the very first run, which also built
the project). Loaded, 131 of 140 containers bound between 55.1 and 60.9 s; **9 never bound before the 60 s and a tenth bound 0.9 s
before its kill**. The failed containers by event time: `loaded-3` start 16:12:08, kill 16:13:09 (61 s); `loaded-7` start 17:32:11
kill 17:33:13 (62 s) and start 17:33:17 kill 17:34:18 (61 s); `loaded-9` seven more. Every log is still booting at the kill
(`Loaded 1584 advancements`, `Initialized 1 plugin`, `Enabling ProofstoneAgent`), and the XML failure is `paper@1.21.11 failed to
start: Container startup failed for image itzg/minecraft-server@sha256:04b5ee18...`, root `ContainerLaunchException at
HostPortWaitStrategy.java:112`. Only the Paper server container failed here. ViaProxy has its own 60 s `awaitListening`;
neither it nor a proxy failed.

The harness's own budgets are not the clock that fired: `PaperHostOptions` has `bootTimeout` 3 minutes for `Done (` and 30 s for the
agent. `LoaderHost` already replaces the default wait with a no-op for this reason (its comment: "a second timeout that would hide a
slow boot").

**Not shown.** The #138 and `TopologyFaultIT` reds themselves: they did not reproduce idle (0 of 10 topology runs, 0 of 20 bridged
and single runs) and no log of them was kept, so that they were this 60 s on a busy host is **inferred** from the identical message
and code path, not seen. What loaded the host then is unknown. Why 20 busy host loops slow the fetch and patch stages 3 to 4x and
not the remap is not diagnosed (Docker Desktop and WSL2 scheduling of the network path, or disk; not separated here). `metaTest`
`paper@26.1.2` was not run.

**Do.** Read the code, run the arms, write this and the proposed card below. No change to any source file.

**Don't.** Change code, add a sleep, a retry, a timeout raise or an allowlist, weaken an assertion, run in parallel.

**Acceptance.**
- [x] At least 10 runs per arm, red counts per arm, in the table above.
- [x] The failing container and its wait strategy named, with log lines and event times.
- [x] A fix card proposed (T-M8-25).
- [x] `bash scripts/gates.sh --max-workers=1` passes (docs only).

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M8-25 · One boot clock for Paper cells: no hidden 60 s port wait in `PaperHost`, `ViaProxyHost`, `ProxyHost`
- tier: sonnet
- depends: T-M8-24
- modules: proofstone-core, docs/platforms
- adrs: 0005, 0016
- specs: specs/self-tests.md
- test-first: yes

**Approved as option A only** (the maintainer's decision, T-M8-24 follow-up). Option B is not approved; it is proposed as T-M8-26 below, not started.

**Goal.** A slow but healthy Paper boot is judged by the harness's one documented budget (`bootTimeout` 3 minutes for `Done (`, then
the agent's 30 s), and a boot that really stalls fails with that message and the log tail, not as `Container startup failed` at
60 s. `LoaderHost.configure` already does this; the Paper, proxy and ViaProxy containers still use the default wait.

**Do.**
- Test first, no Docker: the container of a `PaperHost` built for a Paper cell does not wait on the port (assert the configured
  strategy). Red before.
- In `PaperHost.configure`, `ProxyHost.configure` and `ViaProxyHost`, replace the default wait with the no-op strategy of
  `LoaderHost` (share it as one package-private class), with the why in a comment. `awaitDone`, `awaitAgent`, `awaitListening` and
  the proxy's ready-line await stay as they are and keep failing fast when the container is not running.
- Show it: re-run the loaded arm of T-M8-24 (20 busy loops, `paper@1.21.11` bridged, 10 invocations). Expected: no
  `Container startup failed`; a boot that passes 60 s is green, a boot that passes 180 s fails as `timed out waiting for server log
  shows "Done ("`.

**Decision for the maintainer first.** (A) This card as written. (B) Also cut the per-boot fetch and patch work (about 14 s of 23 idle
and 48 s of 57 loaded: the Paper jar download and Paperclip's Mojang jar download and patch): copy a pre-patched jar in, which needs
a pinned artefact and an image or `versions.yml` change (ADR-0008, 0013). Bigger, and it changes what a cell runs; A does not.

**Don't.**
- Raise the 60 s (it keeps a second clock and only moves the failure), add a sleep, a retry or an allowlist, change a digest.
- Change the `bootTimeout` or `agentTimeout` values.

**Acceptance.**
- [x] The unit case is red before and green after. (`BootClockTest`, 3 cases: 3 of 3 failed before, 3 of 3 pass after.)
- [x] The loaded arm: 10 invocations, XML read, 0 `Container startup failed`; any other red is a finding. (10 of 10 green, 0 reds,
  ROADMAP "M8 findings" T-M8-25.)
- [x] Idle `:conformance:smoke` and `:conformance:run -Dproofstone.cells=paper@1.21.11`, three invocations each, green.
- [x] The `integrationTest` and `topologyTest` of every module touched (`proofstone-core`); `bash scripts/gates.sh --max-workers=1`
  passes.

**Verify.**
`./gradlew :proofstone-core:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :proofstone-core:topologyTest --tests '*TopologyFaultIT*' --no-parallel --max-workers=1`

### T-M8-26 · Cut the per-boot Paper jar fetch and patch work (proposed, not approved)
- tier: opus
- depends: T-M8-25
- modules: proofstone-core, docs/platforms
- adrs: 0005, 0008, 0013
- specs: specs/versions-yml.md
- test-first: yes

**Not approved; not started.** Option B of T-M8-25, recorded as a proposal only. For the maintainer to decide.

**Goal.** Cut the per-boot work that fetches and patches the server (T-M8-24: the Paper jar download and Paperclip's Mojang jar
download and patch, about 14 s of a 23 s idle bind and 48 s of 57 s loaded), so a boot is shorter and less sensitive to host load.

**Do.**
- Decide first whether a pre-patched jar is acceptable: it needs a pinned artefact and an image or `versions.yml` change, and it
  changes what a cell runs.
- If yes, copy the pinned pre-patched jar in (no bind mount) and verify its sha256 as `PaperHost` does today.

**Don't.**
- Start before the maintainer approves; change a digest or a pin without the ADR-0008 and ADR-0013 steps.

**Acceptance.**
- [ ] The maintainer's decision recorded.
- [ ] If approved: boot time before and after on the same host, idle and loaded, from logs.

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M8-27 · `LoaderHost` sets the deterministic-world rules `PaperHost` sets
- tier: sonnet
- depends: T-M8-18, T-M8-21
- modules: proofstone-core, examples/bad-tests, docs/platforms
- adrs: 0017
- specs: specs/self-tests.md §3
- test-first: yes

**Goal.** A loader cell's world rules are the same deterministic ones a Paper cell has (no random ticks, no daylight or weather
cycle, no mob spawning), so the leak detector's before-snapshot is the same on every platform and a loader test is not run in a
world that ticks, rains and spawns differently from Paper's.

**Evidence.** T-M8-18 (ROADMAP, M8 findings): a diagnosis run read `random_tick_speed` at the start of a Fabric test body and got the
vanilla default 3, because `PaperHost.boot` runs `GameRules.commands(mc)` and `LoaderHost.boot` runs nothing. `docs/robustness.md`
R6 (loader review) names the gap. T-M8-21 already shares one constant (`SEED`) between the two hosts, so the seed is not part of
this card and must not change.

**Do.**
- Test first, no Docker: one shared entry point for the setup (`GameRules.apply`, taking the console-command function), tested with
  a recording function for a camelCase and a snake_case version. Then the integration assertion, red before: on `fabric@26.3` and
  `neoforge@26.3` the host IT reads `gamerule random_tick_speed` after start and expects 0 (it reads 3 before).
- `PaperHost` and `LoaderHost` both call `GameRules.apply` after the readiness checks. Same constants, one list.
- `metaLeakFabric` must still give `LEAK`. Its test sets 7 over a baseline that is now 0: update the expected text in
  `examples/bad-tests/README.md` and the comments in `LoaderLeakE2E` to the value the run shows, with the run as evidence.
- Documentation in the same PR: `docs/robustness.md` R6 (loader review row), the Fabric and NeoForge Gotchas, ROADMAP finding.

**Don't.**
- Move the world spawn (`setworldspawn`) to loaders: `PaperHost` sets it for the 1.8.8 flat world, a loader keeps the vanilla spawn.
  Change the seed, `DIFFICULTY` or `GENERATE_STRUCTURES`. Add a switch to skip the rules, a sleep, a retry, a timeout change.

**Acceptance.**
- [x] The unit cases fail before (no `GameRules.apply`) and pass after.
- [x] The host IT assertion is red on `fabric@26.3` and `neoforge@26.3` before the change (one run each), green after, three
  separate invocations each, XML read.
- [x] The world rules snapshot is the same in three separate boots of each row (the four rules read back, from the bundles or logs).
- [x] `metaLeakFabric` is `LEAK` naming `random_tick_speed`, three invocations; its README text matches the run. `:conformance:run` on
  `fabric@26.3` and `neoforge@26.3` stays green, three invocations each.
- [x] `integrationTest` of every touched module (`proofstone-core`, `platforms/fabric/agent`, `platforms/neoforge/agent`), XML read;
  `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`./gradlew :proofstone-core:test --tests '*GameRulesTest*'`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaModFabricTest --no-parallel --max-workers=1`

### T-M8-28 · Decide whether `metaLeakFabric` joins the release mutation check
- tier: sonnet
- depends: T-M8-18
- modules: scripts, examples/bad-tests, docs
- adrs: 0003
- specs: specs/self-tests.md §5
- test-first: yes

**Goal.** Answer the question T-M8-18 left open ("ask in the PR whether it joins the mutation check") from how the check's set is
defined, not from taste.

**Do.**
- Read `scripts/release-mutation-check.sh`, `scripts/lib/mutation-gate.sh`, `scripts/release-gate-test.sh` and
  `examples/bad-tests/README.md`.
- If the set is a plain list of bad tests: add `metaLeakFabric`, the gate test updated first (red before, green after). If it is
  not: change no code and record the finding.

**Don't.**
- Run `release-maven.sh --publish` or `release-images.sh --push`, set `PROOFSTONE_RELEASE_CONFIRM`, or run the full
  `release-mutation-check.sh` (it is long). Widen the set to make a bad test fit it.

**Acceptance.**
- [x] The decision is recorded with the code lines it rests on, in the README and the ROADMAP findings.
- [x] `bash scripts/release-gate-test.sh` and `bash scripts/gates.sh --max-workers=1` pass; no release script was run beyond that test.

**Verify.**
`bash scripts/release-gate-test.sh`
