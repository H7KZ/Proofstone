# Testing a Bedrock path through Geyser

This page is for plugin authors whose plugin behaves differently for a Bedrock player. It covers one thing: a Bedrock client that joins a
Paper server through Geyser and Floodgate. Status: **experimental**, and the Bedrock client library is on a pinned snapshot (below).
Facts and run logs: [platforms/geyser.md](../platforms/geyser.md). Decision: [ADR-0018](../adr/0018-bedrock-driver-geyser-cells.md).
API contract: [test-api section 14](../specs/test-api.md).

## Read this first: the cell is offline-stub

> **WARNING: OFFLINE-STUB.** A Geyser cell turns Xbox login validation off (`validate-bedrock-login: false`). Nothing is checked against
> Microsoft, and the Bedrock "player" is whoever the driver says it is. **A plugin that needs a verified Bedrock identity (an Xbox
> account, an XUID you can trust, a ban list keyed on them) cannot be tested here.** Geyser itself calls this setting not supported, and a
> test that passes on this cell proves nothing about production authentication. `cell.json` of every run says
> `"bedrockAuth": "offline-stub"`.

No Bedrock Dedicated Server (BDS) is in M9. Testing a BDS add-on or a non-Java Bedrock server is planned for M10 and does not exist.

## Declare the test

```java
@Proofstone(plugins = "build/libs/MyPlugin.jar")
@Matrix(platform = Platform.GEYSER, versions = {"latest"})
@Requires(Capability.BEDROCK_CLIENT)
class BedrockBranchE2E {
    @CellTest
    void bedrockOnlyBranch(Agent agent, Clients clients) {
        Client bedrock = clients.bedrock("PsEx1");           // the server sees ".PsEx1"
        assertThat(agent.player(bedrock).name()).isEqualTo(".PsEx1");
    }
}
```

- `Agent` is the Paper server's Bukkit agent. `Client` is the ordinary Java client of the cell. `clients.bedrock(name)` returns a
  `Client` backed by the Bedrock driver, after the spawn status arrived and the agent lists the player.
- The name is 1 to 15 characters from `A-Za-z0-9_`. Anything else is an `IllegalArgumentException` before anything connects.
- A runnable example: [`examples/geyser-bedrock`](../../examples/geyser-bedrock/README.md).
- `mods` and `datapacks` on a Geyser class are refused (`INVALID test declaration`). A differential run, `-Dproofstone.repeat` above 1
  and a driver other than `mcpl` are refused with an error naming the cell.

## Select the cell

The Geyser row is `experimental`, so the class plans a cell only when you name it. One cell per invocation:

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew <task> \
  -Dproofstone.cells=geyser@2.12.1+paper@26.3 --no-parallel --max-workers=1
