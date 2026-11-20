# Spec: self-tests (conformance, control run, differential run, flake detector)

These are what make a green result trustworthy ([robustness.md](../robustness.md)).

## 1. Conformance suite (per pack, per cell, before plugin tests)

It is cached per cell per CI run. If it fails, every plugin test on that cell gets the verdict `INVALID`.

Minimum scenarios for Java packs. Each one uses the dual oracle, with vanilla behaviour and no plugin:

| ID | Scenario | Proves |
|---|---|---|
| C01 | Agent `/v1/health`, plus the protocol version matches | agent loaded, protocol compatible |
| C02 | Client joins, runs `/help`, receives a system message | login, configuration and chat path |
| C03 | `give` round trip: agent gives an enchanted item, and agent and client both read the same enchants | item schema on both oracles |
| C04 | Chest: move a stack from slot A to slot B | window click and resync |
| C05 | Vanilla anvil: rename an item, check the result name and XP cost | anvil window, result slot, XP sync |
| C06 | Vanilla anvil: combine sword + enchanted book, check the vanilla result | result-slot semantics |
| C07 | Grindstone: remove enchants from an item | grindstone window |
| C08 | Shift-click result from a crafting table | non-standard click types |
| C09 | Event probe captures `PlayerJoinEvent` with getters | event subscription |
| C10 | Config patch + restore round trip (on a test fixture plugin shipped with the conformance suite) | config snapshots |
| C11 | Log capture: the fixture plugin logs a SEVERE, `assertNoErrorsLogged` fails, the allowlist works | error oracle |
| C12 | Reset leaves no residue: state before and after compares equal | isolation |
| C-L01 | `Agent.logLines` (T-M11-01, `ConformanceLogLinesTest`, Bukkit family): the harness client's join line and a console `say` line are in the lines since the test start; the boot `Done ... For help, type` line is not | server log capture through the public API |
| C-L02 | `Agent.bootLogLines` (T-M11-03, `ConformanceBootLogLinesTest`, Paper): the agent's own `Proofstone agent listening on port` line and the `Done ... For help, type` line are in the lines before the test; none of the test's own lines are | boot log through the public API |
| C-W01 | `PlayerState.openWindow` (T-M11-02, `ConformanceOpenWindowTest`, Bukkit family): empty before a chest is opened; while open, type `chest`, title `Chest`, the 27 own slots with the moved sword in slot 13 and an empty slot kept in place; empty again after the client closes it | server view of an open window |

v0 limit for C04: it checks the move through server-answered clicks on the client window and dual-checks only the player
inventory (the stack must leave it on both oracles). The chest's own contents have a server view since T-M11-02 (C-W01), but C04
has not been changed to use it.

v0 limit for C07: the grindstone arrived in 1.14, so 1.12.2 has no grindstone and cannot cover C07. The scenario
skips there with that reason (a visible skip, never a pass). Every row from 1.14 on runs it, and it is part of the
conformance evidence only on those rows (docs/platforms/paper.md, T-M3-05).

Each pack can add its own scenarios. For example, Folia adds cross-region teleport and region-thread
assertions.

**Wired into the extension (T-M4-07, T-M4-10b):** core runs one conformance check per cell per run, cached in
`ConformanceCache`, before the first test on that cell. It has two parts. First the vanilla `list` command must print
"players online" on the console path. Then a conformance client joins through the invocation's driver and runs:

- C03 (give round trip) on every cell;
- C04 to C08 (chest, anvil rename, anvil combine, grindstone, shift-click craft) on every bridged cell. C07 is skipped
  with its reason before 1.14.

A failed check, or a scenario that fails or cannot join, makes every test on the cell `INVALID`, and the test body does
not run. The cell's scenario timings are in the log (`PROOFSTONE CONFORMANCE <cell> <scenario> passed in N ms`).

The conformance client's own leave is part of the check (T-M8-22). The server log is checked (`ErrorLogGuard`, the cell's
pack) from before the client joins to just before it closes, and a hit is the conformance's own failure. After the close the
host waits, with the `Await` helper and the usual 10 s budget, for the host log line `PSConf left the game`, then for two
server ticks (`POST /v1/ticks/await`); a leave it cannot confirm is a failed conformance. The first test's log window starts
after that (`PROOFSTONE CONFORMANCE <cell> leave confirmed in N ms`, about 0.2 s). The two ticks cover vanilla handling a
disconnect a second time one tick after the first, which logs `handleDisconnection() called twice` on Fabric and NeoForge
(about 0.4% of closes); that line is not allowlisted.

**Not wired yet, with reasons:**
- C09 (event probe) needs a fixture plugin with a probe; the Folia event probe (`ConformanceFoliaEventTest`) is a
  per-pack scenario, not a cell-wide one.
