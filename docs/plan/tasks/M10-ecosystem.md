# M10: Ecosystem (Bedrock leftovers from M9, then more platforms, packs and docs)

Refined from the M10 epic in [M7-M10-epics.md](M7-M10-epics.md) by the M9 review (T-M9-99, 2026-11-17, ROADMAP "M9 review").
**State after the M10 review (T-M10-99, 2026-11-18, ROADMAP "M10 review").** The maintainer approved the plan on 2026-11-17.
Merged: 08 (#161), 06 (#162), 05 (#163, the protocol switch approved), 07 (#164), 10 (#165). Deferred by the maintainer: 01 (BDS
spike; 02 and 03 wait on it). Blocked upstream: 04 (no CloudburstMC Protocol release, checked 2026-11-17 and 2026-11-18); 09 waits on
04. Part B (11 to 18): not started, waiting on maintainer decisions. Boxes of 05 and 06 were left unticked by their PRs; the M10 review
is their evidence. Cards marked
**Needs maintainer decision** stop at their first step until the maintainer says yes in writing (an issue or PR comment); a
delegate's "decide" does not cover a new image, a Mojang binary, a new repository, hosting, publishing or a workflow.

M10 has two parts. **Part A** finishes the Bedrock work M9 left (BDS was deferred by the maintainer, ADR-0018 section 1; the rest
are M9 review findings). **Part B** is the original ecosystem epic, cut into spikes first: no platform card is written past its spike
until the spike has run.

**Exit criterion.** M10 is open-ended ("ongoing" in the ROADMAP). It closes per part:
- **Part A:** either a BDS cell passes the conformance subset its protocol subset allows (T-M10-03), or the BDS spike says "not
  feasible" with evidence and the BDS row in [platforms.md](../../platforms.md) says so; the Geyser row has the M9 review's open
  items closed or re-carded (T-M10-04 to T-M10-09); the Geyser row is promoted or stays `experimental` with the blocking reason named.
- **Part B:** each platform spike has a verdict; a platform that gets cards gets its own milestone file. The pack template and the
  docs site are built or declined by the maintainer.
- Every real-server acceptance has the flake evidence the **Flake rule** below requires.

## Context the cards rely on

- **What M9 left** (ROADMAP "M9 review"). A real Bedrock client (`drivers/bedrock`, CloudburstMC Protocol in a driver host container)
  joins `geyser@2.12.1+paper@26.3` offline (*offline-stub*: `validate-bedrock-login: false`, a discovery stand-in for two Microsoft
  names). Client v1 does join, chat, command, inventory read (id and count) and position; window clicks, `useBlock`, forms and resource
  packs throw `UnsupportedOperationException`. The driver is pinned to a **timestamped snapshot** (no release of CloudburstMC Protocol
  exists), so the row cannot be promoted (ADR-0018 section 8). Two negative controls exist (`stand-in-absent`, `key-mismatch`); the
  third (a Bedrock protocol the Geyser build refuses) was dropped because it needs a switch in the driver.
- **BDS.** A Bedrock Dedicated Server is a Mojang binary with its own EULA and no redistribution. Its Script API module
  `@minecraft/server-net` is beta and BDS-only. Nothing about BDS has been run. Whether BDS accepts an unsigned (offline) login is
  **unverified** (ADR-0018 section 1).
- **No new image, no workflow, no publishing** unless a card says the maintainer approved it. Local runs only (AGENTS.md, CI cost).

## Flake rule (applies to every real-server acceptance in this file)

Same rule as M9 ([M9-bedrock.md](M9-bedrock.md), Flake rule), unchanged: one cell per invocation under `scripts/dockerlock.sh`,
`--no-parallel --max-workers=1`, XML read; three separate green invocations per acceptance; a red is a finding, copied aside (XML,
bundle, container logs) before the next run, never replaced by a later green, never fixed by a longer timeout or a retry. Bedrock join
timeouts (Geyser and BDS) are counted apart from the Paper, topology and loader numbers, with the stalled stage named.

## Estimate

