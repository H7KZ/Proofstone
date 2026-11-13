# BungeeCord

Status: **spike** (T-M7-01, 2026-11-09), plus the agent notes of T-M7-06. The pins are in `versions.yml` `proxies` (schema 3, T-M7-08, experimental). Every fact below
comes from a local run under `scripts/dockerlock.sh` with `scripts/spike/proxy-spike.sh` or
`scripts/spike/proxy-protocol-probe.sh`, unless it says otherwise. See [platforms.md](../platforms.md) and
[velocity.md](velocity.md) for the shared topology.

## Host
- **Image:** `itzg/mc-proxy`, release `2026.11.1`, tag `java21-2026.11.1`, index digest
  `sha256:4e6901b4640a913ef6d8470de887b89ff6d19702e410edfbccdbdbce4b54159f` (898 MB pulled). Java 21.0.13 (Temurin)
  in the container. Build 2111 started and served on it; older Java tags were not tried.
- **Jar source and pin:** SpigotMC Jenkins, job `BungeeCord`. `https://ci.md-5.net/job/BungeeCord/<n>/` redirects to
  `https://hub.spigotmc.org/jenkins/job/BungeeCord/<n>/`; the artifact is `bootstrap/target/BungeeCord.jar`. Jenkins
  gives md5 fingerprints for the module jars only, **no sha256**, so the pin is a sha256 computed from the download.
  Pinned for the spike: **build 2111** (`lastSuccessfulBuild` on 2026-11-09, git `55e1b0b`, API version
  `26.1-R0.1-SNAPSHOT`), sha256 `1d9dbc31b57b22a37553f160ac1b61762717e9e4a6a1341f3dd27a1f6f2671fd`, 24,524,184 bytes.
  The image downloads it with `TYPE=BUNGEECORD`, `BUNGEE_JOB_ID=2111` to `/server/BungeeCord-2111.jar`; the spike
  script checks the sha256 in the container and fails on a mismatch. It matched in every run. A build number is
  stable on Jenkins, but the job could prune old builds; Decided in [ADR-0016](../adr/0016-topology-cells.md) §2: no host-side cache in M7; a pruned build fails loudly at download and is fixed by a row update.
- **Image env the spike sets:** `ENABLE_RCON=false` (otherwise the image downloads the bungee-rcon plugin from
  GitHub), `SKIP_DOWNLOAD_DEFAULTS=true`.
- **Config file:** `config.yml`, copied into `/server/` with `docker cp` before start. The jar extracts its modules
  (`cmd_find`, `cmd_list`, `cmd_send`, `cmd_server`, `cmd_alert`, `reconnect_yaml`) from itself on first start
  (`Attempting to extract module cmd_server`), no download seen.
- **Ready:** the log line `Listening on /0.0.0.0:25577`.
- **Topology run:** as for Velocity, two `paper@26.3` backends (versions.yml pin, sha256 checked) with the aliases
  `lobby` and `survival`.

### Forwarding recipe (legacy, ran)
| Side | Setting |
|---|---|
| Proxy `config.yml` | `ip_forward: true`, `online_mode: false`, `servers.lobby.address: lobby:25565`, `servers.survival.address: survival:25565`, `listeners[0].priorities: [lobby]`, `host: 0.0.0.0:25577`, `max_players: 20`, `forced_hosts: {}`, `connection_throttle: -1` |
| Backend (Paper 26.3) | `spigot.yml`: `settings.bungeecord: true`; `ONLINE_MODE=FALSE` |

Forwarding was seen to work: the backend logged the client's address (`Spike2[/172.25.0.1:35771] logged in`), and
the same offline UUID `d8520406-990d-3901-ba26-f0302c0f873a` for `Spike2` as through Velocity. BungeeCord has only
legacy forwarding (no secret), so a backend reachable from anywhere but the proxy accepts any forwarded identity.

