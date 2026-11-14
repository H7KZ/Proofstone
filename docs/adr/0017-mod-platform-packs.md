# ADR-0017: Mod platform packs, artefact types and loader rows

- Status: Accepted
- Date: 2026-11-14
- Deciders: maintainer delegated the M8 decisions; decided by the T-M8-03 agent on the orchestrator's decisions (scope,
  Fabric readiness signal, resource-pack source preference, the Mojang question to be decided here). Section 6 (Mojang
  CDN at cold start) is flagged in the PR as the point for the maintainer to veto. Amends nothing; adds to
  [ADR-0008](0008-versions-yml-single-source-of-truth.md), [ADR-0013](0013-host-digest-per-java-tag.md) and
  [ADR-0015](0015-local-evidence-row-promotion.md) for loader rows, as [ADR-0016](0016-topology-cells.md) did for proxies.

## Context
Every fact here comes from the T-M8-01 spike runs ([fabric.md](../platforms/fabric.md), [neoforge.md](../platforms/neoforge.md),
ROADMAP "M8 findings"), all on 2026-11-14 under `scripts/dockerlock.sh`, joins by the real `mcpl` driver:
- **Fabric 0.20.1 on 26.3** (server launcher, installer 1.1.2, sha256 `70446947…`) takes the protocol 777 client natively
  with no mod, Fabric API 0.166.2+26.3, a server-only mod, and both: 20 of 20 driver joins. **A mod that registers one
  item, with Fabric API, gets the vanilla client kicked** in configuration (3 of 3, `This server requires Fabric Loader
  and Fabric API installed on your client!`). Without Fabric API the loader cannot register the item at all (boot fails).
  Fabric on 1.21.11 refuses 777 (2 of 2).
- **NeoForge 26.3.0.103** (installer sha256 `d8a92e17…`, published by the maven) takes the 777 client natively with no
  mod, a server-only mod and a mod adding one item: 13 of 13 driver joins. What the client sees of that item was not run.
- Both run on the `itzg/minecraft-server` `java25` digest that `versions.yml` already pins; no new image was pulled.
  Launcher and installer were copied in (`docker cp`, no bind mount) and passed as `FABRIC_LAUNCHER` / `NEOFORGE_INSTALLER`.
- **A cold start reaches Mojang on both loaders**: Fabric's launcher fetches `launchermeta.mojang.com` and the vanilla
  server jar, NeoForge's installer `piston-meta.mojang.com` and the vanilla jar; both fail with `--network none`. The vanilla
  jar each fetched equals Mojang's manifest entry (sha1, Fabric). Library downloads were seen in the log, their hosts
  were not checked. A restart of an installed container needs no network (12 s Fabric, 11 s NeoForge).
- **Server ready:** Fabric has no such event without Fabric API (`fabric-lifecycle-events-v1` `SERVER_STARTED`); NeoForge has
  `ServerStartedEvent` itself. A Fabric spike mod's non-daemon HTTP thread kept a failed JVM alive for 600 s.
- **Configuration phase:** both loaders send a `ClientboundPingPacket` and wait; `mcpl` answers it since T-M3-03.
- **Data packs:** one pack (a function and a recipe) loaded and worked on Fabric, NeoForge and Paper 26.3 (build 244).
- **Resource packs:** `server.properties` pushes the pack in the configuration phase. Served from the harness JVM on
  `127.0.0.1`, it was fetched only by the client side, never by the server. Accept reached play, decline was kicked, no
  answer stalled the join (`CONFIGURATION_STALLED`, 3 of 3, the only join timeouts of the spike). Run on Fabric only.
- **Stonecutter:** one native row (26.3); 26.3 mod bytecode is Java 25 and 1.21.11 is obfuscated, so a second row needs a
  second build and a bridge no run has shown. 26.x is unobfuscated: the spike mods compiled with plain `javac`.
- The capability `DATAPACKS_VANILLA` was removed in the M5 review because no agent token reported it
  ([test-api.md](../specs/test-api.md) §10).

## Decision

### 1. Scope of M8
- **In:** server-only mods (commands, events, data, behaviour with no synced registry entry) on Fabric and NeoForge, with or
  without Fabric API on Fabric; vanilla data packs; server-sent resource packs (prompt, answer, status, hash; no rendering).
