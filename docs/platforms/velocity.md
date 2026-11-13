# Velocity

Status: **spike** (T-M7-01, 2026-11-09) plus the agent (T-M7-05). The pins are in `versions.yml` `proxies` (schema 3, T-M7-08, experimental); the agent real-server test plans them from there. Every fact below
comes from a local run under `scripts/dockerlock.sh` with `scripts/spike/proxy-spike.sh` or
`scripts/spike/proxy-protocol-probe.sh`, unless it says otherwise. See [platforms.md](../platforms.md).

## Host
- **Image:** `itzg/mc-proxy`, release `2026.11.1`, tag `java25-2026.11.1`, index digest
  `sha256:8efdaa9d990f4c509f21b7ced003dd959060777784422fd72fdd537d613c1813` (939 MB pulled). Java 25.0.5 (Temurin)
  in the container. The tag `java25` moves; the dated tag and the digest do not.
- **Java tag: `java25`.** Velocity 4.3.1 is Java 25 bytecode. On the `java21` tag it does not start:
  `UnsupportedClassVersionError: ... class file version 69.0, this version of the Java Runtime only recognizes class
  file versions up to 65.0`.
- **Jar source and pin:** the Paper fill API, `https://fill.papermc.io/v3/projects/velocity/versions/<v>/builds/<n>`,
  which gives a sha256 per build, like Paper rows. Pinned for the spike: **Velocity 4.3.1 build 45** (channel `STABLE`,
  2026-10-27), sha256 `0844bcabdcdb4f5b875906fd4c3365bc1bb76918e42bc595ed88ad195258dd75`, 42,342,611 bytes. The image
  downloads it with `TYPE=VELOCITY`, `VELOCITY_VERSION=4.3.1`, `VELOCITY_BUILD_ID=45` to `/server/velocity-4.3.1-45.jar`;
  the spike script checks that file's sha256 in the container and fails on a mismatch. It matched in every run.
- **Image env the spike sets:** `ENABLE_RCON=false` (otherwise the image downloads the Velocircon plugin from GitHub),
  `SKIP_DOWNLOAD_DEFAULTS=true` (otherwise it fetches default configs from a GitHub repo at start).
- **Config files:** `velocity.toml` and `forwarding.secret`, copied into `/server/` with `docker cp` before start (no
  bind mount). A first boot with no file writes a default `velocity.toml` with `config-version = "2.9"`. The image
  writes its own random `forwarding.secret` only when the file is absent.
- **Ready:** the log line `Done (0.38s)!` (after `Listening on /[0:0:0:0:0:0:0:0]:25577`). Boot takes under a second
  once the jar is downloaded.
- **Topology run:** one network, backends with the network aliases `lobby` and `survival`, the proxy port published
  on `127.0.0.1` only. Backends are `paper@26.3` exactly as `versions.yml` pins it (java25 digest, build 244, sha256
  checked).

### Forwarding recipes (both ran)
| Mode | Proxy (`velocity.toml`) | Backend (Paper 26.3) |
|---|---|---|
| modern | `player-info-forwarding-mode = "modern"`, `forwarding-secret-file = "forwarding.secret"` (the file holds the secret) | `config/paper-global.yml`: `proxies.velocity.enabled: true`, `online-mode: false`, `secret: <same secret>`; `spigot.yml` `settings.bungeecord` left `false` |
| legacy | `player-info-forwarding-mode = "legacy"` | `spigot.yml`: `settings.bungeecord: true` |

Both sides also need: proxy `online-mode = false` and `force-key-authentication = false` (offline driver), backend
`ONLINE_MODE=FALSE`. The other proxy keys the spike set: `bind = "0.0.0.0:25577"`, `[servers] lobby = "lobby:25565"`,
`survival = "survival:25565"`, `try = ["lobby"]`, empty `[forced-hosts]`, `login-ratelimit = 0`.