### Protocol 777 (26.3) client: verdict
- **BungeeCord build 2111 accepts the 777 client natively.** 10 of 10 driver joins succeeded. Its protocol jar
  (`bungeecord-protocol-26.1-R0.1-SNAPSHOT`) has `MINECRAFT_26_3 = 777`.
- **Waterfall is not a candidate.** Its newest build, `1.21` build 615 (2026-06-16, upstream "Minecraft 26.2 support",
  sha256 `5eda8bfd0691e5088701f87020c68964299586df0faba289a634122d282d598c`), rejects the client. Probe on the
  `java25-2026.11.1` image, no backend, one join: `could not join Spike1 -> 127.0.0.1:11639: Outdated server! I'm still
  on 1.8.x, ... 1.21.x, 26.x`. With the M7 decision (Waterfall EOL, not a required row), it is dropped. A ViaProxy in
  front of Waterfall was not run.

### Join counts (all runs, 2026-11-09)
| Run | Client joins | Join timeouts | Switches ok / tried | Backend logins (lobby / survival) |
|---|---|---|---|---|
| 4 | 5 of 5 | 0 | 10 / 10 | 10 / 5 |
| 7 | 5 of 5 | 0 | 10 / 10 | 10 / 5 |
| probe (Waterfall 1.21-615) | 0 of 1 (rejected, version) | 0 | n/a | n/a |

Total on 2111: 10 client joins, 10 joined, **0 join timeouts**; 30 backend logins (proxy to backend hop), each
followed by `joined the game`; 20 of 20 switches.

## Server switch (what the driver sees)
Transcript of `Spike2`, run 7, the server's own messages:
```
-> ServerboundChatCommandPacket                      (/server)
<- ClientboundSystemChatPacket You are currently connected to lobby.
<- ClientboundSystemChatPacket You may connect to the following servers at this time: survival, lobby
-> ServerboundChatCommandPacket                      (/server survival)
<- ClientboundStartConfigurationPacket
<- ClientboundLoginPacket
<- ClientboundRespawnPacket
<- ClientboundSystemChatPacket multiplayer.player.joined Spike2
```
The switch goes through the configuration state as on Velocity (start configuration, acknowledged, known packs,
registries, tags, finish configuration), then **`ClientboundLoginPacket` followed by `ClientboundPlayerInfoRemovePacket`
and `ClientboundRespawnPacket`**. Velocity sends no respawn. A client oracle that keys on "new world" must accept
both shapes. The mcpl driver stayed connected through all 20 switches with no change. Proxy log for the same switch:
`[Spike2] <-> ServerConnector [survival] has connected`, then `[Spike2] <-> DownstreamBridge <-> [lobby] has
disconnected`.

## Agent notes
The BungeeCord agent is `platforms/bungee/agent` (T-M7-06; [module README](../../platforms/bungee/agent/README.md)). Facts from
building it and from three green real-server runs against build 2111 (`BungeeCordAgentIT`, 2026-11-10; one earlier run of the
same test was red on a wrong guess about `glist` output, see below):
- **The agent is Java 8 bytecode and runs on the java21 image.** The API it compiles against, `bungeecord-api` 1.21-R0.4
  (Maven Central, class major 52), is the newest release; build 2111 reports API `26.1-R0.1-SNAPSHOT`, which is not published as a
  release. The agent uses only API present in both. The JVM floor question left open by the spike is settled for the agent: Java 8
  bytecode, like the Bukkit agent.
- **The real version string** is `git:BungeeCord-Proxy:26.1-R0.1-SNAPSHOT:55e1b0b:2111` (`ProxyServer.getVersion()`), which the
  agent reports as `mcVersion`. It carries the Jenkins build number, so a test can check the row it runs.
