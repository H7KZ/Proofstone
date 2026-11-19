# M7 follow-ups (diagnosis cards)

Written 2026-11-14 from the M7 review (T-M7-99, ROADMAP "M7 review", known open items 1, 2 and 7). Two single-server findings
older than M7 that the review could not explain. **Diagnosis only:** a card here finds a cause and names a fix; the fix is a
separate card. Neither card is started.

**Update (T-M7-15, 2026-11-15):** T-M7-15 found its cause and proposes the fix card **T-M7-17** at the end of this file (not
started, needs approval).

**Update (T-M7-18, 2026-11-15):** T-M7-18 found the cause of the first-login Purpur WARN and proposed the fix card **T-M7-19**
at the end of this file (done, see ROADMAP "T-M7-19").

IDs continue M7-proxies.md (T-M7-01 to T-M7-14, T-M7-99), so the next free IDs were T-M7-15 and T-M7-16. They do not block M8.

**Flake rule.** Read the **Flake rule** in [M7-proxies.md](M7-proxies.md); it applies unchanged. In short: one cell per
invocation under `scripts/dockerlock.sh`, `--no-parallel --max-workers=1`, XML read, a red is a finding that is never retried to
green, and its XML, bundle and container logs are copied aside **before the next run**.

### T-M7-15 · Diagnose `PSConf moved too quickly!` in the single-server smoke (c02 red on folia, purpur, spigot @26.3)
- tier: opus
- depends: T-M7-99
- modules: proofstone-core, conformance
- adrs: 0003, 0007
- specs: specs/self-tests.md §1
- test-first: yes

**Goal.** Name the cause of `ErrorLogGuard` WARN `PSConf moved too quickly! 0.5,64.0,0.5` on `c02`, so the next card can fix it
without an allowlist.

**Evidence so far (ROADMAP "M7 review", item 7).** The full four-cell `:conformance:smoke` (paper, folia, purpur, spigot, all
@26.3) was red at `9192c60`: tests 8, failures 3, errors 0, skipped 0. `c02` failed on folia, purpur and spigot @26.3 with that
WARN; paper@26.3 c01 and c02 were green. Second full smoke red with the same three cells ("M6 pre-release findings" item 2); the
M5 review's full smoke was green. 2 red of 3 recorded full smoke runs. The guard allowlists `T<hex>` names (the topology client
was renamed for the same WARN in T-M7-12, 2 reds then); the single-server conformance client is still `PSConf`. **Cause unknown.**
The bundles of the last red were deleted by the next `:conformance:run` before they were copied, so no log from it exists.
**Update (M8 review, 2026-11-15):** a third red, same three cells (3 red of 4 full smoke runs), bundles kept. Lead from the folia
bundle: the class's first `PSConf` join spawns at the world spawn, the fixture teleports it to `0.5, 64.0, 0.5`, and the next
`PSConf` join logs in at that saved position (the player file persisted between tests); the WARN fires on that login (ROADMAP
"M8 review", Paper and topology numbers).

**Do.**
- Before any run, copy `build/proofstone/` and `build/test-results/` aside to a dated folder outside the repo. `:conformance:run`
  deletes the bundles of the previous run.
- Reproduce with the smoke as it failed (no selector, all four cells), then per cell (`-Dproofstone.cells=folia@26.3`, `purpur@26.3`,
  `spigot@26.3`, `paper@26.3`). Run each cell the same number of times (at least 5) and record red or green per cell per run. A red
  is kept, never replaced by a later green.
- From each red bundle read the server log around the WARN: which step of the c02 scenario moves `PSConf` (reset teleport,
  spawn, join), the server tick and thread, and whether a Paper-only setting (`moved-too-quickly` thresholds, `paper-global.yml`)
  differs on Paper versus Folia, Purpur and Spigot. Compare with the topology case fixed in T-M7-12.
- State the cause as a mechanism backed by log lines, or state that it is still unexplained with the counts. Write the finding
  into ROADMAP (a new "findings" entry) and propose the fix card (reset race in the harness, an ordering await, or a documented
  platform gotcha in `docs/platforms/<platform>.md`).