Forwarding was seen to work in both modes: the backend logged the client's address (`Spike2[/172.25.0.1:42056] logged
in`, the Docker gateway the client came through), not the proxy container's, and the offline UUID
`d8520406-990d-3901-ba26-f0302c0f873a` for `Spike2` on both backends. `none` and `bungeeguard` were not run.

### Protocol 777 (26.3) client: verdict
- **Velocity 4.3.1 build 45 accepts the 777 client natively.** 15 of 15 driver joins through it succeeded, in both
  forwarding modes (counts below).
- **Velocity 3.5.1 build 615 (`RECOMMENDED`, 2026-07-11) rejects it.** Probe, proxy with no backend, one join:
  `could not join Spike1 -> 127.0.0.1:11541: multiplayer.disconnect.outdated_client 1.7.2-26.2`.
  sha256 `b4e3164df5377346854dc6cb9e6a78022b1946ff69e89676313f5f6f1c6f0fb3`. So the 3.x line is not a row for 26.3.

### Join counts (all runs, 2026-11-09)
| Run | Mode | Client joins | Join timeouts | Switches ok / tried | Backend logins (lobby / survival) |
|---|---|---|---|---|---|
| 1 | modern | 0 (both backends crashed at world load, see Gotchas) | 0 | 0 / 0 | 0 / 0 |
| 2 | modern | 0 (client not started: script path bug, fixed) | 0 | 0 / 0 | 0 / 0 |
| 3 | modern | 5 of 5 | 0 | 8 / 9 | 9 / 4 |
| 5 | legacy | 5 of 5 | 0 | 8 / 9 | 9 / 4 |
| 6 | modern, `command-rate-limit = 0` | 5 of 5 | 0 | 10 / 10 | 10 / 5 |
| probe | 3.5.1, no backend | 0 of 1 (rejected, version) | 0 | n/a | n/a |

Total on 4.3.1: 15 client joins, 15 joined, **0 join timeouts**; 41 backend logins (proxy to backend hop), each followed
by `joined the game`. The two failed switches are below (Gotchas); neither was a join timeout.

## Server switch (what the driver sees)
The client types `/server` and `/server survival`; Velocity handles both. Transcript of `Spike2`, run 6, the server's own
messages:
```
-> ServerboundChatCommandPacket                      (/server)
<- ClientboundSystemChatPacket You are currently connected to lobby.
<- ClientboundSystemChatPacket Available servers: lobby, survival
-> ServerboundChatCommandPacket                      (/server survival)
<- ClientboundStartConfigurationPacket
<- ClientboundLoginPacket
<- ClientboundSystemChatPacket multiplayer.player.joined Spike2
```
In full, a switch is: `ClientboundStartConfigurationPacket`, the driver's `ServerboundConfigurationAcknowledgedPacket`,
custom payload, enabled features, known packs (answered), 32 `ClientboundRegistryDataPacket`, tags,
`ClientboundFinishConfigurationPacket` (answered), then a new `ClientboundLoginPacket` and the usual play-state burst
(position, which the driver accepts). **No `ClientboundRespawnPacket`** on a Velocity switch. MCProtocolLib 26.3-2's
default listeners answer the configuration phase, so the mcpl driver stayed connected through every switch with no
change; its state model never left `PLAYING`. Proxy log for the same switch:
`[server connection] Spike2 -> survival has connected`, then `Spike2 -> lobby has disconnected`.

## Agent notes
The Velocity agent is `platforms/velocity/agent` (T-M7-05; [module README](../../platforms/velocity/agent/README.md)). Facts
from building it and from three real-server runs against Velocity 4.3.1 build 45 (`VelocityAgentIT`, 2026-11-09):
- **The 4.3.1 API jar is Java 25 bytecode**, and Adventure 5.2.0 is Java 21. Gradle refuses them for a release 17 compile, so the
  module compiles on a JDK 26 at `--release 17` and overrides the classpath's `TargetJvmVersion` to 25. javac reads the class
  files at release 17 without complaint. The API's POM names `velocity-brigadier` `1.0.0-SNAPSHOT`, which cannot be pinned, so
  Mojang's `brigadier` `1.0.500` (Paper's repository) stands in, and the other types are listed by hand in `libs.versions.toml`.
- **Plugin discovery:** a hand-written `velocity-plugin.json` (id `proofstone-agent`, `main`) is enough. No annotation processor.
  The plugin class takes `ProxyServer` and an SLF4J `Logger` through `@Inject` (Guice) and starts from a `ProxyInitializeEvent`
  handler. Velocity logged `Loaded plugin proofstone-agent 0.1.0` and then `Proofstone agent listening on port 25580` before
  `Listening on` and `Done (0.53s)!`.
- **Velocity bridges JUL into Log4j2.** `Logger.addHandler` on the JUL root is refused with a WARN from Log4j's status logger
  (`Ignoring call to j.u.l.Logger.addHandler(...)`), so the agent reads Log4j2 alone (`LogBuffer.attachPreferringLog4j`). The
  records Velocity logs after the agent attaches, such as `Done (`, are in the buffer.
- **`velocity version` is not a command.** The proxy answered `Incorrect argument for command at position 9: velocity
  <--[HERE]` and still reported the command as handled. The version command is `velocity info`. A syntax error is therefore
  *output*, not a failed dispatch; only a command Velocity does not know at all (`executeAsync` false) is an agent error.
- **A command's output is collected by the agent's own sender.** `CommandManager.executeAsync(sender, line)` with a sender that
  implements `CommandSource` and overrides `sendMessage(Component)`; Components are rendered with `GlobalTranslator` in English
  and flattened with the plain serializer. No log scraping.
- **`proxy/servers` `online` is a live `RegisteredServer.ping()`**, not a cached result (Velocity keeps none). A refused
  connection completes the future exceptionally at once, so a proxy whose backends are down answers `online: false` quickly.
- **`events` is claimed (T-M7-11).** The topology conformance P01 subscribes a probe to `ServerConnectedEvent` before a client joins
  through the proxy and reads the join back from a real Velocity 4.3.1: `getPlayer` and `getServer` serialise as `{name}`, so the probe
  reports `<player>@lobby`. `players` and the proxy `PlayerState` (`{name, uuid, proxy: {server, previousServer}}`) are read there too.
  `previousServer` after a real switch is still only mock-driven; it is checked by P03 (T-M7-12), not claimed here.
- **Observe only:** every handler is `PostOrder.LAST`, no command is registered, no event result is set.
  `ObserveOnlyTest` checks it against a mock whose command registry fails on any call, and against the compiled classes.
- **Harness config the test used:** `command-rate-limit = 0` and `login-ratelimit = 0` as ADR-0016 section 5 says, modern
  forwarding with a generated secret copied to `/server/forwarding.secret`, `bStats` disabled, `/server` files copied in with
  `withCopyToContainer` (no bind mount), the agent port published to the host for the test only.

## Capabilities
None declared for a cell. Not a row yet. The agent itself reports `command`, `players`, `events`, `log` and `proxy` in `health`
(`events` and `players` on the evidence of P01, T-M7-11).

## Direct join (T-M7-11)
P02 joins a client to a backend's own game port, around the proxy, with modern forwarding on. Paper refuses it. Observed on
`velocity@4.3.1+paper@26.3`, the driver's message exactly (the port varies):
`could not join PSDirect -> localhost:25993: This server requires you to connect with Velocity.`
P02 asserts that text. The backend asks every login for `velocity:player_info`; a client that does not answer stalls instead of being
refused, so the mcpl driver answers a login query with an empty answer, as a vanilla client does (T-M7-11, `McplSession`). Before that
change the same join ended in `timed out joining ... last packet received: ClientboundCustomQueryPacket` after 30 s, which is a stall
of the harness's client and not a refusal by the backend (run `velocity-1`, kept in the PR evidence).

## Gotchas
- **A backend's game port is bound to `127.0.0.1` on a random host port** (T-M7-11, test-only exception in ADR-0016 section 8), so
  P02's direct join can reach it; nothing else on the machine's network can. With modern forwarding the secret protects the backend: the direct join is refused. _T-M7-11_
- **A copied `/data/config` kills a Paper backend.** `docker cp` of a staged `config/` directory into `/data/` makes it
  root-owned; the image cannot write `paper-world-defaults.yml` (`AccessDeniedException`), and Paper then crashes at
  world load with `World defaults configuration file 'config/paper-world-defaults.yml' doesn't exist`. Copy
  `paper-global.yml` into `/config/` instead; the image syncs it to `/data/config` as the server user. _Run 1, 2026-11-09_
- **A partial `paper-global.yml` loads, with a WARN:** `[Configurations] The global config file didn't have a version
  set, assuming latest`. The forwarding keys were applied (forwarded joins worked). _Run 3_
- **Velocity can forward a fast `/server <name>` to the backend.** On the first client of runs 3 and 5, `/server
  survival` sent 25 ms after `/server` reached the lobby backend: Paper logged `Spike1 issued server command: /server
  survival` and replied `command.unknown.command`, so no switch happened. The defaults are `command-rate-limit = 50` and
  `forward-commands-if-rate-limited = true`, which fits. With `command-rate-limit = 0` (run 6) the same sequence switched
  10 of 10, the first client's gap being 14 ms. One run, so the cause is likely, not proven. The 30 to 34 ms gaps of the
  other clients passed with the default, so the limiter is not a plain "50 ms since the last command". A harness must
  not send proxy commands back to back; T-M7-09 should either await the previous reply's effect or set the limit.
- **`login-ratelimit` defaults to 3000 ms per address.** Every test client comes from the same address, so the spike
  set it to 0. Not observed with the default.
- **bStats is on by default** (`plugins/bStats/config.txt`); the spike copies `enabled=false`.
- **`moved too quickly!` WARNs on the backend** for every proxied client on its first lobby join (`Spike2 moved too
  quickly! -6.5,-60.0,-9.5`, six lines), as on a direct join. The harness allowlist matches only `T<hex>` names, so a
  proxied harness client must keep that naming.
- **`command-rate-limit` is an `[advanced]` key.** Velocity 4.3.1's `default-velocity.toml` (read from the build 45 jar,
  2026-11-10) has `login-ratelimit`, `command-rate-limit` and `forward-commands-if-rate-limited` under `[advanced]`
  only. `VelocityAgentIT` (T-M7-05) set `command-rate-limit = 0` at the top level, where Velocity does not read it, so
  that test ran with the default 50; it sends no player commands, so its results stood. `TopologyConfig` (T-M7-07)
  renders both limits under `[advanced]`, and T-M7-08 moved the IT's key there too. _T-M7-07_