- C10 (config patch and restore) needs the test fixture plugin to ship with the conformance suite (not yet built).
- C11 (log capture) needs a fixture plugin that logs a SEVERE on purpose; the error oracle is covered by
  `ErrorLogGuard` unit tests and the leak tests, not by a per-cell scenario.
- C12 (reset leaves no residue) depends on the agent state snapshot (`GET /v1/snapshot`, card T-M4-12).

## 1b. Topology conformance (per topology cell, ADR-0016)

A topology cell ([ADR-0016](../adr/0016-topology-cells.md)) runs these instead of the C-scenarios, cached per cell per
run. A red makes every test on that topology cell `INVALID`. The cards are T-M7-11 and T-M7-12. A readiness check
T00 (the proxy agent lists every backend online) runs first, then P01 to P05 in order. `:conformance:run -Dproofstone.cells=<topology cell>` runs it.

| ID | Scenario | Proves |
|---|---|---|
| P01 | Join through the proxy: the proxy agent lists the player and its current server; the default backend's agent lists the same player; a proxy event probe (subscribed before the join) saw the proxy connect it to that server. A client in play that an agent does not show is `DISAGREE` | client to proxy and proxy to backend hops, and the proxy `players` and `events` support |
| P02 | Forwarding: the UUID and name the backend sees equal the proxy's, the UUID is the offline UUID of the name (online mode is off in every test cell), and the client address the backend logs is the one the proxy logs; a mismatch is `DISAGREE`. Then a client joins the backend's own port: Velocity modern must refuse with Paper's message, BungeeCord legacy is asserted as observed ([velocity.md](../platforms/velocity.md), [bungeecord.md](../platforms/bungeecord.md), Direct join) | forwarding mode configured on both sides |
| P03 | Server switch `lobby` to `survival` and back: client, proxy agent and both backend agents agree; the source no longer lists the player | the switch and the dual oracle across agents |
| P04 | Plugin messaging: the backend fixture sends `GetServer` on `BungeeCord`, the proxy answers, the fixture records the reply | the plugin messaging round trip |
| P05 | Proxy command: a console command through the proxy agent runs; a client-typed `/server` is seen by the proxy agent's events | proxy command and event paths |

The backend row's own C-scenarios are not re-run inside a topology.

## 1c. Loader cells and data packs (ADR-0017)

Loader cells (`fabric@<mc>`, `neoforge@<mc>`, [ADR-0017](../adr/0017-mod-platform-packs.md)) run the same C-scenarios as
single-server cells, cached per cell per run, with the mod agent as the server oracle (T-M8-08). A scenario that needs a
Bukkit-only fixture is skipped with the capability named, never passed. A loader row is promoted only under ADR-0015 with
C01 to C08 run and the M8 flake rule's three green invocations.

| ID | Scenario | Needs | Proves |
|---|---|---|---|
| C-D01 | A data pack's function sets a score or a block; the agent reads it, the client sees it where visible | `DATAPACKS_VANILLA` | the data pack loaded and its function ran |
| C-D02 | A data-pack recipe crafts through the client's crafting window, as C08 does | `DATAPACKS_VANILLA` | the data-pack recipe on both oracles |
| C-R01 | A required resource pack: accept reaches play and the server reads it loaded; decline is kicked with the server's message; no declared answer ends the join at once | `RESOURCE_PACKS` | the driver's prompt, answer and statuses, and the server's view of them |
| C-R02 | The same pack with a wrong pushed sha1: accept reports the mismatch and sends `FAILED_DOWNLOAD`; the server reads it | `RESOURCE_PACKS` | a wrong hash is a reported problem, never a silent load |

C-D01 and C-D02 run on `paper@26.3` and the Fabric row (T-M8-09). A cell without `DATAPACKS_VANILLA` skips them with the
capability named. As built (`conformance/src/test/java/.../CD01DataPackFunctionTest` and `CD02DataPackRecipeTest`, pack
`conformance/src/test/datapacks/proofstone-e2e`, declared with `@Proofstone(datapacks = ...)`):
- **C-D01:** the console runs `function proofstone_e2e:hello`, which gives the player five emeralds and sends one `tellraw`.
  The agent and the client read the same five emeralds (`DualAssert`); the client saw the chat line. The function sets no
  block and no score, because the agent API has no block or score read that is not console output (ADR-0002).
- **C-D02:** the recipe (two dirt make one diamond, absent from vanilla) crafts through the client's crafting window as C08
  does: the result shows in the window, shift-click moves it, and after the window closes the agent and the client read one
  diamond and no dirt. The Fabric and NeoForge agents answer no player read while a window is open (501 `NOT_SUPPORTED`), so
  the player is read before the window opens and after it closes only, on every cell: no assertion is weakened for them.