**Don't.**
- Add `PSConf` or `moved too quickly` to the allowlist, or any blanket pattern. Rename the client to `T<hex>` as the diagnosis.
  Raise a timeout, add a sleep or a retry, or edit the guard or conformance to turn the red green.
- Change the wire protocol, `versions.yml`, or any harness code in this card.

**Acceptance.**
- [x] Per-cell red counts over at least 5 runs each are in the ROADMAP entry, with the XML read (`skipped`, `failures`, `errors`).
  Evidence: ROADMAP "M7 follow-up findings (T-M7-15)": full smoke 5 of 5 red (folia, purpur, spigot c02), each cell alone 0 of 5.
- [x] The cause is named with log lines, or the entry says "unexplained" with the counts and what was ruled out. Evidence: the
  shared host is keyed by mc only, so the full smoke runs all four cells on one Paper server and `PSConf` logs in again at its
  saved spot (`Resolving type given PAPER` in every bundle, `"platform":"paper"` in every health reply).
- [x] A follow-up fix card is proposed (or the card says why none is needed). No code changed. Evidence: T-M7-17 below.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:smoke --no-parallel --max-workers=1`

### T-M7-16 · Reproduce and diagnose the spigot@26.3 C04 timeout seen once
- tier: sonnet
- depends: T-M7-99
- modules: proofstone-core, conformance
- adrs: 0007, 0015
- specs: specs/self-tests.md §1, platforms/spigot.md
- test-first: yes

**Goal.** Decide whether the `C04` timeout an agent saw once on `spigot@26.3` is a real defect or a one-off, from counted runs.

**Evidence so far (ROADMAP "M7 review", known open item 1).** An agent saw a `C04` timeout on `spigot@26.3` once. No XML, bundle
or stage message was kept, so its verdict type and stalled stage cannot be named. The review ran `:conformance:run
-Dproofstone.cells=spigot@26.3` once: 12 tests, failures 0, errors 0, 3 reasoned skips (missing `SIMULATED_BEDROCK`,
`FOLIA_REGIONS` x2), C03 to C08 passed, 0 join timeouts, 5 m 38 s. Counted as 1 red of 2 known runs since the report. The join
timeout family on single-server cells is bounded but unexplained (ROADMAP "M6 review addendum"; T-M6-14 names the stalled stage).

**Do.**
- The Spigot jar is built by BuildTools and cached by digest (`docs/platforms/spigot.md`): confirm the cache holds the pinned
  `sha256` first, so a rebuild is not part of the run.
- Run `:conformance:run -Dproofstone.cells=spigot@26.3` 10 times as separate invocations, as the M5 follow-up did for C06. Record
  per run: the XML counts, the C04 result, and for any red the verdict type and the T-M6-14 stalled stage. Copy a red's XML,
  bundle and server log aside before the next run.
- Report the count (for example "k of 10 C04 red") with the join-timeout numbers kept apart from M6's. If there is no red, say
  "not reproduced, 0 of 10" and keep the item open as unexplained, not closed.
- If there is a red, state the stage, read the server log, and propose a fix card. Do not fix it here.

**Don't.**
- Rerun a red to look for a green, raise a timeout, add a retry or a sleep, or demote or edit the `spigot@26.3` row. Change
  harness code.
- Take on the root-owned `bukkit.yml` and `spigot.yml` copy here: that defect (`Could not save bukkit.yml (Permission denied)`,
  M7 review known open item 2) is already carded as **T-M8-02** in [M8-mods.md](M8-mods.md), whose Do covers `PaperHost` and
  `ProxyHost` copy ownership and a start check. This card only notes whether the log of any red shows that line, as a possible
  contributor, and does not fix it. If T-M8-02 is dropped from M8, open a new card for it.

**Acceptance.**
- [x] 10 separate invocations, each reported with its XML counts (`skipped`, `failures`, `errors`) and C04 result. Evidence:
  ROADMAP "T-M7-16 result". Every run: 17 tests, skipped 3, failures 6, errors 0, C04 `skipped="0" failures="0" errors="0"`.
  The 6 failures are the same in all 10 runs and are not C04 (see the result).
- [x] Every red has its verdict type, stalled stage and kept log, or the report says 0 of 10 red with the item still open. Evidence:
  **not reproduced, 0 of 10 C04 red**, 0 join timeouts, 0 `PSConf moved too quickly`; the item stays open as unexplained.
- [x] A fix card is proposed, or the card says why none is needed. No code changed. Evidence: none needed for C04 (no red to
  explain). The related join-stall diagnosis is carded as **T-M6-109** in [M6-dogfood-release.md](M6-dogfood-release.md).

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=spigot@26.3 --no-parallel --max-workers=1`

