# Troubleshooting

Each entry names what was actually seen. Where the cause is unknown, the entry says so. A red run is a finding:
do not retry it into green (AGENTS.md section 3). Keep the JUnit XML and the repro bundle of each red run before you rerun.

## Verdicts

Every test on one cell gets one verdict. Only `PASS` and a `SKIPPED` with an allowed reason are green.

| Verdict or message | Meaning | What to do |
|---|---|---|
| `PASS` | The test passed on that cell. | Nothing. |
| `FAIL` | An assertion failed. `FAIL: ...` comes from `DualAssert`; `FAIL expect <class> on <platform>@<version>: ...` comes from an `@Expect` check. | Read the message. Decide whether the plugin or the test is wrong. |
| `SKIPPED (not a pass)` | Not run, with a printed reason: EULA not accepted, Docker unavailable, a missing capability, or the cell was not selected (`PROOFSTONE NOT SELECTED`). | Fix the reason. A skip never counts as a pass. |
| `INVALID` | The cell's conformance failed, so no plugin verdict exists for it. The test body did not run. | The harness or the server is at fault, not your plugin. Look for `PROOFSTONE CONFORMANCE <cell>` lines in the log. |
| `VACUOUS` | In a control run (`-Dproofstone.controlRun=true`, no plugin jars installed) the test still passed. The test does not prove the plugin's behaviour. | Make the test assert something only the plugin provides. |
| `DISAGREE` | The client's view and the server's view differ (`DISAGREE: ...`), or the primary driver and the reference driver differ in a differential run. | Either side may be the bug. Read both observed values in the bundle. Differential runs cover `paper@26.1.2` only today. |
| `FLAKY` | Identical runs gave different verdicts (`verdicts differ across N runs: PASS, FAIL`). Only with `-Dproofstone.repeat=N`, N at least 3. | Investigate. Never retried to green. |
| `LEAK` | Not a verdict but a failure, message `LEAK <id> on <cell>: state left behind by the test`. The test changed world rules, plugin configs or enabled flags, or a player's XP or inventory, and cleanup did not undo it. | Undo that change in the test, so cleanup or the test itself restores it. A bundle is written after cleanup. |
| `ORACLE MISSING, not a pass` | An oracle (the agent or the client) never answered. | Treat it as an infrastructure problem and read the server log. |

## EULA not accepted: tests are skipped

- **Seen:** the suite printed `PROOFSTONE SKIPPED (not a pass): Minecraft EULA not accepted: set PROOFSTONE_ACCEPT_EULA=true ...`
  and the XML showed skips.
