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
- [x] `bash scripts/gates.sh --max-workers=1` green (check + docsCheck).

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

**Result (2026-11-26).** Reproduced in counted loops and read from both sides; the mechanism is partly shown. **Bridged paper@1.8.8:** 7 stalls in 500 joins, two shapes: quiet (4; where the watcher worked, 2 of 2 show
ViaProxy's receive queue from Paper growing unread for the whole stall: ViaProxy stopped reading its server socket) and a ViaProxy `IndexOutOfBoundsException` on the proxy to server leg (3; Paper sees a reset, the
client is told nothing). **Bridged paper@1.21.11 (client 777 through ViaProxy to Paper 774; the stall of Disenchantment's hosted runs, which bridge every cell below 26.3; an earlier note called it native, it was not):** 13 stalls in 1400 joins; the client gets exactly the 50 login bytes and flushes its acknowledgement, Paper sends nothing more, every
Paper thread is idle; all stalls at join 61 to 98 of a 100-join server, 0 of 500 in 50-join loops; holding the acknowledgement 10 ms did not prevent it (1 of 500). Not shown: why the ViaProxy hop stops there (ViaProxy's own state was not measured on 1.21.11). Details,
counts and the next experiments are in [viaproxy.md](../../platforms/viaproxy.md); the tooling is `scripts/spike/join-stall/`. No `versions.yml`, pin, core flag or driver change (the
driver diagnostic is only a patch file).

**Don't.** Change `versions.yml`, a pin, core flags or the driver in this card.

**Acceptance.**
- [x] `docs/platforms/viaproxy.md` has every run as invocation, count k of n and result.
- [x] `bash scripts/gates.sh --max-workers=1` green (check + docsCheck).

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
- [x] `bash scripts/gates.sh --max-workers=1` green (check + docsCheck). independent sonnet review of the agent change.

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
- [x] `bash scripts/gates.sh --max-workers=1` green (check + docsCheck). independent sonnet review.

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

**Result (2026-11-25, maintainer approved the workflow change).** `scripts/ci-prefetch-deps.sh` already existed (T-M6-113, ADR-0019 amendment): it retries only the dependency download, only when its own output names a dependency resolution error, and never the gates. `release.yml` now runs it before the gates (job `verify`) and before the publish build (job `maven`), so a repository that answers "not found" for an artefact that exists fails there, named. A pinned mirror was not added: it would be a new runtime dependency (AGENTS.md section 3).

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
- [x] `bash scripts/gates.sh --max-workers=1` green (check + docsCheck). independent sonnet review.

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
- [x] `bash scripts/gates.sh --max-workers=1` green (check + docsCheck). independent sonnet review.

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

**Result (2026-11-25).** The decision was already filed as [ADR-0021](../../adr/0021-nethernet-bedrock-driver.md) (Accepted 2026-11-23), which amends nothing. What was missing was the licence: ADR-0011 did not list MPL-2.0, which `libdatachannel-java` and `libdatachannel` carry. The maintainer approved, and ADR-0011 now has an amendment (MPL-2.0 for unmodified shaded binaries, with the licence-text and source-pointer duties). The `NOTICE` entry lands with the dependency in T-M12-09.

**Don't.** Start T-M12-09 before this is accepted.

**Acceptance.**
- [x] Maintainer approval recorded (2026-11-25); ADR-0011 amended; `docs/adr/README.md` index row updated.
- [ ] `bash scripts/gates.sh` green.

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

**Result (2026-11-25).** The driver host joins over NetherNet: `BedrockDriver(transport, "nethernet")` sends `transport` in the session request (absent means RakNet, byte for
byte the old request), the host does the HTTP probe (`SIGNALLING`), then the library's offer and answer and the data channels (`NETHERNET_CONNECT`), one handshake attempt.
Pins moved to the Cloudburst netty line 2.0.0.CR6 (raknet, common and nethernet), Netty 4.1.130, slf4j 2.0.18, plus libdatachannel-java with the glibc x86_64 and aarch64 natives,
Bouncy Castle 1.85 and gson 2.13.2, each sha256 from a fresh download (a pin line may end in `:classifier`). The natives sit in the shaded jar per architecture and are checked as ELF;
`tel.schich.libdatachannel` stays unrelocated (JNI). The 1.26.60 client claims are sent on every transport; NetherNet asks for chunk radius 8 (PowerNukkitX needs 56 chunks).
Real server: 3 of 3 separate invocations green on the final code after four red findings (r1 to r3, see [nethernet.md](../../platforms/nethernet.md)); unit XML read, `skipped="0" failures="0" errors="0"` for
`NetherNetFrameCodecTest` (6), `ClientPacketsTest` (16), `IdentityAndLimitsTest` (8), `RpcServerTest` (12), `BedrockDriverTest` (19), `ArtefactPinsTest` (12). Independent sonnet review: no blocker, findings applied. Not covered: Geyser cell (T-M12-10).

**Don't.** Change the wire protocol, or use `latest` or a range.

**Acceptance.**
- [x] Frame codec and stage-name unit tests green, XML read.
- [x] The spike's PowerNukkitX join through the driver host RPC, three separate invocations green (a1 to a3).
- [x] `bash scripts/gates.sh --max-workers=1` green (check + docsCheck). independent sonnet review.

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

**Result (2026-11-26).** `-Dproofstone.geyser.transport=nethernet` (`GeyserHostOptions.transport`, enum `GeyserTransport`) runs the Geyser cell with
`bedrock.transport: nethernet`, waits for Geyser's `Built-in signaling started on http://0.0.0.0:19132` line and joins the driver over NetherNet; the default stays
`both` (RakNet driver), so the fallback exists and nothing else changed. The choice is a run option, not a `versions.yml` field: the row schema
(`proofstone-protocol/schemas/versions.schema.json`, `additionalProperties: false`) would need a change this card does not authorize. Run 1 was red (8 of 8
`INVALID`, `Please log into Xbox`): Geyser's log said `the login key does not match the admitted transport identity`; the offer identity is now made with the
session's own key pair (PowerNukkitX never checked it). Then green: `ConformanceGeyserTest` 8 tests, 3 reasoned skips (resource packs, forms, window clicks), 0 failures,
errors 0, in 4 invocations (nn2, acc 1 to 3; the last three on the final code after the review). Independent sonnet review: no blocker, findings applied.
Flipping the default to `nethernet` is left until Geyser drops RakNet; not run: the other Geyser tests (`geyserTest`) over NetherNet.

**Don't.** Remove the RakNet path.

**Acceptance.**
- [x] C-G01 to C-G04 green in three separate invocations, XML read (acc 1 to 3; C-G05 too).
- [x] `bash scripts/gates.sh --max-workers=1` green (check + docsCheck).

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

**Result (2026-11-26).** No: BDS 1.26.66.1 accepts the NetherNet transport and the self-made offer identity (signalling, data channels and `NetworkSettings` work) but refuses the
unsigned login with `MALFORMED_PACKET` / `TERMINATING_CONNECTION` / `Connection Request invalid.`, in 2 of 2 runs (b1 the driver host, b2 the driver host and a client that can read the
violation), the same text as over RakNet. T-M10-02 stays closed. The binary was the one already cached and verified (sha256 `e87bcd95...a6bf`, 98,095,547 bytes); nothing was downloaded
and nothing committed. `scripts/spike/bds/bds-spike.sh` gained `TRANSPORT`, and `BdsJoinNn.java` is the NetherNet variant of `BdsJoin.java`. See [nethernet.md](../../platforms/nethernet.md).

**Don't.** Use an account or a secret, or commit a Mojang binary. Reopens T-M10-02 only if the join works.

**Acceptance.**
- [x] `docs/platforms/nethernet.md` has every run as invocation and result.

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
- [x] `bash scripts/gates.sh --max-workers=1` green (check + docsCheck).

**Verify.** `bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:smoke -Dproofstone.cells=paper@1.16.5 --no-parallel --max-workers=1`

### T-M12-15 · The ViaProxy join stall: find the mechanism
- tier: sonnet
- depends: T-M12-02
- modules: docs/platforms, scripts/spike
- adrs: 0004, 0008
- specs: -
- test-first: no

**Goal.** Say why a bridged join stalls after `LoginFinished` in about 1 of 100 joins, with evidence from the proxy side.

**Evidence.** T-M12-02: 7 stalls in 500 joins (bridged paper@1.8.8), 13 in 1400 (bridged paper@1.21.11), client and Paper both idle, ViaProxy's receive queue from Paper unread. Source read: ViaProxy disables auto-read on the server channel and restores it from write listeners.

**Do.** A local diagnostic build (log lines only) of the pinned ViaProxy, loops of 100 joins on bridged paper@1.21.11, one stall read in full; a stress test of the unmodified `ChannelUtil`.

**Don't.** Change `versions.yml`, a pin or the driver for a diagnostic; commit the patches into a runtime path; retry a join.

**Result (2026-11-26).** Mechanism found: a data race in ViaProxy's `ChannelUtil` between the compression pair and the login pair of disable and restore on the server channel; the wrong interleaving leaves auto-read off for good (`docs/platforms/viaproxy.md`, "The mechanism"). A caught stall shows the swapped restore and disable; 14 harmless occurrences of the race in 700 joins; the unmodified class ended with auto-read false in 8 of 600000 stress iterations. Not shown: the flag value of the stalled join after its last restore. Upstream `master` has the same code. Nothing in Proofstone changed.

**Follow-up (2026-11-28).** The callers of the four calls were read from stack frames (`CompressionPacketHandler.handleP2S:47` and its listener `:51`, `LoginPacketHandler.handleP2S:191`, `ConfigurationPacketHandler` listener `:49`): the pairing is shown, not assumed. A mitigation was tried: `NETWORK_COMPRESSION_THRESHOLD=-1` on the Paper container. It does not help (`CompressionPacketHandler` still runs in 101 of 101 joins; 1 stall in the first 100 joins with it), so it was discarded. Without a retry, a pin change or a patched ViaProxy, the stall stays; the maintainer decides.

**Upstream report (for the maintainer to file; not filed).** ViaProxy 3.4.15-SNAPSHOT (`3e81750`) and `master`: `ChannelUtil.disableAutoRead` and `restoreAutoRead` are not atomic, and the server channel gets two overlapping disable/restore pairs during login (`CompressionPacketHandler` and `LoginPacketHandler`, restored from write listeners on the client channel's event loop). One interleaving leaves `autoRead=false` with the stack `[false]`; the proxy then never reads the server again and the client hangs after `LoginFinished`. A stress test with the pinned jar's class left auto-read false in 8 of 600000 runs. A lock around both methods, or running the restore on the channel's own event loop, removes it.

**Acceptance.**
- [x] A stalled join read from the proxy side, and a stress test of the unmodified class that reaches the same final state.
- [x] `bash scripts/gates.sh --max-workers=1` green (check + docsCheck).

**Verify.** `bash scripts/gates.sh --max-workers=1`

### T-M12-14 · Cells allow flight: no anti-fly kick for a client waiting for its chunk
- tier: haiku
- depends: —
- modules: proofstone-core, docs/platforms
- adrs: 0006
- specs: specs/self-tests.md §1
- test-first: yes

**Goal.** No cell kicks a test client with `was kicked for floating too long!`.

**Evidence.** Disenchantment's final repeat series on `42c0732` (run 45772976805): folia@26.3 RED, `BedrockMenuE2E.aPrefixDetectedPlayerGetsTheChestMenuAndOthersDoNot` failed in 2 of 5 repeats with `WARN ServerCommonPacketListenerImpl: <player> was kicked for floating too long!`. Nothing in Proofstone set `allow-flight`.

**Do.**
- `ALLOW_FLIGHT=TRUE` in `PaperHost.configure` and `LoaderEnvironment.env`; a unit test per platform family; read `allow-flight` from a booted cell.

**Don't.** Touch a test, the error-log guard or any other server property.

**Result (2026-11-26).** Done. `AllowFlightTest` (paper, folia, spigot, purpur) and `LoaderEnvironmentTest` assert the variable; a booted folia@26.3 cell has `allow-flight=true` in `/data/server.properties` (read with `docker exec` during the smoke run); `:conformance:smoke` green on folia@26.3 (3 separate invocations, 2 tests, 0 failures each), and on spigot@1.8.8 and paper@1.8.8 (bridged) once each; the loader cells were not booted (their environment is covered by `LoaderEnvironmentTest` only). The long float itself was not reproduced.

**Acceptance.**
- [x] Unit tests green, XML read.
- [x] `:conformance:smoke` green in separate invocations (see the PR), XML read.
- [x] `bash scripts/gates.sh --max-workers=1` green (check + docsCheck).

**Verify.** `bash scripts/gates.sh --max-workers=1`; `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:smoke -Dproofstone.cells=folia@26.3 --no-parallel --max-workers=1`

### T-M12-13 · Re-pin spigot@26.3: BuildTools no longer builds the 2026-11-08 digest
- tier: haiku
- depends: —
- modules: versions.yml, docs/platforms
- adrs: 0005, 0013
- specs: specs/versions-yml.md
- test-first: no

**Goal.** `:conformance:smoke` boots spigot@26.3 again.

**Evidence.** T-M12-01 (2026-11-25): spigot@26.3 FAILED C01 and C02 in 3 of 3 invocations with `BuildTools built spigot@26.3 with content digest 38d584f9..., but versions.yml pins 22b5f5b3...`. The three fresh builds gave the same digest, so the build is reproducible and the upstream branch for the 26.3 rev moved since the pin of 2026-11-08. spigot@1.21.11 built its pin unchanged.

**Do.**
- Re-pin the 26.3 row to `38d584f9...` (the digest all three builds produced) and say why in `observed`.
- Note in `docs/platforms/spigot.md` that a BuildTools row can go stale when upstream moves, and what the check is (3 equal fresh builds, then smoke).

**Don't.** Relax the digest rule (ADR-0005); re-pin without equal digests from at least two fresh builds.

**Acceptance.**
- [x] `:conformance:smoke` green (spigot@26.3 C01 and C02 included) in three separate invocations, XML read.
- [x] spigot@1.21.11 smoke green on its unchanged pin.
- [x] `bash scripts/gates.sh --max-workers=1` green (check + docsCheck).

**Verify.** `scripts/dockerlock.sh` + `PROOFSTONE_ACCEPT_EULA=true ./gradlew :conformance:smoke --no-parallel --max-workers=1`