**Part A: about 1 week of focused work if the BDS spike is feasible (range 3 days to 3 weeks); about 3 days if it is not.** M9 was
estimated at 4 weeks (2 to 6) and took about 8 hours of agent wall clock for 9 cards (first commit 2026-11-17 12:31, last merge
20:15, +01:00), because the Geyser spike answered the auth question with a working route. BDS has the same kind of unknown (an offline
login, a beta scripting API, an inverted transport) and a licence step a human must take, so the range stays wide. T-M10-04 waits on
an upstream release and cannot be scheduled.
**Part B: ongoing.** Each spike is 2 days, time-boxed. The pack template and the guide are about 1 week. The docs site depends on a
hosting decision.

**Cards: 19** (Part A 10, Part B 8, plus the review). Tiers: 1 Haiku, 12 Sonnet, 6 Opus (hints only, ADR-0014).
**Needs maintainer decision: 01, 02, 04, 05, 09, 11, 12, 13, 15, 16, 17.**

## Order

Part A: `08` (ADR amendment, docs only) and `06` (guard gaps) first, then `05`, `07`, `10`. `01` (BDS spike) whenever the maintainer
approves the BDS source; `02` and `03` only if `01` says feasible. `04` when a Cloudburst release carries the codec; `09` after `04`.
Part B: the spikes `11` to `14` in any order, one at a time; `15` to `17` after the maintainer's decisions. `99` review. Cards that touch
the same files run one at a time.

---

## Part A: Bedrock (M9 leftovers)

### T-M10-01 · Spike: a BDS behaviour pack as an agent (Script API, `@minecraft/server-net`) (was T-M9-02)
- tier: opus
- depends: T-M9-99
- modules: docs/platforms, scripts
- adrs: 0002, 0005, 0006, 0011, 0018
- specs: agent-protocol.md
- test-first: no

**Goal.** Decide with runs whether a Bedrock Dedicated Server add-on can serve or relay the wire protocol, read-only, and whether the
M9 Bedrock driver joins BDS offline.

**Needs maintainer decision** before the first step: the BDS source (an image by digest or the zip by sha256), its EULA, its size, and
whether a BDS image may be pulled at all (AGENTS.md section 3: never committed, never redistributed).

**Do.**
- Time box: 2 days. Ask the maintainer first, naming the BDS version, the image or zip, its digest or sha256 and its size.
- Boot BDS with a minimal behaviour pack using `@minecraft/server-net`; record the BDS version, the Script API module versions and the
  beta-API flag. Record whether the pack can make an HTTP request to the harness (it cannot listen) and the round-trip latency.
- Sketch the inverted transport (the pack polls the harness for requests and posts answers) and run one read (`players`) and one
  event (`playerJoin`) through it.
- Join BDS with the M9 driver host (`drivers/bedrock`, protocol 2225 if BDS matches) with an unsigned chain: record the verdict, the
  stage reached, and every outbound name asked (the stand-in's DNS log, as T-M9-01 did).
- Write `docs/platforms/bds.md` from what ran.

**Don't.**
- Change the wire protocol, or claim the observe-only rule holds without showing the pack writes nothing.
- Use a Microsoft or Xbox account, a token or any secret. Commit BDS or any part of it.

**Acceptance.**
- [x] `bds.md` states the BDS pin, the beta flag, whether the HTTP call works, the measured round trip, and the offline-join verdict.
- [x] A plain verdict: feasible (with the transport) or not, with evidence. If not, T-M10-02 and T-M10-03 are closed with a link.

**Verify.**
Reviewer re-runs the documented boot and one round trip under `scripts/dockerlock.sh`.

### T-M10-02 · BDS host and script agent (was T-M9-09)
- tier: opus
- depends: T-M10-01
- modules: platforms, proofstone-core, versions.yml, docs/adr
- adrs: 0002, 0005, 0006, 0008, 0018
- specs: agent-protocol.md, versions-yml.md
- test-first: yes

**Closed: not feasible (T-M10-01, 2026-11-20).** BDS 1.26.66.1 refuses the M9 driver's unsigned login (`Connection Request invalid.`,
5 of 5 joins) and supports only NetherNet for players; with `online-mode=true` it does not start offline. Evidence and what would reopen
this card: [bds.md](../../platforms/bds.md). Not started.

