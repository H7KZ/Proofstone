# M12: follow-ups from the Disenchantment e2e rows and the 2026-11-24 ledger

Written 2026-11-24. One card per open item from the ledger update in [ROADMAP.md](../ROADMAP.md). None started. The **Flake rule** in
[M8-mods.md](M8-mods.md) applies to every real-server acceptance: three separate invocations, a red is a finding, no retry.

### T-M12-01 · McplDriver: a fast server disconnect must not hide the join-stall stage
- tier: sonnet
- depends: T-M6-109
- modules: drivers/mcpl
- adrs: 0004
- specs: specs/client-driver.md §1
- test-first: yes

**Goal.** When the server closes the connection while a join is stalled, the `JoinTimeout` still names the stage it stalled at
(`CONFIGURATION_STALLED`, `LOGIN_STALLED`) instead of a generic disconnect.

**Evidence.** Bridged and native join stalls (T-M6-109; also paper@1.18.2 and paper@1.20.6 on 2026-11-24) are only classifiable
while the driver is still waiting at the 30 s limit. A server that kicks first loses the stage.

**Do.**
- Unit test: a fake server that completes login, sends no configuration packets and then closes. The failure must carry the stage.
- Record the last stage reached in the session and use it in the disconnect failure.

**Don't.** Retry a join, lengthen a timeout, change the public API or the wire protocol.

**Acceptance.**
- [ ] The new unit test fails before the change and passes after; XML read.
- [ ] C02 join loop green on `paper@26.3` in three separate invocations, `skipped="0" failures="0" errors="0"`.
- [ ] `bash scripts/gates.sh --max-workers=1` green.

**Verify.** `bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@26.3 --no-parallel --max-workers=1`

### T-M12-02 · Explain the ViaProxy 1.8.8 bridged join stall
- tier: opus
- depends: T-M9-14
- modules: docs/platforms
- adrs: 0006
- specs: specs/versions-yml.md
- test-first: no

**Goal.** Find the mechanism of the bridged `paper@1.8.8` stall (`LoginFinished` acked, no configuration packets), or record
what was ruled out.

**Evidence.** 2 of 530 joins on ViaProxy 3.4.15 (ROADMAP "T-M6-109 differential runs"); mechanism unproven.

**Do.**
- Capture both sides of a stalled join (proxy debug log, Paper packet log) in a counted loop and compare with a clean join.
- Write the result in `docs/platforms/viaproxy.md` "Gotchas": mechanism, or the list of causes excluded and the counts.

**Don't.** Change `versions.yml`, a pin, core flags or the driver in this card.

**Acceptance.**
- [ ] `docs/platforms/viaproxy.md` has every run as invocation, count k of n and result.
- [ ] `bash scripts/gates.sh --max-workers=1` green.

**Verify.** `bash scripts/gates.sh --max-workers=1`; reviewer re-runs one documented loop under `scripts/dockerlock.sh`.

### T-M12-03 · `datapacks/list` fails with "zip file closed" on paper@1.18.2 after reload cases
- tier: sonnet
- depends: —
- modules: agents/bukkit
- adrs: 0002
- specs: specs/agent-protocol.md §2
- test-first: yes

**Goal.** The datapack list endpoint answers after a plugin or datapack reload on paper@1.18.2.

**Evidence.** Disenchantment e2e on paper@1.18.2: the list failed with a closed zip file after reload cases. The agent holds a
`ZipFile` the server closed on reload.

**Do.**
- Reproduce with a conformance case that reloads datapacks and then lists them on paper@1.18.2.
- Open the pack archive per request and close it; never cache an open handle across a reload.

**Don't.** Change the protocol schema, add a sleep or retry, or interfere with the server (agents only observe).

**Acceptance.**
- [ ] The new case is red before and green after on `paper@1.18.2`, three separate invocations, XML read.
- [ ] `bash scripts/gates.sh --max-workers=1` green; independent sonnet review of the agent change.

**Verify.** `bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@1.18.2 --no-parallel --max-workers=1`

### T-M12-04 · `patchConfig` restore must reach the target plugin
- tier: sonnet
- depends: —
- modules: agents/bukkit, proofstone-core
- adrs: 0002
- specs: specs/test-api.md §3
- test-first: yes

**Goal.** After a test patches a plugin's config, the end-of-test restore puts the plugin's live configuration back, not only the
file.