### T-M7-17 · Boot one server per cell in a PER_CLASS class: key the shared host by the full cell, not the mc version
- tier: sonnet
- depends: T-M7-15
- modules: proofstone-core, conformance
- adrs: 0003, 0007
- specs: specs/self-tests.md §1, specs/test-api.md
- test-first: yes

**Goal.** Every cell of a class runs on its own platform's server. Today `CellInvocation.resource` keys the class-shared host
by `"host:" + cell.mc()`, so cells of one mc version share the first cell's server; that is the cause of the `PSConf moved too
quickly!` red found in T-M7-15 (ROADMAP "M7 follow-up findings (T-M7-15)").

**Do.**
- Test first, no Docker: a unit test that two cells of one mc version and different platforms (`paper@26.3`, `folia@26.3`) in
  one `PER_CLASS` class get two host keys, and that one cell's two tests get one key. Extract the key into a small
  package-private function to test it; it fails on main.
- Test first, real server: C01 in `ConformanceSmokeTest` also checks that the server is the cell's platform. It fails on main
  in the full smoke (the `folia@26.3` agent's `/v1/health` answers `"platform":"paper"`). `Agent` has no platform getter, so
  either compare the health platform where the harness already reads it (`PaperHost` readiness) and fail the class on a
  mismatch, or assert the Bukkit `version` command reply names the platform; record which and why. No API or protocol change.
- Key the host by the full cell identity (platform, mc, and anything else that changes the boot, as `TopologyInvocation` does
  with `cell.identity()`). Check `differentialKey` (`class.method@mc`) for the same collision and key it by the cell too.
- Run the full smoke with no selector (all four cells) 5 times as separate invocations and each cell alone once; record XML
  counts and that each bundle's `server.log` shows its own `Resolving type given <PLATFORM>`.
- Update the `PSConf` entries in `docs/platforms/folia.md` and `docs/user/troubleshooting.md` (cause known, fixed).

**Don't.**
- Allowlist `PSConf` or `moved too quickly`, rename the conformance client, add a sleep, a retry or a timeout raise.
- Change the wire protocol or `versions.yml`.
- Clean player data between tests as the fix: the server must not be shared across cells at all; per-test player cleanup would
  hide the shared server.

**Acceptance.**
- [x] The unit test and the C01 platform assertion fail on main and pass with the fix (both runs recorded in the PR).
- [ ] Full four-cell `:conformance:smoke`, 5 separate invocations: each `tests 8, skipped 0, failures 0, errors 0`, four
  distinct server types in the bundles' logs, 0 `PSConf moved too quickly!`. **Not met as stated:** 7 invocations, 6 green, 1 red
  (a first-login `PSConf` WARN on a fresh Purpur server), ROADMAP "T-M7-17 host key per cell".
- [x] `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:smoke --no-parallel --max-workers=1`

### T-M7-18 · Diagnose `PSConf moved too quickly!` on the first login of a fresh Purpur server
- tier: opus
- depends: T-M7-17
- modules: proofstone-core, conformance
- adrs: 0003, 0007
- specs: specs/self-tests.md §1
- test-first: no

**Goal.** Name the cause of the `ErrorLogGuard` WARN `PSConf moved too quickly! -4.5,-60.0,4.5` that failed `c02` on
`purpur@26.3` in 1 of 8 real smoke runs after T-M7-17 (ROADMAP "T-M7-17 host key per cell"), so the next card can fix it
without an allowlist. Diagnosis only, so test-first does not apply.

**Evidence so far.** On Purpur's own fresh server (`Resolving type given PURPUR`, no shared server, no saved player file) `PSConf`
logged in at `-4.5, -60.0, 4.5` and in the same millisecond got the WARN, before any fixture teleport. paper, folia and spigot
@26.3: 0 of 8. The harness `T<hex>` clients log the same WARN (allowlisted) on Paper and Purpur.

**Do.**
- Copy `build/proofstone/` and `build/test-results/` (and `docker logs` of every container) aside before each next run.
- Reproduce: `:conformance:smoke -Dproofstone.cells=purpur@26.3`, at least 10 separate invocations, and the full four-cell smoke
  at least 5 times. Record red or green per run with the XML read (`skipped`, `failures`, `errors`). A red is kept.
- From every red bundle and the container logs of the greens: the login position, the WARN's numbers, Purpur settings that
  touch movement or spawn (`purpur.yml`, `spigot.yml` `moved-too-quickly-multiplier`), timing against the server tick, and why a
  first login at a superflat spawn (`y -60`) WARNs. Check correlation with boot warmness, port reuse and world seed.
- Test the T-M8-16 lead (loader cells set no `SEED`, Paper pins one): does a fixed `SEED` or `LEVEL_TYPE` change the spawn?
- State the cause as a mechanism backed by log lines, or "unexplained" with counts and what was ruled out. Add a ROADMAP
  findings entry and propose the fix card T-M7-19 here.

**Don't.**
- Change harness code, the guard, an allowlist or `versions.yml` in this card. Throwaway experiments stay outside the repo or
  under `scripts/spike/`, labelled.
- Allowlist `PSConf`, rename the client, add a sleep, a retry or a timeout raise, as the diagnosis or as the proposed fix.

**Acceptance.**
- [x] At least 10 purpur-alone and 5 full smoke invocations recorded with XML counts in the ROADMAP entry; reds kept. Evidence:
  ROADMAP "M7 follow-up findings (T-M7-18)": purpur alone 0 of 10 red, full smoke 1 of 5 red (purpur c02).
- [x] The cause is named with log lines, or the entry says "unexplained" with the counts and what was ruled out. Evidence: the
  join-teleport acknowledgement is handled before the player's first tick, so `firstGood` is `0, 0, 0` and the WARN numbers equal
  the login position (21 of 21); spike: immediate acknowledgement WARNs 9 of 10 joins, after-first-tick 0 of 10, Paper and Purpur.
- [x] The fix card T-M7-19 is proposed (or the card says why none is needed). No harness code changed. Evidence: T-M7-19 below.
- [x] `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:smoke -Dproofstone.cells=purpur@26.3 --no-parallel --max-workers=1`

### T-M7-19 · Acknowledge the join teleport after the server's first player tick (mcpl driver)
- tier: sonnet
- depends: T-M7-18
- modules: drivers/mcpl, proofstone-core
- adrs: 0003
- specs: specs/client-driver.md
- test-first: yes

**Scope change (orchestrator, 2026-11-15).** The hold applies to native cells only: `ConnectSpec` gains a `bridged` flag (small core change,
approved for this card), set from `Cell.bridged()`; a bridged join acknowledges at once, because bridged 1.8.x rows send no first
`SetHealth` before the acknowledgement and deadlocked (ROADMAP "T-M7-19").

**Goal.** No harness client triggers `moved too quickly!` on its join, so `PSConf` never fails c02 and the `T<hex>` allowlist
entries for this WARN can go. Cause (T-M7-18, ROADMAP "M7 follow-up findings (T-M7-18)"): Paper and Purpur handle the 26.3
accept-teleport position before the player's first tick, when `firstGood` is still `0, 0, 0`.

**Do.**
- Test first, no Docker: a `McplSession` unit test that the first `ClientboundPlayerPositionPacket` after a login is acknowledged
  only once the first `ClientboundSetHealthPacket` has arrived (fails on main), and that later positions are acknowledged at once.
- In `McplSession.applyPosition`, hold the join acknowledgement (and `ServerboundPlayerLoadedPacket`) until the first
  `ClientboundSetHealthPacket` of that login; acknowledge the newest held position. Event-driven on the netty thread: no sleep,
  no timer. Reset on a proxy switch (a new `ClientboundLoginPacket`). Note the deviation from the vanilla client in a comment.
- Check the bridged path (ViaProxy, older rows) and the loader rows: record which rows send the health packet on the first tick;
  a row that does not must fail loudly (join timeout naming the stage), never hang silently.
- Run `:conformance:smoke` purpur@26.3 alone 10 times and the full smoke 5 times: 0 `moved too quickly!` lines for any harness
  client (container logs), XML read. Then, in its own commit, drop the `^T[0-9a-f]{8} moved too quickly! ` entries that no
  longer fire (Paper, Folia, Purpur, Spigot packs, `pack.yml` mirrors) and re-run; keep any entry a run still needs, with its reason
  corrected (the WARN is on the join, not the reset teleport).
- Optional, separate: report the missing `tickCount == 0` reset in `handleAcceptTeleportPacket` to Paper (with the spike).

**Don't.**
- Allowlist `PSConf`, rename the client, add a sleep, a retry or a timeout raise.
- Change the wire protocol or `versions.yml`.
- Change the world (spawn height, level type) to make the move small: the race would stay.

**Acceptance.**
- [x] The unit test fails on main and passes with the change. Evidence: `McplJoinAckTest`; with stubs on main's behaviour 4 of the
  first 5 tests failed, `:drivers:mcpl:test` passes (68 tests).
- [x] purpur@26.3 alone 10 of 10 and the full smoke 5 of 5 green, XML `skipped 0, failures 0, errors 0`, 0 `moved too quickly!`
  for harness clients in the container logs. Evidence: ROADMAP "T-M7-19" (all four server types in each full run's logs).
- [x] Allowlist entries that no longer fire are removed, or kept with a corrected reason and the run that needs them. Evidence: the
  four `T<hex>` entries and the pack.yml mirrors are gone; velocity x3, bungeecord x3, fabric, neoforge (1 red C08 DISAGREE, also red
  on main 1 of 3) and each single cell ran. The `.`-prefixed entry stays (not this WARN).
- [x] `bash scripts/gates.sh --max-workers=1` passes. Evidence: PR body.
- [x] Bridged rows are unchanged: `ConnectSpec.bridged` skips the hold; a `McplSession` unit test shows a bridged join acknowledges
  at once and a native one holds. Evidence: ROADMAP "T-M7-19" (bridged re-runs). Open: `:conformance:run paper@1.8.8` C04 red 3 of 6 vs 0 of 6 on main, smoke 10 of 10 on both, unexplained (same ROADMAP entry).

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:smoke -Dproofstone.cells=purpur@26.3 --no-parallel --max-workers=1`