**Goal.** A BDS cell boots with the script agent and serves the protocol subset an ADR names, observe-only.

**Needs maintainer decision:** the BDS pin chain (image or zip) chosen in T-M10-01, and an ADR (new: the BDS host, the transport and
the `versions.yml` key) accepted before code.

**Do.**
- The ADR first (BDS host, inverted transport, protocol subset, `versions.yml` schema bump if needed, the offline route).
- The BDS host (pinned as the ADR says, never committed), the behaviour-pack agent, golden contract tests for the subset, an
  observe-only test (the pack writes nothing), and a real-server boot test.

**Don't.**
- Start if T-M10-01 said not feasible. Re-decide ADR-0018.

**Acceptance.**
- [ ] Golden contract tests for the subset pass. The observe-only test exists and passes.
- [ ] The real-server boot test passes three invocations.

**Verify.**
The card's unit tests, then its real-server task under `scripts/dockerlock.sh`.

### T-M10-03 · BDS conformance subset (was T-M9-10)
- tier: sonnet
- depends: T-M10-02
- modules: conformance
- adrs: 0003, 0015, 0018
- specs: specs/self-tests.md §1
- test-first: yes

**Closed: not feasible (T-M10-01, 2026-11-20).** BDS 1.26.66.1 refuses the M9 driver's unsigned login (`Connection Request invalid.`,
5 of 5 joins) and supports only NetherNet for players; with `online-mode=true` it does not start offline. Evidence and what would reopen
this card: [bds.md](../../platforms/bds.md). Not started.

**Goal.** The BDS row passes the scenarios its protocol subset allows, with the Bedrock driver joining natively.

**Do.**
- Run the C-G scenarios that apply to BDS; skip the rest with the missing capability named. A negative control (the transport
  absent) gives `INVALID`, never a join timeout.

**Don't.**
- Start if T-M10-01 said not feasible. Weaken a C-G assertion to fit BDS.

**Acceptance.**
- [ ] `:conformance:run -Dproofstone.cells=<bds row>` green three invocations, skips reasoned.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=<bds row> --no-parallel --max-workers=1`

### T-M10-04 · Move the Bedrock driver to a CloudburstMC Protocol release
- tier: sonnet
- depends: T-M9-99
- modules: drivers/bedrock, gradle/libs.versions.toml, settings.gradle.kts
- adrs: 0006, 0018
- specs: client-driver.md section 6
- test-first: no

**Goal.** The driver host is built from release artefacts only, so the snapshot rules and the OpenCollab snapshots repository go, and
ADR-0015 promotion of the Geyser row becomes possible (ADR-0018 section 8).

**Needs maintainer decision** only if the release changes the codec (a new Bedrock protocol means a new Geyser row pin too).

**Do.**
- Blocked until a release of `org.cloudburstmc.protocol` carries the codec the Geyser row needs (none existed on 2026-11-17). Check
  Maven Central and `repo.opencollab.dev/maven-releases` first; if none, close the card as "still blocked" with the date.
- Pin the release, remove the snapshot `exclusiveContent` entry and the timestamp checks no longer needed, re-derive every line of
  `drivers/bedrock/cloudburst-artefacts.sha256` from a real download.
- Re-run `:conformance:run` on the Geyser row and `metaBedrockTest`.

**Don't.**
- Pin a `-SNAPSHOT`, a range or a newer snapshot and call it a release. Change the Geyser row in the same card unless the codec moved.

**Acceptance.**
- [ ] The resolved graph of `drivers/bedrock` holds no snapshot (`checkCloudburstPins`).
- [ ] `:conformance:run -Dproofstone.cells=geyser@2.12.1+paper@26.3` green three invocations; `metaBedrockTest` once, XML read.

**Verify.**
`./gradlew :drivers:bedrock:test`, then the two real-server tasks under `scripts/dockerlock.sh`.

### T-M10-05 · The refused-protocol negative control (a driver-host protocol switch)
- tier: sonnet
- depends: T-M9-99
- modules: drivers/bedrock, proofstone-core, examples/bad-tests
- adrs: 0003, 0007, 0018
- specs: specs/self-tests.md §3
- test-first: yes

**Goal.** The third fault of the M9 exit criterion (a Bedrock protocol the Geyser build refuses) is a loud `INVALID` quoting Geyser's
refusal, as T-M9-11 proposed (geyser.md, "Negative controls").

**Needs maintainer decision:** a test-only switch in a production module (`drivers/bedrock`). M8 removed its own test-only switch
(`injectModFault`, T-M8-13) once a declared input could make the fault; here no declared input can, so the switch is the only route.
Alternative: accept the fault as unverified and close this card.

**Do.**
- A default-off `-Dproofstone.bedrock.protocolOverride=<n>` read only by the driver host's `HostSession`, set through the container's
  `JAVA_TOOL_OPTIONS` by `GeyserFault` (`-Dproofstone.injectGeyserFault=refused-protocol`); the driver sends that number in
  `RequestNetworkSettings`.
- First show the fault by a run and record Geyser's refusal text in `geyser.md`; then a `metaBedrockTest` entry expecting `INVALID`
  with that text, and `GeyserConformance.joinFailure` naming the hop.

**Don't.**
- Expect a join timeout, or fake the text. Let the switch be set by anything except the fault injection.

**Acceptance.**
- [ ] Unit tests: the switch is off by default and refused outside the fault.
- [ ] `metaBedrockTest` gives `INVALID` for all three faults, three invocations; the clean Geyser conformance stays green.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaBedrockTest --no-parallel --max-workers=1`

