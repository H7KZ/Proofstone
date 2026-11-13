# Velocity agent (`platforms:velocity:agent`)

## Purpose
The in-proxy half of the oracle for Velocity topologies ([ADR-0016](../../../docs/adr/0016-topology-cells.md), card T-M7-05).
A Velocity plugin that serves the proxy subset of the [agent protocol](../../../docs/specs/agent-protocol.md) (protocol 1.2)
over HTTP: `health`, `command`, `players`, `log`, `proxy/servers`, and the event probes (`events`). It reads the token and the
port from `PROOFSTONE_AGENT_TOKEN` and `PROOFSTONE_AGENT_PORT` (default 25580), like the Bukkit agent, and binds all
interfaces inside the container network; the harness never publishes it outside debug mode.

## Boundaries
- **Observe only** (AGENTS.md section 3). It registers no command, cancels, denies or modifies no proxy event, does not
  touch a player, a server registration, the forwarding settings or the config, and does not delay an event. Every handler it
  registers is at `PostOrder.LAST` and returns at once. `ObserveOnlyTest` proves it two ways: a run against a mock whose command
  registry fails the test on any call, and a scan of the compiled classes for the Velocity API that changes state.
- **No game state.** A proxy has no inventory, location, world or ticks. `world`, `give`, `reset`, `PATCH`, `ticks`, plugin
  config and `snapshot` answer `404 NOT_FOUND`, never an empty success. A backend agent serves those; a backend agent never serves
  `/v1/proxy/*`.
- **Java 17 bytecode** (ADR-0016 section 10), compiled on a JDK 26 because the Velocity 4.3.1 API jar is Java 25 bytecode.
  Velocity 4.x itself needs Java 25, so the agent runs there. `agent-core` inside the jar is Java 8 bytecode.
- **No runtime dependency beyond the shaded set.** The shadow jar holds this module, `platforms:agent-core` and
  `jackson-databind` relocated to `io.github.h7kz.proofstone.shaded.jackson`. The Velocity API, Adventure, Guice and SLF4J are
  `compileOnly`; Velocity provides them.
- **Velocity 4.x only.** The compile-time API is 4.3.1. Velocity 3.x rejects the protocol 777 client and is not a row
  (ADR-0016 section 3).
- Not a public artefact. The harness copies it into the proxy container; it never ships in a user's build.

### Thread model
Velocity has no main thread: events fire on its own pool and its scheduler is a worker pool. The agent says so in the code and
works like this.
- Every `Game` call (players, one player, servers, a command, probes) runs as a task on **Velocity's scheduler** and the HTTP
  thread waits for it, bounded by the request's `timeoutMs`. A stalled proxy gives `504 TIMEOUT`, never a partial result. A task
  whose caller already gave up is skipped when its turn comes.
- A command goes through `CommandManager.executeAsync` with a sender of the agent's own (`CapturingSource`) that holds every
  permission, like the console, and collects what the command prints. An unknown command is `400 INVALID_REQUEST`, not an
  empty reply. Output that a command prints later than its dispatch is not captured (spec section 2: best effort).
- A server's ping is started on the scheduler and read on the HTTP thread, so a slow backend does not hold a scheduler worker.
  `online` is the result of the proxy's own `RegisteredServer.ping()`: a completed ping is online, a failed one (refused,
  unresolved, reset) is not, and a ping that outlasts the request is `504 TIMEOUT`.
- Event handlers run on whichever thread fires the event. The probes read the event's getters inline (an event is only safe
  to read during its own dispatch), keep the last 1000 in a synchronised ring buffer, and return `null` from `executeAsync`'s
  default, so nothing is delayed. A proxy has no ticks, so an event's `tick` is always 0.

## Public surface
- **Entry point:** `ProofstoneAgentPlugin`, found through `velocity-plugin.json` (id `proofstone-agent`); no annotation
  processor. Its constructor is injected with the `ProxyServer` and an SLF4J `Logger`.
