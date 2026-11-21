# Sponge (SpongeVanilla) and a plugin agent

Spike T-M10-11, 2026-11-20, Windows 11 host, Docker Desktop 29.9.3. Everything below was run by hand under `scripts/dockerlock.sh`
with `scripts/spike/mod-spike.sh` (a `sponge` case was added) and the throwaway plugin in `scripts/spike/sponge/`. Nothing here is
built: `versions.yml` has no Sponge row, the wire protocol did not change, [platforms.md](../platforms.md) did not change and no
production code changed. Downloads and run output lived outside the repository (`/c/pw/sponge-cache`), never committed. Where the
runs did not show a thing, the text says so.

## Verdict

**Feasible.** SpongeVanilla `26.3-21.0.0-RC2792` (Minecraft 26.3, SpongeAPI 21) boots on the pinned `itzg/minecraft-server` `java25`
digest that `versions.yml` already holds, with no new image. The real `mcpl` driver joins it **natively** (protocol 777, no
ViaProxy): **23 of 23 joins** over 5 runs. A throwaway Sponge plugin served `GET /v1/health` and `GET /v1/players` read-only in the
wire envelope with bearer auth: **30 of 30** agent reads green in the last three runs (d3 to d5, each 5 joins, `players` named the
joined player every time). One red run (d2) is a real finding: Sponge refuses to construct a plugin whose listener has a generic
event type unless the jar was compiled with `-g` (below).

What a platform card would still have to decide is the oracle gap (section "Oracle gap"): Sponge's inventory, item, config and
reload models are not Bukkit's, and the one usable build for 26.3 is an RC, not a "recommended" build.

## The pin (what ran)

