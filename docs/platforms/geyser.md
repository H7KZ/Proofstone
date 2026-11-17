# Geyser and Floodgate (Bedrock clients on a Java server)

Spike T-M9-01, 2026-11-17, Windows 11 host, Docker Desktop 29.9.2. Everything below was run by hand under
`scripts/dockerlock.sh` with the scripts in `scripts/spike/geyser/`; nothing here is built, `versions.yml` has no Geyser row
and no production code changed. Run output lived in `build/spike/` (not committed, so no repro bundle is linked). Where the
runs did not show a thing, the text says "not run".

## Verdict

**Offline Bedrock through Geyser is feasible on `paper@26.3`, with three conditions that each failed when removed, and it is
not "offline" without the third.**

| Route (Geyser `java.auth-type`) | CloudburstMC Protocol (Java) | `bedrock-protocol` (Node) |
|---|---|---|
| `offline`, Floodgate not installed | 15 of 15 joins green (9 on the host's UDP port, 6 from a client on the cell network) | 6 of 6 |
| `floodgate`, Floodgate installed | 14 of 14 | 7 of 7 |

The counts are joins with all three conditions below in place (a spawn status received and the agent's `players` listing the
player on host-placed joins). Joins without them are in the run log.

Conditions:

1. **`advanced.bedrock.validate-bedrock-login: false` in Geyser's config.** With the default `true`, an offline client's
   self-signed chain is refused: `Please log into Xbox to join this server.` (3 of 3 reds: Java offline, Java Floodgate, Node
   Floodgate). Geyser's own config text calls turning it off "NOT SUPPORTED" and a username-spoofing risk, and says Floodgate
   functionality "will not work" when it is off. With the flag off Floodgate worked in the runs (names, UUIDs, agent); the one Floodgate failure was an empty XUID (see
   Gotchas). With the flag on, nothing could be joined offline, so Floodgate with validation on is only known to refuse.
2. **`bedrock.transport: both`.** Geyser 2.12.1's default is `nethernet`; its only other value is `both`, which adds RakNet
   (UDP 19132). Both drivers speak RakNet. Without `both` no RakNet listener exists (not run: the drivers have no NetherNet
   support, so there was nothing to join with).
3. **A stand-in for two Microsoft discovery names.** CloudburstMC Protocol's `EncryptionUtils` static initialiser fetches
   `https://client.discovery.minecraft-services.net/api/v1.0/discovery/MinecraftPE/builds/1.0.0.0`, then the OpenID
   configuration (and, with login validation on, the JWKS) of `authorization.franchise.minecraft-services.net`. Geyser (the
   server, in the first login of each server start) and the Cloudburst Java driver (in every client JVM) both hit it. Without
   a route out the server join fails (`An internal error occurred!`, `AssertionError: Unable to fetch discovery data`,
   1 red); with a route out the first join contacts these two Microsoft hosts. By the card's rule ("a Microsoft or Xbox host at
   join is a 'not offline' verdict") plain Geyser is **not offline**. The spike's stub (`HostLog`, a DNS answer for those two
   names, a TLS endpoint with invented documents, and a server JVM trust store copy that also trusts the stub certificate) made
   it offline: with the server's default route removed, every join in the table passed and no packet reached Microsoft. The
   documents are made up (they carry no Microsoft key), which is fine only because login validation is off. Whether Proofstone
   owns such a stub was an ADR-0018 decision: it does, as a container of the pinned image ([ADR-0018](../adr/0018-bedrock-driver-geyser-cells.md) section 4).

**If the stub is not acceptable, the narrower scope is the simulated tier only** (`SIMULATED_BEDROCK`, a Java client under a
`.` name, test-api section 11). A Java client (the `mcpl` driver) joined the Geyser and Floodgate server on the Java port
under `PlainJava` and `.PrefJava`: 3 of 3 green after one harness red (see the run log). The server treats both as Java players
(offline UUIDs, no `Floodgate player logged in` log line, the agent lists the dotted name). That is the simulated tier, not a
Bedrock client.

**The other reading of "a Bedrock Java-client-compatible join without Xbox auth":** a Bedrock client written in Java
(CloudburstMC Protocol, the library Geyser is built on) does join with no Xbox account, token or secret, under the three
conditions above. A Java-edition client (`mcpl`) joining as a Bedrock player is not possible: Geyser's Floodgate handshake only
comes from Geyser.

## Host (what ran)