C-R01 and C-R02 run on `paper@26.3` (T-M8-10; `conformance/src/test/java/.../CR01ResourcePackTest` and
`CR02ResourcePackWrongHashTest`, pack zipped from `conformance/src/test/resourcepacks/proofstone-e2e` by `zipResourcePack`):
- **C-R01:** `@ServerResourcePack(required = true, prompt = {"text": ...})`. Accept: one configuration-phase push from the
  loopback source, its prompt and sha1, statuses `ACCEPTED`, `DOWNLOADED`, `SUCCESSFULLY_LOADED`, no problem. Paper fires no
  status event for the configuration phase, so the fixture plugin (`fixture-resourcepack`) pushes the same pack in play and
  the probe reads the same three statuses for the player. Decline: `Clients.create` fails with
  `multiplayer.requiredTexturePrompt.disconnect`, not a timeout. No answer: the join fails at once naming the URL.
- **C-R02:** `sha1` set to forty zeros. Accept: statuses `ACCEPTED`, `FAILED_DOWNLOAD`, problem `hash mismatch: expected 000...,
  got <sha1>`; the client still reaches play; the same push in play gives the server's `ACCEPTED`, `FAILED_DOWNLOAD`.

**Negative controls (T-M8-11).** The new paths can fail loudly, shown by `examples/bad-tests` `metaModTest`: a mod that crashes at
load is `INVALID` naming the mod and quoting the loader's line (Fabric and NeoForge); a data pack whose function does not parse is
`INVALID` naming the pack and quoting the server's line, read from the host's log of the boot (the pack stays enabled, so the
declared-pack check alone passes it); a resource pack pushed with a wrong sha1 is a `FAIL` of the test that expects it to load,
with `FAILED_DOWNLOAD` and the hash mismatch in the message. A failure before conformance whose message starts with `INVALID` is
`INVALID` in the report too. Each bad test is pinned to its cell and refused on another; none is a boot or join timeout. Since T-M8-13 the crashing mod is declared through `@Proofstone(mods)` (the agent's `exampleCrashModJar`), not injected by a switch.

## 1d. Geyser row (ADR-0018, T-M9-08)

A Geyser cell (`geyser@2.12.1+paper@26.3`, selected with `-Dproofstone.cells`, one cell per invocation) runs the Paper cell's own
check first (`list`, C03 with the Java client), then its **first cached check** (`GeyserConformance`): one Bedrock client joins and
leaves, and afterwards the stand-in must have answered exactly the two expected names (`client.discovery.minecraft-services.net`,
`authorization.franchise.minecraft-services.net`), the name guard must be clean (no Xbox or Microsoft account host, no other name
under `minecraft-services.net`) and the server must have logged no error. A red makes every test on the cell `INVALID`. Afterwards, every test and the cell's close re-run the name guard and judge the stand-in and
driver-host logs (an `ERROR`, an uncaught exception or `STUB refused` fails the test that was running). The JUnit display name carries
` (offline-stub)` and each Geyser row of `proofstone-report.json` has `"bedrockAuth": "offline-stub"` (T-M10-06).

| ID | Scenario | Needs | Proves |
|---|---|---|---|
| C-G01 | A Bedrock client joins; the agent lists and reads the player as `.Name` | `BEDROCK_CLIENT` | the Floodgate name reaches the agent |
| C-G02 | The Bedrock client types `/plugins`; the header and the plugin list reach it as messages | `BEDROCK_CLIENT` | a command round trip through Geyser |
| C-G03 | The agent gives `stick x3` and a `diamond_sword`; the client's inventory shows them (`DualAssert`, id and count) | `BEDROCK_CLIENT` | the dual oracle on Bedrock |
| C-G04 | The example plugin's `.` prefix branch (`examples/bedrock-prefix`, as C-B01) sees the real Bedrock client as Bedrock and the Java client as not | `BEDROCK_CLIENT` | a plugin's Bedrock-only branch with a real Bedrock client |
| C-G05 | Two Bedrock clients join through one driver host: both are `.Name` players, their UUIDs differ, and a `/me` line from each reaches the other | `BEDROCK_CLIENT` | two Floodgate players at once |

Skipped with the reason in the XML (`TEST_ABORTED`), never passed: window clicks and `useBlock` (C04 to C08 equivalents), forms,
resource packs. Each first asserts that the driver refuses the call (`UnsupportedOperationException` naming ADR-0018), so the skip
is true. `:conformance:run` with a `geyser@` selector runs `ConformanceGeyserTest` only. A Geyser row is promoted only under
ADR-0015 with this table and three green invocations, and a driver that is a release (ADR-0018 section 8).

**Negative controls (T-M9-11).** The Geyser row's first check can fail loudly, shown by `examples/bad-tests` `metaBedrockTest` (switch
`-Dproofstone.injectGeyserFault`, each bad test pinned to the Geyser row and refused on any other cell). A failed first join is `INVALID`
and names its hop; the Bedrock client's `disconnect.timeout` is quoted but is never the reason. `stand-in-absent` (the stand-in refuses the two
Microsoft names): `the discovery stand-in answered none of the two expected names`, `hop: server and driver host to the stand-in` and the
server's `Unable to fetch discovery data` line. `key-mismatch` (Floodgate reads another key file than the one Geyser takes): `Floodgate
refused the data Geyser sent`, `hop: Geyser to Floodgate on the server` and Floodgate's `Please connect through the official Geyser`. A refused
Bedrock protocol is dropped, not faked (docs/platforms/geyser.md, "Negative controls"). The stand-in is blamed only on evidence (a name it was
asked and did not answer, or Geyser's own failed-fetch line); a join that died before any login stays a plain failed join.

## 2. Control run (nightly)

- Run the user's suite with every `plugins` jar **removed**. The agent and fixtures stay.
- Every test that is `PASS` in the control run gets the verdict `VACUOUS` and fails the nightly run.
- A test may declare `@ControlRun(EXPECTED_PASS, reason)` for genuine invariants, such as "vanilla
  behaviour unchanged when the feature is disabled". This is rare, and it is reported.

## 3. Differential run (nightly)

- For each cell where the reference driver is native, run the suite with `mcpl` (bridged or native) and
  with `mineflayer-ref`.
- Compare the client-oracle observations at each assertion point. Any difference gets the verdict
  `DISAGREE`, with both transcripts attached.

## 4. Flake detector (nightly)

- Repeat each test N=5 times per cell. N can be configured, and must be at least 3.
- Any verdict change gets the verdict `FLAKY` and fails the run, with all transcripts attached.

## 4b. Leak detector (every run, minimal)

Robustness R6: a test must not leave server state behind. Numbered 4b so that the section numbers others cite stay put.

- Before each test, and before its client joins, core reads a baseline: every rule the deterministic world sets
  (`gamerule <name>` through `POST /v1/command`, one per rule) and the agent's full-state dump (`GET /v1/snapshot`,
  agent-protocol §2): each plugin's enabled flag and config values, each online player's XP and inventory.
- After the test body, the harness runs its normal cleanup (restores the configs the test patched, disconnects its
  clients), then reads the same state again with the server up. The comparison is with that after-cleanup state, so the
  test's own work (items given, XP spent) is not a leak. Only what cleanup fails to undo is. A player is compared only
  when it is online in both snapshots. A key present on one side only is a change, except for a player.
- A change fails the test with a message starting `LEAK`. It is a failure, not a verdict: the verdict list in section 7
  of the test-api spec has no leak. A test that already failed is not judged again. A leak gets a repro bundle, written
  after the cleanup.
- Console replies are best effort (ADR-0002), so the detector compares them as they are and fails on a blank one. A
  snapshot that lacks its plugin or player lists is an error, never an empty state.

## 5. Mutation check (release only)

- The example projects contain toggleable injected bugs, such as an anvil-dupe flag or a wrong NMS
  mapping.
- The release pipeline enables each bug, and the suite **must fail**. If any mutation survives, the
  release is blocked.

## 6. Status after the T-M4-08 review

What the code does today, against sections 1 to 5. The gaps and their cards are in [robustness.md](../robustness.md),
section 5. This section is the short list, and it wins over the wording above until those cards land.

- **Section 1 (conformance):** the `list` check, then C03 on every cell and C04 to C08 on bridged cells (T-M4-10b). C09
  to C12 are not wired; reasons in section 1.
- **Section 2 (control run):** done in T-M4-09. A single-run VACUOUS writes a bundle, and 
erun.txt names
  `-Dproofstone.controlRun`.
- **Section 3 (differential):** only client observations at `DualAssert` points are compared, so a test with no dual
  assertion cannot DISAGREE. Only paper@26.1.2 is a differential row. Card T-M4-11.
- **Section 4 (flake detector):** repeats share the class server, so they do not repeat boots. FLAKY is shown on the
  last repeat only; the report lists every run's verdict (T-M4-09). Boot coverage: `@Isolation(PER_TEST)` with repeat (robustness.md section 4, T-M4-13).
- **Section 4b (leak detector):** world rules, plugin configs and enabled flags, and player XP and inventory, compared
  after the harness cleanup (T-M4-12b). Open: no public-API test can leave a state the snapshot covers. See the PR.
- **Section 5 (mutation check):** not built.
- **Report file:** written at run end by `RunReport` (T-M4-09). The bad-test `metaTest` merges the five tasks' reports.
