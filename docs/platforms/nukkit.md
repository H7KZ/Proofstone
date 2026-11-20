# Nukkit family (PowerNukkitX) and the M9 Bedrock driver

Spike T-M10-13, 2026-11-20, Windows 11 host, Docker Desktop. Everything below was run by hand under `scripts/dockerlock.sh` with
`scripts/spike/nukkit/nukkit-spike.sh` and the throwaway agent in `scripts/spike/nukkit/agent/`. Nothing here is built: `versions.yml`
has no Nukkit row, no matrix row changed, the wire protocol did not change and no production code changed. Run output lived in
`build/spike/nukkit/` (not committed). Where a run did not show a thing, the text says "not run" or "from source, not run".
See [platforms.md](../platforms.md), [ADR-0018](../adr/0018-bedrock-driver-geyser-cells.md) and [bds.md](bds.md).

## Verdict

**Not feasible today with the M9 Bedrock driver; the agent half works.**

- **PowerNukkitX 3.0.6** (Bedrock protocol **2225**, the driver's own) is **NetherNet-only**. The driver speaks RakNet only, so it
  never got past `RAKNET_CONNECT` (`ConnectTimeoutException`, 2 of 2 runs). The protocol numbers match; the transport does not.
- **PowerNukkitX 3.0.5** (protocol **2193**, the last release that still binds RakNet) accepted the RakNet connection and refused the
  driver's 2225 with `LOGIN_FAILED_SERVER_OLD` at `NETWORK_SETTINGS` (1 of 1). With the driver's existing test-only protocol switch
  sending 2193 in `RequestNetworkSettings`, the server answered the network settings and refused the login packet (still 2225) with
  the same status at `LOGIN_SENT` (1 of 1).
- So **no run reached the login chain check**: whether PNX accepts the driver's self-signed chain with `xboxAuth: false` is
  **unverified** (the source says it does, see below).
- **A Java plugin agent works**: a 130-line PNX plugin served `GET /v1/health` and `GET /v1/players` in the v1 envelope from another
  container on the cell network, refused a wrong token with 401, and read on the main thread (4 of 4 runs that booted).

What would reopen it: either a Bedrock driver that speaks NetherNet (which would also serve BDS, see [bds.md](bds.md), and Nukkit
master, below), or a PNX release at the driver's protocol with a RakNet listener. Neither exists today.

## Which server, and why

| Candidate | What the official source showed (2026-11-20) | Pick |
|---|---|---|
| **PowerNukkitX** (`PowerNukkitX/PowerNukkitX`, LGPL-3.0) | Versioned GitHub releases, each with a GitHub-computed sha256 asset digest and release notes stating the Minecraft version and the Bedrock protocol; last push 2026-11-19 | **yes** |
| Nukkit (`CloudburstMC/Nukkit`) | No versioned release (one 2018 `snapshot` tag); builds come from `dl.opencollab.dev` / Jenkins, whose API answered HTTP 502; master is at protocol 2225 but its newest commit is "Replace raknet with nethernet, run on Java 17+ (#2296)" (2026-11-09) | no: no pin to take, and NetherNet-only on master too (from source, not run) |

Two PNX releases were taken, both inside the approval for "a Nukkit-family server": 3.0.6 for the protocol match the card asks for,
and 3.0.5 because the 3.0.6 source showed RakNet was removed (`RakNetSettings.java` deleted in #3240, "Bump the Network library
version"), so 3.0.5 is the only way to separate the transport from the protocol.

## The pins (what ran)

Facts written to `/c/pw/nukkit-cache/NOTES.txt` before the download, from the GitHub release API; the downloaded files matched both.

| Fact | 3.0.6 | 3.0.5 |
|---|---|---|
| Release page | `https://github.com/PowerNukkitX/PowerNukkitX/releases/tag/3.0.6` (2026-10-28, not a pre-release) | `.../releases/tag/3.0.5` (2026-09-15) |
| Tag commit | `c57499365737654eca32df008751ef6bca5af142` | |
| Minecraft / Bedrock protocol (release notes, and the server's own log) | 1.26.60 / 2225 | 1.26.50 / 2193 |
| URL | `https://github.com/PowerNukkitX/PowerNukkitX/releases/download/3.0.6/powernukkitx.jar` | `.../download/3.0.5/powernukkitx.jar` |
| Size | 93,876,523 bytes | 86,100,503 bytes |
| sha256 | `10822d818c1f2c7d423092a235f3f04bd0eb915b2298b204ed1697d7a0dd9539` | `bc3c54236256c8e3bf8573ecf090e3853785c7622891d7d28d780a398fc1de60` |
| Log line | `PowerNukkitX version 3.0.6-SNAPSHOT (git-c574993) (API 3.0.6)` | `3.0.5-SNAPSHOT` |
| Transport | `NetherNet signalling listening on tcp/19132`; UDP 19132 held by Query | `RakNet listening on udp/19132` (`network-settings.transport: raknet`) |

- Kept in `/c/pw/nukkit-cache/` (outside every repository), never committed; the script checks the sha256 before every run.
- **Image:** none of PNX's own. The pinned `itzg/minecraft-server` `java25` digest (`versions.yml`) with `--entrypoint sh`; the jar and
  the agent plugin are copied in (no bind mount). PNX needs Java 21.
- **Licence, accounts:** PNX shows an LGPL-3.0 acceptance at its first start; with `PNX_SETUP_NON_INTERACTIVE=1` and `--skip-setup`
  it logs `Automated environment detected. Continuing setup with default configuration.` No EULA, no account, no token, no secret.
  The script still needs `PROOFSTONE_ACCEPT_EULA=true` in the command environment.
- **Not pinned in `versions.yml`.** A real pin would be this release asset and its digest.

## Host

- Started with `java -Xmx1G -DdisableSentry=true -jar powernukkitx.jar --skip-setup --language eng`. `disableSentry` turns off PNX's
  automatic crash reports to Sentry.
- `Done (` in 2.5 to 6.2 s; the whole container start to `Done` in 4 to 8 s.
- Two starts: the first writes `pnx.yml`; the script stops the container, sets `settings.xboxAuth: false` (camelCase; `xbox-auth` does
  not exist, the r1 red), and starts again.
- The server and the driver host lost their default route (`ip route del default`, `NET_ADMIN`) before their process started; their
  only DNS server was the T-M9-01 stand-in (`scripts/spike/geyser/HostLog.java`).
- **Names asked: none**, in all five runs, from the server (boot, two starts) and the driver host. PNX made no lookup at boot with Sentry
  off. The driver host failed before its Cloudburst start-up fetch ran (as in the BDS spike), so the stand-in was never needed.

## Join with the M9 driver host

The driver host (`drivers/bedrock`, `bedrock-host-all.jar`, Bedrock protocol 2225, game version 1.26.60) ran in its own container of the
pinned image and was called with `curl` inside it (`POST /rpc/v1/sessions`, host `pnx`, port 19132, `joinTimeoutMillis` 30000, a
claimed numeric XUID), as in the BDS spike.

| Run | Server | Driver sends | Verdict | Stage reached | Server's answer |
|---|---|---|---|---|---|
| r2 | 3.0.6, `xboxAuth: false` | 2225 | `DISCONNECTED` (HTTP 409) | `RAKNET_CONNECT` | none: `RakNet connect to pnx:19132 failed: ...ConnectTimeoutException` |
| r4 | same | 2225 | same | `RAKNET_CONNECT` | same; curl measured 10.4 s inside the container |
| r3 | 3.0.5, `xboxAuth: false` | 2225 | `DISCONNECTED` | `NETWORK_SETTINGS` (RakNet up) | `PlayStatusPacket` `LOGIN_FAILED_SERVER_OLD` |
| r5 | same | 2193 in `RequestNetworkSettings` (test-only switch), 2225 in `LoginPacket` | `DISCONNECTED` | `LOGIN_SENT` (network settings in, login sent) | `PlayStatusPacket` `LOGIN_FAILED_SERVER_OLD` |

- The agent's `players` read after each join was `[]`; no player ever joined.
- PNX logged nothing for any refused connection or login at its default log level.
- **Protocol gap stated:** PNX 3.0.6 = 2225 = the driver (match), but NetherNet; PNX 3.0.5 = 2193 (gap of one Bedrock release), RakNet.
- r5 used the driver's existing switch from T-M10-05 (`-Dproofstone.injectGeyserFault=refused-protocol
  -Dproofstone.bedrock.protocolOverride=2193`, through `JAVA_TOOL_OPTIONS`). It changes only `RequestNetworkSettings`; the login packet
  takes the codec's 2225 (`ClientPackets`), so PNX refused there. Going further would need a driver change, which a spike does not make.

### What the source says about the login (from source, not run)

PNX 3.0.6 `LoginHandler` reads `settings.xboxAuth` and refuses an unsigned chain only when it is `true`, and it validates the chain with
CloudburstMC Protocol's `EncryptionUtils`. In Geyser that class fetched the two Microsoft discovery names at the first login (ADR-0018),
so a PNX cell would likely need the same stand-in. Neither was shown here.

## A Java plugin agent

What ran: `scripts/spike/nukkit/agent/` (`SpikeAgent.java`, `plugin.yml` with `api: ["3.0.0"]`), compiled with `--release 21` against the
pinned server jar only and packed as `plugins/proofstone-spike-agent.jar`. It uses the JDK `HttpServer`, reads the token from
`PROOFSTONE_AGENT_TOKEN`, listens on 25580 and runs each read on the main thread (`ServerScheduler#scheduleTask`, awaited with a 10 s
bound).

| Read (from the stand-in's container, `http://pnx:25580`) | r2, r4 (3.0.6) | r3, r5 (3.0.5) |
|---|---|---|
| `GET /v1/health` | `{"agentVersion":"0.0.1-spike","protocol":"1.3","platform":"powernukkitx","mcVersion":"1.26.60","capabilities":["players"]}` | same, `mcVersion` `1.26.50` |
| `GET /v1/players` (before and after the join) | `{"players":[]}` | `{"players":[]}` |
| wrong token | 401 `UNAUTHORIZED` | 401 |

- Observe-only: the grep for write-capable calls found none in every run; the agent calls `getOnlinePlayers`, `Player#getName`,
  `getVersion` and `getNukkitVersion` only.
- **Not shown:** a `players` read with a player online, `GET /v1/players/{name}` (`PlayerState`), events, commands, the golden contract
  replay.
- **What a real agent would be:** a PNX plugin (API `org.powernukkitx.*`, Java 21, `plugin.yml`), reusing `platforms/agent-core`
  (Java 8 bytecode, so it loads on Java 21) for the HTTP server, envelope and goldens, with a PNX adapter in place of the Bukkit one. Its
  `platform` token would be `powernukkitx`. Bedrock item ids and inventory slots would need their own mapping into `player-state.json`
  (not tried). PNX's API moved from `cn.nukkit` to `org.powernukkitx` in 3.x, so a Nukkit agent and a PNX agent would be two adapters.

## The oracle gap

ADR-0002 and ADR-0003 want two sides. Here the **server side works** (the agent above) and the **client side does not exist**: the only
Bedrock driver Proofstone has cannot reach a PNX release at its own protocol, because that release has no RakNet. The same wall stands
in front of BDS (NetherNet "the only supported transport type", [bds.md](bds.md)), Nukkit master (source) and, by default, Geyser 2.12.1
(which needs `bedrock.transport: both`, ADR-0018). A NetherNet-capable driver is the common answer to all four; it is a decision, not
this spike.

## Run log

Invocation for every run (Git Bash, from the repository root):

```bash
PNX_VERSION=<3.0.6|3.0.5> [DRIVER_PROTOCOL=2193] PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 \
  bash scripts/dockerlock.sh bash scripts/spike/nukkit/nukkit-spike.sh build/spike/nukkit/<run>
```

| Run | Knobs | Result |
|---|---|---|
| G1 | `./gradlew :drivers:bedrock:shadowJar --no-parallel --max-workers=1` under the lock | green, `bedrock-host-all.jar` 11,399,399 bytes |
| r1 | 3.0.6 | red, script: the edit looked for `xbox-auth`, the key is `xboxAuth`; nothing after the first start ran |
| r2 | 3.0.6 | agent 3 reads green; join `RAKNET_CONNECT` timeout. The join call returned 167 s after it was sent (curl's own time was not measured in r2; `-m 60` should have capped it), cause not found |
| r3 | 3.0.5 | agent green; join refused `LOGIN_FAILED_SERVER_OLD` at `NETWORK_SETTINGS` |
| r4 | 3.0.6 (r2 repeat, curl time measured) | agent green; join `RAKNET_CONNECT` timeout after 10.4 s |
| r5 | 3.0.5, `DRIVER_PROTOCOL=2193` | agent green; join refused `LOGIN_FAILED_SERVER_OLD` at `LOGIN_SENT` |

Time box: about half a day of the two days. Not done: a NetherNet client, a join that spawns, a run with `xboxAuth: true`, EGRESS open,
Nukkit itself.

## Gotchas

- The `pnx.yml` keys are camelCase (`xboxAuth`, `maxPlayers`), except a few (`sub-motd`, `network-settings`).
- PNX 3.0.6 binds UDP 19132 for Query and NetherNet signalling on TCP 19132; nothing answers RakNet there.
- Without `-DdisableSentry=true` (or `disable-auto-bug-report` in a `server.properties`) PNX reports crashes to Sentry.
- First start without `pnx.yml` runs a setup wizard; in a container without a console it continues with defaults, but only when
  `PNX_SETUP_NON_INTERACTIVE` (or `CI`) is set or no console is attached.
- `--disable-ansi` did not remove the colour codes from the log; strip them when reading.
