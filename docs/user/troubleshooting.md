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

## Known intermittent flakes

These were seen on real servers and are **open**. None is fixed.

- **`moved too quickly!` (WARN).** The reset teleport races the client's first move packets. It is seen on Paper, Folia,
  Purpur and Spigot. The log guard allows it only from harness clients (`^T[0-9a-f]{8} moved too quickly! `). Your plugin's
  own copy of this WARN fails the test.
- **`PSConf moved too quickly!` on C02.** Seen in one full four-platform run on folia, purpur and spigot. The conformance
  client is named `PSConf`, and the allowlist covers only `T`-prefixed and `.`-prefixed harness clients. Not reproduced on a
  single cell. Unexplained. The result is `INVALID` on that cell.
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
- **Plugins that log `moved too quickly!` or other WARN lines** fail the test. The allowlist is for harness clients only.
- **Boot ERROR lines on Purpur are not checked.** The log guard covers only the test window, as on Paper and Folia.
- **Maven example:** does not exist yet ([quickstart-maven.md](quickstart-maven.md)).
