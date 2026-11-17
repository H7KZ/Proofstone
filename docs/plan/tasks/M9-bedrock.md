# M9: Bedrock (Geyser and Floodgate on a Java server, then Bedrock Dedicated Server add-ons)

Refined from the M9 epic in [M7-M10-epics.md](M7-M10-epics.md) by the M8 review (T-M8-99, 2026-11-15). **Approved 2026-11-17 by the maintainer's delegate (the maintainer said "decide"), with two limits:** (1) no BDS: T-M9-02 and the BDS cards 09 and 10 are deferred to M10, because a BDS image or zip is a Mojang binary with its own EULA and no redistribution, and a new image needs an explicit maintainer yes; (2) no new image or workflow in M9 at all. Geyser and Floodgate are plugin jars pinned by sha256 on the existing Paper image path. If a card turns out to need an image, it stops and says so.
No card here is started. The maintainer approves the plan, or changes it, before any card is picked up. The two spikes decide
the rest: if a spike says "not feasible", the cards that depend on it are dropped and the plan is re-made, never built on a
guess.

**Exit criterion.** All of the following must be true, each shown by a real-server run with the XML read:
- A Bedrock client driver joins a Java server through Geyser and Floodgate offline (no Xbox account, no Microsoft service
  call), on the newest native `paper` row, and the Bukkit agent sees the player as Floodgate names it.
- A test declares a Bedrock client through the public API, additively to API v0 (label `api-change`, no existing signature
  changed), and the Geyser topology through `versions.yml`, as ADR-0016 did for proxies.
- Conformance scenarios on the Geyser topology (join, command reply, inventory read with the dual oracle, a Bedrock-only
  plugin branch) pass, every skip reasoned in the XML.
- A misconfigured Geyser topology (Floodgate key mismatch, Bedrock protocol the Geyser build refuses) is a loud red, never a
  pass or a join timeout.
- **Only if the T-M9-02 spike says feasible:** a BDS cell boots with the script agent and passes the wire-protocol subset the
  ADR names. If the spike says not feasible, this item is replaced by the spike's evidence and moved to M10.
- Every acceptance above has the flake evidence the **Flake rule** below requires.

## Context the cards rely on

- **What M8 left.** Loader cells (`LoaderHost`), protocol 1.3 (`/v1/mods`, `/v1/datapacks`), resource packs from the harness
  JVM, and an open M8 follow-up list ([M8-followups.md](M8-followups.md)). None of it is needed by Bedrock except the topology
  machinery from M7 (ADR-0016: a cell of several containers on a private network, per-container log guard and bundle).
- **What exists for Bedrock today.** Only the simulated tier: `SIMULATED_BEDROCK`, a Java client under a `.` name
  (test-api.md §11, C-B01). No Bedrock code, no `Edition` in the driver SPI (client-driver.md lists `edition()`, the code has
  none), robustness R13 "Not built".
- **External services.** Every Proofstone run is offline. Bedrock login normally signs a chain with Xbox Live; Geyser's
  `auth-type: offline` and Floodgate's own key are the two documented offline routes, **unverified here**. A driver that needs
  a Microsoft account is out (AGENTS.md: no secrets; ADR-0006: nothing unpinned at run time).
- **Driver candidates** (epic): CloudburstMC Protocol (Java, in-process like `mcpl`) or PrismarineJS `bedrock-protocol` (Node,
  a sidecar like `mineflayer-ref`). Bedrock is UDP (RakNet): Testcontainers UDP port exposure and the ViaProxy-style loopback
  binding are untested here.
- **BDS.** Bedrock Dedicated Server is a Mojang binary with its own EULA and no redistribution; Script API
  `@minecraft/server-net` is beta and BDS-only. Whether a behaviour pack can open an HTTP client to the harness (the agent's
  shape inverted: the pack calls out, it cannot listen) is the second spike's question.
- **Pins.** Geyser and Floodgate are plugin jars (pinned by sha256 like any download, ADR-0006); a BDS image or zip is new and
  needs maintainer approval before the first pull.

## Flake rule (applies to every real-server acceptance in this file)

Same rule as M8 ([M8-mods.md](M8-mods.md), Flake rule), unchanged: one cell per invocation under `scripts/dockerlock.sh`,
`--no-parallel --max-workers=1`, XML read; three separate green invocations per acceptance; a red is a finding, copied aside
(XML, bundle, container logs) before the next run, never replaced by a later green, never fixed by a longer timeout or a retry.
**Bedrock join timeouts are counted apart** from the Paper, topology and loader numbers, with the stalled stage named.

## Estimate