- **Cause:** the variable was missing from the environment of that command.
- **Fix:** put `PROOFSTONE_ACCEPT_EULA=true` in front of each Gradle command, as in
  [quickstart-gradle.md](quickstart-gradle.md#3-run-the-green-example-on-one-cell). Do not write it to a file, a
  `gradle.properties`, a shell profile or a commit. Read the Minecraft EULA first: <https://aka.ms/MinecraftEULA>.

## Docker: lock, parallel runs and "Could not find a valid Docker environment"

- **Seen:** on the maintainer's Docker Desktop, several Docker suites running in parallel failed with
  `Could not find a valid Docker environment`. Two Gradle runs at once have crashed the maintainer's machine.
- **Rule:** one Docker run at a time, through the lock, with `--no-parallel --max-workers=1`:
  `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew <task> --no-parallel --max-workers=1`.
- **Lock:** a directory, `${TMPDIR:-/tmp}/proofstone-docker.lock`. A lock older than 40 minutes is taken over. If a run was
  killed and left the lock behind while nothing runs, remove it with `rmdir`.
- **Before a run:** check that no Gradle or Java worker from an earlier run is still alive. On Windows, for example
  `Get-CimInstance Win32_Process -Filter "Name='java.exe'"`. Stop what is left.
- **Docker itself:** Docker answered `InternalServerError` for one cell (`paper@26.1.2`) in a nine-cell run, and the cell
  passed when run alone. Treat a 500 from Docker as infrastructure, not a result
  ([docs/platforms/paper.md](../platforms/paper.md)).

## Join timeout: `timed out joining`

- **Seen:** `JoinTimeoutException` (a `ClientDriverException`): `timed out joining ... after PT30S; stalled at <STAGE> (...);
  protocol offered: <n>; last packet received: <packet>; transcript: [...]; server login start seen: yes|no (...)`.
  The stage is read from the packets the client sent and received (`ClientIntentionPacket`, `ServerboundHelloPacket`, then
  whatever the server sent back), for example:
  - `LOGIN_START_SENT_NO_REPLY`: handshake and login start sent, and the server sent nothing. The shape of every plain
    `paper@26.3` red on record.
  - `COMPRESSION_SET_NO_LOGIN_SUCCESS`, `ENCRYPTION_REQUESTED_NO_LOGIN_SUCCESS`: the server answered, then stopped before login
    success (encryption means an online-mode server).
  - `CONFIGURATION_STALLED`, `PLAY_NO_JOIN_PACKET`: login worked, then the server stopped sending.
  - `NO_TCP_CONNECTION`, `CONNECTED_NO_HANDSHAKE`, `HANDSHAKE_SENT_NO_LOGIN_START`: the client side never got that far.
  - `UNKNOWN`: the server sent a packet no step expects; the packet is named.

  "Server login start seen" is `yes` when a server log line written since the join began names the player, `no` when none
  does. It is evidence of where the stall is, not a cause. The harness also prints `PROOFSTONE JOIN STALL` to stderr and writes
  `join-stall.txt` (client stage, protocol offered, last packet, server lines naming the player, server log tail at the
  timeout) into the repro bundle.
  Earlier, the same message read `timed out joining ... after PT30S (no server reply)` with only the transcript.
  Observed cases:
  - the Gradle example on `paper@26.3`, once in each of two runs (different tests; both red), out of 4 runs through the action path;
  - `purpur@26.3`, C06, once; not reproduced in 10 repeats;
  - `folia@1.20.6` (bridged), one plain join stall with no server login and no ViaProxy connection line. Seen once.
- **Cause:** not known. The stage says which step the server never answered, not why. Naming it is not a fix, and the timeout
  is unchanged (PT30S).
- **What to do:** open the repro bundle. Read `join-stall.txt` first, then `server.log` for the login and
  `driver-transcript.jsonl` for the last packets.
  Run the same test alone on the same cell. A pass after a timeout is a flake. Report it with the bundle. Do not add a
  sleep or a retry.

## Topology join or switch timeout names a hop

- **Seen:** on a topology cell the join or `transferTo` timeout message adds `; hop: <hop> (<evidence>); stalled stage:
  <STAGE>` and the proxy and each backend log tail. A switch timeout reads `timed out joining ... during a server switch after
  PT..; stalled at <STAGE>`. The hop is one of:
  - `client to proxy`: no proxy log line since the join began names the player.
  - `proxy to backend <name>`: the proxy named the player, but that backend logged no `logged in` line for it. A switch can
    only stop here (the client to proxy hop was up before it).
  - `after both hops (backend <name> logged the player in)`: the backend logged the player in, so look at the stage.
- **Cause:** not known. The hop is evidence of where it stopped, never why. The timeout is unchanged and nothing retries.
- **What to do:** open the bundle `build/proofstone/<identity>/<Test>.<method>/`: `proxy/` and `server-<name>/` each hold a
  log. Read the proxy log and the named backend's log around the player's name. Keep the XML and bundle before the next run.
  Do not raise a timeout or add a retry. Report it with the hop and stage.
- **Conformance red:** a P01 to P05 failure makes every test on the cell `INVALID`; the message names the container and the hop
  (for example `hop: proxy to backend survival`). Fix the topology, not your test.
- **Not observed yet:** no topology join or switch timeout was seen in the M7 runs recorded in `docs/plan/ROADMAP.md`, so there
  is no rate for it. See [topologies](topologies.md).

## Mods, data packs and resource packs (M8)

All entries below are about `experimental` rows and features; the guide is [mods-and-packs.md](mods-and-packs.md). Counts
come from the [ROADMAP, "M8 findings"](../plan/ROADMAP.md). Mod-cell join timeouts are counted apart from the Paper and
topology numbers.

### Fabric content mods kick the vanilla client

- **Seen:** with Fabric API and a mod that registers one item, 3 of 3 driver joins were refused in configuration:
  `This server requires Fabric Loader and Fabric API installed on your client!` (Fabric API's registry sync).
- **Cause:** known. A content mod adds synced registry entries, and the vanilla client the driver emulates cannot have them.
  It is a refusal, not a join timeout.
- **What to do:** none in the harness. Server-only mods (commands, events, data) and data packs work; content mods on Fabric
  need a modded-client driver, which needs its own ADR. NeoForge admitted the client with one added item in 3 of 3 spike joins,
  and nothing more was run on it. (Today a test cannot declare a mod at all, see the guide.)

### Cold start of a loader cell reaches Mojang

- **Seen:** the first start of a Fabric or NeoForge cell fetches the vanilla server jar and libraries from inside the container
  (`launchermeta.mojang.com` and `piston-meta.mojang.com` for the install, plus the loader's maven). With `--network none` the
  cold start fails. A restart of an installed container needs no network (12 s Fabric, 11 s NeoForge).
- **Cause:** known and decided. It is the one listed exception to "no Mojang dependency in runs"
  ([ADR-0017](../adr/0017-mod-platform-packs.md) section 6): the downloads are content-pinned (the row's `vanilla-sha256` is
  checked after the install). Logins never reach Mojang: the server is in offline mode.
- **What to do:** let the first run reach the network. A host that is not on the list in the code is a finding, not something to
  allow silently.

### Paper fires no resource-pack status event in the configuration phase

- **Seen:** on `paper@26.3`, a pack from `server.properties` is pushed in the configuration phase. The event probe on
  `PlayerResourcePackStatusEvent` stayed empty for accept, decline and a wrong hash (C-R01 development run 1). The same event
  does fire for a pack a plugin pushes in play.
- **Also seen:** a **wrong hash does not kick** on Paper. The client sends `ACCEPTED`, then `FAILED_DOWNLOAD`, and reaches play.
  No WARN or ERROR is logged. Declining a **required** pack is kicked (`multiplayer.requiredTexturePrompt.disconnect`).
- **Cause:** observed, not explained inside Paper.
- **What to do:** do not assert a server event for a `@ServerResourcePack` push. Read `Client.resourcePacks()` (statuses,
  `problem`) and the join's outcome. To see the server's side, push the pack from a plugin in play. A test that expects the
  server to refuse a wrong hash will fail, and that is correct for Paper: assert what the client reported.

### Loader cells: player state read with a window open is 501

- **Seen:** on `fabric@26.3` and `neoforge@26.3`, `PlayerHandle.state()` while a `Window` is open throws an error containing
  `NOT_SUPPORTED` and `window open`.
- **Cause:** known. A vanilla server does not keep an open window's title, and the mod agents refuse to invent one. The Bukkit
  agent answers the same read.
- **What to do:** close the window first. The read is answered again once it is closed (`ConformanceLoaderWindowTest` asserts
  both halves on both loaders). C04 to C08 read after closing, so they run unchanged.

### Fabric: a `zombie_villager` WARN fails a test

- **Seen:** once in 6 conformance invocations (3 on Paper, all green, and 3 on Fabric, one red; the verifier run of T-M8-09), C02 failed through the log guard:
  `WARN net.minecraft.world.entity.EntityType: Skipping Entity with id minecraft:zombie_villager`, logged by
  `Worker-Main-3` right after the join.
- **Cause (T-M8-16, reproduced by hand):** the cell runs on `DIFFICULTY=peaceful`, and vanilla's `EntityType.create` returns null
  for a mob that is not allowed in peaceful, so a structure template that holds a zombie villager (the zombie variant of a
  village, about 2% of villages) logs the WARN once per entity. Loader cells used to set no `SEED`, so each boot was a new random
  world and a zombie village near spawn showed up in about 1 boot in 430. It is vanilla, not a data pack, and **not allowlisted**.
- **Fixed (T-M8-21):** loader cells pin `SEED=1234567`, as Paper does. That world's nearest village is 353 blocks from spawn
  (Fabric and NeoForge, `locate structure #minecraft:village`), outside the generated area.
  After it, `:conformance:run` on `fabric@26.3` and `neoforge@26.3` (3 invocations each) had 0 `Skipping Entity` lines in every container log.
- **If it shows up again:** a mod under test may have moved worldgen. Keep the XML and bundle of the red run, do not retry it into
  green, and report it with the line quoted.

### Fabric and NeoForge: `handleDisconnection() called twice` in the server log

- **Seen:** once, `neoforge@26.3` C03 (T-M8-15 verifier): `WARN net.minecraft.network.Connection: handleDisconnection() called
  twice`, right after `PSConf left the game`. In the T-M8-16 runs it was in the server log of 2 of 210 boots (Fabric), both green,
  because it came after a test's log check.
- **Cause (T-M8-16):** a vanilla race, not a double close by the harness. `Connection.tick` and `ServerConnectionListener.tick` can
  both handle the same disconnect when the client's close lands in the middle of that connection's tick; the second call runs one
  tick later. About 0.4% of client closes in a hand loop, on both loaders. Before T-M8-22 it failed a test when the closing
  client was the conformance client `PSConf`, whose close was inside the first test's log window.
- **Since T-M8-22 it cannot fail a test.** The conformance checks its own log window up to just before `PSConf` closes (a hit
  there fails the conformance, naming the line, and every test on the cell is `INVALID`). After the close it waits for the
  `PSConf left the game` line in the host log, then two server ticks (`POST /v1/ticks/await`), and only then does the first
  test's log window start. The line is **not allowlisted**: it can still be in the server log, between the conformance's last
  check and the first test's cursor, and nothing reads it there. Every other record is checked as before. The wait costs
  about 0.2 s per cell per run (155 to 215 ms, median 166 ms, 26 conformances; printed as `leave confirmed in N ms`).
- **What to do:** if a test or the conformance still names this line, the second handling came outside the gap or the server
  changed: keep the evidence, do not retry it into green, report it with the line quoted. A conformance that says it could not
  confirm the leave (no `left the game` line, or no ticks, in the wait budget) is a real failure of the cell.

### `Failed to start FML` and `Failed to start the minecraft server` fail the start at once

- **Seen:** a Fabric start that fails logs `Failed to start the minecraft server`; a NeoForge start that fails before any mod
  loads logs `Failed to start FML: ...` (for example `InvalidModuleDescriptorException`). A NeoForge mod that throws at
  construction logs `Failed to create mod instance. ModID: <id>` and the container exits, with no `Failed to start FML` line seen.
- **What the host does:** it fails the cell after about 15 s (Fabric) or 17 to 18 s (NeoForge) on the line or on the container
  exit, never on the 5-minute boot timeout. The test is `INVALID`, naming the mod when the loader's log names one.
- **What to do:** read the quoted loader line in the message and `server.log` in the bundle. A start that fails without naming a
  declared mod is not blamed on a mod. Do not raise the boot timeout.
- **Data packs:** a pack with a function that does not parse stays enabled, and the server logs the error at boot. The harness
  reads the whole boot log for WARN or worse lines naming the pack's namespace and makes the class `INVALID`, quoting the line.

### `PSConf moved too quickly!` (unchanged)

The conformance client is named `PSConf`. The log-guard allowlist no longer has a `moved too quickly!` entry for it or for `T`
plus eight hex digits names (dropped in T-M7-19); only `.`-prefixed Clients names keep one. Both causes are fixed: the shared
server (T-M7-17) and the join teleport acknowledged before the player's first tick (T-M7-19, the mcpl driver now waits for the
first `SetHealth`). A `PSConf moved too quickly!` now would be a new finding, and makes the cell `INVALID`. If a join fails
with stage `JOIN_TELEPORT_HELD_NO_HEALTH`, the server never sent its first `SetHealth` after the join position.

## Known intermittent flakes

These were seen on real servers and are **open**, except where a line says fixed.

- **`moved too quickly!` (WARN), fixed in T-M7-19.** It was seen on Paper, Folia, Purpur and Spigot for harness clients and was
  allowlisted. The cause was the join teleport acknowledged before the player's first tick; the driver now waits for the first
  `SetHealth` and the allowlist entries are gone. Any `moved too quickly!` (yours or a harness client's) fails the test.
- **Two platforms of one version sharing a server (fixed in T-M7-17).** A class with the default `PER_CLASS` isolation used to
  share one server per **Minecraft version**, not per cell: with `paper@26.3` and `folia@26.3` selected, the second cell ran on
  the first cell's Paper server, and the conformance client logged in again at its saved spot, which Paper and Purpur WARN about
  (`PSConf moved too quickly!` on C02). The shared host is now keyed by the full cell, and a server whose agent reports another
  platform than the cell is refused as `INVALID`. Not caused by that: a `PSConf moved too quickly!` on the **first** login of a
  fresh `purpur@26.3` server was seen once in 8 runs after the fix. Cause found in T-M7-18: Paper and Purpur check the client's
  join-teleport acknowledgement before the player's first tick, so the move is measured from `0, 0, 0` (the WARN shows the login
  position). Fixed in T-M7-19 (ROADMAP "T-M7-19").
- **Gradle example on `paper@26.3`.** Red in 2 of 4 runs through the action path (join timeouts, above) and in 1 of 5 plain
  Gradle runs. The plain red was `java.lang.AssertionError` at `VerdictGate.java:150` with no message. The verdict text was
  lost because a later run overwrote the XML. Keep the XML of each red run.
- **`spigot@1.21.11` C06.** One inventory `DISAGREE`, not reproduced in 10 repeats.
- **`folia@1.20.6`.** Join stall (above), and a C07 cursor assertion. Folia 1.20.6 is not a `required` row.

Until these are explained, the claim "the examples are green" is not established.

## Repro bundles

Every result other than `PASS` writes a bundle under the test project's `build/proofstone/<platform>@<mc>/<TestClass>.<method>/`. Bundles are local.
They are never committed.

| File | Holds |
|---|---|
| `server.log` | The server log for the run. |
| `viaproxy.log` | On bridged cells only: the ViaProxy log. |
| `agent-transcript.jsonl` | The agent calls and replies. |
| `driver-transcript.jsonl` | The client packets. |
| `cell.json` | Every pinned version of the cell. |
| `snapshot.json` | The agent's state snapshot. It may hold `NOT_FOUND` (a gap noted in the M4 review). |
| `host.json` | The host details. |
| `rerun.txt` | The exact Gradle command, with its cell selector and switches. |

A run also writes `build/proofstone/proofstone-report.json` at its end. The JUnit XML is in `build/test-results/`.

When you report a red run, paste the Gradle command and the bundle path, and say that the bundle is local.

## Open findings for users

- **Bedrock-prefixed logins on bridged cells** stall at ViaProxy. The image bundles ViaBedrock but has no prefix option.
  Not reproduced, no fix. Use native cells for those tests ([docs/platforms/viaproxy.md](../platforms/viaproxy.md)).
- **Bedrock through Geyser** is a separate path with its own findings (stand-in absent, Floodgate key mismatch, join stages) and an
  offline-stub warning: [bedrock.md](bedrock.md).
- **Plugins that log `moved too quickly!` or other WARN lines** fail the test. The allowlist has no `moved too quickly!` entry for harness clients any more (T-M7-19).
- **Boot ERROR lines on Purpur are not checked.** The log guard covers only the test window, as on Paper and Folia.
- **Maven example:** does not exist yet ([quickstart-maven.md](quickstart-maven.md)).