| Fact | Value |
|---|---|
| SpongeVanilla | `26.3-21.0.0-RC2792` (manifest: `Implementation-Version 26.3-21.0.0-RC2792`, `Git-Branch api-21`, `Git-Commit f0b880328cef1ede93e3f22a045f518c167f826f`) |
| Minecraft / API | 26.3 (Proofstone's newest row) / SpongeAPI `21.0.0` (the jar's own metadata says `21.0.0-SNAPSHOT`) |
| Status | `recommended: false` on the downloads API. The newest recommended build is `1.21.10-17.0.0` (API 17). Newer builds exist only for 26.4 snapshots (API 22), which are not a Proofstone row |
| Source | downloads API `https://dl-api.spongepowered.org/v2/groups/org.spongepowered/artifacts/spongevanilla/versions/26.3-21.0.0-RC2792`, which points at Sponge's Maven |
| URL | `https://repo.spongepowered.org/repository/maven-releases/org/spongepowered/spongevanilla/26.3-21.0.0-RC2792/spongevanilla-26.3-21.0.0-RC2792-universal.jar` |
| Size | 8,611,562 bytes (matches `Content-Length`; last-modified 2026-11-17 01:05:26 GMT) |
| sha256 | `603d2fa0292d9c15f39a3ba39624823ad90b25ff3349380620f7d72455b170b7` (the repository's published `.sha256`; computed after download, equal) |
| sha1 / md5 | `3339a427209e5593a6e9ad7da1e84caf77f48a50` / `84308e569856b0ef51b01d461198312e` (downloads API, `.sha1`/`.md5` and ETag agree) |
| Image | none of Sponge's own: `itzg/minecraft-server@sha256:e2fabc53…` (`java25`, `versions.yml`), `TYPE=CUSTOM`, `CUSTOM_SERVER=/pins/<universal jar>` (copied in, no bind mount). JVM: Eclipse Adoptium 25.0.5 |
| EULA | `PROOFSTONE_ACCEPT_EULA=true` in the command environment only (the script turns it into the image's `EULA=TRUE`) |

The universal jar is an installer. At every first start (each run is a fresh container) it reads Mojang's
`launchermeta.mojang.com` version manifest, downloads `minecraft_server-bundle.jar` (checksum verified, its log says so), unpacks the
26.3 server and its libraries, then downloads Sponge's own libraries into `/data/libraries`. Those libraries are listed with a
**sha512 each** in the jar's `sponge-libraries.json` (28 bootstrap, 26 main), so they are pinned by the universal jar's own hash. The
host they come from is not in the log (not shown). Seen in `/data/libraries` after boot (sha256):
`minecraft_server.jar` `a362163eec5d1612d520772bc16e5b39c09e3b234fdc045f56bf544284ee8ae6`, `minecraft_server-bundle.jar`
`d052f14d7a173734fba553711e5b570162e2f2a313267ee31a21b975a679be64`. 95 jars in total, 96 with the spike plugin. A start with no
network was not tried, so whether an installed `/data` boots offline is unknown.

## Runs (all on 2026-11-20, one container per run, removed by the script)

| Run | What | Boot to `Done` (container start, includes install) | Joins | Agent reads |
|---|---|---|---|---|
| g1 | `./gradlew :drivers:mcpl:spikeClasspath` (classpath for `ModJoin`), no Docker | n/a | n/a | n/a |
| d1 | bare server, `EXPORT=1` (the plugin's compile classpath) | 22 s | 3 of 3 | none (no plugin) |
| d2 | spike plugin built **without** `-g` | 21 s | 5 of 5 | **red: 0 of 10** (health 500 "no container", players 500 "server not started") |
| d3 | spike plugin built with `-g` | 31 s | 5 of 5 | 10 of 10 |
| d4 | same as d3 | 45 s | 5 of 5 | 10 of 10 |
| d5 | same as d3 | 24 s | 5 of 5 | 10 of 10 |

Joins: **23 of 23**, no join failure, no timeout. The server's own `Done (…)` was 1.15 to 1.72 s; the rest is the install and the
JVM. d2 is kept as a red: it was not re-run into green, the plugin was changed (`-g`) and the next runs are new runs. Each green run
also read `/v1/health` once before any join (`ready: true`) and once with a wrong token (`401 UNAUTHORIZED`).

Reproduce (Git Bash, repo root; a reviewer re-runs d3):

```bash
export GRADLE_USER_HOME=<scratch> SPIKE_CACHE=<dir with the universal jar> SPIKE_JAVA=<JDK 25>/bin/java
SPIKE_CP=$(bash scripts/dockerlock.sh ./gradlew -q --init-script scripts/spike/classpath.init.gradle :drivers:mcpl:spikeClasspath \
  --no-parallel --max-workers=1 | sed -n 's/^SPIKE_CP=//p'); export SPIKE_CP
# d1: bare boot, keeps /data/libraries for the plugin's classpath
SPIKE_OUT=<out>/d1 EXPORT=1 PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 bash scripts/dockerlock.sh bash scripts/spike/mod-spike.sh sponge 26.3 3
SPIKE_JDK=<JDK 25> bash scripts/spike/sponge/build-plugin.sh <universal jar> <out>/d1/export <out>/plugin
# d3: plugin loaded, 5 joins, players and health read while each player is online
SPIKE_OUT=<out>/d3 MODS=<out>/plugin/proofstone-spike-sponge.jar PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 \
  bash scripts/dockerlock.sh bash scripts/spike/mod-spike.sh sponge 26.3 5 'http:/v1/players=>"name":"{player}"' 'http:/v1/health=>"ready":true'
```

## What an agent is on Sponge

- **Plugin model.** A jar in `/data/mods` (the `java_directory` locator found it; Sponge also creates `/data/mods/plugins`) with
  `META-INF/sponge_plugins.json` (loader `java_plain` 1.0, a plugin id, an `entrypoint` class). The main class is annotated
  `@org.spongepowered.plugin.builtin.jvm.Plugin("<id>")` (from `plugin-spi` 0.5.2). Sponge creates it through Guice; a no-arg
  constructor works, no `@Inject` was needed, and the instance is registered as a listener automatically. The container is reached
  with `Sponge.pluginManager().plugin(id)`. A dependency `spongeapi` `21.0.0` in the metadata did not stop loading although the build
  reports `21.0.0-SNAPSHOT` (shown: `Loaded plugin(s): [minecraft, spongevanilla, sponge, spongeapi, proofstone_spike]`).
- **API and bytecode.** SpongeAPI 21 has no release on Sponge's Maven (`spongeapi` `21.0.0` is 404 in releases; only a
  `21.0.0-SNAPSHOT` directory exists in snapshots). The API classes ship inside the universal jar's nested
  `jars/spongevanilla-mod.jar` (2,656 `org/spongepowered/api` entries, class file 69, Java 25), and the spike compiled against
  that plus `/data/libraries`. A real agent would pin either that jar (sha256 above) or a released `spongeapi` once one exists.
  The shared `platforms:agent-core` is Java 8 bytecode, so it would load; the plugin class itself compiles against Java 25 classes.
- **Event order.** `@Listener(order = …)` takes `PRE, AFTER_PRE, FIRST, EARLY, DEFAULT, LATE, LAST, BEFORE_POST, POST`. The SpongeAPI
  javadoc (branch `api-21`) gives `POST` as "last minute things and monitoring of events for rollback or logging": Sponge's MONITOR.
  `@Listener` also has `beforeModifications()` (default `false`; `true` runs before other server mods). Without `@IsCancelled` a
  listener is **not called for cancelled events** (javadoc); a probe that must see cancelled events needs
  `@IsCancelled(Tristate.UNDEFINED)`. Only the javadoc says this; the spike did not cancel anything.
- **Observe-only** means: listeners at `Order.POST`, `beforeModifications = false`, never `setCancelled` or a setter, and reads on the
  server thread. Shown: `Join` and `Disconnect` listeners at `POST` only logged, on `Server thread`.
- **Threads and readiness.** The plugin is constructed on `main` about 9 to 12 s before `StartedEngineEvent<Server>` fires on
  `Server thread` (d3 12,379 ms, d4 9,066 ms, d5 9,768 ms). `players` was read through
  `Sponge.server().scheduler().executor(container)`: every read logged `Server thread onMainThread=true`, and the HTTP thread waited
  on a future with a 10 s bound (never a partial answer).
- **Serving the wire protocol read-only.** The spike opened `com.sun.net.httpserver` on 25590 in the constructor, answered 401 to a
  missing or wrong bearer token, reported `ready: false` in `health` until `StartedEngineEvent<Server>`, and refused `players`
  (500) before it. A real agent would be
  `platforms/sponge/agent`: `AgentServer` from agent-core (auth, envelope, routes, `X-Proofstone-Request-Id` logging, which the spike
  did only as a log line) plus a `SpongeGame` that implements `game.Game` with Sponge calls on the server scheduler, shaded and
  relocated like the other agents, and **built with `-g`**.

## Oracle gap (what the spike did not cover)

The spike served two endpoints. For a cell that passes conformance, a `SpongeGame` would need, each unshown:
- `PlayerState`: `inventory` keys are "Bukkit raw player-inventory indexes" (agent-protocol §3). Sponge's inventory is a query/slot
  tree (`PrimaryPlayerInventory`, `Slot`), so a slot-index mapping has to be defined and documented per pack. `openWindow`,
  `cursor`, `xp`, `gamemode` and `location` have Sponge keys (`Keys.*`); not tried.
- `item.json` (`give`, reads): Sponge `ItemStack` with `Keys` and registry keys (`minecraft:…`); `raw` would come from
  `DataContainer`. Not tried.
- `config` endpoints: Sponge plugin configs are Configurate (HOCON by default), not Bukkit YAML; the dotted-path model of
  `GET/PATCH /v1/plugins/{id}/config` would need a Configurate reader. Not tried.
- `reload`: Sponge has no plugin disable/enable; the nearest thing is `RefreshGameEvent`. This changes what `reload` can mean.
- `events/subscribe`: `EventManager` supports registering a listener object per class at runtime; generic events need a type
  token, and a jar built without `-g` breaks annotated generic listeners (below). Not tried for dynamic listeners.
- `log`: Sponge logs through Log4j2 (`[c.mod.mod.Launcher/MODLAUNCHER]`-style loggers); `LogBuffer` should apply. Not tried.
- `command`, `ticks`, `world`, `datapacks`, `snapshot`: not tried.
- Protocol: joins were native 777 only. Older clients through ViaProxy, `SPIKE_PROBE=1` (raw login packets) and an offline restart
  were not run. The login had two `ClientboundCustomQueryPacket`s (Sponge's own login queries); the driver's empty answers were
  enough to join.

Pins a Sponge row would need: the universal jar (version, sha256), the `java25` host digest (already pinned), and the agent's
SpongeAPI compile input. The Mojang jar and libraries are pinned through the universal jar's manifest and the Mojang version
manifest, but fetched over the network at every fresh container; a cache or a second "installed" image layer would be a design
decision, not taken here.

## Gotchas

- **`-g` is required.** Sponge reads a listener's generic event type (`StartedEngineEvent<Server>`) from the method's
  **LocalVariableTable** (`ListenerClassVisitor.visitLocalVariable`, SpongeCommon at the build's commit), not from the `Signature`
  attribute alone. A plain `javac` build (no `-g`) fails with `Failed to construct plugin proofstone_spike` /
  `IllegalArgumentException: Incorrect number of type arguments for [interface …StartedEngineEvent]: expected 1, but got 0` (d2).
  Gradle's default compile has `-g`, so a normal Gradle build would not hit it; a hand build does.
- **A failed plugin does not stop the server.** Sponge logs `SPONGE PLUGINS FAILED TO LOAD … Your game will continue to load
  without these plugins` and reaches `Done`. In d2 the spike's port was already open (opened in the constructor), so a readiness
  check on the port alone would pass. A real agent must not answer `health` as ready unless its plugin container exists and
  `StartedEngineEvent<Server>` has fired, and core's readiness must keep requiring both the log line and `health` (ADR-0005).
- **Log lines differ from vanilla and Paper.** A login is `<name>[ServerPlayer[…]] logged in to world 'minecraft:overworld' with
  entity id …` and there is **no `joined the game` line**; `left the game` is present. Anything that counts joins from the log needs
  the Sponge pattern (`mod-spike.sh` now accepts both).
- `ERROR No key layers in MapLike[{}]` is logged once at world creation in every run, with the image's flat world
  (`LEVEL_TYPE=minecraft:flat`, no generator settings). The world loaded and players joined; whether vanilla 26.3 logs the same with
  these settings was not checked.
- Java 25 warns about `sun.misc.Unsafe` (`net.minecraftforge.unsafe.UnsafeHacks`) and native access (jansi, jline, jna, netty).
  Only warnings in these runs.
- The spike plugin reports `agentVersion` `0.0.0-spike-spike` (its metadata version plus a suffix). Cosmetic.