**4 focused weeks after the spikes, range 2 to 6** (the epic said 4 after the spike). Spikes: 2 days each, time-boxed.
M8 was estimated at 4 weeks (2 to 5) and took about 17 hours of agent wall clock for 12 cards (first merge 2026-11-14 13:42,
last 2026-11-15 06:58, +01:00), because its biggest unknown (does the vanilla client join a modded server) was answered "yes"
by its spike. M9's unknowns are larger: offline Bedrock auth, a second protocol family, UDP, and a beta scripting API. If the
Geyser spike shows no offline route, M9 shrinks to the simulated tier plus docs (about 1 week) and a driver needs its own ADR.

**Cards: 13** (12 plus the review; 5 conditional on a spike). Tiers: 0 Haiku, 6 Sonnet, 7 Opus (hints only, ADR-0014).

## Order

`01` (Geyser spike) and `02` (BDS spike) in either order, then `03` (ADR). Then `04` (schema and SPI), `05` (driver), `06`
(Geyser topology host), `07` (API), `08` (conformance), `11` (negative controls), `12` (docs). `09` and `10` (BDS) only if `02`
said feasible. `99` review. Cards that touch the same files run one at a time.

---

### T-M9-01 · Spike: an offline Bedrock client through Geyser and Floodgate on paper@26.3
- tier: opus
- depends: T-M8-99
- modules: docs/platforms, scripts
- adrs: 0004, 0005, 0006, 0016
- specs: client-driver.md
- test-first: no

**Goal.** Turn "offline Bedrock through Geyser" from unverified into facts, with commands that were run, before any design.

**Do.**
- Time box: 2 days. Start by hand (Docker, `scripts/dockerlock.sh`, files copied in, no bind mount) `paper@26.3` with the
  newest Geyser and Floodgate builds for it, each named with version and sha256 before download.
- Join with CloudburstMC Protocol (Java) and with `bedrock-protocol` (Node, sidecar), each: Geyser `auth-type: offline`, then
  Floodgate. Record per join: the Bedrock protocol number and game version, the packets up to spawn, whether any Microsoft or
  Xbox host is contacted (a logging proxy as the only route out, as T-M8-07 did), the name and UUID the server sees, and
  whether the Bukkit agent's `players` reads the player.
- Record what Geyser and Floodgate fetch at start (mappings, the Floodgate key) and whether each can be pinned.
- Try UDP: Testcontainers port exposure for RakNet, or a driver on the cell network. Record which works.
- Count every join; a join timeout is listed with its stage. Ask the maintainer before pulling any image or downloading any
  jar or npm package not already pinned, naming it, its digest and size.
- Write `docs/platforms/geyser.md` (Host, Agent notes, Capabilities, Gotchas) from what ran.

**Don't.**
- Use a Microsoft or Xbox account, a token, or any secret. Commit production code, a `versions.yml` pin or a dependency
  (a helper script stays under `scripts/spike/`).
- Claim a route works because the docs say so. Only a run counts.

**Acceptance.**
- [x] `geyser.md` states, per driver and per auth route, the join verdict with the command output summary and the join count.
- [x] Every outbound host seen at start and at join is listed; a Microsoft or Xbox host at join is a "not offline" verdict.
- [x] A plain verdict: offline Bedrock through Geyser feasible (with the driver and route), or not, with the narrower scope
  proposed (simulated tier only). The gap is not hidden.

**Result (2026-11-17, [geyser.md](../../platforms/geyser.md)).** Feasible on `paper@26.3` with Geyser 2.12.1 build 1303 and
Floodgate 2.2.5 build 141, with CloudburstMC Protocol (Java) and `bedrock-protocol` (Node, `raknet-native`), on both routes
(`offline` without Floodgate, `floodgate`), only with three conditions: `validate-bedrock-login: false`, `bedrock.transport:
both`, and a stand-in for two Microsoft discovery names that Cloudburst's login code fetches at the first login (without it
the join fails offline, or contacts Microsoft online, which is "not offline"). 59 Bedrock joins, 48 spawned; the 42 with all
conditions in place were all green. No new image was needed; the Node driver on the cell network was not run (it would need one).

**Verify.**
Reviewer re-runs the documented commands for one driver and one route under `scripts/dockerlock.sh` and compares.

### T-M9-02 · Spike: a BDS behaviour pack as an agent (Script API, `@minecraft/server-net`)
- tier: opus
- depends: T-M8-99
- modules: docs/platforms, scripts
- adrs: 0002, 0005, 0006, 0011
- specs: agent-protocol.md
- test-first: no

**Goal.** Decide with runs whether a Bedrock Dedicated Server add-on can serve or relay the wire protocol, read-only.