- **A paused backend stalls a join on the proxy-to-backend hop.** With the lobby container paused (`docker pause`), the
  client got login success from Velocity and stalled at `CONFIGURATION_STALLED`; Velocity logged `[connected player]
  T5a1 ... has connected` and the backend logged nothing. A stopped (not paused) backend was not run. `TopologyFaultIT`
  uses the pause. _T-M7-07_
- **Docker on Windows:** run the spike with `MSYS_NO_PATHCONV=1`; host paths given to `docker cp` and `java` go through
  `cygpath -m` (run 2 failed on `/c/pw/...`).

## Not run
- Bridged combinations: a backend older than 26.3 behind Velocity, and ViaProxy in front of the proxy. Not run in this
  spike, so nothing is claimed. Velocity 4.3.1 takes 777 natively, so a bridge is not needed for the `paper@26.3` rows.
- `none` and `bungeeguard` forwarding; a wrong secret (T-M7-13).

## How to re-run
```bash
export JAVA_HOME="C:\\Program Files\\Java\\jdk-21"   # the mcpl driver needs 21+
export SPIKE_CP=$(./gradlew -q --init-script scripts/spike/classpath.init.gradle :drivers:mcpl:spikeClasspath --max-workers=1 | sed -n 's/^SPIKE_CP=//p')
export SPIKE_JAVA="/c/Program Files/Java/jdk-21/bin/java"
PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh bash scripts/spike/proxy-spike.sh velocity-modern 5
MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh bash scripts/spike/proxy-protocol-probe.sh VELOCITY 3.5.1 615
```
Output (client lines, transcripts, every container log, the staged configs) goes to `build/spike/<mode>-<time>/`.
The script removes its containers and network on exit.