- Image: the pinned `itzg/minecraft-server` `java25` digest (`sha256:e2fabc53...adf1e0`, `versions.yml`), `TYPE=PAPER`,
  `VERSION=26.3`, `PAPER_BUILD=244`; the jar in the container hashed `9601f9a26a2a52a0f2c807cf1abf691f96ee4fb287878313b601cebc2c51eabf`,
  the value `versions.yml` pins. `ONLINE_MODE=FALSE`, flat world, the Bukkit agent (`agent-all.jar`) copied to `/plugins`.
  Jars and the stub files were copied in (`docker cp`), never mounted.
- Plugins copied to `/plugins` (the image syncs them to `/data/plugins`), downloaded from the official API
  `download.geysermc.org/v2` and checked against the sha256 it publishes:

  | Plugin | Version, build | File | sha256 | Bytes |
  |---|---|---|---|---|
  | Geyser-Spigot | 2.12.1, build 1303 (2026-11-15), log `2.12.1-b1303 (git-master-440be82)` | `Geyser-Spigot-2.12.1-b1303.jar` | `218f6e1a09a2fe458cfd531e7295a0f19d17b00db38ceec16222e8982a4d7f21` | 52,216,643 |
  | floodgate (spigot) | 2.2.5, build 141 (2026-09-17, "Update to 26.3 (#686)") | `floodgate-spigot-2.2.5-b141.jar` | `21570aff9ce17d6983928e8552777760e1ede5050026b04c686b0ae112e6fd7e` | 11,561,096 |

  Floodgate is copied only for the `floodgate` route. With it installed, Geyser hands every login to Floodgate whatever
  `auth-type` says (one run showed this, see Gotchas), so the `offline` route has to run without Floodgate.
