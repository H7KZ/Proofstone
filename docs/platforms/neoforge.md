# NeoForge

Status: **spike** (T-M8-01, 2026-11-14). No pin is in `versions.yml` yet (ADR-0017, T-M8-03/04 decide). Every fact below
comes from a local run under `scripts/dockerlock.sh` with `scripts/spike/mod-spike.sh` (joins by
`scripts/spike/ModJoin.java` on the real `mcpl` driver), unless it says otherwise. Run outputs were kept under
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
  Not run: the installer's offline options.
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

## Capabilities
Observed in the spike, not yet tokens:
- **Data packs (vanilla):** the same pack as on Fabric loaded (`Found new data pack file/spike-datapack.zip, loading it
  automatically`); `function spike:hello` gave the driver `spike function ran` (5 of 5 joins) and `recipe give` logged
  `Unlocked 1 recipe(s) for Spike1`. NeoForge also logged `Loaded 0 recipe priority overrides` (its own recipe layer).
- **Resource packs:** not run on NeoForge (run on Fabric and the vanilla path; see [fabric.md](fabric.md)).

## Gotchas
- **A configuration ping must be answered** (`ClientboundPingPacket(id=0)`), as on Fabric with Fabric API.
- **Boot ERROR lines on every run:** `No key layers in MapLike[{}]` (flat world, as on Paper and Fabric) and
  `Server thread ERROR An exception occurred processing Appender DebugFile` with `NoClassDefFoundError: Could not
  initialize class io.netty.channel.kqueue.Native` (a log4j appender in NeoForge's logging configuration probing netty's macOS
  transport on Linux). A log-guard list for NeoForge must account for both, each with a reason (T-M8-07). With the
  network disconnected, log4j adds `main ERROR Could not determine local host name` three times.
- **The agent's HTTP threads must be daemon threads:** on Fabric a mod that opened its port and then failed kept the
  JVM alive after `Failed to start the minecraft server` ([fabric.md](fabric.md), Agent notes). Not reproduced on NeoForge.
- **`System.out` from a mod is not routed through log4j** on NeoForge (no timestamp or thread prefix in the container
  log), unlike Fabric's `[STDOUT]` wrapping. Log-line ordering against `Done` is therefore not reliable for stdout.
- Java 25 prints `WARNING: A restricted method in java.lang.System has been called` (JNA) and `sun.misc.Unsafe` warnings at
  start; not errors.