### T-M10-06 · Close the Geyser guard and label gaps: name guard per test, driver-host and stand-in logs, `offline-stub` in the report
- tier: sonnet
- depends: T-M9-99
- modules: proofstone-core
- adrs: 0007, 0016, 0018
- specs: specs/self-tests.md §1
- test-first: yes

**Goal.** The two Geyser cell guards cover the whole cell life, not only its first check, and the *offline-stub* label is where
ADR-0018 section 4 puts it (M9 review findings).

**Do.**
- The name guard (`GeyserHost.requireNameGuardClean`) runs only in the cell's first cached check (`GeyserConformance`). Run it again
  after every test and at cell close, so a deny-listed name asked later (a second driver JVM, a plugin) fails the test that asked it.
- The log guard covers only the server (`ErrorLogGuard.Patterns.GEYSER`). Add a guard on the driver host's and the stand-in's logs:
  an `ERROR`, an uncaught exception or a `STUB refused` outside the `stand-in-absent` fault fails the test, each allowlisted line with a
  reason from a run (empty-then-grow, ADR-0016).
- ADR-0018 section 4 says "the report and display name say `offline-stub`"; at `be123b2` only `cell.json` (bundles) and the
  `PROOFSTONE GEYSER BOOT` stderr line do. The JUnit display name and `proofstone-report.json` show `geyser@2.12.1+paper@26.3` alone
  (T-M9-99 run, `geyser1` XML and report), and `GeyserCell.bedrockAuth()`'s Javadoc claims otherwise. Add the label to both (a report
  field, not a change of the cell identity used by the selector), or amend the ADR in T-M10-08 if the maintainer prefers.
- Unit tests for each with scripted logs.

**Don't.**
- Allowlist a line without a run behind it. Close the cell's egress (ADR-0018 section 4).

**Acceptance.**
- [ ] Unit tests red before, green after.
- [ ] `:conformance:run` on the Geyser row green three invocations; `metaBedrockTest` once, XML read.

**Verify.**
`./gradlew :proofstone-core:test`, then the two real-server tasks under `scripts/dockerlock.sh`.

### T-M10-07 · Two Bedrock clients at once on a Geyser cell
- tier: sonnet
- depends: T-M9-99
- modules: drivers/bedrock, proofstone-core, conformance
- adrs: 0018
- specs: client-driver.md section 6
- test-first: yes

**Goal.** A test can join two Bedrock clients in one cell, each with its own XUID and Floodgate UUID (unrun in M9: ADR-0018
Consequences, geyser.md "Still unrun").

**Do.**
- Join two clients through one driver host; assert the agent lists both `.Name` players with distinct UUIDs, a chat line from one
  reaches the other, and both leave cleanly.
- A conformance scenario C-G05 for it.