- **Fabric content mods are out:** a mod that adds a synced registry entry on Fabric shuts out the vanilla driver. Such a
  test fails at join with the server's kick message quoted (a loud red, never a skip that hides it); the user guide says so.
- **NeoForge content mods are `unverified`:** the client joined with one added item. Nothing about content is promised: the
  driver's item table is vanilla's, so a mod-added item id reaching the client is an error naming the id (fail loudly on
  unknowns), never read as air or as a vanilla item. Dual assertions on mod-added content are not supported in M8.
- **Newest native row only:** one row per loader, `26.3`. **Stonecutter is out** (T-M8-01). Bridged loader rows are refused by
  the planner with an error naming this ADR, never a skip and never a silent bridge, until a run shows ViaProxy in front
  of a modded server works.

### 2. Loader cells and identities
- A **loader cell** is a single-server cell on a loader row: identity `fabric@<mc>` or `neoforge@<mc>` (`fabric@26.3`), used
  unchanged for `-Dproofstone.cells=`, `PROOFSTONE_CELLS`, `printMatrix`, the report, `cell.json` and the bundle directory.
  It never contains `+`, so it cannot collide with a topology identity.
- `Platform` gains `FABRIC` and `NEOFORGE` (additive, `api-change`, in the first card that plans a loader cell from the API).
- Loader cells start through their own host type, `LoaderHost`, next to `PaperHost`. It reuses what `PaperHost` already has
  (copy with the T-M8-02 ownership, `PermissionCheck`, log tail, readiness) by composition or a pure extraction; Paper and
  topology behaviour, rows and `printMatrix` lines stay identical.

### 3. `versions.yml` schema 4 (additive)
- A new top-level key `loaders`, a sibling of `platforms` and `proxies`. A schema 3 file is a valid schema 4 file, and a
  reader of `platforms` or `proxies` sees no change. A loader id is not also a `platforms` or `proxies` id.
- Loader rows carry `mc`, `loader` (the loader version), `installer` (Fabric: the launcher's installer version; absent on
  NeoForge, where `loader` names the installer), `sha256` (the jar the host copies in: the Fabric server launcher, the
  NeoForge installer), `vanilla-sha256` (section 6), optional `java` (the `java-rules` apply, keyed on `mc`), `protocol`,
  `status`, `native-drivers`, `observed`, `notes`. The Fabric row also carries `fabric-api: {version, sha256}` (section 5).
- **Host:** `itzg/minecraft-server`, by digest per Java tag ([ADR-0013](0013-host-digest-per-java-tag.md)), `TYPE=FABRIC` or
  `TYPE=NEOFORGE`. 26.3 uses the `java25` digest Paper 26.x already pins. **No new image.**
- **Sources:** the harness downloads the launcher (`meta.fabricmc.net/v2/versions/loader/<mc>/<loader>/<installer>/server/jar`),
  the installer (`maven.neoforged.net`) and Fabric API (`maven.fabricmc.net`), checks the sha256 on the host before the copy,
  and copies them in (ADR-0005). A mismatch fails the cell loudly naming the file. Nothing is committed or cached in the
  repo. The Fabric meta service publishes no hash: the pin is computed from the download (stable across two downloads).
- **Rows:** `fabric@26.3` (loader 0.20.1, installer 1.1.2, Fabric API 0.166.2+26.3) and `neoforge@26.3` (26.3.0.103), both
  `experimental` (T-M8-04). A loader row's protocol must equal the driver's (`drivers.mcpl.protocol`), else the file is invalid.
- **Required rows** come only through [ADR-0015](0015-local-evidence-row-promotion.md): C01 to C08 run on the row (skips
  reasoned, section 8), `failures="0" errors="0"`, and the M8 flake rule's three separate green invocations. The promotion
  PR records the table. Promotion is a separate decision, not part of any M8 build card.

### 4. Artefact types and how a test declares each
All additions to API v0 are additive (`api-change`, new annotation elements with defaults, new types, new constants, default
methods); no frozen signature changes.

