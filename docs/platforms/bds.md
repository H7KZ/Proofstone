# Bedrock Dedicated Server (BDS) and a behaviour-pack agent

Spike T-M10-01, 2026-11-20, Windows 11 host, Docker Desktop 29.9.3. Everything below was run by hand under `scripts/dockerlock.sh`
with the scripts in `scripts/spike/bds/`. Nothing here is built: `versions.yml` has no BDS row, the wire protocol did not change and no
production code changed. Run output lived in `build/spike/bds/` (not committed). Where the runs did not show a thing, the text says so.

## Verdict

**Not feasible as a Proofstone cell with the M9 Bedrock driver.** BDS 1.26.66.1 refuses the driver's unsigned (self-signed) login
with `MALFORMED_PACKET`, `TERMINATING_CONNECTION`, context `Connection Request invalid.`, in every join run (5 of 5: the driver host 3
of 3, the spike client 2 of 2, all with `online-mode=false`). With `online-mode=true` and no route out BDS does not serve at all: it
stops itself after 60 s (`Could not connect to Minecraft services. This is required to accept connections in online mode.`). It also logs at every start that NetherNet is "the only supported transport type", while the driver
speaks RakNet only. No player ever joined, so the `playerJoin` event could not be shown and every `players` read was empty.

**The agent half works.** A behaviour pack with `@minecraft/server-net` 1.0.0-beta reached a harness over HTTP on the cell network
and answered `players` reads through the inverted transport (the pack long-polls the harness) with a harness-side round trip of 46 to
95 ms (8 reads, r4 and r5). That is not enough for a cell: without a client that joins, no test can put a player on the server.

So T-M10-02 and T-M10-03 are closed as "not feasible" with a link here (`M10-ecosystem.md`). What would reopen them: a BDS build that
accepts an offline login, or an offline Bedrock client that speaks NetherNet and the login BDS expects, shown by a run.

**Update 2026-11-26 (T-M12-11):** over NetherNet, with the self-made offer identity, BDS completes signalling, the data channels and `NetworkSettings`, then refuses the
login with the same `MALFORMED_PACKET` / `Connection Request invalid.`. The verdict stands. See [nethernet.md](nethernet.md).

## The pin (what ran)

