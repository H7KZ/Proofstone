# NeoForge

Status: **spike** (T-M8-01, 2026-11-14); row `neoforge@26.3` in `versions.yml` (T-M8-04); agent built (T-M8-06, "The
agent" below). Every spike fact below comes from a local run under `scripts/dockerlock.sh` with
`scripts/spike/mod-spike.sh` (joins by `scripts/spike/ModJoin.java` on the real `mcpl` driver), unless it says otherwise.
Run outputs were kept under
`build/spike/{A,B,D,E}/` (not committed). See [platforms.md](../platforms.md) and [fabric.md](fabric.md).

## Host
- **Image:** `itzg/minecraft-server`, the `java25` digest `versions.yml` already pins for Paper 26.x
  (`sha256:e2fabc536673f8c6b343055c93f7a73e10caa79cd3368f645987593c65adf1e0`, mc-image-helper 1.75.0, Java 25.0.5). No
  new image was pulled. `TYPE=NEOFORGE`.
- **Java tag: `java25`** (26.3 needs Java 25).
- **Pinnable jar: the NeoForge installer** from
  `https://maven.neoforged.net/releases/net/neoforged/neoforge/<v>/neoforge-<v>-installer.jar`. NeoForge versions for 26.x
  are `26.3.0.<build>` (no `-beta` suffix on 26.3). Spike pin: **26.3.0.103** (newest on 2026-11-14, published
  2026-11-12), sha256 `d8a92e17cf12b5739a2518b11afd47eeec0e636a6e63f94b6400c5dbd3603e48`, 3,695,694 bytes. The maven
  publishes `.sha256`, `.sha512` and `.sha1`; the published sha256 matched the download.
- **How the image takes it:** copied (`docker cp`) to `/pins/` and passed as `NEOFORGE_INSTALLER=/pins/<jar>`.
  mc-image-helper logs `Running NeoForge 26.3.0.103 installer for Minecraft 26.3` and starts the server through the
  installer's `run.sh` (`SERVER="run.sh"`, `FAMILY="FORGE"`). The installer's own output is not in the container log.
- **What is fetched at first start (the installer):** the first request is `piston-meta.mojang.com` (Mojang's version
  metadata): a first start with `--network none` failed with `UnknownHostException: piston-meta.mojang.com`, then
  `Forge installer failed with exit code 1`. After a networked install, `/data/libraries` holds 73 jars (sha256 list in
  each run's `data-jars.txt`), among them the vanilla server jar `net/minecraft/server/26.3/server-26.3.jar` (sha256
  `d052f14d...`, the same bytes Fabric downloads; sha1 equals Mojang's manifest), the installer-built
  `net/neoforged/minecraft-server-patched/26.3.0.103`, `neoforge-26.3.0.103-universal.jar`, FancyModLoader 12.0.12,
  `bus` 8.0.5, `installertools` 4.0.17 and the game libraries. The hosts of the library downloads were not checked.
- **Can it be pinned or pre-fetched?** The installer: yes (sha256 above). Its downloads: not by the installer. **Once
  installed, a restart needs no network:** the same container restarted with its network disconnected (`OFFLINE=1`)
  logged `NeoForge version 26.3.0.103 for minecraft version 26.3 is already installed` and reached `Done` in 11 s.
  Not run: the installer's offline options. **Decided ([ADR-0017](../adr/0017-mod-platform-packs.md) §6):** the cold-start
  fetch stays, as a listed exception pinned by content (`vanilla-sha256` checked in the container).
- **Ready:** `[minecraft/DedicatedServer]: Done (0.450s)! For help, type "help"`. First start including the install
  took 17 to 18 s.
- **Image environment the spike set:** as for Fabric: `ONLINE_MODE=FALSE`, `ENFORCE_SECURE_PROFILE=FALSE`, flat world,
  `ENABLE_RCON=false`, `CREATE_CONSOLE_IN_PIPE=true`, `VERSION=26.3`.

### Protocol 777 (26.3) client: verdict
- **NeoForge 26.3.0.103 takes the `mcpl` 777 client natively, with no mod and with a server-only mod (no payload
  registered), and with a mod that adds one item.** 13 of 13 counted driver joins succeeded (table below), 0 join
  timeouts. NeoForge needs no client mod for this: it treats the client as vanilla.
- **Configuration phase seen by the probe** (server-only mod loaded): `minecraft:unregister` and `minecraft:register`
  (`minecraft:register`, `minecraft:unregister`, `neoforge:register`, `neoforge:network`,
  `neoforge:modded_network_setup_failed`, `c:version`, `c:register`, later `neoforge:split`, `neoforge:feature_flags_ack`,
  `neoforge:frozen_registry_sync_completed`, `neoforge:known_registry_data_maps_reply`, `neoforge:extensible_enum_ack`),
  `neoforge:register`, then a **`ClientboundPingPacket` it waits on**, then brand `neoforge`, enabled features
  `minecraft:vanilla`, known packs `minecraft:core 26.3`, `neoforge:mod/proofstone_spike 0.0.0`,
  `neoforge:mod/neoforge 26.3.0.103`, registries, tags, finish. After finish and before the play login it sends
  `neoforge:network` and re-registers channels. The driver answers the ping and ignores the payloads.
- **Content mods (a mod that registers an item): joined** (one item only); see "Content mods" below.
- **Stonecutter:** out for M8, decided in [fabric.md](fabric.md) (one native row, 26.3). Not run for NeoForge on another
  row.

### Join counts (all runs, 2026-11-14)
| Run | Mods | Driver joins ok | Join timeouts | Other refusals | Probe |
|---|---|---|---|---|---|
| A neoforge-none | none | 5 of 5 | 0 | 0 | stalled after the configuration ping (probe did not answer pings; fixed) |
| B neoforge-spikemod-dp | spike mod, data pack | 5 of 5 | 0 | 0 | reached play |
| E neoforge-content | spike mod registering an item | 3 of 3 | 0 | 0 | reached play |

**Totals: 13 counted driver joins on NeoForge, 13 ok, 0 join timeouts, 0 refused.** 3 probe connections are counted
apart. The spike mod jar was rebuilt between phase B and E (item registration added behind
`PROOFSTONE_SPIKE_CONTENT=1`); without that variable it behaves the same.

### Content mods: the vanilla client joined (one item)
With the spike mod registering one item (`proofstone_spike:probe_item`, `DeferredRegister.createItems`), 3 of 3 driver
joins and the probe reached play, with the same configuration packets as without the item (known packs include
`neoforge:mod/proofstone_spike`). Unlike Fabric API, NeoForge did not refuse a vanilla client for an added registry
entry. Not run: what the client sees if the item reaches it (in an inventory or a window), a mod that registers a
network payload, or blocks, entities and other registries. So the verdict is narrow: **NeoForge lets the vanilla driver
in with a server-only mod and with one added item; whether content is testable through it is unverified.**

## Agent notes
- **Entrypoint:** a class annotated `@Mod(value = "<id>", dist = Dist.DEDICATED_SERVER)` with a constructor taking the mod
  `IEventBus`; `META-INF/neoforge.mods.toml` with `modLoader = "javafml"` and one `[[mods]]` entry (no client side
  declared). The constructor ran on `modloading-worker-0` during mod loading and opened the HTTP port there; `/health`
  answered from the host.
- **Earliest "server ready" event:** `net.neoforged.neoforge.event.server.ServerStartedEvent` on `NeoForge.EVENT_BUS`
  (the game bus, not the mod bus), registered with `addListener(EventPriority.LOWEST, ServerStartedEvent.class, ...)`,
  on the `Server thread`, 1,902 ms after the constructor. `ServerStartingEvent` came 1 ms before it. Both are part of
  NeoForge itself, so a NeoForge agent needs no extra mod.
- **No mappings on 26.x:** the spike mod compiled with plain `javac` (JDK 26, `--release 25`) against the installed
  `/data/libraries` jars (`scripts/spike/build-testmods.sh`), no ModDevGradle.
- **Configuration phase:** a mod that registers no payload adds nothing the client must answer; the server's own
  configuration ping is the only extra the client must handle. T-M8-06 must keep the agent payload-free.

### The agent (T-M8-06, `platforms/neoforge/agent`)
From `NeoForgeAgentIT` runs on the neoforge@26.3 row (2026-11-14, `scripts/dockerlock.sh`; the PR lists each run):
- **Build:** plain `javac` on a Java 25 toolchain, `--release 25`, against the vanilla 26.3 server jar (the Fabric agent's
  pin) and NeoForge `26.3.0.103` `universal`, FancyModLoader `loader` 12.0.12, `bus` 8.0.5, `mergetool` 2.0.7 `api` (the
  `Dist` enum) and `maven-artifact` 3.10.0 (the type of `IModInfo.getVersion()`), each checked by its catalog sha256.
  **ModDevGradle is not used:** its NeoForm pipeline fetches the game and its libraries at build time outside the catalog,
  each of which would need its own sha256 pin. Not run. ADR-0017 section 5 allows the `javac` fallback.
- **Mod:** `@Mod(value = "proofstone_agent", dist = Dist.DEDICATED_SERVER)` (a NeoForge mod id allows no `-`), one
  `[[mods]]` entry and no `[[dependencies]]`. FML listed it as `Proofstone Agent 0.1.0 (proofstone_agent)` from
  `jar(mods/proofstone-agent.jar)`.
- **Configuration phase:** the agent registers no payload, no channel and nothing on the mod bus, so it asks nothing of the
  client. In each counted run the `mcpl` driver (a vanilla 777 client, no mod) completed configuration and logged in
  (`Alex ... logged in`, 1 of 1 per run, 3 of 3 in all) with the agent loaded, and `/v1/mods` listed it at that time.
- **Ready:** the HTTP server starts in `ServerStartedEvent` (game bus, `LOWEST`), so `health` answers once the server logged
  `Done (`. Each counted run was a cold start (`Running NeoForge 26.3.0.103 installer`), about 17 s from the first image
  log line to `Done`; the test class took 21.0 to 21.3 s (XML `time`). ERROR lines in each run were the two known ones
  (`No key layers in MapLike[{}]`, `Appender DebugFile`).
- **Server thread:** the login probe on `PlayerEvent$PlayerLoggedInEvent` recorded thread `Server thread`; every game
  endpoint runs there through `MinecraftServer.execute`.
- **Events:** a probe names a concrete NeoForge event class (binary name, `$` for nested). It listens at `LOWEST` with
  cancelled events included, and records the zero-arg `get*` getters by name, as the Bukkit agent does:
  `getEntity` of the login event is `{name: "Alex"}`. NeoForge's bus has no MONITOR priority: a listener another mod adds
  at `LOWEST` after the probe runs after it. `DELETE` unregisters the probe's listener.
- **`/v1/mods`** lists the mods whose jar is in `/data/mods`: only `proofstone_agent` on the row. `minecraft` and
  `neoforge` are FML mods too, loaded from the libraries, and are left out, as Fabric leaves out its loader.

### The loader host (T-M8-07, `LoaderHost` in `proofstone-core`)
From `NeoForgeLoaderHostIT` runs on the neoforge@26.3 row (2026-11-14, `scripts/dockerlock.sh`; the PR lists each run):
- **Boots:** the installer, the agent and the example mod (`exampleModJar`, built from
  `platforms/neoforge/agent/src/exampleMod`) are copied in; `health` and `/v1/mods` (`proofstone_example`,
  `proofstone_agent`) are read. Three separate cold starts: **19.7 s, 19.5 s, 18.9 s** from container start to `Done (`
  (the host's measured boot time, install included), 19.4 to 20.5 s to ready. Host findings, never a join timeout. The boot
  timeout stays at 5 minutes; it was not raised.
- **Vanilla jar:** `/data/libraries/net/minecraft/server/26.3/server-26.3.jar` has sha256
  `d052f14d7a173734fba553711e5b570162e2f2a313267ee31a21b975a679be64`, the same bytes as Fabric's (sha1 `33680f5f…`, Mojang's
  published value). It is the row's `vanilla-sha256`, checked in the container after the install and before readiness.
- **Failed start is immediate:** the example mod's constructor throws when its config says `fail=true`. The container
  exited, and the host failed the cell with the log tail after 17.9 s and 17.3 s (two runs), not the boot timeout, no
  container left. Both times it was the exit that ended the wait: the host did not read `Failed to start FML` for a mod
  that throws, a line T-M8-06 saw for the invalid module descriptor. Both signals stay in the host.
- **Install hosts (ADR-0017 section 6):** measured as on Fabric (`scripts/spike/install-hosts.sh`, an `--internal` network
  with a logging proxy as the only way out). The cold start (18 s, reached `Done`) connected to exactly:
  `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `maven.neoforged.net`,
  `mirrors.neoforged.net` and `neoforged.net`. One start was measured; a new host is a finding and changes
  `LoaderEnvironmentTest`. The harness JVM itself downloads the installer from `maven.neoforged.net`.
- **Log guard:** the `neoforge` pack lists `No key layers in MapLike` and `An exception occurred processing Appender
  DebugFile` (log4j's DebugFile appender probes netty's macOS kqueue transport on Linux), each with its reason. The second
  one is log4j's status logger writing straight to the container's stderr, so the agent's log capture may never see it.

### Conformance C01 to C08 (T-M8-08, `:conformance:run -Dproofstone.cells=neoforge@26.3`)
Three separate invocations on 2026-11-14 under `scripts/dockerlock.sh`, each green: 9 tests (C01, C02, C03 to C08 and the
window quirk test), `skipped="0" failures="0" errors="0"` in every XML, 0 join timeouts, 0 boot timeouts, 0 `PSConf moved too
quickly`. Boot times, container start to `Done (`, install included, one boot per test class (8 per run): 16.7 to 18.1 s (24 boots).
Quirks, each asserted as it is (never a weakened assertion):
- **`/help` is vanilla's own.** C02 reads `/help [<command>]` (one system message per command usage); Paper prints a `Help`
  header. `ConformanceSmokeTest` branches on the platform, each side asserting its real reply.
- **A player-state read with a window open is `501 NOT_SUPPORTED`** (the server keeps no window title).
  `ConformanceLoaderWindowTest` asserts the refusal while a chest is open and the read again once it is closed. C04 to C08 read
  the player only after closing the window, so they run unchanged.
- **Capabilities:** the loader pack holds `CONSOLE` (agent token `command`, verified by C03 and the per-cell check). Not
  `BUKKIT_EVENTS`: the agent's `events` token is a loader probe, not a Bukkit listener. No scenario was skipped.

## Capabilities
Observed in the spike, not yet tokens:
- **Data packs (vanilla):** the same pack as on Fabric loaded (`Found new data pack file/spike-datapack.zip, loading it
  automatically`); `function spike:hello` gave the driver `spike function ran` (5 of 5 joins) and `recipe give` logged
  `Unlocked 1 recipe(s) for Spike1`. NeoForge also logged `Loaded 0 recipe priority overrides` (its own recipe layer).
- **T-M8-09:** the NeoForge agent does not serve `GET /v1/datapacks` and the NeoForge pack does not declare `DATAPACKS_VANILLA`, so
  C-D01 and C-D02 skip on `neoforge@26.3` with `missing capability DATAPACKS_VANILLA` (a skip, never a pass). A later card may add it.
- **Resource packs:** not run on NeoForge (run on Fabric and the vanilla path; see [fabric.md](fabric.md)).

## Gotchas
- **A mod that throws at construction ends the container, with no `Failed to start FML` line seen** (T-M8-11, `metaModCrashNeoForge`,
  dev run). FML logs `[modloading-worker-0/ERROR] [ne.ne.fm.ja.FMLModContainer/LOADING]: Failed to create mod instance.
  ModID: <id>, class <mod class>` and the exception's message, then the container exits. The host fails on the exit (not the
  boot timeout), and the first line names the mod, so the test is `INVALID` naming it.
- **FML refuses a mod jar whose `META-INF/services` file names a class it does not hold** (T-M8-06, first dev run): FML
  reads every mod jar as an automatic module, and the shaded agent kept Jackson's `META-INF/services/com.fasterxml.jackson.core.JsonFactory`
  after relocating the class. The boot failed before any mod loaded: `Failed to start FML:
  java.lang.module.InvalidModuleDescriptorException: Service provider file com.fasterxml.jackson.core.JsonFactory contains
  service that is not in this Jar file`. The test then waited the full 5 min boot timeout, because waiting for `Done (`
  does not notice the exit. Fix: `mergeServiceFiles()` in the shadow jar relocates the files with the classes. Fabric's
  loader does not check this.
- **A configuration ping must be answered** (`ClientboundPingPacket(id=0)`), as on Fabric with Fabric API.
- **Boot ERROR lines on every run:** `No key layers in MapLike[{}]` (flat world, as on Paper and Fabric) and
  `Server thread ERROR An exception occurred processing Appender DebugFile` with `NoClassDefFoundError: Could not
  initialize class io.netty.channel.kqueue.Native` (a log4j appender in NeoForge's logging configuration probing netty's macOS
  transport on Linux). A log-guard list for NeoForge must account for both, each with a reason (T-M8-07). With the
  network disconnected, log4j adds `main ERROR Could not determine local host name` three times.
- **The agent's HTTP threads must be daemon threads:** on Fabric a mod that opened its port and then failed kept the
  JVM alive after `Failed to start the minecraft server` ([fabric.md](fabric.md), Agent notes). Not reproduced on NeoForge;
  the agent's threads are daemons (`NeoForgeAgentStartTest`).
- **`System.out` from a mod is not routed through log4j** on NeoForge (no timestamp or thread prefix in the container
  log), unlike Fabric's `[STDOUT]` wrapping. Log-line ordering against `Done` is therefore not reliable for stdout.
- Java 25 prints `WARNING: A restricted method in java.lang.System has been called` (JNA) and `sun.misc.Unsafe` warnings at
  start; not errors.