### T-M7-20 · Diagnose the bridged `paper@1.8.8` window and cursor flake in C03 to C05
- tier: opus
- depends: T-M7-19
- modules: drivers/mcpl, conformance
- adrs: 0003, 0007
- specs: specs/client-driver.md, specs/self-tests.md §1
- test-first: no

**Goal.** Name why `:conformance:run -Dproofstone.cells=paper@1.8.8` (bridged through ViaProxy) is red in some runs, with C04 "cursor
after picking up slot A", C05 "renamed sword on the cursor" null, and C03 and C05 INVALID after a C04 failure (ROADMAP "T-M7-19",
verifier run: main 2 of 5 red, PR 3 of 5; combined 2 of 11 and 6 of 11).

**Do.**
- At least 10 separate invocations per arm (main and the T-M7-19 merge), same machine and load, copying XML, bundles and container
  logs aside before each next run. Record red or green per run and which class and assertion failed. A red is kept.
- From red bundles compare the window packets (open window, set slot, window items, cursor slot) of a red and a green run: does the
  cursor update (slot -1) arrive late, never, or before the click is answered; is the driver reading the cursor before the
  server's answer arrives (a race in the harness, in the driver, or in ViaProxy's 1.8 translation)?
- State the cause as a mechanism backed by packet or log lines, or "unexplained" with counts and what was ruled out. Propose a fix
  card (an await for the cursor state, a driver fix, or a documented ViaProxy gotcha in `docs/platforms/viaproxy.md`).