```

`PROOFSTONE_CELLS` is the environment form of the same selector (shared with every other row; the runs recorded here used the `-D`
form). Without a selector the class prints `NOT SELECTED (not run, not a pass)`. Read the EULA first (<https://aka.ms/MinecraftEULA>);
the harness never accepts it for you.

Geyser cells in v1 run from a repository checkout: the cell needs the Bedrock driver host jar (`drivers/bedrock`, built by
`:drivers:bedrock:shadowJar`) through `-Dproofstone.bedrockHostJar`, which `examples/geyser-bedrock/build.gradle.kts` shows. Whether a
build outside this repository can run a Geyser cell has not been run.

## What the Bedrock client can do in v1

| Works (each ran on `geyser@2.12.1+paper@26.3`) | Throws `UnsupportedOperationException` naming ADR-0018, or is skipped |
|---|---|
| `chat`, `command`, `awaitMessage` | `useBlock` and `openBlock`, window clicks |
| `inventory` (id and count only), `position` | forms |
| join and disconnect | resource packs, `transferTo` |

`@Requires(BEDROCK_CLIENT)` on any other cell, including `paper@26.3` alone, is `SKIPPED` with `CAPABILITY_MISSING`. A skip is never a
pass. The cell does **not** hold `SIMULATED_BEDROCK` (that means a Java client under a `.` name), `RESOURCE_PACKS`, `DATAPACKS_VANILLA`,
`PROXY` or `TOPOLOGY`. `DualAssert.item` compares id and count only: the driver reads no item data components.

## Quirks you will meet

- **The Floodgate name.** Floodgate prefixes one `.`. The Bedrock client is `.PsEx1`, so `agent.player(bedrock)` and a plugin's
  `getPlayerExact` use the dotted name. Detecting Bedrock by that prefix is what the example plugin does.
- **The `§r` prefix.** Every system message a Bedrock client receives starts with `§r` (a reset code); the Java client gets the plain
  text. Match it as it is, as the example does, or match a substring. The join message arrives as a message too
  (`§r§e.PsEx1 joined the game`), so a pattern may meet messages from before your command.
- **Formatting codes in plugin output.** Paper's `/plugins` reaches a Bedrock client as separate messages with codes
  (`§r§bℹ §r§fServer Plugins (4):`, then the list).
- **Commands.** Vanilla `/list` answered a Bedrock client "Unknown or incomplete command" in one run, although the server logged the
  command. Whether Geyser or Paper permissions cause it is unverified. `/plugins` worked.
- **Reconnecting.** The server notices a Bedrock disconnect about 10 s late. `clients.bedrock(name)` waits (an await, never a sleep)
  until the agent forgets an earlier `.name` before it joins, and again after the test.

## Troubleshooting (only what ran)

A red run is a finding. Keep the XML and the bundle (`build/proofstone/geyser@2.12.1+paper@26.3/<Class.method>/`: `server/`,
`stand-in/` and `driver-host/` logs, transcripts, `cell.json`) and do not retry it into green. The Floodgate key is redacted from the
bundle.

| Seen | Meaning | What to do |
|---|---|---|
| `INVALID`: `the discovery stand-in answered none of the two expected names`, `hop: server and driver host to the stand-in` | The stand-in is absent or refused the two Microsoft names. Geyser's log has `Unable to fetch discovery data` and `UnknownHostException: client.discovery.minecraft-services.net`; the client alone sees `disconnect.timeout` at stage `LOGIN_SENT` after about 11 s. Reproduced on purpose by the bad test (fault `stand-in-absent`). | Read the `stand-in/` and `server/` logs. Check Docker DNS between the containers. |
| `INVALID`: `Floodgate refused the data Geyser sent`, `hop: Geyser to Floodgate on the server`, client text `Please connect through the official Geyser` (stage `START_GAME`) | Floodgate decrypted with a key Geyser never used. Reproduced by changing Floodgate's own `key-file-name`; changing Geyser's key setting did nothing, because on one server Geyser takes Floodgate's key. | The harness writes the key per cell. Look for a plugin or config of yours that replaces Floodgate's files. |
| `PROOFSTONE BEDROCK JOIN TIMEOUT ... stage=<stage>` | The join did not finish in 30 s. The stage names the last step reached; the server, stand-in and driver-host logs are printed. Bedrock join timeouts are counted apart from Paper numbers. | Read the stage and the three logs. No timeout occurred in the recorded runs, so one is new information: keep it. |
| Client sees `Please log into Xbox to join this server.` | Login validation is on. In the spike an offline client was refused with it. The harness cell turns validation off. | Seen only when the setting was absent. |
| A WARN from your plugin fails the test | The log guard applies as on Paper. Geyser's three `XBOX AUTHENTICATION IS DISABLED` lines at its start are known and listed. | Fix the plugin. Do not allowlist to make it green ([troubleshooting](troubleshooting.md)). |

**Docker and UDP.** The driver runs in a container on the cell network, so **no UDP port is published** and Docker's host UDP mapping
does not matter. The general Docker rules (one run at a time under the lock, `--no-parallel --max-workers=1`, a 500 from Docker is
infrastructure) are in [troubleshooting](troubleshooting.md#docker-lock-parallel-runs-and-could-not-find-a-valid-docker-environment).

**First boot.** The first run downloads the pinned Geyser and Floodgate jars (sha256 checked) into `build/geyser-downloads` and pulls the
pinned image. In the recorded runs a cell took 31 to 37 s to become ready and the first Bedrock join about 2.5 s (Windows 11, Docker
Desktop). Those are observations on one host, not a promise. The first-run download time was not measured.

**Snapshot driver.** The Bedrock client library (CloudburstMC Protocol, `3.0.0.Beta15-20261116.161745-6`) exists only as a snapshot; no
release carries protocol 2225. It is pinned by exact timestamped version and sha256 and shaded into the driver host, so your Gradle graph
never sees it. If the snapshot repository drops that build, resolution fails loudly. This is one reason the row is not promoted.

## Evidence and status

The Geyser row is `experimental`. Recorded, each on one Windows 11 machine: 3 of 3 green invocations of the conformance
(`tests=7 skipped=3 failures=0 errors=0`: 4 passed, 3 skipped with reasons), 3 of 3 of the cell host test, 3 of 3 of the negative
controls, with 0 Bedrock join timeouts. Red runs also happened while the scenarios and the cell were written, each recorded as a finding
in [geyser.md](../platforms/geyser.md). No flake rate over many runs exists and none is claimed. ADR-0015 is not applied to the row, and
it cannot be while the driver is a snapshot.
