# M7 follow-ups (diagnosis cards)

Written 2026-11-14 from the M7 review (T-M7-99, ROADMAP "M7 review", known open items 1, 2 and 7). Two single-server findings
older than M7 that the review could not explain. **Diagnosis only:** a card here finds a cause and names a fix; the fix is a
separate card. Neither card is started.

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
- [ ] Per-cell red counts over at least 5 runs each are in the ROADMAP entry, with the XML read (`skipped`, `failures`, `errors`).
- [ ] The cause is named with log lines, or the entry says "unexplained" with the counts and what was ruled out.
- [ ] A follow-up fix card is proposed (or the card says why none is needed). No code changed.

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
- [ ] 10 separate invocations, each reported with its XML counts (`skipped`, `failures`, `errors`) and C04 result.
- [ ] Every red has its verdict type, stalled stage and kept log, or the report says 0 of 10 red with the item still open.
- [ ] A fix card is proposed, or the card says why none is needed. No code changed.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=spigot@26.3 --no-parallel --max-workers=1`
