# M8: Mods and data (Fabric, NeoForge, data packs, resource packs)

Refined from the M8 epic in [M7-M10-epics.md](M7-M10-epics.md) by the M7 review (T-M7-99, 2026-11-14). **Not approved yet.**
No card here is started. The maintainer approves the plan, or changes it, before any card is picked up.

**Exit criterion.** All of the following must be true, each shown by a real-server run with the XML read:
- A Fabric cell and a NeoForge cell, each on the newest row the T-M8-01 spike shows the `mcpl` client joins natively, boot with
  their mod agent and pass C01 to C08 (every skip reasoned in the XML).
- A test can put a mod under test (`/mods`), a data pack and a resource pack into a cell through the public API, additively to
  API v0 (label `api-change`, no existing signature changed).
- A data-pack scenario (a function result and a recipe check) passes on Paper and on Fabric, and is skipped with the
  capability named where `DATAPACKS_VANILLA` is missing.
- The driver reports a resource-pack prompt (URL, hash, required flag), answers it as the test says, and the server-side status
  is read by the agent. No rendering check.
- A broken mod, a data pack that fails to load and a resource pack with a wrong hash are each a loud red, never a pass (T-M8-11).
- Every acceptance above has the flake evidence the **Flake rule** below requires.

## Context the cards rely on

- **What M7 left that M8 inherits.** `platforms/agent-core` (envelope, auth, HTTP server, log buffer, Java 8 bytecode, T-M7-04)
  is the base of every new agent. The host copies files into containers and never mounts (ADR-0005), and the copied file is
  owned by root: on every Paper cell Paper logs `ERROR Could not save bukkit.yml ... (Permission denied)` at start, and BungeeCord
  backends add the same for `spigot.yml` (M7 review; the copy of `bukkit.yml` dates from T-M1-10). A mod host copies more
  config files than Paper, so T-M8-02 fixes the copy ownership first and asserts no such line.