**Do.**
- Time box: 2 days. Ask the maintainer first: the BDS source (an image by digest or the zip by sha256), its EULA and size.
  BDS is never committed or redistributed (AGENTS.md section 3).
- Boot BDS with a minimal behaviour pack using `@minecraft/server-net`; record the BDS version, the Script API module versions
  and the beta-API flag needed. Record whether the pack can make an HTTP request to the harness (it cannot listen) and the
  latency of a round trip.
- Sketch the inverted transport (the pack polls the harness for requests and posts answers) and run one read
  (`players`) and one event (`playerJoin`) through it, with a client from T-M9-01 if one joined BDS, else with no client.
- Write `docs/platforms/bds.md` from what ran.

**Don't.**
- Change the wire protocol or claim the agent rule (observe only) holds without showing the pack writes nothing.

**Acceptance.**
- [ ] `bds.md` states the BDS pin, the beta flag, whether the HTTP call works, and the measured round trip.
- [ ] A plain verdict: feasible (with the transport) or not feasible, with evidence. If not feasible, T-M9-09 and T-M9-10 are
  dropped and the BDS exit item moves to M10.

**Verify.**
Reviewer re-runs the documented boot and one round trip under `scripts/dockerlock.sh`.

### T-M9-03 · ADR-0018: Bedrock driver, Geyser topology and BDS agent (design, docs only)
- tier: opus
- depends: T-M9-01, T-M9-02
- modules: docs/adr, docs/specs, docs/architecture.md, docs/glossary.md, docs/plan
- adrs: 0002, 0004, 0006, 0008, 0015, 0016
- specs: client-driver.md, test-api.md, agent-protocol.md, versions-yml.md
- test-first: no

**Goal.** Decide, from the spikes, the Bedrock driver, the Geyser topology identity, and the BDS agent or its absence.

**Do.**
- Write ADR-0018: the driver library (in-process or sidecar) and its pin; `Edition` in the driver SPI; the Geyser topology
  identity (for example `geyser@<build>+paper@26.3`) and its `versions.yml` key (an additive schema bump); the auth route;
  the UDP binding; the capabilities and agent tokens; required rows only through ADR-0015.
- If T-M9-02 said feasible: the BDS host, the inverted transport and the protocol subset (and whether that is a protocol
  version bump). If not: say so and keep BDS out of M9.
- Rejected alternatives with reasons (for example "an Xbox test account").

**Don't.**
- Change code, `versions.yml` data or a schema file. Re-decide an accepted ADR.

**Acceptance.**
- [ ] ADR-0018 Accepted, listed in `docs/adr/README.md`, with rejected alternatives.
- [ ] Glossary, `client-driver.md` and `versions-yml.md` carry the new terms; `./gradlew docsCheck` passes.
- [ ] A decision list for the maintainer is in the PR (driver, auth route, new images or packages, BDS in or out).

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M9-04 · Schema, planner and driver SPI `Edition` for Geyser cells
- tier: sonnet
- depends: T-M9-03
- modules: proofstone-core, proofstone-protocol, versions.yml, docs/specs/versions-yml.md
- adrs: 0008, 0016, 0018
- specs: versions-yml.md, client-driver.md
- test-first: yes

**Goal.** `versions.yml` names the Geyser rows and the planner, selector and `printMatrix` handle the new identity.

**Do.**
- The additive schema bump and the rows from T-M9-01, all `experimental`. `Edition` (`JAVA`, `BEDROCK`) in the driver SPI,
  with `mcpl` and `mineflayer-ref` reporting `JAVA`.
- `CellPlanner`, `CellSelector`, `printMatrix --scope` for the new identity; a malformed one fails loudly.

**Don't.**
- Change how Paper, topology or loader cells select or run. Hand-edit a pin without a run behind it.