| Fact | Value |
|---|---|
| BDS version | `1.26.66.1` (log: build ID 53696239, branch `r/26_u6`, commit `cb39b93aee4586408851619d83ed05780bbd842b`) |
| Source | the official links API `https://net-secondary.web.minecraft-services.net/api/v1.0/download/links` (`serverBedrockLinux`) |
| URL | `https://www.minecraft.net/bedrockdedicatedserver/bin-linux/bedrock-server-1.26.66.1.zip` |
| Size | 98,095,547 bytes (matches `Content-Length`; MD5 matches the server's `Content-MD5`) |
| sha256 | `e87bcd95bec5cc34b065ca956825c6abb7a0c356ae79408302845069cecaa6bf` (downloaded twice, 2026-11-19 and 2026-11-20, same hash) |
| Kept | outside every repository (`/c/pw/bds-cache`), never committed, never redistributed (AGENTS.md section 3) |
| EULA | `PROOFSTONE_ACCEPT_EULA=true` in the command environment only, approved by the maintainer for this spike |
| Image | none of BDS's own: the pinned `itzg/minecraft-server` `java25` digest (`versions.yml`) with `--entrypoint sh`; the zip is copied in and unpacked inside the container, then `bedrock_server` runs as root from `/bds` |

Minecraft.net serves only the newest release and preview (on 2026-11-20 the preview was `1.26.70.26`). Older zips were not tried.

## Script API facts (from BDS's own error text)

- `@minecraft/server`: available `0.1.0`, `1.19.0`, `2.11.0`, `2.12.0-beta`, `3.0.0-alpha`. The pack used **`2.11.0`** (stable).
- `@minecraft/server-net`: available **`1.0.0-beta`** only ("Current [beta] version is [1.0.0-beta]").
- **Beta flag.** server-net is beta-only. Without the Beta APIs experiment BDS logs `requesting dependency on beta APIs
  [@minecraft/server-net - 1.0.0-beta], but the Beta APIs experiment is not enabled.` and the pack does not run. BDS has no
  `server.properties` key for experiments, so the spike sets it in the world: a first start creates `level.dat`, the container stops,
  `scripts/spike/bds/leveldat.py` adds `experiments: {gametest: 1, experiments_ever_used: 1, saved_with_toggled_experiments: 1}`
  (little-endian NBT, round-trip checked), and the second start logs `Experiment(s) active: gtst`.
- **Permissions.** The shipped `config/default/permissions.json` does not list `@minecraft/server-net`; the spike adds it.
- The shipped editor packs declare `"version": "beta"` for `@minecraft/server`, so an alias exists; the spike used numbers.

## Inverted transport (what ran)

A pack cannot listen, so the harness is the server: `scripts/spike/bds/Harness.java` (JDK only) in a container of the pinned image,
alias `harness`, no published port. The pack (`scripts/spike/bds/pack/`) does three things:
1. 20 timed `GET /pack/ping` calls at start (`Date.now()` around `http.request`).
2. A loop of `GET /pack/poll`: the harness holds the request up to 5 s and answers with a request (`{"id","op":"players"}`) or 204.
   The pack answers with `POST /pack/answer`.
3. `world.afterEvents.playerJoin` posts `{type:"playerJoin", playerName, playerId}` to `POST /pack/event`.

The spike script drives the harness with `curl` inside its container (`/ctl/request`, `/ctl/await-event`), the way the harness reaches
an agent today. A bearer token per run is baked into the staged pack (a real agent should read it through `@minecraft/server-admin`
secrets; not tried).

| Measure | r4 | r5 |
|---|---|---|
| Pack ping, 20 calls: min / median / max | 100 / 100 / 150 ms | 100 / 100 / 149 ms |
| `players` read, harness enqueue to answer (3 reads before the join, 1 after) | 83.5, 76.4, 91.6, 80.4 ms | 94.7, 54.7, 87.7, 45.6 ms |
| `playerJoin` event | not seen (no player joined) | not seen |

The pack's ping times come in steps of 50 ms (one figure 150, all others 100), so script HTTP completions look tied to the tick loop
(20 ticks a second); this is an inference from the numbers, not a documented fact. The answer is a real read:
`{"players":[]}` from `world.getAllPlayers()`.

## Observe-only (what is shown and what is not)

- The pack's server calls are `world.getAllPlayers()`, `Player.name` and `Player.id`, `world.afterEvents.playerJoin.subscribe` (an
  after-event cannot cancel), `system.run` and `system.currentTick`. Everything else is `@minecraft/server-net`. The script greps the
  pack for write-capable calls (`runCommand`, `setDynamicProperty`, `setBlock`, `teleport`, `beforeEvents`, `cancel`, `setItem`,
  `addTag`, `sendMessage`, `applyDamage` and others) and records `none` in every run.
- The harness log shows what the pack sent: only `GET /pack/ping`, `GET /pack/poll`, `POST /pack/answer`, `POST /pack/metric`.
- **Not shown:** a world-state comparison before and after, and any read with a player online. With no player, "the pack writes
  nothing" is shown for the source and the traffic, not for a world with players in it.

## Offline join with the M9 driver host

Setup (ADR-0018 style, no account, no token, no secret): BDS `online-mode=false` (r6: `true`), `allow-list=false`,
`transport=raknet`, `enable-lan-visibility=false`. The driver host (`drivers/bedrock`, `bedrock-host-all.jar`, Bedrock protocol 2225,
game version 1.26.60) runs in its own container of the pinned image and is called with `curl` inside it (`POST /rpc/v1/sessions`,
host `bds`, port 19132, a claimed numeric XUID). BDS, the driver host and the second client all lose their default route
(`ip route del default`, `--cap-add NET_ADMIN`) before their process starts, and their only DNS server is the T-M9-01 stand-in
(`scripts/spike/geyser/HostLog.java`, which answers the two Microsoft discovery names with its own address and serves invented documents
over TLS).

| Run | Client | Verdict | Stage reached | Server's answer |
|---|---|---|---|---|
| r3, r4, r5 | M9 driver host | `PROTOCOL_ERROR` (HTTP 409) | `LOGIN_SENT` (`NetworkSettingsPacket` received) | a `PacketViolationWarningPacket` the library registers as server-bound only, so the driver fails the decode: `Packet PacketViolationWarningPacket was sent to CLIENT instead of SERVER` |
| r4, r5 | `BdsJoin` (same library, that packet made decodable) | closed, `disconnect.closed` | login sent | `PACKET_VIOLATION_WARNING type=MALFORMED_PACKET severity=TERMINATING_CONNECTION causeId=1 context=Connection Request invalid.` |
| r6 | none (BDS `online-mode=true`) | not attempted | | BDS logged `Waiting for Minecraft services...`, then after 60 s `Could not connect to Minecraft services. This is required to accept connections in online mode.` and stopped itself |