- **External services.** A Paper backend behind BungeeCord silently fetched every player's profile from Mojang until T-M7-10
  turned it off (`HTTP 429` red). Every M8 host is offline-mode with no outbound service call the run depends on; the spike
  lists what each loader fetches at start (libraries, the Fabric installer, NeoForge's installer, mappings) and the host pins
  or pre-fetches it, by digest, as Paper rows are pinned.
- **The driver.** `mcpl` speaks the newest protocol (777 for `26.3`) and answers a login query with an empty answer (T-M7-11).
  NeoForge negotiates channels in the configuration phase, and Fabric API may send its own configuration tasks. Whether a
  vanilla client joins a modded server at all, and with which mods, is **unverified** and is the first question of T-M8-01.
- **Mappings and builds.** 26.1 and newer ship unobfuscated, so an agent built for them needs no mappings. Older rows need
  Loom (Fabric) or ModDevGradle (NeoForge) and an agent jar per Minecraft version. Stonecutter is the epic's proposal for the
  multi-version build; T-M8-01 decides whether it is used or whether M8 is newest-row only (bridged mod rows are out of scope
  until a run shows ViaProxy in front of a modded server works).
- **Data packs** already run on Paper (vanilla data packs are loaded by the server). The capability `DATAPACKS_VANILLA` was
  removed in the M5 review because no agent token reported it; M8 re-adds it with a token.
- **Resource packs** need a pack served over HTTP to the client. The pack server must not be the internet; the spike decides
  between the harness JVM (Testcontainers host port exposure) and a pinned static-file container (a new image needs approval).

## Flake rule (applies to every real-server acceptance in this file)

Same rule as M7 (`M7-proxies.md`, Flake rule), unchanged:

1. One cell per invocation, under `scripts/dockerlock.sh`, `--no-parallel --max-workers=1`. Read the XML: `skipped`, `failures`
   and `errors` are 0 except reasoned skips, which the PR lists.
2. **Three separate invocations**, each reported, all green, for the acceptance to be ticked. No invocation is discarded, and a
   red in any of the three is not replaced by a later green.
3. A red is a finding. Before the next run, copy its XML, bundle and container log aside (the bundle directory is deleted by the
   next run of the same task). Name its verdict type and, for a join timeout, the stalled stage (T-M6-14). Never raise a timeout,
   never add a retry. The red goes into ROADMAP "M8 findings".
4. Join timeouts on mod cells are counted separately from the single-server Paper numbers and the M7 topology numbers. Modded
   servers boot slower; a boot timeout is a host finding, not a join timeout, and is reported as such.
5. The known log-guard family `PSConf moved too quickly!` (ROADMAP "M6 pre-release findings" item 2, recurring in the M7 review)
   is counted as its own family, never allowlisted to make a run green without a card.

## Estimate

**4 focused weeks** (the epic said 4; M7 said 3 and took about 4 days of agent wall time for 15 cards, ROADMAP "M7 review").
13 cards including the review. Why it is not smaller: two new loaders with their own agent APIs and build tools (Loom,
ModDevGradle), a possible per-version agent build (Stonecutter), a driver change for configuration-phase mod channels and
resource packs, a resource-pack HTTP source, and the largest unknown so far: whether the vanilla `mcpl` client can join a
modded server at all. If T-M8-01 shows it cannot, the plan changes (a modded driver is a new ADR and at least 2 more weeks).
Honest range: **2 to 5 focused weeks**, re-made in T-M8-99.

**Cards: 13** (12 plus the review). Tiers: 1 Haiku, 6 Sonnet, 6 Opus (hints only, ADR-0014).

## Order

`01` spike, then `02` (host copy fix, independent of the spike) and `03` (ADR). Then `04` (schema and planner), `05` and `06`
(agents), `07` (mod host), `08` (conformance on mod hosts), `09` (data packs) and `10` (resource packs), `11` (negative controls),
`12` (docs and example), `99` review. Cards that touch the same files run one at a time.

---

### T-M8-01 · Spike: Fabric and NeoForge boot, agent hook and vanilla-client join on 26.3
- tier: opus
- depends: T-M7-99
- modules: docs/platforms, scripts
- adrs: 0004, 0005, 0006, 0013
- specs: client-driver.md §5
- test-first: no

**Goal.** Turn the unverified M8 context into facts, with commands that were run, before any design is frozen.

**Do.**
- Start by hand (Docker, `scripts/dockerlock.sh`, files copied in) a Fabric server and a NeoForge server on the newest
  Minecraft version each loader supports, with the `itzg/minecraft-server` image pinned by digest. Record per loader: the loader
  and installer versions and how to pin them (version, sha256), the Java tag, what the image downloads at start and from where,
  and whether that can be pinned or pre-fetched.
- Join with the `mcpl` driver (offline): first with no mod, then with Fabric API, then with a server-only mod. Record the
  configuration-phase packets the driver sees and whether the join completes. Do the same on NeoForge.
- Load the smallest possible server-side mod that opens an HTTP port from the environment (the agent's shape) and record the
  entrypoint and the earliest event usable for "server ready".
- Try one data pack (a function and a recipe) on Fabric and on Paper, and one resource pack prompt from `server.properties`
  served from the harness host, and record what the driver receives.
- Decide, with runs, whether Stonecutter is needed for M8 (newest row only, or a second row such as `1.21.11`).
- Ask the maintainer before pulling any image or downloading any jar not already pinned, naming it, its digest and size.
- Write `docs/platforms/fabric.md` and `docs/platforms/neoforge.md` (Host, Agent notes, Capabilities, Gotchas) from what ran.

**Don't.**
- Commit production code, add a `versions.yml` pin or a runtime dependency. A helper script stays under `scripts/spike/`.
- Claim a combination works because the docs say so. Only a run counts.

**Acceptance.**
- [ ] Both platform docs state, per loader: the pinnable jar and installer source, the Java tag, what is fetched at start, and a
  verdict on the protocol 777 client with the command output summary.
- [ ] Every join in the spike is counted; any join timeout is listed with its stage (T-M6-14 message).
- [ ] The result says plainly if the vanilla client cannot join a loader with a given mod set, and proposes the narrower scope
  (server-only mods) or a modded-driver ADR. It does not hide the gap.
- [ ] A Stonecutter decision (in or out for M8) with the run that supports it.

**Verify.**
Reviewer re-runs the documented commands for one loader under `scripts/dockerlock.sh` and compares.

### T-M8-02 · Copied config files are writable by the server (the `bukkit.yml` and `spigot.yml` ERROR)
- tier: sonnet
- depends: T-M7-99
- modules: proofstone-core
- adrs: 0005, 0016
- specs: architecture.md §3
- test-first: yes

**Goal.** A file the host copies into a container is owned by the server's user, so the server never logs a permission error at
start (M7 review: `Could not save bukkit.yml (Permission denied)` on every Paper cell, plus `spigot.yml` on BungeeCord
backends).

**Do.**
- Copy every config file with the container user's ownership and mode (Testcontainers `Transferable` mode, or a post-copy step
  run as root before start, whichever the docs and a run show works), in `PaperHost` and `ProxyHost`.
- Add a start check that fails the cell loudly when the server log holds `Permission denied` for a copied file, naming the file.
- Unit-test the mode and owner of the rendered copies.

**Don't.**
- Bind-mount anything. Allowlist the line. Change what a config contains.

**Acceptance.**
- [ ] `:conformance:run` on `velocity@4.3.1+paper@26.3` and `bungeecord@2111+paper@26.3`, three invocations each: green, and
  no backend log in the bundle or the XML holds `Permission denied`. Evidence: grep counts per run.
- [ ] `:conformance:smoke -Dproofstone.cells=paper@26.3` green, and no bundle's `server.log` holds a `Could not save`
  line (evidence: the `metaLeak` and `metaExpect` bundles of one `metaTest` run).
- [ ] `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`./gradlew :proofstone-core:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=velocity@4.3.1+paper@26.3 --no-parallel --max-workers=1`

### T-M8-03 · ADR-0017: mod platform packs, artefact types and rows (design, docs only)
- tier: opus
- depends: T-M8-01
- modules: docs/adr, docs/specs, docs/architecture.md, docs/glossary.md, docs/plan
- adrs: 0002, 0003, 0005, 0008, 0013, 0015
- specs: versions-yml.md, test-api.md, self-tests.md, agent-protocol.md
- test-first: no

**Goal.** Decide how a modded server is a cell and how mods, data packs and resource packs enter one, keeping the Paper and
topology paths unchanged.

**Do.**
- Write ADR-0017: the `fabric@<mc>` and `neoforge@<mc>` identities, loader and installer pins in `versions.yml` (an additive
  schema bump), the host image and Java tags (ADR-0013), required rows (native only, as ADR-0016 section 3), and the Stonecutter
  decision from T-M8-01.
- Decide the artefact types: plugin (exists), mod (`/mods`), data pack (`world/datapacks`, copied before start) and resource pack
  (served to the client), how a test declares each, and which capabilities they need (`DATAPACKS_VANILLA`, `RESOURCE_PACKS`,
  `MODS`), each with an agent token so the agent can report it (M5 review, item 3).
- Decide the resource-pack source (harness JVM or a pinned container) from the spike, and the driver's resource-pack contract
  (prompt seen, answer, status sent) for the client-driver spec.
- Decide whether the wire protocol changes (a data-pack list endpoint, a resource-pack status in `players`). If it does, T-M8-04
  carries the bump.
- Glossary terms (mod agent, data pack, resource pack, loader row) and `docs/platforms.md`.

**Don't.**
- Change code, `versions.yml` data or a schema file. Re-decide an accepted ADR.

**Acceptance.**
- [ ] ADR-0017 Accepted, listed in `docs/adr/README.md`, with rejected alternatives (for example "a modded driver").
- [ ] The glossary, `versions-yml.md` and `client-driver.md` carry the new terms; `./gradlew docsCheck` passes.
- [ ] A decision list for the maintainer is in the PR: loader rows, image, resource-pack source, Stonecutter.

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M8-04 · Schema, planner and (if ADR-0017 says so) protocol 1.3 for mod rows
- tier: sonnet
- depends: T-M8-03
- modules: proofstone-core, proofstone-protocol, versions.yml, docs/specs/versions-yml.md
- adrs: 0008, 0015, 0017
- specs: versions-yml.md, agent-protocol.md §4
- test-first: yes

**Goal.** `versions.yml` names the loader rows and the planner, selector and `printMatrix` handle `fabric@` and `neoforge@` cells.

**Do.**
- The additive schema bump and the loader rows from T-M8-01, all `experimental`.
- `CellPlanner`, `CellSelector` and `printMatrix --scope` for the new identities. A malformed or unknown loader selector fails
  loudly.
- If ADR-0017 adds endpoints: schemas, golden files, the minor protocol bump, and the Bukkit and proxy agents' contract tests
  still green (the new goldens listed as pending where an agent never serves them, with the reason).

**Don't.**
- Change how Paper or topology cells select or run. Hand-edit a pin without a run behind it.

**Acceptance.**
- [ ] Planner, selector and matrix unit tests pass, including malformed input.
- [ ] `printMatrix --scope=pr` output before and after is identical for every existing single-server and topology cell.
- [ ] `bash scripts/gates.sh --max-workers=1` passes.

**Verify.**
`./gradlew :proofstone-core:test :proofstone-protocol:test :proofstone-core:printMatrix`, then `bash scripts/gates.sh --max-workers=1`

### T-M8-05 · Fabric agent
- tier: opus
- depends: T-M8-04
- modules: platforms/fabric/agent
- adrs: 0002, 0017
- specs: specs/agent-protocol.md §2
- test-first: yes

**Goal.** A Fabric server mod that serves the protocol from `platforms/agent-core` and observes without interfering.

**Do.**
- Build `platforms/fabric/agent` with Loom (the Java baseline and build model from ADR-0017). Reuse `agent-core`; jackson shaded
  and relocated as in the other agents.
- Game endpoints (players, inventory, windows, world rules, snapshot, command, events, log) on the server thread, as the Bukkit
  agent runs them on the main thread. Event probes via Fabric API callbacks, read-only, registered last.
- Golden contract test against a mock server, and a real-server integration test on the pinned Fabric row.
- An observe-only test as the proxy agents have (no command registered, no callback that changes a result).

**Don't.**
- Use a mixin that changes behaviour. Register commands, cancel or alter events. Add a runtime dependency beyond the shaded set.

**Acceptance.**
- [ ] `./gradlew :platforms:fabric:agent:test` passes, golden files replayed.
- [ ] The real-server integration test passes on the Fabric row, three invocations (Flake rule).
- [ ] The observe-only test exists and passes. Module README and `docs/platforms/fabric.md` Agent notes updated.

**Verify.**
`./gradlew :platforms:fabric:agent:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :platforms:fabric:agent:integrationTest --no-parallel --max-workers=1`

### T-M8-06 · NeoForge agent
- tier: opus
- depends: T-M8-04
- modules: platforms/neoforge/agent
- adrs: 0002, 0017
- specs: specs/agent-protocol.md §2
- test-first: yes

**Goal.** The same protocol for NeoForge, built with ModDevGradle.

**Do.**
- Build `platforms/neoforge/agent` as T-M8-05 does for Fabric, with NeoForge's event bus at the lowest priority, read-only.
- Golden contract test, real-server integration test on the pinned NeoForge row, observe-only test.
- Record in `docs/platforms/neoforge.md` how the agent behaves in the configuration phase (it must not require a client mod).

**Don't.**
- Declare the agent as required on the client side. Register commands or cancel events.

**Acceptance.**
- [ ] `./gradlew :platforms:neoforge:agent:test` passes, golden files replayed.
- [ ] The real-server integration test passes on the NeoForge row, three invocations (Flake rule).
- [ ] The observe-only test exists and passes. Module README and platform doc updated.

**Verify.**
`./gradlew :platforms:neoforge:agent:test`, then `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :platforms:neoforge:agent:integrationTest --no-parallel --max-workers=1`

### T-M8-07 · Mod host: loader cells with the mod under test in `/mods`
- tier: sonnet
- depends: T-M8-02, T-M8-05, T-M8-06
- modules: proofstone-core
- adrs: 0005, 0013, 0017
- specs: architecture.md §3, specs/test-api.md
- test-first: yes

**Goal.** The extension boots a Fabric or NeoForge cell from `versions.yml` with the agent and the mod under test copied in.

**Do.**
- A host for loader cells next to `PaperHost` (or a narrow extension of it, as ADR-0017 says), pins from `versions.yml`, loader
  and installer checked by sha256 in the container, offline mode, files copied with the T-M8-02 ownership.
- Readiness by log line and agent `health`. A failed start reports the log tail and stops every container.
- Log-guard pack lists for Fabric and NeoForge, empty at first, each line added with a reason (as the Folia list was).

**Don't.**
- Mount anything. Let a loader download an unpinned artefact at start. Raise a boot timeout to hide a slow start: measure it and
  record it.

**Acceptance.**
- [ ] Unit tests for the rendered environment, pins and copy list.
- [ ] A real-server test boots each loader row with the agent and one example mod and reads `health`, three invocations each.
- [ ] No container of ours runs after any run.

**Verify.**
`./gradlew :proofstone-core:test`, then the card's real-server task under `scripts/dockerlock.sh`.

### T-M8-08 · Conformance C01 to C08 on mod hosts
- tier: sonnet
- depends: T-M8-07
- modules: proofstone-core (conformance), conformance
- adrs: 0003, 0015, 0017
- specs: specs/self-tests.md §1
- test-first: yes

**Goal.** Every loader row proves the same agent round trips the Bukkit rows prove.

**Do.**
- Run the per-cell conformance and `:conformance:run` on the loader rows. A scenario that needs a Bukkit-only fixture is skipped
  with the capability named, never passed.
- Record each quirk in the platform doc and assert it as it is.

**Don't.**
- Weaken an assertion to match a loader. Skip a scenario silently.

**Acceptance.**
- [ ] `:conformance:run -Dproofstone.cells=<loader row>` green on the Fabric row and the NeoForge row, three invocations each,
  skips reasoned and listed.
- [ ] The ADR-0015 table for each row is in the PR (promotion is a separate decision).

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :conformance:run -Dproofstone.cells=<loader row> --no-parallel --max-workers=1`

### T-M8-09 · Data packs: artefact type, `DATAPACKS_VANILLA`, function and recipe scenarios
- tier: sonnet
- depends: T-M8-07
- modules: proofstone-api, proofstone-core, platforms/bukkit/agent, platforms/fabric/agent, conformance
- adrs: 0001, 0003, 0017
- specs: specs/test-api.md, specs/self-tests.md
- test-first: yes

**Goal.** A test ships a data pack into a cell and asserts its effect with the dual oracle.

**Do.**
- The data-pack artefact (copied into the world's `datapacks` before start), the additive API to declare it, and the
  `DATAPACKS_VANILLA` capability with an agent token on the Bukkit and Fabric agents.
- Scenarios C-D01 (a function sets a score or a block; agent reads it, client sees it where visible) and C-D02 (a data-pack recipe
  crafts through the client's window, as C08 does).
- Label the PR `api-change` if the API changes; additions only.

**Don't.**
- Reload data packs during the act phase from the agent. Change an existing signature.

**Acceptance.**
- [ ] C-D01 and C-D02 pass on `paper@26.3` and on the Fabric row, three invocations each; skipped with the capability named on
  a cell without it.
- [ ] The api-baseline diff shows additions only.

**Verify.**
`./gradlew :proofstone-api:test :proofstone-core:test`, then `:conformance:run` per cell under `scripts/dockerlock.sh`.

### T-M8-10 · Resource packs: driver prompt, answer and status
- tier: opus
- depends: T-M8-03
- modules: drivers/mcpl, proofstone-api, proofstone-core
- adrs: 0004, 0017
- specs: specs/client-driver.md, specs/test-api.md
- test-first: yes

**Goal.** A test sees the server's resource-pack prompt and answers it, and the server's view of the answer is read back.

**Do.**
- The driver records each resource-pack push (URL, hash, required, prompt) in play and configuration state, and answers accept,
  decline or fail as the test says, sending the statuses a vanilla client sends. It downloads the pack only to check the hash,
  from the source ADR-0017 picked, never from the internet.
- The additive API (`Client.resourcePacks()` or as ADR-0017 names it), and the server-side status through the agent.
- Real server: a cell with a required pack: accept passes, decline is kicked with the server's message, a wrong hash is reported.

**Don't.**
- Render or unpack textures. Accept a pack silently when the test did not say.

**Acceptance.**
- [ ] Unit tests for the driver state machine with scripted packets (push, pop, configuration-phase push).
- [ ] The real-server test passes on `paper@26.3`, three invocations.
- [ ] The api-baseline diff shows additions only.

**Verify.**
`./gradlew :drivers:mcpl:test`, then the real-server task under `scripts/dockerlock.sh`.

### T-M8-11 · Negative controls: broken mod, broken data pack, wrong pack hash
- tier: sonnet
- depends: T-M8-08, T-M8-09, T-M8-10
- modules: examples/bad-tests, proofstone-core, scripts
- adrs: 0003, 0007, 0017
- specs: specs/self-tests.md §3
- test-first: yes

**Goal.** Prove the new paths can fail, as T-M7-13 did for topologies.

**Do.**
- Bad tests: a mod that crashes at load (INVALID naming the mod), a data pack with a broken function (INVALID or FAIL with the
  server's message), a resource pack whose hash does not match (FAIL). Each pinned to its cell, refused on another.
- `metaModTest` (or entries in `metaTest`) expecting each verdict; a fault that passes fails the meta test.
- Ask in the PR whether they join `scripts/release-mutation-check.sh` (runtime cost), as T-M7-13 did.

**Don't.**
- Treat a boot or join timeout caused by the fault as the expected verdict: assert the specific reason.

**Acceptance.**
- [ ] Each fault gives its verdict with the expected message; the clean runs are green, three invocations.
- [ ] The existing `metaTest` and `metaTopologyTest` still give every expected verdict (one run each, XML read).

**Verify.**
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:bad-tests:metaTest --no-parallel --max-workers=1`

### T-M8-12 · Docs, example and module READMEs
- tier: haiku
- depends: T-M8-11
- modules: docs, examples
- adrs: 0017
- specs: test-api.md
- test-first: no

**Goal.** A mod or data-pack author can run a test from the docs alone.

**Do.**
- A user guide page for mods, data packs and resource packs, and troubleshooting entries for the M8 findings.
- A small example that runs on one loader row (only what ran in M8 conformance).
- `docs/platforms.md` status for Fabric, NeoForge and data packs at the level the evidence supports (`experimental` until
  ADR-0015 is met).

**Don't.**
- Claim a status, a version or a flake rate that was not run.

**Acceptance.**
- [ ] `bash scripts/gates.sh --max-workers=1` passes.
- [ ] The example runs green on one loader row, three invocations, XML read.

**Verify.**
`bash scripts/gates.sh --max-workers=1`

### T-M8-99 · M8 milestone review
- tier: opus
- depends: T-M8-01, T-M8-02, T-M8-03, T-M8-04, T-M8-05, T-M8-06, T-M8-07, T-M8-08, T-M8-09, T-M8-10, T-M8-11, T-M8-12
- modules: docs/plan, docs/robustness.md, docs/platforms.md
- adrs: 0010, 0014, 0015, 0017
- specs: vision.md
- test-first: no

**Goal.** Decide whether the M8 exit criterion is met, from runs, not from PR descriptions, and refine M9 into cards.

**Do.**
- Read the code and the XML. Re-run: `bash scripts/gates.sh --max-workers=1`, `:conformance:smoke`, `:conformance:run` on each
  loader row and each topology row (three invocations each), `:examples:bad-tests:metaTest`, `metaTopologyTest`, the M8 meta
  task and `bash scripts/release-mutation-check.sh` at HEAD. One Docker run at a time under `dockerlock`.
- Report the join-timeout and log-guard rates for loader cells separately from the Paper and topology numbers.
- Review `docs/robustness.md` §5 against the new code (observe-only on both mod agents, log guard per loader, leak detector).
- Check the diffs for a weakened test, a new runtime dependency, a workflow, or a committed secret. API v0 additive only.
- Re-score the exit criterion in ROADMAP "M8 review", re-estimate M9 and M10, and refine M9 (Bedrock, spike first) into
  `docs/plan/tasks/M9-bedrock.md`. Stop for approval.

**Don't.**
- Start M9, tag, or publish. Round up a partial result.

**Acceptance.**
- [ ] ROADMAP has an "M8 review" section with the exit criterion re-scored row by row, each with evidence, and a plain verdict.
- [ ] Loader flake numbers and reasons are stated, with the run count.
- [ ] `docs/plan/tasks/M9-bedrock.md` exists in the standard card format and `docsCheck` passes.

**Verify.**
`bash scripts/gates.sh --max-workers=1`