**Acceptance.**
- [ ] Planner, selector and matrix unit tests pass, including malformed input.
- [ ] `printMatrix --scope=pr` before and after is identical for every existing cell.
- [ ] `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`./gradlew :proofstone-core:test :proofstone-protocol:test printMatrix --scope=pr`, then `bash scripts/gates.sh --max-workers=1`

### T-M9-05 · Bedrock client driver
- tier: opus
- depends: T-M9-04
- modules: drivers
- adrs: 0004, 0006, 0007, 0018
- specs: client-driver.md
- test-first: yes

**Goal.** A driver that joins offline as ADR-0018 says and exposes the client view the conformance needs.

**Do.**
- The driver module (library pinned in the catalog, or the sidecar image by digest), join, chat and command, inventory read,
  form and window events as far as ADR-0018 lists them; every unknown packet field an error, never a default.
- Join timeouts name the stalled stage (as T-M6-14 does for Java).
- Unit tests with scripted packets; a real join through Geyser on `paper@26.3`, three invocations.

**Don't.**
- Use any online auth, add a retry, or a sleep. Add a runtime dependency the ADR did not name.

**Acceptance.**
- [ ] `./gradlew :drivers:<bedrock>:test` passes.
- [ ] The real join passes three invocations (Flake rule), join count and timeouts reported apart.

**Verify.**
`./gradlew :drivers:<bedrock>:test`, then the card's real-server task under `scripts/dockerlock.sh`.

### T-M9-06 · Geyser topology host (Paper, Geyser, Floodgate, Bukkit agent)
- tier: sonnet
- depends: T-M9-04
- modules: proofstone-core
- adrs: 0005, 0013, 0016, 0018
- specs: architecture.md §3
- test-first: yes

**Goal.** The extension boots a Geyser cell from `versions.yml` with Geyser and Floodgate pinned and the Bukkit agent.

**Do.**
- A host (or a topology kind of `TopologyHost`, as ADR-0018 says) that copies the pinned Geyser and Floodgate jars and their
  config, generates the Floodgate key per cell from memory and redacts it from every bundle (as the forwarding secret is).
- Readiness by Geyser's start line and agent `health`; a failed start reports the log tail and stops every container.
- Log-guard list for Geyser, empty at first, each line added with a reason.

**Don't.**
- Mount anything, commit a key, or let Geyser download an unpinned artefact at start.

**Acceptance.**
- [ ] Unit tests for the rendered config, pins, key redaction and copy list.
- [ ] A real-server test boots the row and reads `health`, three invocations. No container of ours runs after.

**Verify.**
`./gradlew :proofstone-core:test`, then the card's real-server task under `scripts/dockerlock.sh`.

### T-M9-07 · Test API: declare a Bedrock client (additive)
- tier: sonnet
- depends: T-M9-05, T-M9-06
- modules: proofstone-api, proofstone-core
- adrs: 0001, 0018
- specs: test-api.md
- test-first: yes

**Goal.** A test asks for a Bedrock client on a Geyser cell through the public API, and a non-Geyser cell skips it with the
capability named.

**Do.**
- The additive API ADR-0018 names (for example `Clients.bedrock(name)` and a capability), the planner hook, and the dual oracle
  for a Bedrock client's inventory.
- Label the PR `api-change`; api-baseline additions only.

**Don't.**
- Change an existing signature, or let the simulated tier (`SIMULATED_BEDROCK`) claim real Bedrock support.

**Acceptance.**
- [ ] `api-baseline.txt` diff shows additions only.
- [ ] Unit tests for the declaration, the skip and the refusal on a bridged or loader cell.

**Verify.**
`./gradlew :proofstone-api:test :proofstone-core:test`

### T-M9-08 · Conformance on the Geyser topology
- tier: sonnet
- depends: T-M9-07
- modules: conformance, proofstone-core
- adrs: 0003, 0015, 0018
- specs: specs/self-tests.md §1
- test-first: yes

**Goal.** The Geyser row proves the round trips the Java rows prove, as far as a Bedrock client can.

**Do.**
- Scenarios C-G01 (join, the agent sees the Floodgate name), C-G02 (command reply), C-G03 (inventory read, dual oracle),
  C-G04 (a plugin's Bedrock-only branch with a real Bedrock client, the counterpart of C-B01). A scenario a Bedrock client
  cannot do is skipped with the reason, never passed.
- Record each quirk in `geyser.md` and assert it as it is.

**Don't.**
- Weaken an assertion to match Geyser's translation. Skip silently.

**Acceptance.**
- [ ] `:conformance:run -Dproofstone.cells=<geyser row>` green three invocations, skips reasoned and listed.
- [ ] The ADR-0015 table for the row is in the PR (promotion is a separate decision).

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=<geyser row> --no-parallel --max-workers=1`

### T-M9-09 · BDS host and script agent (only if T-M9-02 said feasible)
- tier: opus
- depends: T-M9-03
- modules: platforms, proofstone-core
- adrs: 0002, 0005, 0006, 0018
- specs: agent-protocol.md
- test-first: yes

**Goal.** A BDS cell boots with the script agent and serves the protocol subset ADR-0018 names, observe-only.

**Do.**
- The BDS host (pinned as ADR-0018 says, never committed), the behaviour-pack agent with the inverted transport, golden
  contract tests for the subset, an observe-only test (the pack writes nothing), and a real-server boot test.