- Geyser config, generated at the first start and edited in place as uid 1000 before a restart (the "warm start"):
  `bedrock.transport: both`, `bedrock.webrtc-port: 19133` (the NetherNet UDP port must differ from RakNet's),
  `bedrock.signaling.stun-discovery: false` (otherwise two STUN hosts are queried), `java.auth-type: offline` or `floodgate`,
  `advanced.bedrock.validate-bedrock-login: false`. Everything else stayed default. Geyser binds `0.0.0.0:19132` (RakNet,
  UDP) and its built-in signaling on TCP 19132: the log line `Started Geyser on UDP port 19132` followed by
  `Built-in signaling started on http://0.0.0.0:19132, with NetherNet on UDP port 19133`.
- Paper's `bukkit.yml` `connection-throttle` was set to `-1` for the Java joins (see Gotchas).
- Measured start times, container start to the second `Done (`: cold start 25 to 27 s (jar download, Geyser's Minecraft jar
  fetch), warm start (restart of the same container) 14 to 17 s. Geyser's own `Done` takes about 2.3 s of that.
- Ready for a Bedrock client when the log shows `[Geyser-Spigot] Started Geyser on UDP port 19132` and the Paper `Done (`, and
  the agent's `/v1/health` answers (not wired into any host).
- **The way out is closed before the joins.** The spike's cell is two containers on a user network: the server and a
  `HostLog` container (the server's only DNS server, `--dns`, which logs every name it is asked and forwards it to Docker's
  resolver). After the warm start the script runs `ip route del default` in the server (`--cap-add NET_ADMIN`); `curl` then
  fails with exit 7 (no route), and from then on the server can reach only the cell network. DNS names tried are in
  `dns.log` with timestamps, and the script's `markers.log` brackets each join. The same image already holds `ip`, so no
  new image was needed.
- Driver placements run: (a) the driver on the host, joining the **UDP port Docker publishes** (`-p 127.0.0.1::19132/udp`);
  (b) the Java driver in a container of the same pinned image **on the cell network** (`--network-alias`, no published port,
  its own default route removed before the JVM starts). Both work (see UDP).

## What was joined, per join

Same for every successful join unless a row says otherwise.

- **Bedrock protocol and game version:** both drivers sent protocol **2225**, game version **1.26.60** (CloudburstMC
  `Bedrock_v2225`; `bedrock-protocol` 3.61.0 `1.26.60`), RakNet protocol 11. Geyser logged
  `Player connected with username PsBed1 (2225)`. The Geyser jar carries codecs 2168, 2169, 2193 and 2225 and lists Bedrock
  26.40 to 26.63 as supported; only 2225 was run.
- **Packets up to spawn**, in order (client side, counted from the join logs; `x12` is twelve in a row):
  - Cloudburst client: `NETWORK_SETTINGS`, `SERVER_TO_CLIENT_HANDSHAKE`, `PLAY_STATUS` (login success),
    `RESOURCE_PACKS_INFO` (1 resource pack, the Geyser integrated pack), `RESOURCE_PACK_STACK`, `JIGSAW_STRUCTURE_DATA`,
    `VOXEL_SHAPES`, `START_GAME`, `SYNC_ENTITY_PROPERTY` x12, `LEVEL_CHUNK`, `BIOME_DEFINITIONS_LIST`,
    `AVAILABLE_ENTITY_IDENTIFIERS`, `CAMERA_PRESETS`, `PLAY_STATUS` (player spawn), then `SET_COMMANDS_ENABLED` and
    `UPDATE_ATTRIBUTES`. 27 packets counted. `ITEM_REGISTRY` and `CREATIVE_CONTENT` did not decode in this client (see Gotchas).
  - Node client: the same order with `item_registry` after the entity properties and `creative_content` before the spawn
    status, 26 packets. The client answers the packs with have-all, then completed; it sends a chunk radius request after
    `start_game` and `SetLocalPlayerAsInitialized` at spawn.
  - Time from RakNet connect to the spawn status, 47 timed joins: 1.0 to 5.7 s, median 2.4 s (the first join of a boot is the
    slowest; the cell-network client is about 1.0 s after its first).
- **Name and UUID the server sees** (agent `GET /v1/players/{name}` and the server log):

  | Route | Driver | Name | UUID |
  |---|---|---|---|
  | `offline` | both | `PsBed1` (the client's display name) | `b557fe7d-6855-312b-a977-bd5564104feb`, the Java offline UUID of that name; the identity UUID the client put in its chain is not used |
  | `floodgate` | Cloudburst (claims XUID `2535400000000001`) | `.PsBed1` (Floodgate prefix `.`) | `00000000-0000-0000-0009-01eed05d1001` (the XUID in the low bits) |
  | `floodgate` | Node (XUID 0, not settable) | `.PsBed1` | `00000000-0000-0000-0000-000000000000` for every player (two Node players at once would collide; not run) |

- **Does the Bukkit agent's `players` read the player?** Yes, in all 40 joins that ran the hook (one more hook failed on a
  harness path to `bash`, 6 cell-network joins and 1 hand run had none; 47 of the 48 green joins are timed in the logs):
  `players` listed the name, `GET /v1/players/{name}` returned the state (survival, health 20, location), and the agent's
  `bedrock-prefix` token was present in `/v1/health` (it is present on every offline server, so it says nothing about
  Floodgate). The agent shows no Bedrock-specific field. Cell-network joins were checked in the server log only (`UUID of player`, `logged in with entity id`).
- **Microsoft or Xbox hosts at join** (server side, `dns.log` between `JOIN 1 begin` and `JOINS`): in every join run with the stub the
  server looked up exactly `client.discovery.minecraft-services.net` and `authorization.franchise.minecraft-services.net`
  (once per server start, at the first login), and `HostLog` answered both itself (`STUB GET .../discovery/...` and
  `.../.well-known/openid-configuration`). No other name was looked up during any join. Without the stub and with a route
  out (5 runs with the route open) the same two names reached Microsoft. Cloudburst's client JVM fetches the same two documents at
  every start (`-Djavax.net.debug=ssl:handshake` showed SNI `client.discovery.minecraft-services.net` and
  `authorization.franchise.minecraft-services.net` on a host-placed client with internet); in the cell-network placement the
  client's DNS is `HostLog` and its trust store holds the stub, and 6 of 6 joins passed with the stub answering the client.
  The Node client made no JS-level lookup, connect, TLS or fetch (`net-log.js`, 1 join); its RakNet socket is native and not
  visible to that hook, so only "the address it was given" is known. `login.live.com` and other Xbox hosts: never looked up by
  the server in any run, never seen by the client hooks.

## What Geyser and Floodgate fetch at start, and whether it can be pinned

Names the server looked up (cold start, default config, network open). "Asked by" is read from the log order (the DNS log carries no process), so it is an inference:

| Host | Asked by | Used for | Needed? |
|---|---|---|---|
| `fill.papermc.io`, `fill-data.papermc.io`, `piston-data.mojang.com` | itzg image, Paperclip | Paper jar, vanilla jar (the existing Paper start) | yes on a cold start; the image also re-resolves the Paper build at **every** start |
| `discovery.minecraftservices.com`, `api.minecraftservices.com` | Paper (looked up before any plugin loads) | server start | not needed by Geyser |
| `api.geysermc.org` | Floodgate at its load, then Geyser at its enable (Geyser's classes hold the update check and news URLs; Floodgate's config enables global linking) | `Unable to resolve Geyser api! Cannot check for Geyser updates.` is only a WARN | no |
| `launchermeta.mojang.com`, `piston-meta.mojang.com` (and `resources.download.minecraft.net` in one hand run) | Geyser | Mojang's version manifest, the client jar and locale assets for translations (`Downloading Minecraft JAR ...`) | no: starts without them, `WARN Failed to load locale asset cache: Unable to resolve requested url ... Are you offline?` |
| `stun.cloudflare.com`, `stun.l.google.com` | Geyser, NetherNet | public address discovery | no: `stun-discovery: false` removes both |
| `client.discovery.minecraft-services.net`, `authorization.franchise.minecraft-services.net` | Geyser at start when `validate-bedrock-login` is true (also the JWKS), and at the first login either way | login code's start-up documents | **yes at the first login** (see Verdict, condition 3) |

- **Mappings are not fetched.** Block, item and skull mappings, the custom block and item overrides (`Registered 199 custom
  block overrides`, `7 custom blocks`, `1 custom items`) and the integrated resource pack come from the Geyser jar, so the jar's
  sha256 pins them. Geyser also writes `locales/`, `cache/`, `custom_mappings/`, `packs/`, `nethernet/` and `tmp_locale.jar`
  into `plugins/Geyser-Spigot`.
- **Geyser pins:** the jar by sha256 (the API publishes it per build; a build number is stable). The Mojang manifest and
  locale fetch cannot be pinned and need not be: a warm start with every name except `papermc.io` and the stub names answered
  `NXDOMAIN` started Geyser (`Done (2.404s)`) with the two WARNs above and then joined 3 of 3.
- **The Floodgate key** is not downloaded: Floodgate creates `plugins/floodgate/key.pem` (16 bytes, an AES key) at its first
  start, different each time (two runs, two hashes), and Geyser on the same server picks it up (`floodgate-key-file: key.pem`
  is only used when Floodgate is elsewhere). It can be pinned: a `key.pem` copied to `/plugins/floodgate/` before the first
  start survived (sha256 `795a251b...0a612b` before and after, 3 of 3 joins). Not run: Geyser and Floodgate on different
  servers.
- Floodgate's config enables bStats metrics and global linking; no bStats host appeared in runs of 1.5 minutes. Not run: a run long
  enough for the metrics timer.

## UDP

- **Docker UDP publishing works from a host driver.** `-p 127.0.0.1::19132/udp` gave a random host port; both drivers
  joined through it on Windows Docker Desktop (53 host-placed joins, 42 green; every red but the 4 `jsp-raknet` ones got an
  answer from Geyser). Geyser sees the Docker gateway, `172.29.77.1`, as the player's address, not the driver's.
- **A driver on the cell network works** for the Java driver (6 of 6 joins, host `ps-gy-srv`, port 19132). The Node driver in a
  container needs a Node image, which the pinned images do not have, so it was **not run** and is a new-image finding.
- **Testcontainers 2.0.5** (`scripts/spike/geyser/TcUdpProbe.java`, a Python UDP echo on 19132 in the pinned image):
  `withExposedPorts(19132)` publishes TCP only, `getMappedPort(19132)` returned a TCP port and a UDP datagram to it got no echo.
  A `withCreateContainerCmdModifier` that adds `ExposedPort.udp(19132)` and a `PortBinding` to `127.0.0.1` publishes the UDP
  port and the echo came back, but `getMappedPort(19132)` then throws `Requested port (19132) is not mapped`: the host port
  has to be read from the container's inspect data (`getNetworkSettings().getPorts().getBindings()`). So a host needs its own
  UDP mapping code; the Testcontainers API does not give one. The Geyser join itself was run with `docker create -p`, the same
  Docker binding the modifier produces; Geyser through a Testcontainers cell was **not run**.

## Capabilities

`SIMULATED_BEDROCK` keeps meaning a Java client under a `.` name. A real Bedrock client would be a different capability
(not defined, ADR-0018). Held by a Bukkit agent on this cell, as observed: `command`, `players`, `config`, `reload`, `world`,
`events`, `log`, `ticks`, `bedrock-prefix`, `datapacks`, `resource-pack-status`. Since T-M9-06 the agent also reports the `floodgate` token when the plugin is enabled (see "Geyser cell host" below). It cannot tell a Floodgate player from a Java player except by the name prefix and the UUID's shape.

## Agent notes

- The Bukkit agent loaded beside both plugins and answered health and players for every join. It observed only: no agent write
  ran in the spike.
- Floodgate names carry the prefix (`.PsBed1`), so a test that reads `players` or `GET /v1/players/{name}` uses the dotted
  name; the agent returns it as the server's name.
- Geyser and Floodgate add log lines the log guard has not seen: `[Geyser-Spigot] XBOX AUTHENTICATION IS DISABLED ON THIS GEYSER
  INSTANCE!` with two follow-up WARNs (its text names a `disable-xbox-auth` option; the real key is `validate-bedrock-login`),
  and the JVM `WARNING: sun.misc.Unsafe` lines from Floodgate's shaded Guava (stderr, not log lines). With the network open at
  start no ERROR line was added; `No key layers in MapLike[{}]` is the known flat-world line.

## Gotchas

- **Geyser 2.12.1 defaults to NetherNet, not RakNet** (`bedrock.transport: nethernet`). RakNet needs `both`, and the NetherNet
  UDP port must differ from RakNet's (`webrtc-port: 19133`). _paper@26.3, Geyser 2.12.1 b1303_
- **An unsigned (offline) chain is refused unless `validate-bedrock-login: false`:** the server disconnects with `Please log
  into Xbox to join this server.` and the Cloudburst client only sees `disconnect.timeout` after about 11 s (stage
  `login-sent`), the Node client `closed` (stage `login`). _3 reds_
- **Cloudburst's `EncryptionUtils.<clinit>` makes the first Bedrock login (server) and every client JVM (Java driver) fetch Microsoft's
  discovery document.** With no route the server answers `An internal error occurred!` and the log shows `AssertionError:
  Unable to fetch discovery data` with `SocketException: Network is unreachable`. _1 red_
- **Floodgate parses the XUID as a number.** A chain with an empty `XUID` (the first Java client) made Floodgate throw
  `NumberFormatException: For input string: ""` and Geyser kicked with `An internal error happened while handling Floodgate
  data` (stage `resource-packs`, 1 red, Floodgate installed with `auth-type: offline`). The Java spike client now claims a
  numeric XUID, unique per join; the Node client's offline XUID is 0.
- **A second login with the same claimed XUID while the first is still online is refused:** `PsBed2 is already logged in!`
  (2 reds, same XUID by mistake; the script now makes XUIDs unique and waits until the agent lists nobody).
- **`bedrock-protocol` 3.61.0 with the pure JS RakNet backend (`jsp-raknet` 2.2.0) cannot join Geyser:** it hard codes RakNet
  protocol 10, Geyser answers incompatible protocol (packet `0x19`, version 11), and the client retries until its connect
  timeout (stage `raknet-connect`, 4 reds: 3 in a run, 1 by hand). The `raknet-native` backend (its prebuilt `.node` ships in
  the npm tarball, so `npm install --ignore-scripts` still works) joined every time.
- **The Cloudburst client without item definitions logs two decode errors** (`ItemComponentPacket`: `Tried to read 2088 bytes
  but maximum is 1536`; `CreativeContentPacket`: `itemDefinitions is null`). The join still reached the spawn status, but a driver
  that reads inventory needs an item definition registry and a raised limit; not run.
  T-M9-05 read the library: the "bytes" in that text is a list size. The client initialiser starts with the default encoding settings
  (`maxListSize` 1536), below the 2088 item definitions the server sends; the library's `EncodingSettings.CLIENT` allows 10240, and the driver host sets it
  per session and installs the start game packet's item definitions on the codec helper. Proven on the codec in a unit test (2088 entries fail with
  the default, decode with the client settings); not yet against a real Geyser (T-M9-06).
