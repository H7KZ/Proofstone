# Fabric

Status: **spike** (T-M8-01, 2026-11-14). No pin is in `versions.yml` yet (ADR-0017, T-M8-03/04 decide). Every fact below
comes from a local run under `scripts/dockerlock.sh` with `scripts/spike/mod-spike.sh` (joins by
`scripts/spike/ModJoin.java` on the real `mcpl` driver), unless it says otherwise. Run outputs were kept under
`build/spike/{A,B,C,D,E}/` (not committed). See [platforms.md](../platforms.md) and [neoforge.md](neoforge.md).

## Reproduce (Git Bash, from the repo root)
```bash
# Pinned inputs into build/spike/cache (sha256 checked by mod-spike.sh):
mkdir -p build/spike/cache && cd build/spike/cache
curl -sSfL -o fabric-server-mc.26.3-loader.0.20.1-launcher.1.1.2.jar https://meta.fabricmc.net/v2/versions/loader/26.3/0.20.1/1.1.2/server/jar
curl -sSfL -o fabric-api-0.166.2+26.3.jar "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.166.2%2B26.3/fabric-api-0.166.2%2B26.3.jar"
curl -sSfL -O https://maven.neoforged.net/releases/net/neoforged/neoforge/26.3.0.103/neoforge-26.3.0.103-installer.jar
cd ../../..
# A bare Fabric 26.3 server, probe plus 5 counted joins (EXPORT=1 keeps the compile classpath for the test mods):
PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 SPIKE_PROBE=1 EXPORT=1 JAVA_HOME="C:/Program Files/Java/jdk-21" \
  SPIKE_OUT=build/spike/A/fabric-none bash scripts/dockerlock.sh bash scripts/spike/mod-spike.sh fabric 26.3 5
# Test mods, data pack and resource pack (after the same with neoforge into build/spike/A/neoforge-none):
SPIKE_JDK="C:/Program Files/Java/jdk-26" bash scripts/spike/build-testmods.sh build/spike/A/fabric-none/export \
  build/spike/A/neoforge-none/export build/spike/cache/fabric-api-0.166.2+26.3.jar
# Then, for example, the content-mod refusal:
PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 SPIKE_PROBE=1 CONTENT=1 JAVA_HOME="C:/Program Files/Java/jdk-21" \
  MODS="build/spike/testmods/proofstone-spike-fabric.jar build/spike/cache/fabric-api-0.166.2+26.3.jar" \
  bash scripts/dockerlock.sh bash scripts/spike/mod-spike.sh fabric 26.3 3
```
Other runs: `DATAPACK=build/spike/testmods/spike-datapack.zip` with the steps `"console:function spike:hello=>spike function
ran" "console:recipe give {player} spike:dirt_to_diamond" "console:datapack list enabled"`; `PACK=build/spike/testmods/spike-resourcepack.zip
SPIKE_PACK_ANSWER=accept|decline`; `OFFLINE=1`; `NO_NETWORK=1`; `fabric 1.21.11`. The container is `ps-spike-mod`, removed
on exit.

## Host
- **Image:** `itzg/minecraft-server`, the `java25` digest `versions.yml` already pins for Paper 26.x
  (`sha256:e2fabc536673f8c6b343055c93f7a73e10caa79cd3368f645987593c65adf1e0`, image build 2026-11-02, mc-image-helper
  1.75.0, Java 25.0.5). No new image was pulled. `TYPE=FABRIC`.
