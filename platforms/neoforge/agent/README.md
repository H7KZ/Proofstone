# NeoForge agent (`platforms:neoforge:agent`)

## Purpose
The in-server half of the oracle for NeoForge loader cells ([ADR-0017](../../../docs/adr/0017-mod-platform-packs.md), card
T-M8-06). A server-only NeoForge mod (id `proofstone_agent`: a NeoForge mod id allows no `-`) that serves the
[agent protocol](../../../docs/specs/agent-protocol.md) 1.3 over HTTP from `platforms:agent-core`: `health`, `command`,
`players` (state, give, reset, PATCH), `world/block`, `snapshot`, `events`, `log`, `ticks` and `mods`. It reads
`PROOFSTONE_AGENT_TOKEN` and `PROOFSTONE_AGENT_PORT` (default 25580) like the other agents and binds all interfaces inside
the container network.

## Boundaries
- **Observe only** (AGENTS.md section 3, ADR-0017 section 5). No mixin, no access transformer, no command, no network
  payload, nothing on the mod bus, no listener that can decide a result. The agent registers three listeners on the game bus
  (`NeoForge.EVENT_BUS`): `ServerTickEvent.Post`, `ServerStartedEvent` and `ServerStoppingEvent`, and the probes, all at
  `EventPriority.LOWEST` (cancelled events included), all reading only. `ObserveOnlyTest` proves it from the descriptor and
  the `@Mod` annotation, from NeoForge's real event bus (ordering, cancellation, unregistering), and from a scan of the
  compiled classes.
- **No client side.** `@Mod(dist = Dist.DEDICATED_SERVER)`; `META-INF/neoforge.mods.toml` has one `[[mods]]` entry and no
  `[[dependencies]]` (where a `side` would ask for a client counterpart). With no payload registered the vanilla client
  needs no mod in the configuration phase (`NeoForgeAgentIT` joins with the `mcpl` driver; docs/platforms/neoforge.md).
  `loaderVersion = "[1,)"` is the `javafml` language-loader declaration the T-M8-01 spike mod booted with, not a pin.
- **Server ready is `ServerStartedEvent`** (ADR-0017 section 5): the HTTP server only starts there, so a `health` reply
  means the server is up. NeoForge needs no extra mod for it.
- **Daemon threads only.** The HTTP server is started from a daemon thread, so its threads are daemons and a server that
  fails still ends its JVM (`NeoForgeAgentStartTest`).
- **No runtime dependency beyond the shaded set.** The shadow jar holds this module, `platforms:agent-core` and
  `jackson-databind` relocated to `io.github.h7kz.proofstone.shaded.jackson`. The game, FML, the bus and NeoForge are compile
  only; the server provides them.
- **Java 25 bytecode, 26.3 only.** Built on a Java 25 toolchain with `--release 25` against the unobfuscated 26.3 game (no
  mappings). **Not ModDevGradle:** its NeoForm pipeline fetches the game and its libraries at build time outside the version
  catalog, and each such download would need its own sha256 pin; so the module compiles with `javac` against pinned jars,
  the fallback ADR-0017 section 5 allows. `unpackGame` checks every jar by its catalog sha256 (the vanilla server jar, which
  it unpacks, NeoForge `universal`, FancyModLoader `loader`, `bus`, `mergetool` `api`, `maven-artifact`, and `typetools` for
  the unit tests) and puts them in `build/game`. Nothing of them is committed or shaded.
- **Shared game code is a copy.** `MinecraftView`, `ItemJson`, `ServerThread`, `TickCounter`, `GameView` and the game
  wrapper are the Fabric agent's (vanilla names on both loaders); only `mods()`, the probes and the entrypoint differ. A
  shared module would change the Fabric agent, which this card does not own.
- Not a public artefact. The harness copies it into the container's `/mods`; it never ships in a user's build.

### Thread model
- Every game read and write runs on the **server thread** (`MinecraftServer.execute`); the HTTP thread waits, bounded by the
  request's `timeoutMs`, and a stalled server answers `504 TIMEOUT`, never a partial result.
- `log`, `ticks`, the probes and `mods` answer from the HTTP thread: they are thread-safe. `ticks/await` completes from the
  end-of-tick listener, so nothing polls or sleeps.
- NeoForge posts an event on the thread that fires it (the server thread for logins). A probe reads the getters inline.

## Public surface
- **Entry point:** `ProofstoneNeoForgeAgent` (`@Mod`, `META-INF/neoforge.mods.toml`, dedicated server only).
- **`NeoForgeAgent.start(config, serverThread, view, probes, logs, ticks, version, mcVersion)`:** the agent on any
  `GameView`, which is how the contract test runs it on a mock.
- **Endpoints** (envelope and error codes: [Bukkit agent README](../../bukkit/agent/README.md)): as the
  [Fabric agent](../../fabric/agent/README.md), with these differences:

  | Endpoint | Reply on NeoForge |
  |---|---|
  | `GET /v1/health` | `platform: "neoforge"`, `protocol: "1.3"`, `mcVersion` the game version; tokens `command`, `players`, `world`, `events`, `log`, `ticks`, `mods` |
  | `GET /v1/mods` | `{mods: [{id, version}]}` sorted by id: the mods whose jar is in the mods folder (not `minecraft` or `neoforge`, not a jar-in-jar mod) |
  | `POST /v1/events/subscribe` | `{class: "<event class>"}` of a concrete NeoForge `Event` (binary name, `$` for nested classes); fields are its zero-arg `get*` getters by method name, as on Bukkit; a player is `{name}`. An abstract, unknown or non-event class is `400 INVALID_REQUEST`. `DELETE` unregisters the listener |
  | `GET /v1/plugins/...` | `404 NOT_FOUND`: a NeoForge server has no plugins |

## How to test
- `./gradlew :platforms:neoforge:agent:test`: `NeoForgeContractTest` replays the golden cases over HTTP on a mock view
  (wire, threading, timeouts, probes on NeoForge's real bus), `ObserveOnlyTest`, `NeoForgeAgentStartTest`, `NeoForgePinsTest`
  (catalog NeoForge equals the neoforge@26.3 row of `versions.yml`).
- `PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :platforms:neoforge:agent:integrationTest --no-parallel --max-workers=1`:
  `NeoForgeAgentIT` boots the neoforge@26.3 row (installer downloaded to `build/neoforge-it/cache`, checked by sha256, copied
  in), joins `Alex` with the mcpl driver and replays the goldens on the real server. The server log is kept in
  `build/neoforge-it/server.log`.