**Evidence.** Disenchantment suite: a restore wrote the original file but the plugin kept the patched values until reload.
Hosted flake report on master (2026-11-24, `-Dproofstone.repeat=5`, one server per cell): on every cell that ran the whole suite
(paper 1.18.2, 1.20.4, 1.20.6, 1.21.4, 1.21.7, 1.21.11, 26.3 and folia 26.3) `DisabledWorldE2E` was green in repeat 1 and red in repeats 2 to 5
("vanilla offers nothing for a sword and a blank book", line 65; on 1.20.6 repeat 2 was green and repeat 1 was a join stall). Single
invocations of the same test are green. Cause not proven; leaked patched or restored config, or a result that arrives after the empty read
(a negative read cannot be awaited), between repeats are the working hypotheses.

**Do.**
- Conformance case: patch, run the test, check the next test sees the original values through the plugin.
- Make the restore trigger the same reload path the patch used.

**Don't.** Change the public API or protocol; add a sleep or retry.

**Acceptance.**
- [ ] New case green on `paper@26.3` and `paper@1.18.2`, three separate invocations each, XML read.
- [ ] `bash scripts/gates.sh --max-workers=1` green; independent sonnet review.

**Verify.** `bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@26.3 --no-parallel --max-workers=1`

### T-M12-05 · `release.yml`: prefetch dependencies before the release build
- tier: sonnet
- depends: —
- modules: .github/workflows, scripts
- adrs: 0019
- specs: —
- test-first: no

**Goal.** A transient failure of an external Maven repository (repo.opencollab.dev, 2026-11-24) cannot fail a release build or a
consumer e2e build midway.

**Evidence.** Disenchantment e2e Build and the 1.20.4 cell failed with `Could not find artifact ... text-serializer-nbt:jar:0.2.1`
from opencollab; the same artifact resolved on the rerun. Needs maintainer approval (workflow change, ADR-0019).

**Do.**
- Propose a dependency-prefetch step with the failure reported as a failure (no retry loop that hides red): resolve once, fail loudly.
- Evaluate a pinned mirror of the one artifact set; write the options in the PR.

**Don't.** Change a workflow without the maintainer's approval; add retries that turn red into green.

**Acceptance.**
- [ ] Maintainer approval recorded in the PR before any workflow edit.
- [ ] `bash scripts/gates.sh` green.

**Verify.** See the PR body.

### T-M12-06 · Cover the server banner, library loading and plugin list in `bootLogLines`
- tier: sonnet
- depends: T-M11-03
- modules: agents/bukkit, proofstone-core
- adrs: 0002
- specs: specs/agent-protocol.md §2
- test-first: yes

**Goal.** `Agent.bootLogLines()` includes the lines logged before the agent enables, or the docs say plainly which lines are
missing and a test pins the boundary.

**Evidence.** ROADMAP T-M11-03 row: not covered are the banner, library loading, the plugin loading list and the agent's own
Enabling line.