**Don't.**
- Start a second driver host to dodge a driver bug: report the bug.

**Acceptance.**
- [x] `:conformance:run -Dproofstone.cells=geyser@2.12.1+paper@26.3` green three invocations with C-G05.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=geyser@2.12.1+paper@26.3 --no-parallel --max-workers=1`

### T-M10-08 · Amend ADR-0018 to what M9 built
- tier: haiku
- depends: T-M9-99
- modules: docs/adr, docs/specs
- adrs: 0018
- specs: client-driver.md, test-api.md
- test-first: no

**Goal.** ADR-0018 says what was built, as T-M8-17 did for ADR-0017.

**Do.**
- An "Amendments" section listing each deviation the M9 review found (ROADMAP "M9 review", "ADR-0018 against the build"), each with
  the file or run that shows it: three snapshot groups pinned, not one; the refused-protocol fault dropped; the key mismatch made on
  Floodgate's side; the harness reaches the driver host by `docker exec curl`; the `floodgate` token at the first tick and the health
  re-read; the log guard on the server only; the name guard once per cell; `TEST_ABORTED`; the test-only `injectGeyserFault` switch.

**Don't.**
- Re-decide anything. Change code.

**Acceptance.**
- [x] The amendment lists each change with its evidence. `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M10-09 · Promote the Geyser row (ADR-0015)
- tier: sonnet
- depends: T-M10-04
- modules: versions.yml, docs/platforms.md
- adrs: 0015, 0018
- specs: versions-yml.md
- test-first: no

**Goal.** `geyser@2.12.1+paper@26.3` (or its successor) is `required`, or stays `experimental` with the reason.

**Needs maintainer decision:** promotion is the maintainer's call (ADR-0015); this card prepares the table.

**Do.**
- The ADR-0015 table for the row with C-G01 to C-G04 (and C-G05 if T-M10-07 merged), three green invocations, on a release driver.

**Don't.**
- Promote on a snapshot driver (ADR-0018 section 8).

**Acceptance.**
- [ ] The table is in the PR; the row status matches the maintainer's decision.

**Verify.**
`./gradlew printMatrix --scope=pr`, then `bash scripts/gates.sh --max-workers=1`

### T-M10-10 · Diagnose `/list` for a Bedrock client against a Java client
- tier: sonnet
- depends: T-M9-99
- modules: docs/platforms, conformance
- adrs: 0018
- specs: specs/self-tests.md §1
- test-first: no

**Goal.** Decide whether vanilla `/list` answering a Bedrock client "Unknown or incomplete command" is Geyser's translation or Paper's
permission for a non-op (unverified, geyser.md T-M9-08).

**Do.**
- Run `/list` from the Java client and the Bedrock client on the same cell, both non-op, then both op; record the four answers.
- If Geyser differs from Java, add a C-G scenario asserting it as it is and a Gotcha; if it is a permission, say so and close.

**Don't.**
- Change C-G02 (it uses `/plugins` on purpose).