`causeId=1` is the login packet's id. `scripts/spike/bds/BdsJoin.java` is the T-M9-01 client `BedrockJoin.java` with its imports
moved to the driver jar's shaded packages and that one packet re-registered, so the text could be read; the driver host itself is
unchanged.

**Why it refuses is not proven.** Two causes fit the runs and the spike could not separate them: (1) the self-signed login chain
(BDS's own `server.properties` text: "Clients connecting to remote (non-LAN) servers will always require Xbox Live authentication
regardless of this setting"); (2) the RakNet transport (BDS at every start: `Your current connection type is not set to NetherNet. In
this release, NetherNet is the only supported transport type. Players will not be able to connect to your game without NetherNet.`).
RakNet itself answered: the RakNet connection and the network settings exchange completed. A NetherNet client does not exist in
Proofstone, so the second cause was not tested.

### Names asked (the stand-in's DNS log, r4; r5 the same)

| Container | Names | What happened |
|---|---|---|
| BDS (`172.29.78.4`) | `client.discovery.minecraft-services.net` (A and AAAA), at each start and about once a minute | the stand-in answered with its own address; **no HTTPS request from BDS reached the stand-in** (BDS does not trust the stand-in's certificate) and BDS has no route out, so nothing reached Microsoft |
| M9 driver host (`.5`) | none | the driver stopped before its Cloudburst start-up fetch ran |
| `BdsJoin` (`.6`: not in the IP list, which is taken at cleanup, but the only other container on the network) | `client.discovery.minecraft-services.net`, `authorization.franchise.minecraft-services.net` | answered by the stand-in, which served the discovery and OpenID documents (as on Geyser cells) |
| harness (`.3`) | none | |

No Xbox or Microsoft account host (`login.live.com`, `*.xboxlive.com`) was asked. BDS logs `Signed in to signaling service
successfully` at every start even with no route out and no completed TLS request, so that line does not mean a Microsoft contact.

## Run log

Invocation for every run (Git Bash, from the repository root):

```bash
SERVER_VERSION=2.11.0 NET_VERSION=1.0.0-beta PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 \
  bash scripts/dockerlock.sh bash scripts/spike/bds/bds-spike.sh build/spike/bds/<run>
```

| Run | Knobs | Result |
|---|---|---|
| r1 | versions `99.0.0`, `99.0.0-beta` | red, environment: host disk full while unpacking BDS on the host (the script now unpacks inside the container) |
| r2 | same | red, script: the stand-in readiness wait read the container log, but `HostLog` writes to a file |
| r3 | same | BDS booted; the pack failed with the version list above (wanted, to learn the versions); driver join `LOGIN_SENT` + violation |
| r4 | `2.11.0`, `1.0.0-beta` | pack ran: ping, 4 reads green; driver join refused; `BdsJoin` read `Connection Request invalid.` |
| r5 | same as r4 | same as r4 (ping, 4 reads green; both joins refused the same way) |
| r6 | r4 plus `ONLINE_MODE=true` | BDS stopped itself at the first start (no Minecraft services offline); exit 1, nothing after the boot ran |

## Gotchas

- `transport` defaults to `nethernet` and `enable-lan-visibility=true`; the spike sets `raknet` and `false`.
- `allow-list` defaults to `true`.
- The Beta APIs flag lives in `level.dat`, so a BDS cell needs a two-phase first start (or a prepared world).
- BDS starts in about 1 to 3 s. It ignores SIGTERM: `docker stop -t 60` ended in a kill after 60 s (no `Quit correctly` in r4, r5);
  `level.dat` was still usable. A host should send `stop` on the console instead (not tried).
- With `online-mode=true` and no route out, BDS waits 60 s for Minecraft services and stops itself (r6).
- BDS writes `ContentLog*.txt` and prints script `console.warn` lines as `[Scripting]` when `content-log-console-output-enabled=true`.