- **`VelocityAgent.start(proxy, plugin, logger, version, env)`:** starts everything from a `ProxyServer`, which is what lets the
  contract test run it on a mock.
- **Endpoints** (the shared server is `platforms:agent-core`; see the [Bukkit agent README](../../bukkit/agent/README.md) for the
  envelope and error codes):

  | Endpoint | Reply |
  |---|---|
  | `GET /v1/health` | `platform: "velocity"`, `mcVersion` is the Velocity version string, `protocol: "1.2"` |
  | `POST /v1/command` | `{raw}`: what the command printed, as the console would show it (English, plain text) |
  | `GET /v1/players` | names of the players on the proxy, sorted |
  | `GET /v1/players/{name}` | `{name, uuid, proxy: {server, previousServer}}`; `PLAYER_NOT_FOUND` when absent, and also while the player is on the proxy but not yet on a backend |
  | `GET /v1/proxy/servers`, `GET /v1/proxy/servers/{name}` | `{name, address: "host:port", online, players}`; `SERVER_NOT_FOUND` for an unknown name |
  | `GET /v1/log?since=&minLevel=` | Log4j2 records with stack traces |
  | `POST /v1/events/subscribe`, `GET`, `DELETE /v1/events/{probeId}` | read-only probes at `PostOrder.LAST` |

- **Capabilities** in `health`: `command`, `players`, `events`, `log`, `proxy`. `events` is claimed on real-server evidence: the
  topology conformance P01 (T-M7-11, `:conformance:run -Dproofstone.cells=velocity@4.3.1+paper@26.3`) subscribes a probe to
  `ServerConnectedEvent`, a client joins through the proxy, and the probe reads the join back. The golden health case lists
  `players` and `proxy`; this agent adds `command`, `events` and `log`, as the Bukkit agent adds tokens to its golden.
- **`previousServer`** comes from two handlers (`ServerConnectedEvent`, `DisconnectEvent`) at `PostOrder.LAST` that record the
  previous server per player. It is `null` until the player has switched. Only the mock has exercised it; a real switch is checked by P03 (T-M7-12).
- **Log capture** reads the Log4j2 root logger by reflection (`Log4jTap`) and does not add a JUL handler: Velocity bridges JUL
  into Log4j2, which refuses `Logger.addHandler` with a WARN of its own (`LogBuffer.attachPreferringLog4j`).

## How to test
- `./gradlew :platforms:velocity:agent:test`: runs on a JDK 26 (the Velocity API is Java 25 bytecode). `VelocityContractTest`
  replays every proxy-family golden case (`health/proxy-ok`, `proxy/*`, `players/proxy-*`, plus `health/unauthorised`,
  `command/reply`, `players/not-found`, `log/read`) against a `MockProxy` built from the real API interfaces, and checks timeouts,
  the offline ping and the 404s. `ObserveOnlyTest` is the observe-only proof. `EventProbesTest` covers the probes.
  `PluginDescriptorTest` checks `velocity-plugin.json`.
- `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :platforms:velocity:agent:integrationTest --no-parallel --max-workers=1`:
  `VelocityAgentIT` boots **Velocity 4.3.1 build 45** from `itzg/mc-proxy` (java25 tag, pinned by digest) with the shaded jar and calls
  `health`, `proxy/servers`, `command` and `log` against the golden files. No backend runs, so the servers are unreachable and
  `online` is `false`; the test edits those two fields of the golden and compares the rest by shape. The pins live in
  `gradle/libs.versions.toml` until `versions.yml` schema 3 exists (T-M7-08). The jar is downloaded by the image from the pinned
  build, and the test checks its sha256 in the container before it runs anything. The forwarding secret and the token are
  generated in memory. The proxy log is kept in `build/velocity-it/proxy.log`.
- Not covered here, on purpose: a joined player, `events` and `players` on a real proxy (P01 and P02 cover them, T-M7-11), a server
  switch and `previousServer` (P03, T-M7-12), and a forwarding fault (T-M7-13).