- **The client's `disconnect()` is not noticed at once:** the server logs `Bedrock client timed out` about 10 s after the client
  exits, so a script waits for the agent to list nobody before the next join.
- **Paper throttles reconnects** (`connection-throttle: 4000`): a second Java join within 4 s was `Connection throttled! Please
  wait before reconnecting.` (1 red, the Java client). The proxy and loader hosts set `-1`; the spike did the same.
- **With Floodgate installed Geyser treats `auth-type: offline` as Floodgate** (the run's Floodgate handshake error above), so
  an offline-route cell must not install Floodgate.
- **The itzg image re-resolves the Paper build at every start** (`[init] [ERROR] Failed to download paper` when its name
  gets `NXDOMAIN`, even with the jar on disk). A fully offline restart of the same container therefore does not start; the
  warm start with only `papermc.io` reachable does. _paper@26.3, itzg java25, 1 boot red, 0 joins_
- **A cell client container resolves the server by container name** (`ps-gy-srv`); an alias the server was never given
  (`srv`) fails with `UnknownHostException` before any packet (3 harness errors, not joins).

## Run log (every join counted)

Runs `r1` to `r26`, `h1` (a hand run) and `geyser-verify` (the documented command below, from a fresh cache). Bedrock joins: **59, 48 spawned, 11 not**. Java joins: 4, 3 joined.
Join timeouts with the stalled stage: `raknet-connect` x4 (`jsp-raknet`, above); `login-sent` x2 and `login` x1 (the Xbox
refusal as seen by the client); everything else that failed was a server disconnect with a message, listed under Gotchas.
Not joins and not counted: 3 Node runs that never started (`Cannot find module` before the join, a harness path), 3 cell-client
attempts with `UnknownHostException`, 1 boot that stopped in the image (`Failed to download paper`).

| Runs | Route, driver, conditions | Joins | Result |
|---|---|---|---|
| r1 | offline (Floodgate present), Cloudburst, validation on, no route out | 1 | red, Xbox refusal |
| r2 | same, validation off, no stub | 1 | red, discovery fetch, network unreachable |
| r3 | same, route out open | 1 | red, Floodgate XUID `NumberFormatException` |
| r4, r5, r6 | offline, Cloudburst, validation off, route out open, no stub | 1, 3, 3 | 1 green; 1 green and 2 red (same XUID); 3 green |
| r7, h1 | offline, Node with `jsp-raknet`, route out open | 3, 1 | 4 red, `raknet-connect` |
| h1 | offline, Node with `raknet-native`, route out open | 1 | green |
| r8, r18, r20 | offline, Cloudburst on the host, stub, route closed (r18: warm start with names blocked) | 3 each | 9 green |
| r13, r25 | offline, Cloudburst on the cell network, stub, route closed | 3 each | 6 green |
| r9, r22 | offline, Node, stub, route closed | 3 each | 6 green |
| r10, r15, r19, geyser-verify | floodgate, Cloudburst, stub, route closed (r15 with the pinned key) | 3 each | 12 green |
| r23, r26 | floodgate, Cloudburst, stub, route closed (r23 with the client TLS log) | 1 each | 2 green |
| r11, r21 | floodgate, Node, stub, route closed | 3 each | 6 green |
| r24 | floodgate, Node, stub, route closed (client net log) | 1 | green |
| r16, r17 | floodgate, validation on, stub, route closed (Cloudburst, Node) | 1 each | 2 red, Xbox refusal |
| r15, r19 (Java) | `mcpl` on the Java port with Floodgate installed: `PlainJava`, `.PrefJava` | 1 + 2 + 1 | 3 joined, 1 red (throttled) |

## How to re-run (the reviewer's check: one driver, one route)

```bash
bash scripts/spike/geyser/fetch.sh                      # downloads into build/spike/geyser-cache, checks every sha256
./gradlew :platforms:bukkit:agent:shadowJar
PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 VALIDATE=false STUB=1 \
  bash scripts/dockerlock.sh bash scripts/spike/geyser/geyser-spike.sh floodgate cloudburst 3 build/spike/geyser-run
```

Expected: `JOINS driver=cloudburst auth=floodgate total=3 spawned=3 not-spawned=0` in `markers.log`, the agent line in each
`joins/join-N.log` listing `.PsBed1`, and `dns.log` between the markers holding only the two stub names. Other routes: first
argument `offline`; driver `node`; `CLIENT_PLACE=cell` (Java only); `EGRESS=open` (diagnosis only); `WARM_OFFLINE=1`;
`CLIENT_NETLOG=1`; `FLOODGATE_KEY=<key.pem>`; `JAVA_JOIN="PlainJava .PrefJava"`; `VALIDATE=true` (expected red). The
Testcontainers check is `bash scripts/dockerlock.sh bash scripts/spike/geyser/tc-udp-probe.sh`.

## Unverified

- Anything older than Bedrock protocol 2225, and Geyser's refusal text for a protocol it does not support.
- Geyser and Floodgate on different servers, a Geyser cell with a proxy, Geyser through a Testcontainers cell.
- Whether the stub's TLS trust store change affects Paper or Geyser outside the two stub names (all other TLS worked in the cold
  start, which ran with the trust store).