**Don't.**
- Add a sleep, a retry or a timeout raise, weaken a C03 to C05 assertion, or change harness code in this card.

**Acceptance.**
- [x] Per-arm red counts over at least 10 runs each, XML read, in a ROADMAP entry. Evidence: ROADMAP "T-M7-20", main 3 of 10, before T-M7-19 2 of 10.
- [x] The cause is named with packet or log lines, or marked unexplained with counts and what was ruled out. Evidence: ROADMAP "T-M7-20".
- [x] A fix card is proposed. No code changed. Evidence: T-M7-21 below.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=paper@1.8.8 --no-parallel --max-workers=1`


**Result (2026-11-16).** Red on both arms, 3 of 10 (main) and 2 of 10 (before T-M7-19), so the flake predates T-M7-19 and survives
T-M8-23. Mechanism: `McplSession.click` returns on the `ContainerSetContent`, whose carried stack is empty on 1.8.8, and the real
cursor is a separate `SetCursorItem` 0 to 9 ms later; a pick-up read in C04 or C05 in that gap sees an empty cursor. Details and packet
lines in ROADMAP "T-M7-20".

### T-M7-21 · Await the cursor after a pick-up click on bridged pre-1.17 rows (C04, C05)
- tier: sonnet
- depends: T-M7-20
- modules: proofstone-core, conformance
- adrs: 0003, 0007
- specs: specs/self-tests.md §1
- test-first: yes

**Goal.** C04 "cursor after picking up slot A" and C05 "the renamed sword on the cursor" stop going red on bridged `paper@1.8.8`
(about 3 in 10 full runs, ROADMAP "T-M7-20").

**Do.**
- Test first, no Docker (the `ConformanceCloseRaceTest` style): a fake window whose cursor update arrives after the click returns; the
  C04 and C05 pick-up read is red before and green after.
- In C04 and C05 await the cursor with the `Await` helper and the 10 s client budget, then assert with the same message and
  expectation. Not a sleep, not a retry, not a looser assertion: a cursor that never shows still fails at the timeout.
- Weigh in the PR a driver-side alternative: `McplSession.click` on pre-1.17 bridged rows also waits for the cursor packet. It has no
  marker for it (the cursor is the last packet of the answer), so the scenario await is the default.
- Separate commit: keep the conformance client's driver transcript in the bundle when the cell conformance fails (today the INVALID
  tests' bundles hold 0 lines and the failing client has no packet record).
- Run `:conformance:run -Dproofstone.cells=paper@1.8.8` 10 times, XML read, reds kept.

**Don't.**
- Weaken C03 to C05, raise a timeout, add a sleep or a retry, or change the wire protocol.

**Acceptance.**
- [x] The unit cases fail before and pass after. Evidence: `ConformanceCursorRaceTest` before the fix, C04 "cursor after picking up slot A" and C05 "the renamed sword on the cursor" red, the never-shows case red with the wrong exception (3 of 4 tests failed); after, 4 of 4 pass, `skipped="0"`.
- [x] 10 of 10 `:conformance:run` paper@1.8.8 runs show no cursor red (join stalls counted apart), XML `skipped` only the known skips. Evidence: each run `tests` 17, `skipped` 10, `failures` 0, `errors` 0; the 10 skips are the same in run 1 and every run (C07 no grindstone on 1.8.8, missing capabilities), no join stall seen. The flake rate was 3 in 10 and 2 in 10 before, so 10 green runs are consistent with the fix, not a proof (a 25% rate gives 10 greens about 6% of the time). ROADMAP "T-M7-21".
- [x] `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=paper@1.8.8 --no-parallel --max-workers=1`