| Artefact | Declared by | Enters the cell | Needs |
|---|---|---|---|
| Plugin (exists) | `@Proofstone(plugins = {...})` | copied to `/plugins` before start | a Bukkit-family cell |
| Mod | `@Proofstone(mods = {...})` | copied to `/mods` before start | `MODS` |
| Data pack | `@Proofstone(datapacks = {...})` | copied to `<level>/datapacks/` before start | `DATAPACKS_VANILLA` |
| Resource pack | `@ServerResourcePack(value, required, prompt)` on the class | served to the client by the harness (section 7) | `RESOURCE_PACKS` |

- Every path is a glob relative to the project directory with exactly one match, as `plugins` is. A data pack is a `.zip` or
  a directory holding `pack.mcmeta`. Copies use the T-M8-02 ownership.
- `mods` or `datapacks` on a cell without the capability is `SKIPPED` with `CAPABILITY_MISSING`, exactly as an explicit
  `@Requires` (the declaration implies it, as `@Topology.Backends` implies `TOPOLOGY`). `plugins` on a loader cell is
  `INVALID test declaration` naming the platform: `plugins` predates capabilities and no loader loads it.
- **Loaded or loud.** After boot the host checks each declared mod's id (read from `fabric.mod.json` or
  `META-INF/neoforge.mods.toml` on the host) in the agent's mod list, and each declared data pack as `file/<name>` in the
  agent's enabled list (section 9). A declared artefact that did not load makes the class's tests on that cell `INVALID`
  with the artefact named and the log tail; a loader crash at load is a failed boot, also `INVALID` naming the mod. A data
  pack that loads but logs an error (a broken function) is caught by the log guard: `FAIL` with the server's message.
- A test's mod with the id `fabric-api` is refused (`INVALID test declaration`): the Fabric cell provides the pinned one.
- Multiple packs pushed by a plugin under test are recorded by the driver too (section 7); the declared pack is the one
  the server's `server.properties` pushes.

### 5. Mod agents
- `platforms/fabric/agent` and `platforms/neoforge/agent` serve the wire protocol from `platforms/agent-core`, observe and never
  interfere (AGENTS.md section 3): game endpoints on the server thread, listeners registered last or at `LOWEST`, read-only,
  no command registered, no event cancelled, **no behaviour-changing mixin**.
- **Fabric "server ready" is Fabric API's `ServerLifecycleEvents.SERVER_STARTED`.** The Fabric row therefore always carries the
  pinned Fabric API, copied in next to the agent. A Fabric cell without Fabric API is not a row. Observing-only mixins
  are not used either: one pinned server-side mod is less code and less risk than a mixin into the server's start.
- **NeoForge "server ready" is `ServerStartedEvent`** on `NeoForge.EVENT_BUS` at `EventPriority.LOWEST`. The agent registers
  no network payload and declares no client side, so it never makes a client mod necessary.
- **Every agent thread is a daemon thread** (HTTP server and any background thread), so a failed start ends the JVM. The host
  also treats `Failed to start the minecraft server` as a failed boot at once, not after the boot timeout.
- **Build:** a Java 25 toolchain, `--release 25` (Fabric API is class file 69; JDK 21 `javac` fails). Loom for Fabric and
  ModDevGradle for NeoForge, pinned in the version catalog (build-time plugins, approved by T-M8-05 and T-M8-06). If one of
  them cannot build the agent on the pinned setup, the card may compile with plain `javac` against build-time-fetched game
  and loader jars, as the spike did, with the reason in its PR. No mappings on 26.x. Jackson shaded and relocated as in
  the other agents. Gradle itself keeps running on JDK 21.
- Loader log-guard lists start empty. Each known line (`No key layers in MapLike[{}]`, NeoForge's `Appender DebugFile`
  `kqueue` ERROR) is added by T-M8-07 with its reason, never allowlisted to make a red green.

