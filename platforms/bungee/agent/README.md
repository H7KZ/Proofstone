# BungeeCord agent (`platforms:bungee:agent`)

## Purpose
The in-proxy half of the oracle for BungeeCord topologies ([ADR-0016](../../../docs/adr/0016-topology-cells.md), card T-M7-06).
A BungeeCord plugin that serves the proxy subset of the [agent protocol](../../../docs/specs/agent-protocol.md) (protocol 1.2)
over HTTP: `health`, `command`, `players`, `log`, `proxy/servers`, and the event probes (`events`). It reads the token and the
port from `PROOFSTONE_AGENT_TOKEN` and `PROOFSTONE_AGENT_PORT` (default 25580), like the Bukkit and Velocity agents, and binds
all interfaces inside the container network; the harness never publishes it outside debug mode.

## Boundaries
- **Observe only** (AGENTS.md section 3). It registers no command, cancels or changes no proxy event, does not touch a player,
  a server registration, the forwarding settings or the config, and does not delay an event. `ObserveOnlyTest` proves it three
  ways: the command registry of a real `PluginManager` is the same before, during and after; every `@EventHandler` of every
  listener the agent registered on the real `EventBus` is read back by reflection and must be `EventPriority.HIGHEST`; and a
  scan of the compiled classes for the BungeeCord API that changes state (`setCancelled`, `setTarget`, `registerCommand`,
  `registerIntent`, `sendData`, and so on). A run through the real event dispatch shows an event comes out as it went in.
- **"Lowest priority" means last to run, which is `HIGHEST` on BungeeCord.** BungeeCord runs handlers in ascending number order
  and the highest has the last word (`LOWEST` is -64 and runs first, `HIGHEST` is 64 and runs last), the reverse of Bukkit's
  names. Observing at `LOWEST` would see an event before the other plugins had changed it. The agent sits where Bukkit's `MONITOR`
  and Velocity's `PostOrder.LAST` sit: after everyone, deciding nothing.
- **No game state.** A proxy has no inventory, location, world or ticks. `world`, `give`, `reset`, `PATCH`, `ticks`, plugin
  config and `snapshot` answer `404 NOT_FOUND`, never an empty success. A backend agent serves those; a backend agent never
  serves `/v1/proxy/*`.
- **Java 8 bytecode** (`verifyBytecode`, part of `check`), like the Bukkit agent. The API it compiles against is
  `bungeecord-api` 1.21-R0.4 from Maven Central, itself Java 8 bytecode. The pinned row (build 2111) reports API
  `26.1-R0.1-SNAPSHOT`, which is not published as a release and cannot be pinned, so the agent only uses API present in both
  (`ProxyServer`, `PluginManager`, `TaskScheduler`, `ServerInfo`, `ProxiedPlayer`, and the eleven events below). It runs on
  Java 21 in the pinned host image.
- **No runtime dependency beyond the shaded set.** The shadow jar holds this module, `platforms:agent-core` and
  `jackson-databind` relocated to `io.github.h7kz.proofstone.shaded.jackson`. The BungeeCord API is `compileOnly`; BungeeCord
  provides it. `bungee.yml` declares no `libraries`, so BungeeCord downloads nothing.
- **Waterfall is out** (ADR-0016 section 4). It is end-of-life and its newest build rejects the protocol 777 client, so this agent
  is not tested on it and no Waterfall row exists. The plugin might load there; nobody has checked, and nothing here claims it.
- Not a public artefact. The harness copies it into the proxy container; it never ships in a user's build.

### Thread model
BungeeCord has no main thread: Netty event loops carry the connections, and `ProxyServer.getScheduler().runAsync` hands a
task to the plugin's executor service. The agent works like this.
- Every `Game` call (players, one player, servers, a command, probes) runs as a task on **BungeeCord's scheduler** and the HTTP
  thread waits for it, bounded by the request's `timeoutMs`. A stalled proxy gives `504 TIMEOUT`, never a partial result. A task
  whose caller already gave up is skipped when its turn comes.
- A command goes through `PluginManager.dispatchCommand` with a sender of the agent's own (`CapturingSender`, named
  `CONSOLE`) that holds every permission, like the console, is not a `ProxiedPlayer`, and collects what the command prints.
  Messages come out as plain text with colour codes dropped, one entry per message. `dispatchCommand` returns `false` only for a
  command BungeeCord does not know: that is `400 INVALID_REQUEST`, not an empty reply. A command that refuses or fails prints its
  own message, which is output. `dispatchCommand` runs the command on the scheduler thread, so a command that blocks makes the
  request time out. Output printed later than the dispatch is not captured (spec section 2: best effort).
- A server's ping is started on the scheduler (`ServerInfo.ping`) and read on the HTTP thread, so a slow backend does not hold a
  worker. `online` is the result of the proxy's own ping: a reply is online, an error (refused, unresolved, reset) is not, and a
  ping that outlasts the request is `504 TIMEOUT`.
- Event handlers run on whichever thread fires the event. The probes read the event's getters inline (an event is only safe to
  read during its own dispatch), keep the last 1000 in a synchronised ring buffer, and register no intent, so nothing is delayed.
  A proxy has no ticks, so an event's `tick` is always 0.

## Public surface
- **Entry point:** `ProofstoneAgentPlugin extends Plugin`, found through `bungee.yml` (name `ProofstoneAgent`). `onEnable` starts
  the agent, `onDisable` stops it. A missing token logs an error and leaves the HTTP server down.
