# M7: Proxies (Velocity, BungeeCord)

Refined from the M7 epic in [M7-M10-epics.md](M7-M10-epics.md) on 2026-11-09 (after the M6 review addendum). **Not approved yet.**
No card here is started. The maintainer approves the plan, or changes it, before any card is picked up.

**Exit criterion.** All of the following must be true, each shown by a real-server run with the XML read:
- A Velocity topology (one proxy, two Paper backends with the Bukkit agent) passes the topology conformance scenarios P01 to P05
  on `paper@26.3` backends.
- A BungeeCord topology passes the same scenarios, with legacy forwarding.
- The test API can address a backend (`Topology.server("lobby")`) and switch a client (`client.transferTo("survival")`), and the
  change is additive to API v0 (label `api-change`, no existing signature changed).
- A forwarding misconfiguration (a wrong secret, forwarding off) is a loud red, never a pass (card T-M7-13).
- Every acceptance above has the flake evidence required by the **Flake rule** below.

## Context the cards rely on

- A cell today is one platform at one version (`Cell`, `versions.yml` schema 2). A bridged cell adds one ViaProxy container
  (ADR-0004). A topology adds a **proxy container plus N backend containers** on one Docker network, so the core's "one cell = one
  server" assumption (`PaperHost`, `CellInvocation`, `HostAgent`) is the largest change in M7. Card T-M7-02 decides the model
  before code.
- The Bukkit agent's HTTP server, envelope, auth and log capture live inside `platforms/bukkit/agent`. A proxy agent needs the same
  code, so T-M7-04 extracts it first (no behaviour change).
- The wire protocol changes only through a card that says so (AGENTS.md section 3). T-M7-03 is that card.
- The mcpl driver has one join per session today. A proxy server switch moves a live session from one backend to another
  (configuration state again on 1.20.2+), which the driver has never done. T-M7-09 covers it, with a real server.
- **Unverified, to be settled by the T-M7-01 spike, not assumed:** which Velocity and BungeeCord builds accept the protocol 777
  client (`paper@26.3`, `drivers.mcpl.protocol`); the exact backend settings for each forwarding mode on Paper 26.3; whether the
  Velocity and BungeeCord jars can be pinned by build number and sha256 the way Paper rows are; whether `itzg/mc-proxy` is a usable
  host image. The cards below say what they will do if the answers are bad.

## Flake rule (applies to every real-server acceptance in this file)

A topology join has **two hops** (client to proxy, proxy to backend), so it has at least twice the exposure of the plain join. The join-timeout family is
bounded (3 of 61 Gradle-example runs on `paper@26.3`) and not explained (ROADMAP "M6 review addendum", T-M6-10). T-M6-14 names the
stalled handshake stage in INVALID. For every card whose acceptance says "real server":

1. One cell per invocation, under `scripts/dockerlock.sh`, `--no-parallel --max-workers=1`. Read the XML: `skipped`, `failures`
   and `errors` are 0 except reasoned skips, which the PR lists.
2. **Three separate invocations**, each reported, all green, for the acceptance to be ticked. This is evidence, not a retry: no
   invocation is discarded, and a red in any of the three is not replaced by a later green.
3. A red is a finding. Before the next run, copy its XML, bundle and container logs (proxy and each backend) aside. Name its verdict
   type (`join-timeout`, `INVALID`, and so on) and, for a join timeout, the stalled stage from the T-M6-14 message and which hop it was.
   Never raise a timeout, never add a retry. The card stays open and the red goes into ROADMAP "M7 findings".
4. A join timeout is never a harness pass. If it recurs in M7 runs, the milestone review (T-M7-99) reports the rate separately from
   the M6 numbers.

## Estimate