**Don't.**
- Start this card if T-M9-02 said not feasible; close it with a link to the spike instead.

**Acceptance.**
- [ ] Golden contract tests for the subset pass. The observe-only test exists and passes.
- [ ] The real-server boot test passes three invocations.

**Verify.**
The card's unit tests, then its real-server task under `scripts/dockerlock.sh`.

### T-M9-10 · BDS conformance subset (only if T-M9-02 said feasible)
- tier: sonnet
- depends: T-M9-05, T-M9-09
- modules: conformance
- adrs: 0003, 0015, 0018
- specs: specs/self-tests.md §1
- test-first: yes

**Goal.** The BDS row passes the scenarios its protocol subset allows, with the Bedrock driver joining natively.

**Do.**
- Run the Geyser scenarios that apply to BDS; skip the rest with the missing capability named.

**Don't.**
- Start this card if T-M9-02 said not feasible.

**Acceptance.**
- [ ] `:conformance:run -Dproofstone.cells=<bds row>` green three invocations, skips reasoned.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=<bds row> --no-parallel --max-workers=1`

### T-M9-11 · Negative controls: Floodgate key mismatch, refused Bedrock protocol, broken pack
- tier: sonnet
- depends: T-M9-08
- modules: examples/bad-tests, proofstone-core, scripts
- adrs: 0003, 0007, 0018
- specs: specs/self-tests.md §3
- test-first: yes

**Goal.** Prove the Bedrock paths can fail, as T-M7-13 and T-M8-11 did.

**Do.**
- Bad tests, each pinned to its cell and refused on another: a Floodgate key mismatch (INVALID naming the hop), a Bedrock
  protocol the Geyser build refuses (INVALID with Geyser's message), and, if BDS is in, a behaviour pack that fails to load.
- A `metaBedrockTest` expecting each verdict; a fault that passes fails the meta test.

**Don't.**
- Treat a join timeout caused by the fault as the expected verdict: assert the specific reason.

**Acceptance.**
- [ ] Each fault gives its verdict with the expected message; the clean runs are green, three invocations.
- [ ] `metaTest`, `metaTopologyTest` and `metaModTest` still give every expected verdict (one run each, XML read).

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaBedrockTest --no-parallel --max-workers=1`

### T-M9-12 · Docs, example and platform status for Bedrock
- tier: sonnet
- depends: T-M9-11
- modules: docs, examples
- adrs: 0018
- specs: test-api.md
- test-first: no

**Goal.** A plugin author can test a Bedrock path through Geyser from the docs alone.

**Do.**
- A user guide page, troubleshooting for the M9 findings, a small example on the Geyser row, `docs/platforms.md` status at the
  level the evidence supports (`experimental` until ADR-0015 is met).

**Don't.**
- Claim a status, version or flake rate that was not run.

**Acceptance.**
- [ ] `bash scripts/gates.sh --max-workers=1` passes.
- [ ] The example runs green on the Geyser row, three invocations, XML read.

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M9-99 · M9 milestone review
- tier: opus
- depends: T-M9-01, T-M9-02, T-M9-03, T-M9-04, T-M9-05, T-M9-06, T-M9-07, T-M9-08, T-M9-11, T-M9-12
- modules: docs/plan, docs/robustness.md, docs/platforms.md
- adrs: 0010, 0014, 0015, 0018
- specs: vision.md
- test-first: no

**Goal.** Decide whether the M9 exit criterion is met, from runs, not from PR descriptions, and refine M10 into cards.

**Do.**
- Read the code and the XML. Re-run gates, `:conformance:smoke`, `:conformance:run` on each Geyser row (and the BDS row if
  built) and on one loader and one topology row (three invocations each), the meta tasks, and
  `bash scripts/release-mutation-check.sh` at HEAD. One Docker run at a time under `dockerlock`.
- Report Bedrock join-timeout and log-guard rates apart from the Paper, topology and loader numbers.
- Review `docs/robustness.md` R13 against the code. Check diffs for a weakened test, a new runtime dependency, a workflow, a
  committed secret (the Floodgate key above all). API v0 additive only.
- Re-score the exit criterion in ROADMAP "M9 review", re-estimate M10, and refine M10 into cards. Stop for approval.

**Don't.**
- Start M10, tag, or publish. Round up a partial result.

**Acceptance.**
- [ ] ROADMAP has an "M9 review" section with the exit criterion re-scored row by row, each with evidence and a verdict.
- [ ] Bedrock flake numbers and reasons are stated, with the run count.
- [ ] An M10 card file exists in the standard card format and `docsCheck` passes.

**Verify.**
`bash scripts/gates.sh --max-workers=1`
