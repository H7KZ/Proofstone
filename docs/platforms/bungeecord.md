# BungeeCord

Status: **spike** (T-M7-01, 2026-11-09). Nothing is pinned in `versions.yml` yet and no agent exists. Every fact below
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
None yet. No proxy agent exists (T-M7-06). The image runs the proxy on Java 21; whether a Java 8 agent is still the
right target is for T-M7-02 and T-M7-06.

## Capabilities
None declared. Not a row yet.

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