- Two Node clients at once (their Floodgate UUID is the same), inventory and window packets through a Bedrock driver, chat
  and command round trips: no scenario was run.
- A start with the stub in place and the image's `papermc.io` name also blocked (needs the Paper jar copied in as `CUSTOM`).

## Geyser cell host (T-M9-06, 2026-11-17: what ran)

`GeyserHost` (`proofstone-core`, `core.host`) boots `geyser@2.12.1+paper@26.3` from `versions.yml` on a Testcontainers network: the
discovery stand-in, the Paper server (Geyser 2.12.1 b1303, Floodgate 2.2.5 b141, the Bukkit agent) and the Bedrock driver host. Run by
`PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :proofstone-core:geyserTest --no-parallel --max-workers=1` (source set
`geyserTest`, class `GeyserCellIT`). Three separate invocations, one cell each: 3 of 3 green, `skipped=0 failures=0 errors=0`; each joined
one Bedrock client through the driver host (spawn status 2.4 to 2.5 s after connect) and the agent listed `.PsBed1`. Cell ready (container
start to the Geyser, health and driver-host lines): 35 to 37 s. Bedrock join timeouts: 0 of 3 (no stage to name). Before those three, two
diagnosis runs failed at join stage SPAWN_STATUS with decode errors in the driver, found and fixed in T-M9-05's code (below).

