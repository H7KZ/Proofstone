# Fabric agent (`platforms:fabric:agent`)

## Purpose
The in-server half of the oracle for Fabric loader cells ([ADR-0017](../../../docs/adr/0017-mod-platform-packs.md), card
T-M8-05). A server-only Fabric mod (id `proofstone-agent`) that serves the [agent protocol](../../../docs/specs/agent-protocol.md)
1.3 over HTTP from `platforms:agent-core`: `health`, `command`, `players` (state, give, reset, PATCH), `world/block`, `snapshot`,
`events`, `log`, `ticks` and `mods`. It reads `PROOFSTONE_AGENT_TOKEN` and `PROOFSTONE_AGENT_PORT` (default 25580) like the
other agents and binds all interfaces inside the container network.

## Boundaries
- **Observe only** (AGENTS.md section 3, ADR-0017 section 5). No mixin, no access widener, no command, no listener that can
  decide a result. The agent registers three Fabric API lifecycle listeners (end of tick, server started, server stopping) and
  the probes, all in its own phase `proofstone:observe`, ordered after the default phase, all returning nothing.
  `ObserveOnlyTest` proves it from the descriptor, from real Fabric API events, and from a scan of the compiled classes.
- **Fabric API is required** (ADR-0017 section 5): "server ready" is `ServerLifecycleEvents.SERVER_STARTED`, and the HTTP server
  only starts there, so a `health` reply means the server is up. `fabric.mod.json` depends on `fabric-lifecycle-events-v1`.
- **Daemon threads only.** The HTTP server is started from a daemon thread, so its threads are daemons and a server that
  fails still ends its JVM (`FabricAgentStartTest`).
- **No runtime dependency beyond the shaded set.** The shadow jar holds this module, `platforms:agent-core` and
  `jackson-databind` relocated to `io.github.h7kz.proofstone.shaded.jackson`. The game, the loader and Fabric API are compile
  only; the server provides them.
- **Java 25 bytecode, 26.3 only.** Built on a Java 25 toolchain with `--release 25` against the unobfuscated 26.3 game (no
  mappings). Not Loom: Loom 1.18+ needs Gradle on a JVM 25 and Loom 1.17 adds project repositories, which `settings.gradle.kts`
  refuses, so the module compiles with `javac` against the pinned jars, the fallback ADR-0017 section 5 allows. `unpackGame`
  checks the vanilla server jar and Fabric API by sha256 (catalog) and unpacks the game jar, its libraries and Fabric API's
  modules into `build/game`. Nothing of them is committed or shaded.
- Not a public artefact. The harness copies it into the container's `/mods`; it never ships in a user's build.

### Thread model
- Every game read and write runs on the **server thread** (`MinecraftServer.execute`); the HTTP thread waits, bounded by the
  request's `timeoutMs`, and a stalled server answers `504 TIMEOUT`, never a partial result.
- `log`, `ticks`, the probes and `mods` answer from the HTTP thread: they are thread-safe. `ticks/await` completes from the
  end-of-tick listener, so nothing polls or sleeps.
- Fabric API calls a callback on the thread that fires it (the server thread for joins). A probe reads the arguments inline.

## Public surface
- **Entry point:** `ProofstoneFabricAgent` (`DedicatedServerModInitializer`, `fabric.mod.json`, environment `server`).
- **`FabricAgent.start(config, serverThread, view, probes, logs, ticks, version, mcVersion)`:** the agent on any `GameView`,
  which is how the contract test runs it on a mock.
- **Endpoints** (envelope and error codes: [Bukkit agent README](../../bukkit/agent/README.md)):

  | Endpoint | Reply on Fabric |
  |---|---|
  | `GET /v1/health` | `platform: "fabric"`, `protocol: "1.3"`, `mcVersion` the game version; tokens `command`, `players`, `world`, `events`, `log`, `ticks`, `mods` |
  | `GET /v1/mods` | `{mods: [{id, version}]}` sorted by id: the mods whose jar is in the mods folder (not the game, Java, the loader, or a nested module) |
  | `POST /v1/command` | `{raw}`: what the command sent to its source; an unknown command is `400 INVALID_REQUEST` |
  | `GET /v1/players/{name}` | `PlayerState`; worlds named as Bukkit names them (`world`, `world_nether`, `world_the_end`, else the dimension id); slots are the vanilla indexes (0-35, 36-39 feet to head, 40 offhand). A player with a window open is `501 NOT_SUPPORTED` (the server keeps no window title) |
  | `POST .../give`, `.../reset`, `PATCH` | as the Bukkit agent; enchantments resolve through the server's registries; `UNKNOWN_KEY` before anything changes |
  | `GET/PUT /v1/world/block` | `{world, x, y, z, type, state}`; an unknown block, property or value is `UNKNOWN_KEY` naming `type[k=v]` |
  | `GET /v1/snapshot` | players as above; `plugins` is always `[]` |
  | `POST /v1/events/subscribe` | `{class: "<holder class>.<FIELD>"}` of a Fabric API event whose callback returns `void`; fields are `arg0..argN`; a player or a play connection is `{name}` |
  | `GET /v1/plugins/...` | `404 NOT_FOUND`: a Fabric server has no plugins |

## How to test
- `./gradlew :platforms:fabric:agent:test`: `FabricContractTest` replays the golden cases over HTTP on a mock view (wire,
  threading, timeouts, probes on real Fabric API events), `ObserveOnlyTest`, `FabricAgentStartTest`, `FabricPinsTest` (catalog
  equals the fabric@26.3 row of `versions.yml`).
- `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :platforms:fabric:agent:integrationTest --no-parallel --max-workers=1`:
  `FabricAgentIT` boots the fabric@26.3 row (launcher and Fabric API downloaded to `build/fabric-it/cache`, checked by
  sha256, copied in), joins `Alex` with the mcpl driver and replays the goldens on the real server. The server log is kept in
  `build/fabric-it/server.log`.