**Acceptance.**
- [x] `geyser.md` states the cause with the four transcripts.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=geyser@2.12.1+paper@26.3 --no-parallel --max-workers=1`

## Part B: Ecosystem (the original epic, spikes first)

### T-M10-11 · Spike: Sponge (SpongeVanilla) with an agent
- tier: opus
- depends: T-M9-99
- modules: docs/platforms, scripts
- adrs: 0002, 0005, 0006, 0013
- specs: agent-protocol.md
- test-first: no

**Goal.** Decide with runs whether a SpongeVanilla server on the newest row boots on a pinned image, and an agent serves `health`
and `players` read-only.

**Needs maintainer decision:** the image (an existing pinned `itzg/minecraft-server` type, or new) before any pull.

**Do.**
- Time box 2 days. Boot SpongeVanilla on the newest row Sponge supports; join with `mcpl` natively (or record the protocol gap).
- A throwaway agent under `scripts/spike/` serving `health` and `players`; record the API version, the event priority model and what
  observe-only means there.
- Write `docs/platforms/sponge.md`.

**Don't.**
- Commit production code or a `versions.yml` pin.

**Acceptance.**
- [ ] A verdict with the runs, the join count and the pins that would be needed.

**Verify.**
Reviewer re-runs the documented boot under `scripts/dockerlock.sh`.

### T-M10-12 · Spike: Gate (the Go proxy) as a topology proxy
- tier: sonnet
- depends: T-M9-99
- modules: docs/platforms, scripts
- adrs: 0006, 0016
- specs: agent-protocol.md
- test-first: no

**Goal.** Decide whether Gate fits ADR-0016 topologies (modern forwarding to Paper) and how a proxy agent could observe it.

**Needs maintainer decision:** Gate's distribution (binary or image) and pin before any download.

**Do.**
- Time box 2 days. Run Gate in front of `paper@26.3` with modern forwarding; join with `mcpl`; record P01 to P05 by hand.
- Record whether Gate has a plugin or extension model an agent could use (it is Go: likely none in-process), and the fallback (an
  agent-less proxy with the backends' agents as the only oracle).
- Write `docs/platforms/gate.md`.

**Don't.**
- Build a Go agent or add a Go toolchain.

**Acceptance.**
- [ ] A verdict with runs, and the oracle gap stated.

**Verify.**
Reviewer re-runs one documented join under `scripts/dockerlock.sh`.

### T-M10-13 · Spike: a Java Bedrock server (Nukkit family) joined natively by the Bedrock driver
- tier: opus
- depends: T-M9-99
- modules: docs/platforms, scripts
- adrs: 0002, 0006, 0018
- specs: client-driver.md section 6, agent-protocol.md
- test-first: no

**Goal.** Decide whether the M9 Bedrock driver joins a Nukkit-family server (PowerNukkitX or Nukkit) offline, and whether a Java plugin
agent can serve `health` and `players` there.

**Needs maintainer decision:** which server and its jar pin, before any download.

**Do.**
- Time box 2 days. Boot the server on the existing pinned Java image (files copied in), join with the driver host, record the protocol
  number match, the login route (offline, no Xbox) and the names asked (stand-in DNS log).
- A throwaway agent under `scripts/spike/`. Write `docs/platforms/nukkit.md`.

**Don't.**
- Commit production code or a pin. Use an account.

**Acceptance.**
- [ ] A verdict with runs and the protocol gap stated (the server's Bedrock protocol vs the driver's 2225).

**Verify.**
Reviewer re-runs one documented join under `scripts/dockerlock.sh`.

### T-M10-14 · Spike: PocketMine-MP and Endstone (scope only)
- tier: sonnet
- depends: T-M9-99
- modules: docs/platforms
- adrs: 0002, 0018
- specs: agent-protocol.md
- test-first: no

**Goal.** A written scope for the two non-Java Bedrock servers, before anything is run: what an agent would be written in, what image
it needs, and whether the M9 driver's protocol matches.

**Do.**
- Read their docs and release notes; list the agent language (PHP; Endstone is a BDS plugin loader, so it needs T-M10-01's BDS answer),
  the image each needs, and the Bedrock protocol of their newest release.
- Write the findings in `docs/platforms.md` (the third-party Bedrock row) and propose cards or "not now".

**Don't.**
- Pull an image or download a server. Claim feasibility from docs.

**Acceptance.**
- [ ] The scope is written and marked unverified; `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M10-15 · A platform-pack template and a "write a platform pack" guide
- tier: sonnet
- depends: T-M9-99
- modules: docs/user, docs/specs
- adrs: 0002, 0003, 0015
- specs: agent-protocol.md, specs/self-tests.md
- test-first: no

**Goal.** A community author can write an agent for a new platform and prove it with the conformance checklist.

**Needs maintainer decision:** a separate template repository (new repo, licence, ownership) or a directory in this repository.

**Do.**
- The guide: the wire protocol subset, golden contract tests, observe-only rules, capabilities, the conformance checklist (C01 to C08,
  P01 to P05, C-G01 to C-G04 by family) and ADR-0015 promotion.
- The template where the maintainer decides, with a contract test that runs against the goldens.

**Don't.**
- Publish anything or create a repository without the decision.