- **The stand-in works through Testcontainers on the pinned image, no new image.** A `GenericContainer` with
  `withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java"))` runs the one compiled `StandIn` class copied in (`withCopyToContainer`),
  with a per-run PKCS12 keystore; the server and the driver host take it as DNS through `getHostConfig().withDns(<its address from
  getContainerInfo>)`. Docker's embedded resolver on the cell network still answers the container names (`server`, `driver`) and sends
  every other name to the stand-in. `StandIn` is Java 17 bytecode and JDK classes only; it ran on `java25`. The image's `cacerts` is read from
  `$JAVA_HOME/lib/security/cacerts` of the stand-in container; the trust store (cacerts plus the stand-in certificate, PKCS12) is built with the
  `KeyStore` API and copied to `/stub/cacerts` of the server and the driver host; `JAVA_TOOL_OPTIONS` points both JVMs at it. The certificate
  comes from the harness JDK's `keytool` (EC secp256r1, SAN the two names, valid from yesterday for three days).
- **Geyser accepted the pre-rendered `config.yml`.** The harness copies `/plugins/Geyser-Spigot/config.yml` (the pinned build's default,
  `geyser-2.12.1-b1303-config.yml` in the core resources, five values replaced: `bedrock.transport: both`, `bedrock.webrtc-port: 19133`,
  `bedrock.signaling.stun-discovery: false`, `java.auth-type: floodgate`, `advanced.bedrock.validate-bedrock-login: false`). Geyser logged
  `Started Geyser on UDP port 19132` and joins worked, so the spike's first-start-then-edit-restart fallback (about 15 s more) is not needed.
  A pin bump needs a new template: start the new build once, copy its default file, keep the five keys.