- **Java tag: `java25`** for 26.x (the game's `version.json` says `java_version: 25`). The 1.21.11 run used the `java21`
  digest `sha256:04b5ee189f5f2c1992fa557e366cf2e4d09fe2abfbd57d2d1f977d8c038c1b98`.
- **Pinnable jar: the Fabric server launcher**, one jar per (Minecraft, loader, installer) triple, from
  `https://meta.fabricmc.net/v2/versions/loader/<mc>/<loader>/<installer>/server/jar`. Spike pins:

  | Row | Loader | Installer | sha256 | Bytes |
  |---|---|---|---|---|
  | 26.3 | 0.20.1 | 1.1.2 | `70446947655b4f8f286aba896aa4c19a255e8ca8a730636ef2a6017ae4415d95` | 181,838 |
  | 1.21.11 | 0.20.1 | 1.1.2 | `2b06e5e0719199ba100659a283626d9b055e5fded1cb87fa1cdf4df571c0754d` | 181,841 |

  The meta service publishes no hash; the sha256 is computed from the download. Two downloads of the 26.3 jar a few
  minutes apart gave the same sha256 (the jar is generated but stable, `Cache-Control: max-age=86400`). The jar holds only
  the launcher classes and `install.properties` (`fabric-loader-version=0.20.1`, `game-version=26.3`).
- **How the image takes it:** the jar is copied (`docker cp`, no bind mount) to `/pins/` and passed as
  `FABRIC_LAUNCHER=/pins/<jar>`. The image then runs it as the server jar (`SERVER="/pins/..."` in
  `/data/.install-fabric.env`) and does not download a launcher itself.
- **What is fetched at first start (the launcher, not the image):**
  1. `https://launchermeta.mojang.com/mc/game/version_manifest_v2.json`, then the vanilla server jar ("Downloading
     Minecraft server"). Shown by a first start with `--network none` (`NO_NETWORK=1`): `UnknownHostException:
     launchermeta.mojang.com`, server not started. The vanilla jar is stored as `/data/.fabric/server/26.3-server.jar`;
     its sha1 `33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c` equals Mojang's 26.3 manifest entry (62,294,556 bytes).
  2. Loader libraries ("Downloading library ..."): `org.ow2.asm` asm, asm-analysis, asm-commons, asm-tree, asm-util
     9.10.1, `net.fabricmc:sponge-mixin:0.17.5+mixin.0.8.7`, `net.fabricmc:fabric-loader:0.20.1`. The log does not
     print the host; it was not checked which maven each comes from.
  3. The game libraries are unpacked from the vanilla bundler (no download). 50 jars under `/data` after a bare install
     (list with sha256 in each run's `data-jars.txt`).
- **Can it be pinned or pre-fetched?** The launcher: yes (sha256 above). What it fetches: not by the launcher itself
  (no hash pin for the libraries). **Once installed, a restart needs no network:** the same container restarted with its
  network disconnected (`OFFLINE=1`, with Fabric API) reached `Done` in 12 s. So T-M8-07 can pre-fetch by installing
  once and copying the installed tree, or by an image per row; a cold start always reaches Mojang. Not run: placing the
  vanilla jar in `/data` before first start to skip step 1. **Decided (ADR-0017 §6):** the cold-start fetch stays, as a
  listed exception pinned by content (`vanilla-sha256` checked in the container); no install cache, no new image.
- **Ready:** the log line `Done (0.351s)! For help, type "help"`. First start including install took 13 to 16 s
  (bare, with Fabric API, with the spike mod); Paper 26.3 in the same runs took 23 s.
- **Image environment the spike set:** `ONLINE_MODE=FALSE`, `ENFORCE_SECURE_PROFILE=FALSE`, `LEVEL_TYPE=minecraft:flat`,
  `ENABLE_RCON=false`, `CREATE_CONSOLE_IN_PIPE=true` (console commands through `mc-send-to-console`), `VERSION=26.3`.

### Protocol 777 (26.3) client: verdict
- **Fabric 0.20.1 on 26.3 takes the `mcpl` 777 client natively, with no mod, with Fabric API 0.166.2+26.3, with a
  server-only mod, and with both.** 20 of 20 driver joins on those four mod sets succeeded (table below), 0 join
  timeouts. **It does not join once a mod adds a synced registry entry** (Content mods, below). The
  configuration phase the driver sees on a bare server is vanilla's: `minecraft:brand` (`fabric`), enabled features
  `minecraft:vanilla`, known packs `minecraft:core 26.3`, registries, tags, finish.
- **With Fabric API** the server first sends `minecraft:register` with `c:version`, `fabric:recipe_sync/supported_serializers`,
  `fabric:accepted_attachments_v1`, `c:register`, `fabric:custom_ingredient_sync`, `fabric:registry/sync/complete`, then a
  **`ClientboundPingPacket` it waits on**, then the vanilla tasks. Known packs add `vanilla:fabric-convention-tags-v2`.
  The driver answers the ping (it does so since T-M3-03) and the join completes. A raw MCProtocolLib client that does not
  answer it stalls in configuration (phase A probe, see Gotchas).
- **Content mods (a mod that registers an item): refused** (Fabric API registry sync); see "Content mods" below.
- **1.21.11 is not native:** `could not join Spike1 -> 127.0.0.1:21016: multiplayer.disconnect.incompatible 1.21.11`, 2 of 2
  driver joins and the probe (protocol 777 offered). A 1.21.11 Fabric row would be bridged.

### Join counts (all runs, 2026-11-14)
| Run | Mods | Driver joins ok | Join timeouts | Other refusals | Probe |
|---|---|---|---|---|---|
| A fabric-none | none | 5 of 5 | 0 | 0 | reached play |
| A fabric-api | Fabric API | 5 of 5 | 0 | 0 | stalled after the configuration ping (probe did not answer pings; fixed) |
| B fabric-spikemod | spike mod | 5 of 5 | 0 | 0 | reached play |
| B fabric-api-spikemod-dp | spike mod, Fabric API, data pack | 5 of 5 | 0 | 0 | reached play |
| B fabric-rp-accept | required resource pack | 0 of 2 | **2** (`CONFIGURATION_STALLED`) | 0 | accepted, fetched, loaded, reached play |
| B fabric-rp-decline | required resource pack | not run | 0 | 0 | declined, kicked |
| B fabric-1.21.11 | none, 1.21.11 | 0 of 2 | 0 | 2 (`incompatible 1.21.11`) | refused |
| C fabric-rp-prompt | required pack, JSON prompt | not run | 0 | 0 | prompt seen; pack server absent (script bug), sent `FAILED_DOWNLOAD`, reached play |
| C three content runs | | not run (script bug, `PACK: unbound variable`; rerun as E) | 0 | 0 | not run |
| E fabric-rp-prompt | required pack, JSON prompt | 0 of 1 | **1** (`CONFIGURATION_STALLED`) | 0 | prompt seen, accepted, fetched, reached play |
| E fabric-api-content | spike mod registering an item, Fabric API | 0 of 3 | 0 | 3 (kicked, Fabric API) | kicked |
| E fabric-content-noapi | spike mod registering an item, no Fabric API | boot failed (see Gotchas) | 0 | 0 | not run |

**Totals: 28 counted driver joins on Fabric, 20 ok, 3 join timeouts, 5 refused.** The 3 join timeouts are all
`CONFIGURATION_STALLED` on a required resource pack the driver does not answer (expected, see Capabilities); none on
any mod set. The 5 refusals are 2 version refusals on 1.21.11 and 3 Fabric API registry-sync kicks. 10 probe
connections are counted apart. The spike mod jar was rebuilt between phase B and E (the item registration was added
behind `PROOFSTONE_SPIKE_CONTENT=1`); without that variable it behaves the same.

### Content mods: the vanilla client cannot join
**A Fabric server with Fabric API and a mod that adds a registry entry refuses the vanilla `mcpl` client.** With the spike
mod registering one item (`proofstone_spike:probe_item`) and Fabric API loaded, 3 of 3 driver joins and the probe were
kicked in configuration, right after the ping:
`could not join Spike1 -> 127.0.0.1:19317: This server requires Fabric Loader and Fabric API installed on your client!
The following registry entry namespaces may be related: proofstone_spike Contact the server's administrator for more
information!` (Fabric API's registry sync). Without Fabric API the loader alone cannot register the item at all (the
boot fails, Gotchas), so on Fabric **any mod that adds blocks, items or other synced registry entries is out of reach of
the vanilla driver.** What the driver can test on Fabric: server-only mods (commands, events, data, network-free
behaviour) with or without Fabric API, and data packs. A content mod needs a modded client: a separate ADR (a Fabric
client driver), not M8 as planned.

### Stonecutter: out for M8
Decision: **M8 is newest-row only; Stonecutter (a multi-version agent build) is not used.** The runs behind it:
- The `mcpl` driver joins only 26.3 natively; Fabric on 1.21.11 refused it (`multiplayer.disconnect.incompatible 1.21.11`,
  2 of 2). A second row would be bridged, and bridged mod rows are out of scope until a run shows ViaProxy in front of a
  modded server works (M8 context). That run was **not** made in this spike.
- A 26.3 mod jar does not load on the 1.21.11 row even when its code touches no game class (Java 25 bytecode on Java 21,
  Gotchas), and 1.21.11 is obfuscated (it needs intermediary mappings and Loom), so a second row means a second build
  per version, which is what Stonecutter is for. With one native row there is nothing to multiplex.
- 26.x needs no mappings, and the spike mods compiled with `javac` alone. Whether Loom is still wanted for the 26.3
  agent (run configs, remapping of dependencies) is T-M8-05's call, not a Stonecutter question.
Revisit when the driver gains a second native row or a bridged mod row is shown to work.

## Agent notes
- **Entrypoint:** `fabric.mod.json` with `"environment": "server"` and `"entrypoints": {"server": [...]}` implementing
  `net.fabricmc.api.DedicatedServerModInitializer`. It runs on the `main` thread before the server starts, so an HTTP
  port can open there: `[proofstone-spike] fabric mod init, HTTP on port 25590 (thread main)`, and `/health` answered
  from the host through the published port.
- **Earliest "server ready" event:** Fabric API's `ServerLifecycleEvents.SERVER_STARTED` (module
  `fabric-lifecycle-events-v1`), on the `Server thread`, logged right after `Done (...)` and 1,656 ms after mod init;
  `SERVER_STARTING` came 962 ms after init, before `Done`. **The loader alone has no such event:** without Fabric API
  the spike mod logged `fabric-lifecycle-events-v1 absent: no server-started event` and `/health` stayed `ready:false`.
  So the agent either requires Fabric API on the server (a server-side dependency, still no client mod) or uses a
  mixin into the server's start, which T-M8-05 must decide; the observe-only rule forbids a mixin that changes
  behaviour, not one that only observes. **Decided ([ADR-0017](../adr/0017-mod-platform-packs.md) §5): every Fabric row
  carries the pinned Fabric API and the agent uses `SERVER_STARTED`; no mixin.**
- **No mappings on 26.x:** Fabric's intermediary for 26.3 is `0.0.0` (meta), and the loader logs `Mappings not present!`.
  The spike mod compiled with plain `javac` against the vanilla jar extracted from the bundler, the loader jar and Fabric
  API's nested `fabric-lifecycle-events-v1-4.1.9` jar (`scripts/spike/build-testmods.sh`), no Loom.
- **The HTTP server must not keep a failed JVM alive.** In the E content run without Fabric API, the spike mod opened its
  port and then threw; the log said `Failed to start the minecraft server`, but the `HttpServer`'s non-daemon thread kept
  the JVM and the container running until the spike's 600 s boot timeout. The agent's HTTP threads must be daemon threads
  (or stopped on a failed start), and the host must treat that log line as a failed boot.
- **Bytecode: Java 25.** Fabric API 0.166.2+26.3 classes are class file 69; compiling against them needs a JDK 25 or
  newer (JDK 21 `javac` fails: `class file has wrong version 69.0, should be 65.0`). The spike used JDK 26 `--release 25`.
- The test mods are spike code under `scripts/spike/testmod/`, not the agent.

## Capabilities
Observed in the spike, not yet tokens:
- **Data packs (vanilla):** a pack copied in with the image's `DATAPACKS` (to `world/datapacks`) loaded on Fabric
  (`Found new data pack file/spike-datapack.zip, loading it automatically`). Per join, from the console: `function
  spike:hello` gave the driver the chat line `spike function ran` (5 of 5); `recipe give <player> spike:dirt_to_diamond`
  logged `Unlocked 1 recipe(s) for Spike1`; `datapack list enabled` logged `[vanilla (built-in)],
  [fabric-convention-tags-v2 (Fabric mod)], [file/spike-datapack.zip (world)]`. Pack format for 26.3 is data 121.0 and
  resource 97.1 (`version.json` `pack_version`), written as `min_format`/`max_format`.
  **The same pack on Paper 26.3** (build 244, the pinned row) behaved the same: `spike function ran` on the driver,
  `Unlocked 1 recipe(s) for Spike1`, enabled list `[vanilla (built-in)], [file/spike-datapack.zip (world)], [paper
  (built-in)]` (runs C and E; 2 of 4 Paper joins were refused `Connection throttled! Please wait before reconnecting.`
  because the spike did not set `bukkit.yml` `connection-throttle`, which the Paper host does; not join timeouts).
- **Resource packs:** `server.properties` `resource-pack`, `resource-pack-sha1`, `require-resource-pack=true` and
  `resource-pack-prompt` (a JSON text component) send, in the **configuration phase**, `ClientboundResourcePackPushPacket(id,
  url, hash (sha1), required=true, prompt)`. The id is derived from the URL when `resource-pack-id` is unset (the server
  warns). Served from the harness JVM on `127.0.0.1:28765`, the pack was fetched only by the probe (one `PACK HTTP GET`),
  never by the server or the driver. So the pack source only has to be reachable from the harness host: the harness JVM
  on loopback was enough, and no static-file container was needed (input for ADR-0017).
  - Accept (`ACCEPTED`, `DOWNLOADED`, `SUCCESSFULLY_LOADED`, sha1 checked): configuration finished and the client
    reached play.
  - Decline (`DECLINED`): kicked in configuration with `multiplayer.requiredTexturePrompt.disconnect`.
  - No answer (the driver today): the server waits; the join times out at `CONFIGURATION_STALLED` with keep-alives
    flowing (3 of 3 driver joins in B and E). T-M8-10 must answer it.
  - `FAILED_DOWNLOAD` (once, run C, the pack server was not up): the server still finished configuration and let the
    client into play within 0.2 s. One observation only; not explained.
- **RCON:** not used (`ENABLE_RCON=false`). The console pipe worked for every command.

## Gotchas
- **A modded server's configuration ping must be answered.** Fabric API sends `ClientboundPingPacket` in configuration and
  waits. The `mcpl` driver answers it; a client that does not stalls with only keep-alives (phase A probe, 30 s).
- **`resource-pack-prompt` must be a text component.** A plain string is dropped: `Failed to parse resource pack prompt
  'Proofstone spike pack'` (WARN) and the push carries `prompt=null`. `{"text":"..."}` works.
- **Mods for 26.x are Java 25 bytecode.** A 26.3-built jar on the 1.21.11 (`java21`) row fails the boot:
  `UnsupportedClassVersionError: proofstone/spike/SpikeFabric ... class file version 69.0 ... up to 65.0` inside
  `Could not execute entrypoint stage 'server'`. So one agent jar cannot span 26.x and 1.21.x even when the code is
  version-neutral.
- **The loader alone cannot register an item.** Without Fabric API, `Registry.register(BuiltInRegistries.ITEM, ...)` in
  the server entrypoint failed the boot: `IllegalStateException: This registry can't create intrusive holders`.
- **Boot ERROR lines:** `No key layers in MapLike[{}]` (flat world without generator settings, the same line Paper's
  allowlist holds) on every Fabric run. With the network disconnected, log4j adds `main ERROR Could not determine local
  host name` (offline restart only).
- **SLF4J warning in the client JVM** (`No SLF4J providers were found`) is the spike client's, not the server's.
- The vanilla jar Fabric downloads lives in `/data/.fabric/server/`, the unpacked game jar in `/data/versions/26.3/`.