**Do.**
- Find a source that does not interfere (the server's `latest.log` read after boot) and merge it before the live buffer, or
  document the boundary in `specs/test-api.md` and add a test that fails if it moves.

**Don't.** Change the wire protocol without a card that says so; let the agent change server behaviour.

**Acceptance.**
- [ ] Conformance case green on `paper@26.3`, three separate invocations, XML read.
- [ ] `bash scripts/gates.sh --max-workers=1` green; independent sonnet review.

**Verify.** `bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run -Dproofstone.cells=paper@26.3 --no-parallel --max-workers=1`

### T-M12-07 · `ItemView.pdc` `getKeys` on Bukkit 1.14 to 1.17
- tier: sonnet
- depends: T-M11-05
- modules: agents/bukkit
- adrs: 0002
- specs: specs/test-api.md §3
- test-first: yes

**Goal.** Listing the persistent-data keys of an item works on Bukkit 1.14 to 1.17, or the capability is reported as missing.

**Evidence.** `PersistentDataContainer.getKeys` does not exist before 1.15 on some builds; the agent targets Java 8 bytecode.

**Do.**
- Reflection fallback or a capability flag; an absent method is a visible `UnsupportedOperationException`, never an empty list.

**Don't.** Return an empty result for an unknown; add a dependency.

**Acceptance.**
- [ ] Conformance on one cell per range (1.14, 1.16, 1.17), three separate invocations each, XML read.
- [ ] `bash scripts/gates.sh --max-workers=1` green; independent sonnet review.

**Verify.** `bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run --no-parallel --max-workers=1`

### T-M12-08 · NetherNet: file the ADR (needs the maintainer's yes)
- tier: opus
- depends: T-M10-19
- modules: docs/adr
- adrs: 0011, 0018, 0021
- specs: client-driver.md §6
- test-first: no

**Goal.** Accept ADR-0021 text for NetherNet in the driver host, amending ADR-0011 (MPL-2.0) and ADR-0018 (stages, transport).

**Do.** Move the draft from `docs/platforms/nethernet.md` into the ADR index after the maintainer's yes.

**Don't.** Start T-M12-09 before this is accepted.

**Acceptance.**
- [ ] Maintainer approval recorded; `docs/adr/README.md` index updated; `bash scripts/gates.sh` green.

**Verify.** `bash scripts/gates.sh --max-workers=1`

### T-M12-09 · Driver host: NetherNet transport
- tier: sonnet
- depends: T-M12-08
- modules: drivers/bedrock
- adrs: 0021, 0018
- specs: client-driver.md §6
- test-first: yes

**Goal.** The Bedrock driver host can join over NetherNet.

**Do.** Pins and sha256 list for the two dependency groups, Netty 4.1.130, transport switch, frame codec, identity, stages, one
handshake attempt (see `docs/platforms/nethernet.md`). Natives are vendored in the image build, not downloaded at runtime.

**Don't.** Change the wire protocol, or use `latest` or a range.

**Acceptance.**
- [ ] Frame codec and stage-name unit tests green, XML read.
- [ ] The spike's PowerNukkitX join through the driver host RPC, three separate invocations green.
- [ ] `bash scripts/gates.sh --max-workers=1` green; independent sonnet review.

**Verify.** `bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run --no-parallel --max-workers=1`

### T-M12-10 · Geyser cell over NetherNet
- tier: sonnet
- depends: T-M12-09
- modules: conformance, docs/platforms
- adrs: 0021
- specs: client-driver.md §6
- test-first: no

**Goal.** `bedrock.transport: nethernet` works for the Geyser cell; `both` stays as the fallback until it is green.

**Do.** C-G01 to C-G04 on NetherNet; update `docs/platforms/geyser.md` and `docs/platforms/nethernet.md`.

**Don't.** Remove the RakNet path.

**Acceptance.**
- [ ] C-G01 to C-G04 green in three separate invocations, XML read.
- [ ] `bash scripts/gates.sh --max-workers=1` green.

**Verify.** `bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:run --no-parallel --max-workers=1`

### T-M12-11 · Spike: BDS over NetherNet (needs the maintainer's yes for the BDS binary)
- tier: opus
- depends: T-M12-09
- modules: docs/platforms
- adrs: 0021
- specs: client-driver.md §6
- test-first: no

**Goal.** Find out whether BDS 1.26.66.1 accepts a self-made offer identity and an unsigned chain with `online-mode=false`.

**Do.** Run the documented join under `scripts/dockerlock.sh`; write each run in `docs/platforms/nethernet.md`. Any download is
written down first (version, URL, sha256, size).

**Don't.** Use an account or a secret, or commit a Mojang binary. Reopens T-M10-02 only if the join works.

**Acceptance.**
- [ ] `docs/platforms/nethernet.md` has every run as invocation and result.

**Verify.** `bash scripts/gates.sh --max-workers=1`

### T-M12-12 · Bridged paper@1.16.5: the cell's console check reads `list` as blank
- tier: sonnet
- depends: —
- modules: proofstone-core, docs/platforms
- adrs: 0006
- specs: specs/self-tests.md §1
- test-first: no

**Goal.** Find out why the bridged `paper@1.16.5` cell fails its own conformance with ``cell conformance failed: `list` gave a blank reply, so the console path is unverified``, and fix the cause or document it.

**Evidence.** T-M12-07 (2026-11-25): C-I01 on paper@1.16.5 was INVALID in 3 of 3 separate invocations, before the test body ran; the same test is green on paper@1.17.1 (3 of 3). The matrix row is `required`, observed only for C01 ("T-M3-02", `java8`).

**Do.**
- Reproduce with `:conformance:smoke -Dproofstone.cells=paper@1.16.5`, read the agent's reply to `list` and the server log (does the 1.16.5 console print the player list through a path the agent's command capture misses?).
- Fix in the agent's command capture if it is there; if it is the bridge, record the mechanism in `docs/platforms/paper.md`. Update the row's `observed` text with the result.

**Result (2026-11-25).** Cause: Paper 1.16.5 on Java 8 runs Log4j `AsyncLogger`, so events reach an appender after the logging call returns, and the capture detached first (`seen=0`; same logger context and root, so not a context problem). Fix: the capture logs an end marker behind the command's events and waits for it (`Log4jTap.emit`, `LogCapture.close`). See `docs/platforms/paper.md`.

**Don't.** Weaken or skip the console check; change the row status without per-row evidence (ADR-0015).

**Acceptance.**
- [ ] The cause is named with evidence, and `paper@1.16.5` passes C01 and C02 in three separate invocations, or the row is demoted with the reason in `versions.yml`.
- [ ] `bash scripts/gates.sh --max-workers=1` green.

**Verify.** `bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:smoke -Dproofstone.cells=paper@1.16.5 --no-parallel --max-workers=1`