- **Plugin discovery:** a hand-written `bungee.yml` (`name`, `main`, `version`, `author`) in the jar is enough; no `libraries`, so
  nothing is downloaded. BungeeCord logged `Loaded plugin ProofstoneAgent version 0.1.0 by Proofstone`, then
  `[ProofstoneAgent] Proofstone agent listening on port 25580`, then `Enabled plugin ProofstoneAgent ...`, all before
  `Listening on /0.0.0.0:25577`. The proxy's own modules (`cmd_find`, `cmd_server`, ...) load as plugins of their own, in the same
  log lines.
- **`Plugin()` needs BungeeCord's class loader.** The no-argument constructor throws `Plugin requires PluginClassloader` anywhere else,
  and the protected `Plugin(ProxyServer, PluginDescription)` constructor (meant for tests) does not set the proxy: both fields
  come from the package-private `init`, which the unit tests call by reflection. The mock proxy in the tests subclasses the abstract
  `ProxyServer` and uses the real `PluginManager` (its four-argument constructor is public) and `EventBus`.
- **Priority runs the other way from Bukkit.** On BungeeCord the number is the order: `LOWEST` (-64) runs first and `HIGHEST` (64) runs
  last and has the final word. The card says "lowest priority"; the observe-only meaning is "the last to run", which is `HIGHEST`
  here, the counterpart of Bukkit's `MONITOR` and Velocity's `PostOrder.LAST`. Every handler the agent registers is at `HIGHEST`
  and `ObserveOnlyTest` reads that back from the real event bus' registrations.
- **A command's output is collected by the agent's own sender.** `PluginManager.dispatchCommand(sender, line)` with a sender that
  implements `CommandSender` (name `CONSOLE`, every permission, not a `ProxiedPlayer`) and collects `sendMessage` text, colour
  codes dropped. `dispatchCommand` returns `false` only for an unknown command, which is the agent's `400 INVALID_REQUEST`; a
  refusal or a failure is the command's own output. It also asks `ProxyServer.getConfig().isLogCommands()` (seen in the API's
  bytecode and by the mock proxy failing without it), so a proxy with `log_commands: true` would probably log the harness's
  commands; that was not run. The real replies the test checks: `bungee` prints a line that names BungeeCord; `glist` with nobody online
  prints exactly `Total players online: 0` (the per-server lines appear only for servers that have players).
- **`proxy/servers` `online` is a live `ServerInfo.ping`**, not a cached result. A refused connection calls back with an error at
  once, so a proxy whose backends are down answers `online: false` quickly.
- **Log capture is on `ProxyServer.getLogger()`, not the JUL root logger.** BungeeCord's `BungeeLogger` is built as a `Logger`
  with a null parent, and each plugin's logger parents to it, so a root handler cannot see its records (read from BungeeCord's
  source and modelled in the unit tests; not shown against the real proxy by a failing root handler). `LogBuffer.attachTo` takes
  the one logger. The real run captured BungeeCord's own `Listening on /0.0.0.0:25577` line, with no duplicates, and no Log4j2 twin
  exists to match. A record that third-party code logs on the root logger is not captured.
- **`events` is not claimed.** The probes work on the mock with the real event bus; no real BungeeCord has fired a player event
  through them (the test has no backend). A probe cannot attach to an arbitrary class by name, because BungeeCord finds
  handlers by scanning a listener for annotated methods: the agent has a handler for each of eleven events and any other class is
  a `400`. `previousServer` likewise comes from `ServerSwitchEvent#getFrom` handlers that only the mock has driven. Both wait for
  T-M7-11, which needs a backend. `players` is in the capability list (the golden health case requires it) and is checked on the empty
  proxy only.
- **Observe only:** every handler is `EventPriority.HIGHEST`, no command is registered, no event is cancelled or changed, no intent is
  registered. `ObserveOnlyTest` checks it against the real command registry and event bus and against the compiled classes.