**Acceptance.**
- [ ] The guide passes `docsCheck`; the template's contract test passes against the goldens.

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M10-16 · A docs site and a compatibility page from local reports
- tier: sonnet
- depends: T-M9-99
- modules: docs, scripts
- adrs: 0010, 0015
- specs: vision.md
- test-first: no

**Goal.** A static docs site and a compatibility page generated from `proofstone-report.json` files of local runs.

**Decision (2026-11-19, maintainer):** GitHub Pages hosting and one new workflow, `docs.yml`, are approved. A "nightly" does not exist
(it is local today) and is not claimed.

**Do.**
- A local script (`scripts/docs-site.sh`) that builds the site from `docs/` and a compatibility table from report files given to it.
- `.github/workflows/docs.yml`: push to `main` with paths `docs/**` and `scripts/docs-site.sh` only, SHA-pinned actions, Pages deploy.

**Don't.**
- Add a schedule, `workflow_dispatch` or `pull_request_target`. Claim a nightly that does not run. Enable Pages (the maintainer does).

**Acceptance.**
- [x] The script builds the site locally from the repository and two report files; `bash scripts/gates.sh --max-workers=1` passes. Evidence (2026-11-19): `bash scripts/docs-site.sh --out build/docs-site scripts/fixtures/docs-site/report-a.json scripts/fixtures/docs-site/report-b.json` wrote 80 pages and the matrix; `gates: OK` under dockerlock.

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M10-17 · Decide the optional TypeScript front end and Fabric client gametest (scope only)
- tier: opus
- depends: T-M9-99
- modules: docs/adr, docs/plan
- adrs: 0001, 0002
- specs: test-api.md
- test-first: no

**Goal.** A short written decision for the two optional epic items, so neither is built by drift.

**Needs maintainer decision:** both are "optional" in the epic; the card writes the options, the maintainer chooses.

**Do.**
- For the TypeScript front end: who it serves (Bedrock add-on authors, after BDS), what it needs (the wire protocol is language
  neutral), the cost (a Node toolchain, a second API to keep stable).
- For Fabric client gametest: whether it fits "real server, real client" (it is a client-side test framework), and what Proofstone would
  add.
- Recommend build or not, per item.

**Don't.**
- Write code or an ADR marked Accepted.

**Acceptance.**
- [ ] The options and a recommendation are in the PR and in this file; `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M10-18 · Paper forks: which forks get rows
- tier: sonnet
- depends: T-M9-99
- modules: docs/platforms, versions.yml
- adrs: 0008, 0013, 0015
- specs: versions-yml.md
- test-first: no

**Goal.** Decide, with one boot each, which Paper forks beyond Purpur and Folia are worth an `experimental` row.

**Do.**
- List the forks with a pinned download and a published checksum; boot the two most used on the existing image with the Bukkit agent
  and run `:conformance:smoke` on each by hand.
- Propose rows (experimental) or "not now" per fork, with the run.

**Don't.**
- Add a row without a run. Add a new image.

**Acceptance.**
- [ ] A table per fork: pin source, checksum, boot, smoke result.

**Verify.**
Reviewer re-runs one boot under `scripts/dockerlock.sh`.

### T-M10-99 · M10 milestone review
- tier: opus
- depends: T-M10-05, T-M10-06, T-M10-07, T-M10-08, T-M10-10
- modules: docs/plan, docs/robustness.md, docs/platforms.md
- adrs: 0010, 0014, 0015, 0018
- specs: vision.md
- test-first: no

**Goal.** Decide whether the M10 exit criterion is met per part, from runs, not from PR descriptions.

**Do.**
- Read the code and the XML. Re-run gates, `:conformance:smoke`, `:conformance:run` on the Geyser row (and the BDS row if one exists),
  one loader and one topology row (three invocations each), the meta tasks, and `bash scripts/release-mutation-check.sh` at HEAD.
- Report Bedrock join-timeout and log-guard rates apart. Re-score the exit criterion in ROADMAP "M10 review". Stop for approval.

**Don't.**
- Tag or publish. Round up a partial result.

**Acceptance.**
- [ ] ROADMAP has an "M10 review" section with the exit criterion re-scored row by row, each with evidence and a verdict.

**Verify.**
`bash scripts/gates.sh --max-workers=1`