### 6. Mojang CDN at cold start: a listed, content-pinned exception (maintainer may veto)
- **Decision:** a loader cell's first start fetches Mojang's version metadata and the vanilla server jar from Mojang's
  servers, and the loader's libraries from its maven. This is an **explicit, documented exception** to "no Mojang dependency
  in runs", limited to a listed host set and pinned by content:
  - **Hosts:** `launchermeta.mojang.com` (Fabric, seen), `piston-meta.mojang.com` (NeoForge, seen), the host serving the
    vanilla jar, and the loader mavens. T-M8-07 completes the list from a run (the spike did not check the library or jar
    hosts) and writes it into each platform doc; a host not on the list is a finding, not a silent addition.
  - **Pins:** the launcher, installer and Fabric API by sha256 (section 3), and the **vanilla server jar by
    `vanilla-sha256`**, checked in the container after install and before readiness. A mismatch fails the cell loudly.
  - **Never at join or per login:** the exception covers install only. Every loader cell is offline mode; no session-server,
    profile or skin call is allowed (T-M7-10 rule unchanged).
  - **Failure:** a CDN outage or a 4xx/5xx at cold start is a failed boot, `INVALID` with the loader's message, counted as
    a host finding (M8 flake rule 4), never retried.
- **Why this and not an install cache:** an install-once cache only moves the fetch (the first fill reaches Mojang too), adds a
  stateful host-side tree of Mojang binaries outside the repo, invalidation and a copy of about 70 jars per boot, and
  ADR-0016 already deferred host-side jar caches. The content pin gives what the rule protects (identical inputs, a loud
  red on any change) with no new code path. If a CDN outage is ever seen in a run, a cache card reopens this.
- No Mojang binary is committed or redistributed (AGENTS.md section 3): jars live only inside the cell's container.

### 7. Resource packs: harness-JVM source and the driver contract
- **Source: the harness JVM.** A per-cell HTTP server in the harness JVM (the JDK's `com.sun.net.httpserver`, no new
  dependency) bound to `127.0.0.1` on a free port, serving only the declared pack's bytes. The server never fetched the
  pack in the spike; only the client, which runs in the harness JVM, does. So no Testcontainers host-port exposure and **no
  pinned static-file container**. The harness writes `resource-pack` (the loopback URL), `resource-pack-sha1` (computed from
  the bytes), `resource-pack-id` (set, so the server does not derive one and warn), `require-resource-pack` and
  `resource-pack-prompt` (always a JSON text component; a plain string is dropped, fabric.md Gotchas).
- **Answer is declared before the join**, because the push arrives in configuration, before the test body runs:
  `@ResourcePackAnswer(ACCEPT | DECLINE | FAIL_DOWNLOAD)` on the class or method. **No default answer:** a push with no
  declared answer ends the join at once with a `ClientDriverException` naming the URL and "no answer declared", never a
  silent accept and never a `CONFIGURATION_STALLED` timeout.
- The driver contract is in [client-driver.md](../specs/client-driver.md) §2b: every push and pop is recorded, in
  configuration and play; `ACCEPT` downloads **only from the harness source** and checks the sha1; a URL that is not the
  harness source is recorded and may be declined or failed, and `ACCEPT` on it is an error naming the URL. The test reads the
  records through `Client.resourcePacks()` (default method, additive).
- **Server side:** the Bukkit agent reads the server's status through the existing event probe
  (`PlayerResourcePackStatusEvent`, MONITOR, read-only); no new endpoint. Mod agents do not report `RESOURCE_PACKS` in M8
  (no read-only server-side status hook was run).
- `RESOURCE_PACKS` is held on native cells only until a bridged run shows ViaProxy carries the push. A differential run of
  a resource-pack test is refused with an error in M8.

### 8. Capabilities and agent tokens
A cell holds a capability when its pack declares it **and** its agent reports the token (`CapabilityModel`, test-api §10).

| Capability | Token | Reported by | Declared by (M8) | Meaning |
|---|---|---|---|---|
| `MODS` | `mods` | Fabric and NeoForge agents (they serve `GET /v1/mods`) | `fabric`, `neoforge` | the cell loads mods from `/mods` and lists the loaded ones |
| `DATAPACKS_VANILLA` | `datapacks` | Bukkit agent where the server API lists data packs; Fabric agent (T-M8-09) | `paper`, `fabric` | the cell loads vanilla data packs from the world and lists them |
| `RESOURCE_PACKS` | `resource-pack-status` | Bukkit agent (the status event exists) | `paper` (native) | the server pushes a pack and the agent reads the client's status |

- Other packs (Folia, Purpur, Spigot, NeoForge) declare a capability only after a run shows it, by their own card; the token
  alone never makes a cell hold it. Version strings never imply a capability.