**3 focused weeks** (the epic said 2). 15 cards including the review. Reasons it is more than 2: a new multi-container orchestration
layer, a new agent family twice over (Velocity is Java 17+ and a different plugin model, BungeeCord is Java 8), a driver
state-machine change, an additive API surface, and runs that boot three or four containers each (slower, and exposed to the join
flake twice). History says the work itself runs faster than the estimate (M1 to M6 took about one agent-day each plus the
maintainer's gating), so the honest range is **1.5 to 3 focused weeks**, with the unknowns in T-M7-01 as the main uncertainty. The
estimate is re-made in T-M7-99.

**Cards: 15** (14 plus the review). Tiers: 1 Haiku, 9 Sonnet, 5 Opus (tiers are hints, any model may take any card, ADR-0014).

## Order

`01` spike, then `02` and `03` (design and protocol), then `04` to `06` (agents), `07` and `08` (host and cells), `09` and `10`
(driver and API), `11` to `13` (conformance and the negative controls), `14` docs, `99` review. Cards that touch the same files
run one at a time.

---

### T-M7-01 · Spike: Velocity and BungeeCord boot, forward and join on paper@26.3
- tier: opus
- depends: T-M6-14
- modules: docs/platforms, scripts
- adrs: 0004, 0005, 0006
- specs: client-driver.md §5
- test-first: no

**Goal.** Turn the "unverified" list in the M7 context into facts, with commands that were run, before any design is frozen.

**Do.**
- Start by hand (Docker, `scripts/dockerlock.sh`, no committed bind mounts) a Velocity and a BungeeCord proxy with two `paper@26.3`
  backends on one network. Record, per proxy: the jar source and how to pin it (build number, sha256), the Java image that runs it,
  whether the newest client protocol (777) joins it natively, the config files and the exact settings of each forwarding mode on both sides.
- Join with the mcpl driver (offline, no Mojang auth) through each proxy, switch backend with `/server <name>` typed by the client, and write down
  what the driver sees (state changes, join and respawn packets).
- Decide which Waterfall and which bridged-client combinations work or do not, so T-M7-02 can scope them out with evidence.
- Ask the maintainer before pulling any new image or downloading any jar not already pinned, naming the image, digest and size.
- Write `docs/platforms/velocity.md` and `docs/platforms/bungeecord.md` (Host, Agent notes, Capabilities, Gotchas) from what ran.

**Don't.**
- Commit production code, add a pin to `versions.yml` or add a runtime dependency. Any helper script stays under `scripts/` and is marked a spike.
- Claim a combination works because the docs say so. Only a run counts.

**Acceptance.**
- [ ] Both platform docs state, per proxy: the pinnable jar source, the Java tag, the forwarding recipe for each side, and a verdict on the protocol 777 client with the command output summary.
- [ ] A client switch between two backends was run on each proxy and its transcript is quoted (the server's own messages, not predicted).
- [ ] Every join in the spike is counted. Any join timeout is listed with its stage (T-M6-14 message). Evidence: the counts in the PR.
- [ ] The result says plainly if a proxy cannot accept the 777 client: the card then proposes the bridged option or drops that proxy from the required rows. It does not hide the gap.

**Verify.**
Reviewer re-runs the documented commands for one proxy under `scripts/dockerlock.sh` and compares.

### T-M7-02 · ADR-0016: topology model, rows and scope (design, docs only)
- tier: opus
- depends: T-M7-01
- modules: docs/adr, docs/specs, docs/architecture.md, docs/glossary.md, docs/plan
- adrs: 0002, 0003, 0005, 0008
- specs: versions-yml.md, test-api.md, self-tests.md
- test-first: no

**Goal.** Decide how a topology is a cell, in a way that the existing single-server code keeps working unchanged.

**Do.**
- Write ADR-0016: a **topology cell** is one proxy row plus a backend row set; what its identity string is for `-Dproofstone.cells=`, reports and bundles (for example `velocity@3.x+paper@26.3`); how `versions.yml` (schema 3, an additive bump) names proxy platforms and which proxy rows are `required`; what `printMatrix --scope` does with topologies; how a bridged backend composes (answer from T-M7-01: required rows are native only, or not).
- Decide the **dual oracle for a topology**: client oracle (what the client sees on the proxy and after a switch) and agent oracle (proxy agent for the proxy's view, the backend agents for each backend), and what is `DISAGREE` when they differ.
- Decide the server switch term. `transferTo` means a proxy server switch (`/server`), **not** the vanilla 1.20.5 transfer packet. Say so in the glossary.
- Decide isolation: `PER_CLASS` topology, reset between tests, and which resets run on which agent (every backend, plus proxy player state).
- Decide the Waterfall question and the forwarding-secret rule: generated per cell, copied in, never written to a file in the repo, never in a bundle (extends the token redaction rule).
- Add glossary terms (topology, backend, proxy agent, server switch) and update `docs/architecture.md` §2.3, §3 and `docs/platforms.md`.

**Don't.**
- Change code, `versions.yml` data or any schema file. Change the Bukkit agent, or re-decide an accepted ADR (amend by a new ADR only).

**Acceptance.**
- [ ] ADR-0016 Accepted, listed in `docs/adr/README.md`, with rejected alternatives (for example "one cell per server with a shared network").
- [ ] The glossary and `docs/specs/versions-yml.md` carry the new terms and the schema 3 additive rule; `./gradlew docsCheck` passes.
- [ ] The ADR says what happens to the join-flake accounting (per-hop logs, which hop is named).
- [ ] A decision list for the maintainer is in the PR: Waterfall in or out, host image choice, rows to require.

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M7-03 · Protocol 1.2: proxy endpoints, schemas and golden files
- tier: sonnet
- depends: T-M7-02
- modules: proofstone-protocol, docs/specs
- adrs: 0002, 0016
- specs: specs/agent-protocol.md §2, §4
- test-first: yes

**Goal.** Add the wire contract for proxies, so the agents and the core build against the same schemas.

**Do.**
- Add `GET /v1/proxy/servers` (name, address, online state, player count), and the player's current server and previous server on the proxy's `GET /v1/players/{name}` reply (an additive optional field, `null` on a backend). Do not add `POST /v1/proxy/servers/{name}/connect`: ADR-0016 §5 keeps the switch client-side (`/server`).
- Add the schema, the golden request and reply files, and the error case (`SERVER_NOT_FOUND`, 404). Bump the protocol minor version as spec §4 says, and update the golden files.
- Keep `health` reporting the proxy platform id and the proxy version in the existing fields.

**Don't.**
- Change an existing field, remove one, or reuse a code. Touch the Bukkit agent's behaviour: the Bukkit agent must still reply to the old goldens unchanged.

**Acceptance.**
- [ ] `./gradlew :proofstone-protocol:test` passes, including a round-trip test for each new schema.
- [ ] The Bukkit agent's `GoldenContractTest` still passes with no golden edited except additive new files and the version number.
- [ ] `docs/specs/agent-protocol.md` §2 lists the new endpoints and §4 records the bump.

**Verify.**
`./gradlew :proofstone-protocol:test :platforms:bukkit:agent:test`

### T-M7-04 · Extract the agent core shared by Bukkit and proxy agents
- tier: sonnet
- depends: T-M7-03
- modules: platforms/agent-core, platforms/bukkit/agent
- adrs: 0002, 0016
- specs: specs/agent-protocol.md §1, §5
- test-first: no

**Goal.** One implementation of the envelope, bearer auth, HTTP server, log buffer and routes that do not touch the game, so the proxy agents reuse them.

**Do.**
- Create `platforms/agent-core` (Java 8 bytecode, jackson shaded and relocated like the Bukkit agent) holding `protocol/*`, `server/*` and the platform-neutral parts of `LogBuffer`. The Bukkit agent depends on it. Add the module README (Purpose, Boundaries, Public surface, How to test).
- No behaviour change. Every Bukkit test and golden file passes untouched.
- Keep relocation names unchanged for the Bukkit jar, so a plugin that shades it is not affected (it is not a public artefact).

**Don't.**
- Change a wire byte, a log line or an endpoint. Add a runtime dependency. Move anything to `proofstone-api`.

**Acceptance.**
- [ ] `./gradlew :platforms:agent-core:test :platforms:bukkit:agent:test` passes with no test edited except imports.
- [ ] `AgentContractIT` on `paper@26.3` is green (one invocation, XML read, skipped 0 failures 0 errors 0), plus the flake rule's three invocations.
- [ ] The Bukkit agent jar's content list is unchanged apart from the extracted package names (PR shows a before and after list).
- [ ] `docsCheck` passes (module README sections present).

**Verify.**
`./gradlew :platforms:agent-core:test :platforms:bukkit:agent:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :platforms:bukkit:agent:integrationTest --no-parallel --max-workers=1`

### T-M7-05 · Velocity agent
- tier: sonnet
- depends: T-M7-04
- modules: platforms/velocity/agent
- adrs: 0002, 0016
- specs: specs/agent-protocol.md §2
- test-first: yes

**Goal.** A Velocity plugin that serves the proxy subset of the protocol (`health`, `players`, `command`, `events`, `log`, `proxy/servers`) and observes without interfering.

**Do.**
- Build `platforms/velocity/agent` (the Java baseline comes from T-M7-01; Velocity 3.x runs on Java 17 or newer). It reads the port and the token from the environment as the Bukkit agent does and binds on the cell network only.
- Events through a reflective listener at the last priority and read-only (Velocity's `PostOrder.LAST`), getters serialised like the Bukkit probes.
- Log capture from Velocity's logger (Log4j) with stack traces.
- The agent's own thread model: Velocity events are asynchronous, so the agent uses Velocity's scheduler for anything that is not thread-safe and says so in the module README.
- The golden contract test replays every golden file against a mock proxy. Add an integration test that boots a real Velocity (`velocity@<row from T-M7-01>`) with the agent and calls `health`, `proxy/servers`, `command` and `log` (no backend needed for these).

**Don't.**
- Cancel, modify or delay a proxy event, register commands, change forwarding or touch the player list. Add a runtime dependency beyond the shaded and relocated set.

**Acceptance.**
- [ ] The golden contract test passes. Evidence: `./gradlew :platforms:velocity:agent:test`.
- [ ] The real-server integration test passes against Velocity, with the flake rule's three invocations. `players` and `events` are NOT claimed here: they need a backend and are covered by T-M7-11.
- [ ] A test proves the agent registers no command and no cancelling listener (observe-only rule, AGENTS.md section 3).
- [ ] The module README and `docs/platforms/velocity.md` Agent notes are updated.

**Verify.**
`./gradlew :platforms:velocity:agent:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :platforms:velocity:agent:integrationTest --no-parallel --max-workers=1`

### T-M7-06 · BungeeCord agent
- tier: sonnet
- depends: T-M7-04
- modules: platforms/bungee/agent
- adrs: 0002, 0016
- specs: specs/agent-protocol.md §2
- test-first: yes

**Goal.** The same protocol subset for BungeeCord, at Java 8 bytecode.

**Do.**
- Build `platforms/bungee/agent` against the BungeeCord API, Java 8 bytecode, shaded and relocated like the Bukkit agent. Events at the lowest priority, read-only.
- Capture the log (java.util.logging), including stack traces. Handle BungeeCord's asynchronous event model as in T-M7-05.
- Golden contract test against a mock proxy, and a real-server integration test with `health`, `proxy/servers`, `command` and `log`.
- Waterfall: follow the T-M7-02 decision. If it is in scope, the same jar must load on it and the integration test runs on it too. If out, say so in the README.

**Don't.**
- Register commands, cancel events, or edit the proxy config at run time. Use Bungee-only API unavailable on the pinned row without a version check.

**Acceptance.**
- [ ] `./gradlew :platforms:bungee:agent:test` passes, golden files replayed.
- [ ] The real-server integration test passes on BungeeCord, with the flake rule's three invocations. `players` and `events` are covered by T-M7-11.
- [ ] The observe-only test exists and passes.
- [ ] Module README and `docs/platforms/bungeecord.md` Agent notes updated.

**Verify.**
`./gradlew :platforms:bungee:agent:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :platforms:bungee:agent:integrationTest --no-parallel --max-workers=1`

### T-M7-07 · Topology host: proxy and N backends on one network
- tier: opus
- depends: T-M7-05, T-M7-06
- modules: proofstone-core
- adrs: 0005, 0006, 0016
- specs: architecture.md §3, specs/self-tests.md §3
- test-first: yes

**Goal.** Start a proxy container and N Paper backends together, forwarding configured, with every file copied in and every pin from `versions.yml`.

**Do.**
- Add `TopologyHost` (and a `ProxyHost` for the proxy container) next to `PaperHost` and `ViaProxyHost`. It starts the backends first, then the proxy, on one network with aliases (`server-lobby`, `proxy`), has the image download the proxy jar by pinned build and checks its sha256 in the container before start, as for Paper rows (ADR-0016 §2), copies in the agent, the generated forwarding secret and the proxy and backend config files, and waits readiness by log line and agent `health` for each container.
- Generate the forwarding secret per cell (`SecureRandom`, like the agent token). It is written only inside containers and never to the bundle, a log or the report; a unit test greps the bundle for it.
- Forwarding settings per proxy and backend, exactly as T-M7-01 documented them, set through copied files, never bind mounts. Backend `online-mode` is set explicitly on every row.
- On a join timeout, extend the T-M6-14 server's-view output with the proxy log tail and the backend log tail, and name the hop (client to proxy, or proxy to backend) in the message.
- All containers stop on failure. A failed start reports every container's log tail. None of ours remain running.

**Don't.**
- Touch `ohlidame-*` containers. Add a retry or raise a timeout. Mount anything. Publish a backend port beyond what debug mode already allows.

**Acceptance.**
- [ ] Unit tests cover: config rendering for each proxy and mode, the secret never reaching a bundle, start order, cleanup on a failed second backend.
- [ ] A real-server test starts Velocity plus two Paper backends and reads `health` on all three. Same for BungeeCord. Flake rule: three invocations each.
- [ ] A forced failure (a backend that never becomes ready) removes every container of ours and its INVALID message names the container.
- [ ] A forced join stall (proxy up, backend stopped) yields a message that names the proxy-to-backend hop.
- [ ] No container of ours is running after any run (`docker ps` names listed, `ohlidame-*` ignored).

**Verify.**
`./gradlew :proofstone-core:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :proofstone-core:topologyTest --no-parallel --max-workers=1` (the task name is created by this card)

### T-M7-08 · Pack, rows and cell planning for topologies
- tier: sonnet
- depends: T-M7-07
- modules: proofstone-core, versions.yml, docs/specs/versions-yml.md
- adrs: 0008, 0015, 0016
- specs: versions-yml.md
- test-first: yes

**Goal.** `versions.yml` names the proxy platforms, the planner turns them into topology cells, and the selector, report and `printMatrix` understand the new identity.

**Do.**
- Schema 3 (additive): proxy platforms with `host` pins and `rows`, using the pins from T-M7-01. New rows start at `experimental`. Promotion to `required` follows ADR-0015, on local per-row evidence.
- `CellPlanner`, `CellSelector` (`-Dproofstone.cells=velocity@<ver>+paper@26.3`) and `printMatrix --scope` handle topology cells. A malformed or unknown topology selector fails loudly.
- The report and bundle carry the topology identity and the proxy and backend pins. `cell.json` records them.
- `./gradlew updateVersions` is not extended here (a separate card if wanted). The proxy rows are hand-pinned, as the earlier rows were.

**Don't.**
- Hand-edit a pin without a run behind it. Change how single-server cells select or run. Promote a row to `required` without the evidence.

**Acceptance.**
- [ ] The planner, selector and matrix unit tests pass, including malformed input (empty backend list, unknown proxy, duplicate server name).
- [ ] The versions-yml schema check accepts schema 3 and still accepts the existing file (no cell changed). Evidence: `printMatrix --scope=pr` output before and after, identical for the 21 existing cells.
- [ ] `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`./gradlew :proofstone-core:test :proofstone-core:printMatrix`, then `bash scripts/gates.sh --max-workers=1`

### T-M7-09 · mcpl driver: survive a proxy server switch
- tier: sonnet
- depends: T-M7-07
- modules: drivers/mcpl, proofstone-core
- adrs: 0004, 0016
- specs: specs/client-driver.md §2, §4
- test-first: yes

**Goal.** A client stays usable when the proxy moves it to another backend, and the driver reports the switch.

**Do.**
- Make `McplSession` handle a second configuration and join sequence on a live connection (and the respawn path for pre-1.20.2 backends if the T-M7-01 rows use one). State goes PLAY, then (re)configuration, then PLAY again, with an await helper that completes only when the new join is seen.
- Add `ClientSession.awaitServerSwitch(...)`, based on `Await`, with an explicit timeout. No sleeps. Velocity's command rate limit is a harness setting (`command-rate-limit = 0`, ADR-0016 §5), rendered by T-M7-07; a `/server` that reaches a backend instead is a loud red quoting the backend's reply.
- Keep window, inventory and position views consistent: they must reset to the new server's state, never keep stale slots (the server is authoritative, client-driver spec §2).
- Unit-test the state machine with a scripted packet sequence, then the real path: a Velocity topology, one client, `/server survival` and back, twice.

**Don't.**
- Predict state after the switch. Swallow a disconnect during the switch as success (a kick mid-switch is a failure with its reason). Change the single-server join path.

**Acceptance.**
- [x] Unit tests pass for: normal switch, kick during switch, switch to an unreachable server (the proxy keeps the client on the first server), double switch.
- [x] Real server: a Velocity and a BungeeCord topology, client switches lobby to survival and back, window and inventory views are the new server's, not stale. Flake rule: three invocations each.
- [x] A join timeout during the switch names the stage and the hop (T-M7-07 message).
- [x] The C01 to C08 conformance on a plain `paper@26.3` cell is unchanged (one invocation, XML read).

**Verify.**
`./gradlew :drivers:mcpl:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=velocity@<row>+paper@26.3 --no-parallel --max-workers=1`

### T-M7-10 · Test API: `Topology`, `@Topology`, `client.transferTo`
- tier: opus
- depends: T-M7-08, T-M7-09
- modules: proofstone-api, proofstone-core, docs/specs/test-api.md
- adrs: 0001, 0003, 0016
- specs: specs/test-api.md
- test-first: yes

**Goal.** A test addresses backends and switches clients through the public API, additively, with the dual oracle applied.

**Do.**
- Add `Topology` (`server(String)` returns the backend's `Agent`, `proxy()` returns the proxy's `Agent`, `servers()`), the `@Topology` annotation that selects a topology for a test class, and `Client.transferTo(String)` (client oracle: the server switch is seen, then await; agent oracle: the proxy agent's view and the target backend agent say the player is there, the source says not).
- Add the capabilities `PROXY` and `TOPOLOGY` (additive enum values). Declared by the proxy packs only. `@Requires(TOPOLOGY)` skips with a reason on a single-server cell.
- Label the PR `api-change`. No existing signature, name or behaviour changes. Update `docs/specs/test-api.md` and the API freeze note.
- The extension wires reset per backend and proxy, the log guard per container (allowlists in the pack), the bundle per container, and the leak detector across backends.

**Don't.**
- Change `Client`, `Agent` or `Clients` signatures. Let `@Topology` run a test on a single-server cell. Report a test as passed when `transferTo` raced the agent view: use `await`.

**Acceptance.**
- [ ] A unit test and a real-server test show `Topology.server("lobby")` and `transferTo` work on Velocity and BungeeCord, with the flake rule's three invocations each.
- [ ] A test on a single-server cell with `@Topology` is not run silently: it is SKIPPED with a reason (or INVALID if misdeclared), never a pass. Evidence: XML.
- [ ] A binary and source compatibility check against the 0.1 API (the existing check, or `javap` diff listed in the PR) shows additions only.
- [ ] A bundle written by a failing topology test lists a log per container and names no secret.

**Verify.**
`./gradlew :proofstone-api:test :proofstone-core:test`, then a topology run as in T-M7-09.

### T-M7-11 · Conformance P01 and P02: join through the proxy, forwarding check
- tier: sonnet
- depends: T-M7-10
- modules: proofstone-core (conformance), conformance
- adrs: 0003, 0016
- specs: specs/self-tests.md §3
- test-first: yes

**Goal.** Every topology cell proves that a client joins through the proxy and that the backend sees the right identity.

**Do.**
- **P01** join through the proxy: the client joins; the proxy agent lists the player and its current server; the backend agent lists the same player. Dual oracle.
- **P02** forwarding: the UUID and name the backend sees equal what the proxy sees, and the UUID equals the offline UUID of the name (online mode is off in tests, say so in the scenario). For legacy forwarding also the forwarded address. The backend refuses a direct join when forwarding is required (Velocity modern: the backend kicks a direct client; BungeeCord legacy: record what the backend does, as `ip_forward` is not a security feature).
- Both scenarios go into the cached per-cell conformance. A red makes every test on that topology cell INVALID (as the Bukkit conformance does).
- Add the `players` and `events` checks left open in T-M7-05 and T-M7-06 (proxy `PlayerState`, a proxy event probe seeing the join).

**Don't.**
- Skip a scenario silently or weaken an assertion to match a quirk. A quirk is documented in `docs/platforms/` and asserted as it is.

**Acceptance.**
- [x] P01 and P02 pass on `velocity@<row>+paper@26.3` and `bungeecord@<row>+paper@26.3`. Evidence: XML, skipped 0 failures 0 errors 0, three invocations per cell (flake rule).
- [x] The direct-join behaviour on each backend is recorded in the platform doc with the observed message.
- [x] A unit test shows a forwarding mismatch makes the scenario DISAGREE, not a pass.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=<topology cell> --no-parallel --max-workers=1`

### T-M7-12 · Conformance P03 to P05: switch, plugin messaging, proxy command
- tier: sonnet
- depends: T-M7-11
- modules: proofstone-core (conformance), platforms/bukkit/fixture, conformance
- adrs: 0003, 0016
- specs: specs/self-tests.md §3
- test-first: yes

**Goal.** Cover the cross-server behaviour a proxy plugin author actually tests.

**Do.**
- **P03** switch: the client moves from `lobby` to `survival` and back. Client sees each new join; the proxy agent and both backend agents agree on where the player is. The previous backend no longer lists the player.
- **P04** plugin messaging round trip: the backend fixture plugin sends a `GetServer` message on the `BungeeCord` channel; the proxy answers; the fixture records the reply and the proxy agent's event probe sees the message. Works on Velocity (its `bungee-plugin-message-channel` setting, from T-M7-01) and BungeeCord. Extend `platforms/bukkit/fixture` only for this.
- **P05** proxy command: a proxy console command through the agent's `command` runs on the proxy, and a client-typed proxy command (`/server`) is observed by the proxy agent's events. Records that command output is best-effort, like the Bukkit one.
- Reasoned skips only, with the missing capability named.

**Don't.**
- Add a sleep around the messaging round trip: use `await` on the fixture's recorded reply. Make the fixture do more than record.

**Acceptance.**
- [x] P03 to P05 pass on both topology cells. Evidence: XML, skipped 0 failures 0 errors 0, three invocations per cell (flake rule). Two reds on the way (kept, ROADMAP "M7 findings"): the first Velocity run (P04 probe, Velocity fires no event for BungeeCord-channel messages) and the first BungeeCord run (P02 address parser).
- [x] Any skip is reasoned and listed in the PR. P04 on Velocity does not require the event probe (reason in `velocity.md`); P04 is skipped, with its reason printed, on a backend without the fixture (user cells).
- [x] The fixture change is limited to the messaging record (diff shown).

**Verify.**
As T-M7-11.

### T-M7-13 · Negative controls: forwarding misconfiguration and a broken proxy
- tier: sonnet
- depends: T-M7-12
- modules: proofstone-core, examples/bad-tests, scripts
- adrs: 0003, 0007, 0016
- specs: specs/self-tests.md §3
- test-first: yes

**Goal.** Prove the topology conformance can fail, the way M4 proved the single-server verdicts can.

**Do.**
- Add purpose-built faults, switched on by system property like the existing `-Dproofstone.injectConformanceFault`: a wrong forwarding secret on the backend (Velocity) and forwarding off on one side (BungeeCord), and a proxy that never reaches one backend.
- `metaTest` entries expect INVALID with a message naming the container and the hop. A fault that passes is a failure of the meta test.
- Add the topology mutations to `scripts/release-mutation-check.sh` only if the maintainer approves widening it (a runtime cost). Otherwise run them by hand and record the result. Ask in the PR.

**Don't.**
- Edit the mutation check without approval. Treat a join timeout caused by an injected fault as the product flake: the fault tests assert the specific reason.

**Acceptance.**
- [x] Each injected fault gives a red with the expected verdict type and message; a run without the fault is green. Evidence: XML for both, three invocations of the clean run. (7 fault runs, 6 clean runs, ROADMAP M7 findings.)
- [ ] The existing `metaTest` set still reports all 8 verdicts as expected. (Open: the one run was red on a Paper download timeout at the two `paper@26.1.2` tasks; 6 of 8 verdicts given. ROADMAP M7 findings.)
- [x] The mutation-check question is recorded in the PR for the maintainer.

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaTest --no-parallel --max-workers=1`

### T-M7-14 · Docs, example and module READMEs
- tier: haiku
- depends: T-M7-13
- modules: docs, examples
- adrs: 0016
- specs: test-api.md
- test-first: no

**Goal.** A plugin author can run a topology test from the docs alone.

**Do.**
- A user guide page for topologies (a Velocity example with `@Topology`, `Topology.server`, `transferTo`) and the troubleshooting entry for a hop-named join timeout.
- A small example test in `examples/` that runs on a topology cell (only what ran in M7 conformance, no new claims).
- `docs/platforms.md` status for Velocity and BungeeCord set to the level the evidence supports (**experimental** until the rows pass the rule in ADR-0015). Module READMEs for new modules.

**Don't.**
- Claim a status, a version or a flake rate that was not run.

**Acceptance.**
- [ ] `bash scripts/gates.sh --max-workers=1` passes (`docsCheck` links, glossary, card format).
- [ ] The example runs green on one topology cell, three invocations, XML read.
- [ ] Every claim in the new docs has a run behind it in the PR.

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M7-99 · M7 milestone review
- tier: opus
- depends: T-M7-01, T-M7-02, T-M7-03, T-M7-04, T-M7-05, T-M7-06, T-M7-07, T-M7-08, T-M7-09, T-M7-10, T-M7-11, T-M7-12, T-M7-13, T-M7-14
- modules: docs/plan, docs/robustness.md, docs/platforms.md
- adrs: 0010, 0014, 0015, 0016
- specs: vision.md
- test-first: no

**Goal.** Decide whether the M7 exit criterion is met, from runs, not from PR descriptions, and refine M8 into cards.

**Do.**
- Read the code and the XML, not the PR text. Re-run: `bash scripts/gates.sh --max-workers=1`, `:conformance:smoke`, `:conformance:run` on each topology cell, `:examples:bad-tests:metaTest`, and `bash scripts/release-mutation-check.sh` at the current HEAD. One Docker run at a time under `dockerlock`.
- Report the **join-timeout rate for topology runs separately** from the M6 numbers, with the stalled hop and stage of every red (T-M6-14, T-M7-07). If any topology red is unexplained, say so and keep it open.
- Review the register (`docs/robustness.md` §5) against the new code: leak detector across backends, log guard per container, observe-only rule on both proxy agents, secret redaction.
- Check the diffs for a weakened test, a new runtime dependency, a workflow, or a committed secret. Confirm API v0 changed additively only.
- Re-score the exit criterion in ROADMAP "M7 review", re-estimate M8 to M10, and refine M8 into `docs/plan/tasks/M8-mods.md` (cards plus its own review card). Stop for approval.

**Don't.**
- Start M8, tag, or publish. Round up a partial result.

**Acceptance.**
- [ ] ROADMAP has an "M7 review" section with the exit criterion re-scored row by row, each with evidence, and a plain verdict.
- [ ] Topology flake numbers and reasons are stated, including "none observed" only if the run count is shown.
- [ ] `docs/plan/tasks/M8-mods.md` exists in the standard card format and `docsCheck` passes.

**Verify.**
`bash scripts/gates.sh --max-workers=1`