- **Harness config the test used:** `online_mode: false`, `ip_forward: true`, `connection_throttle: -1` as ADR-0016 section 5 says,
  `log_pings: false`, `config.yml` copied into `/server` with `withCopyToContainer` (no bind mount), the agent port published to the
  host for the test only. The image downloads `BungeeCord-2111.jar` (sha256 checked in the container) with `ENABLE_RCON=false` and
  `SKIP_DOWNLOAD_DEFAULTS=true`; the whole test, container boot included, takes about 9 seconds with the image cached.
- **The one red run was a wrong test expectation, not a product or flake fault.** The first run of `BungeeCordAgentIT` expected
  `glist` to list `lobby` and `survival`; BungeeCord printed only `Total players online: 0`. The expectation was corrected to
  what BungeeCord does and the three acceptance invocations were run afterwards; the red run is kept in the PR evidence.
- **Waterfall:** not run, not a row (ADR-0016 section 4).

## Capabilities
None declared for a cell. Not a row yet. The agent itself reports `command`, `players`, `log` and `proxy` in `health` (`events` after T-M7-11).

## Gotchas
- **The default `config.yml` has `max_players: 1`** on the listener, and `connection_throttle: 4000` with
  `connection_throttle_limit: 3`. The spike raised players to 20 and turned the throttle off (`-1`), because every
  test client comes from one address. Not observed with the defaults.
- **The default config has a forced host `pvp.md-5.net: pvp`** with no `pvp` server, which logs `WARNING Forced host
  server pvp is not defined`. The spike sets `forced_hosts: {}`.
- **The image's health check pings the proxy**, which logs `<-> InitialHandler has pinged` repeatedly
  (`log_pings: true` by default).
- **`moved too quickly!` WARNs on the lobby backend** for proxied clients (54 lines in run 4), as on a direct join. See
  the Velocity note on harness client names.
- **A Paper backend behind legacy forwarding fetched each player's profile properties from Mojang at login** (T-M7-10,
  run 2 of 3 red). Evidence from the `lobby` log: `GET https://sessionserver.mojang.com/session/minecraft/profile/<uuid>?unsigned=false`
  (the properties/skin fetch, not a name-to-UUID lookup) for the offline UUID `46101010-7191-3509-...`, answered `HTTP 429`,
  logged as an authlib `ERROR` and `WARN Couldn't look up profile properties`, stack
  `CraftPlayerProfile.complete` <- `ServerLoginPacketListenerImpl.callPlayerPreLoginEvents`. Why: Paper calls
  `profile.complete(true)` on every login, and it goes to the network when `GlobalConfiguration.proxies.isProxyOnlineMode()`
  is true, which is `Bukkit.getOnlineMode() || (spigot bungeecord && proxies.bungee-cord.online-mode) || (proxies.velocity.enabled && proxies.velocity.online-mode)`.
  `proxies.bungee-cord.online-mode` **defaults to true**, so `server.properties` `online-mode=false` was not enough. A lookup
  that succeeds logs nothing, so green runs depended on Mojang too.
  **Setting (harness default):** `/config/paper-global.yml` with `proxies: bungee-cord: online-mode: false` (it matches the
  proxy's `online_mode: false`). `TopologyHost` reads the `paper-global.yml` Paper wrote and refuses to start a backend
  whose proxy online-mode is not false. **Velocity backends** were not exposed: their copied `paper-global.yml` already sets
  `proxies.velocity.online-mode: false`, and the same check now runs on them.
- No `/server` command was forwarded to a backend in 20 switches with the same back-to-back command timing that hit
  Velocity's rate limit.

## Not run
- Bridged combinations (an older backend, or ViaProxy in front). Build 2111 takes 777 natively, so a bridge is not
  needed for the `paper@26.3` rows.
- Forwarding off or misconfigured (T-M7-13).

## How to re-run
As in [velocity.md](velocity.md), with the mode `bungeecord-legacy`:
```bash
PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh bash scripts/spike/proxy-spike.sh bungeecord-legacy 5
MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh bash scripts/spike/proxy-protocol-probe.sh WATERFALL 1.21 615
```