- **The pre-seeded Floodgate key worked** (`/plugins/floodgate/key.pem`, 16 `SecureRandom` bytes per cell, from memory): 3 of 3 joins. Paper's
  `connection-throttle: -1` is the host's `bukkit.yml` in every cell.
- **The `floodgate` token is added at the first tick, not at the agent's enable.** The agent enables at `STARTUP`, before Floodgate, so a plugin
  lookup at enable would say "not enabled". `FloodgateProbe` runs once on the first global tick (read-only, by plugin name, no Floodgate class
  touched) and adds the token to the live list `health` answers from. The health schema does not enumerate tokens (items are free strings), so
  there is no protocol bump and no golden change; the harness awaits the token after readiness. Health on the cell:
  `command, players, config, reload, world, events, log, ticks, bedrock-prefix, datapacks, resource-pack-status, floodgate`.
- **Names the stand-in was asked** (cell start and one join, every run): `fill.papermc.io`, `fill-data.papermc.io`, `piston-data.mojang.com`,
  `discovery.minecraftservices.com`, `api.minecraftservices.com` (the image and Paper), `api.geysermc.org`, `launchermeta.mojang.com`,
  `piston-meta.mojang.com` (Floodgate and Geyser's optional fetches), and the two Microsoft names, each looked up by both the server JVM and the
  driver host JVM (`A` and `AAAA`; the stand-in answers `AAAA` for them with an empty success, so no real resolver is asked a Microsoft name).
  The stand-in answered itself exactly `client.discovery.minecraft-services.net` and `authorization.franchise.minecraft-services.net` in all
  three runs and served the discovery and OpenID documents to each JVM (the JWKS was not requested with login validation off). The name guard
  (live.com, xboxlive.com, xboxlive.cn, xbox.com, microsoftonline.com, msauth.net, and any other name under minecraft-services.net) was clean.
  Geyser's other optional fetches resolved to the real hosts (egress is open, ADR-0018 section 4) and logged no WARN.
- **Log guard list (`ErrorLogGuard.Patterns.GEYSER`)**, from the runs: the three `XBOX AUTHENTICATION IS DISABLED ON THIS GEYSER INSTANCE!` lines
  (the second starts "While this allows using Bedrock edition proxies", the third names `disable-xbox-auth`), logged by Geyser at its start; and,
  from the spike's blocked-names run and not seen in these three, `Failed to load locale asset cache` and `Unable to resolve Geyser api`. Paper's
  four offline-mode WARNs (`DedicatedServer`) are in every cell's boot (`ONLINE_MODE=FALSE`), before a test window. No ERROR record from Geyser or
  Floodgate; `No key layers in MapLike[{}]` is the known flat-world line.
- **Two driver bugs the first real join found, fixed in T-M9-05's code** (own commit, plus a unit test each): (1) from protocol 2225 the start game
  packet's item definitions are empty and the 2088 definitions come in `ItemComponentPacket`, so the creative content decode failed; (2) the
  creative content packet also names blocks by runtime id and the library dereferenced a null block registry; the driver now installs a registry
  that carries the id the server sent (`RuntimeIdBlocks`). A decode error now names its innermost cause. After both, the join reached the spawn
  status in 2.2 to 2.7 s and `GET /v1/players` listed `.PsBed1` (UUID `00000000-0000-0000-0009-01ee...`, survival, health 20, at the world spawn).
- **Bundle.** `GeyserBundle.write` (core, `junit` package) writes `server/`, `stand-in/` and `driver-host/` logs, the agent transcript and
  snapshot, the client transcript and `cell.json` (`"bedrockAuth": "offline-stub"`); every token and the Floodgate key in raw, hex (both cases)
  and base64 (standard and URL, padded or not) are redacted and each file is read back; `plugins/floodgate` is never part of it. The real cell's
  bundle held no form of the key in any run. It does not yet write `host.json` or `rerun.txt`; the JUnit extension's Geyser bundle is T-M9-07/08.
- **Still unrun:** two Bedrock clients at once; any chat, command or window round trip through the driver (the run read position and inventory
  only); a Java 8 image (the trust store is PKCS12 made by a newer JDK; only `java25` ran); the JWKS document (never fetched with login
  validation off), so its shape is unchecked.

## Test API on a Geyser cell (T-M9-07, 2026-11-17: what ran)

`@Matrix(platform = Platform.GEYSER)` with `@Requires(Capability.BEDROCK_CLIENT)` and `clients.bedrock("PsBed1")` ran through the public API
on `geyser@2.12.1+paper@26.3` (`GeyserApiContract`, source set `geyserTest`, run with `--tests '*GeyserApiContract'
-Dproofstone.cells=geyser@2.12.1+paper@26.3`). The Bedrock client reports `.PsBed1`, `agent.player(client).give(stick x3)` and
`DualAssert.item(...).inInventory(stick).amount(3)` agree on both oracles (id and count; Bedrock sends no data components).

- **Gotcha: the agent's health at readiness is too early for Floodgate.** `PaperHost` reads the health once at `Done (`, and Floodgate
  reports its `floodgate` token a little later. The first set of runs read the cell's capabilities from that health: run 2 of 3 skipped
  both tests with `missing capability BEDROCK_CLIENT on cell geyser@2.12.1+paper@26.3` (a skip, not a pass, and a real finding of
  T-M9-07). `GeyserHost` now re-reads the health after its own wait for the token. The three runs after the fix were all green.
- A Geyser cell holds `CONSOLE`, `BUKKIT_EVENTS` and `BEDROCK_CLIENT`; it does not hold `SIMULATED_BEDROCK`, `PROXY`, `TOPOLOGY`,
  `RESOURCE_PACKS` or `DATAPACKS_VANILLA`.
- The Java client of the injected `Client` parameter joins the Paper port directly; the conformance of the cell (C03 and the leave) ran with
  it and passed. The JUnit extension's Geyser bundle is `GeyserBundle` (a directory per container, `cell.json` with `offline-stub`).