- Loader cells run C01 to C08 ([self-tests.md](../specs/self-tests.md) §1c). A scenario that needs a Bukkit-only fixture is
  skipped with the capability named. The data-pack scenarios C-D01 and C-D02 need `DATAPACKS_VANILLA`.

### 9. Wire protocol 1.3 (minimal, carried by T-M8-04)
- **`GET /v1/datapacks`** → `{enabled: [id], available: [id]}`, ids as the server names them (`vanilla`, `file/x.zip`), in the
  server's order. Served by agents that report `datapacks`. Needed because a data pack's load check must not use the
  console (ADR-0002: console output is setup only, never an oracle).
- **`GET /v1/mods`** → `{mods: [{id, version}]}`, sorted by id. Served by mod agents only (token `mods`); the loaded-or-loud check
  needs it.
- No resource-pack field in `players`: the event probe covers the server side (section 7). No change to any existing schema
  or golden except `health/ok` moving to `1.3`. Agents that do not serve a new endpoint list its goldens as pending with the
  reason. The bump is T-M8-04's.

## Consequences
- The Paper, folia, purpur, spigot and topology paths, their rows and their `printMatrix` lines stay as they are; loader cells
  are a parallel host and a new `versions.yml` key.
- M8 tests server-only mods, data packs and resource-pack handshakes. Fabric content mods need a modded client, which is
  out of M8; NeoForge content stays `unverified`.
- Every Fabric cell carries Fabric API, so a mod that conflicts with the pinned Fabric API fails at boot (`INVALID`), and a
  mod needing another Fabric API version is not testable until the row moves.
- A cold loader start depends on Mojang's and the loader's servers being up (section 6). The cost is visible: a failed boot
  with the loader's message, counted as a host finding.
- Two build plugins (Loom, ModDevGradle) and a Java 25 toolchain enter the build for the agent modules only.
- New work is carded: T-M8-04 (schema 4, planner, protocol 1.3), T-M8-05/06 (agents), T-M8-07 (host, host list,
  `vanilla-sha256`), T-M8-08 (conformance), T-M8-09 (data packs), T-M8-10 (resource packs), T-M8-11 (faults).

## Alternatives considered
- **A modded-client driver** (a Fabric client with Fabric API, or a protocol-level fake of Fabric's registry sync): rejected
  for M8. It is a second driver with its own protocol surface and at least two more weeks (M8 estimate). Content mods on
  Fabric need it; a future ADR may add one, with a spike.
- **Stonecutter and a second row** (`1.21.11`): rejected for M8. The only second row is bridged, bridged mod rows have no run,
  and a 26.3 jar cannot load there (Java 25 bytecode).
- **Loader rows inside `platforms`:** rejected. Every reader of `platforms` assumes a Paper-family build and jar; a separate
  key keeps schema 3 readers untouched (the reasoning ADR-0016 used for proxies).
- **A behaviour-free mixin for Fabric readiness instead of Fabric API:** rejected. It is a mixin into the server's start that
  every Minecraft update can break, while Fabric API is one pinned jar the spike ran.
- **An install-once cache tree outside the repo, copied into each container:** rejected for M8 (section 6). Kept as the
  fallback if a CDN outage shows in a run.
- **An image per loader row with the install baked in:** rejected. It is a new image (needs approval), it would contain the
  vanilla jar, and a local-only image breaks the "pinned by digest" rule for anyone else.
- **A pinned static-file container for resource packs:** rejected. The server never fetches the pack, so the harness JVM on
  loopback is enough, and a new image needs approval.
- **Testcontainers host-port exposure for the pack server:** not needed for the same reason; revisit if a server-side fetch
  (a plugin hashing the pack) is ever tested.
- **Answer every pack push with accept by default:** rejected. A silent accept hides a wrong pack, and the card forbids it.
- **Read the data-pack list with `datapack list` on the console:** rejected. Console output is never an oracle (ADR-0002).
- **A resource-pack status field in `players`:** rejected for M8. The Bukkit event probe reads it without a schema change, and
  no mod-side hook was run.
- **NeoForge content mods in scope:** rejected until a run shows a mod-added item reaching the client and both oracles
  reading it.