- **`BungeeAgent.start(plugin, version, env)`:** starts everything from a `Plugin` on a `ProxyServer`, which is what lets the
  contract test run it on a mock proxy built on the real `PluginManager` and `EventBus`.
- **Endpoints** (the shared server is `platforms:agent-core`; see the [Bukkit agent README](../../bukkit/agent/README.md) for the
  envelope and error codes):

  | Endpoint | Reply |
  |---|---|
  | `GET /v1/health` | `platform: "bungeecord"`, `mcVersion` is BungeeCord's version string (`git:BungeeCord-Proxy:26.1-R0.1-SNAPSHOT:55e1b0b:2111` on the pinned row), `protocol: "1.2"` |
  | `POST /v1/command` | `{raw}`: what the command printed, as plain text, one line per message |
  | `GET /v1/players` | names of the players on the proxy, sorted |
  | `GET /v1/players/{name}` | `{name, uuid, proxy: {server, previousServer}}`; `PLAYER_NOT_FOUND` when absent, and also while the player is on the proxy but not yet on a backend |
  | `GET /v1/proxy/servers`, `GET /v1/proxy/servers/{name}` | `{name, address: "host:port", online, players}`; `SERVER_NOT_FOUND` for an unknown name |
  | `GET /v1/log?since=&minLevel=` | JUL records of the `BungeeCord` logger and its plugins, with stack traces |
  | `POST /v1/events/subscribe`, `GET`, `DELETE /v1/events/{probeId}` | read-only probes at `EventPriority.HIGHEST` |

- **Capabilities** in `health`: `command`, `players`, `events`, `log`, `proxy`. `events` is claimed on real-server evidence: the
  topology conformance P01 (T-M7-11, `:conformance:run -Dproofstone.cells=bungeecord@2111+paper@26.3`) subscribes a probe to
  `ServerConnectedEvent`, a client joins through the proxy, and the probe reads the join back. `players` is in the golden
  `health/proxy-ok`; this integration test covers it for the empty proxy, and P01 and P02 read a joined player's proxy state.
- **Events a probe can watch.** BungeeCord finds handlers by scanning a listener for annotated methods, so a probe cannot attach to
  an arbitrary class by name as on Bukkit and Velocity. `EventProbes` has one handler per event: `PreLoginEvent`, `LoginEvent`,
  `PostLoginEvent`, `PlayerDisconnectEvent`, `ServerConnectEvent`, `ServerConnectedEvent`, `ServerSwitchEvent`,
  `ServerDisconnectEvent`, `ServerKickEvent`, `ChatEvent` and `PluginMessageEvent`. Any other class (a custom plugin event
  included) is `400 INVALID_REQUEST` naming the supported ones, never a probe that silently sees nothing. A plugin message's
  payload is recorded as its size (`"3 bytes"`), not its bytes.
- **`previousServer`** comes from two handlers (`ServerSwitchEvent#getFrom`, `PlayerDisconnectEvent`) at `EventPriority.HIGHEST` that
  record the previous server per player. It is `null` until the player has switched. Only the mock has exercised it (with the real
  dispatch); a real switch is checked by P03 (T-M7-12).
- **Log capture** attaches to `ProxyServer.getLogger()` (`LogBuffer.attachTo`), not to the JUL root logger. BungeeCord builds its
  `BungeeLogger` with no parent, and every plugin logger parents to it, so a handler on the root logger would see none of its
  records. BungeeCord has no Log4j2, so there is no second copy to match.

## How to test
- `./gradlew :platforms:bungee:agent:test`: runs on JDK 17. `BungeeContractTest` replays every proxy-family golden case
  (`health/proxy-ok`, `proxy/*`, `players/proxy-*`, plus `health/unauthorised`, `command/reply`, `players/not-found`, `log/read`)
  against a `MockProxy`, and checks timeouts, the offline ping, colour stripping, permissions and the 404s. The mock subclasses the
  abstract `ProxyServer` and uses the REAL `PluginManager` and `EventBus`, so commands and event dispatch behave as on BungeeCord.
  One golden is adapted: `health/proxy-ok` is recorded with `platform: "velocity"`, and the test changes that one field to
  `bungeecord`, the id spec section 2 gives this agent. `ObserveOnlyTest` is the observe-only proof. `EventProbesTest` covers the
  probes. `PluginDescriptorTest` checks `bungee.yml`.
- `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :platforms:bungee:agent:integrationTest --no-parallel --max-workers=1`:
  `BungeeCordAgentIT` boots **BungeeCord build 2111** from `itzg/mc-proxy` (java21 tag, pinned by digest) with the shaded jar and calls
  `health`, `proxy/servers`, `command` and `log` against the golden files. No backend runs, so the servers are unreachable and
  `online` is `false`; the test edits those two fields of the golden and compares the rest by shape. The pins live in
  `gradle/libs.versions.toml` until `versions.yml` schema 3 exists (T-M7-08). The jar is downloaded by the image from the pinned
  Jenkins build, and the test checks its sha256 in the container (Jenkins publishes md5 only) before it runs anything. The agent
  token is generated in memory. The proxy log is kept in `build/bungee-it/proxy.log`.
- Not covered here, on purpose: a joined player, `events` and `players` on a real proxy (P01 and P02 cover them, T-M7-11), a server
  switch and `previousServer` (P03, T-M7-12), and a forwarding fault (T-M7-13).
