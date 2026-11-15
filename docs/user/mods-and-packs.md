# Mods, data packs and resource packs

**Status: experimental.** The Fabric and NeoForge rows (`fabric@26.3`, `neoforge@26.3`) and the data-pack and resource-pack
support came with M8. No loader row is `required` yet ([ADR-0015](../adr/0015-local-evidence-row-promotion.md) is not applied
to them), so a loader cell runs only when you name it with `-Dproofstone.cells`. Design:
[ADR-0017](../adr/0017-mod-platform-packs.md). Everything below was run unless it says it was not. The run counts are in the
[ROADMAP, "M8 findings"](../plan/ROADMAP.md).

## What exists today, and what does not

| You want to test | How a test declares it | Cells it runs on | Status |
|---|---|---|---|
| A data pack (functions, recipes, tags) | `@Proofstone(datapacks = "...")` | `paper@26.3`, `fabric@26.3`. `neoforge@26.3` skips: it lacks `DATAPACKS_VANILLA`. | Run: C-D01 and C-D02 |
| A server-sent resource pack (prompt, answer, hash) | `@ServerResourcePack`, `@ResourcePackAnswer`, `Client.resourcePacks()` | `paper@26.3` only | Run: C-R01 and C-R02 |
| A server-only mod (commands, events, data) | `@Proofstone(mods = "...")` | `fabric@26.3`, `neoforge@26.3` | Run: the example mod through `examples/loader-mod`; the crash-at-load bad test |
| A content mod (adds items, blocks, entities) | not possible with the vanilla driver | none | Out of scope for M8 |

### The mod under test: `@Proofstone(mods)`

```java
@Proofstone(mods = "build/libs/my-mod-*.jar")
@Matrix(platform = Platform.FABRIC, versions = {"26.3"})
@Matrix(platform = Platform.NEOFORGE, versions = {"26.3"})
class MyModE2E { ... }
```

- Each glob is relative to the project directory and must match **exactly one** jar. None, or two, is `INVALID test declaration`
  naming the glob. The jar is copied into the server's `/mods` before the server starts.
- Declaring a mod implies the capability `MODS`, which the Fabric and NeoForge cells hold. A cell without it (every Paper-family
  cell, and a topology) is `SKIPPED` with `missing capability MODS`, never a pass.
- **Loaded or loud.** After boot the harness reads the mod's id from its own descriptor (`fabric.mod.json`,
  `META-INF/neoforge.mods.toml`) and checks the agent lists it (`GET /v1/mods`). A mod the agent does not list is `INVALID`,
  naming the mod, with the agent's list and the log tail. A mod that crashes the loader at load is a failed boot that is
  `INVALID` naming the mod and quoting the loader's line, never a boot or join timeout.
- A jar with the id `fabric-api` is refused (`INVALID test declaration`): the Fabric cell already carries the pinned Fabric API.
  A jar that is neither a Fabric nor a NeoForge mod is refused too.
- A control run installs no declared mod (unit-tested; no control run was run on a loader cell).
- **You cannot give the mod a config file.** The mod runs with the defaults it has on the server. Server-only mods only: a mod
  that adds items, blocks or entities is out of scope (see below).
- The [`examples/loader-mod`](../../examples/loader-mod/README.md) example is one test class on both loader rows, with the
  repository's own example mod built by the loader agent's build. The crash case is the bad test
  `examples/bad-tests` `mod/Broken{Fabric,NeoForge}ModE2E`.

## Loader cells

- Name the cell: `-Dproofstone.cells=fabric@26.3` (or `PROOFSTONE_CELLS`), **one cell per invocation**. Without it the class
  prints `NOT SELECTED (not run, not a pass)` for the loader platform.
- Declare the platform in the test: `@Matrix(platform = Platform.FABRIC, versions = {"26.3"})`. `Platform.NEOFORGE` works the
  same.
- A loader cell uses the native `mcpl` driver only. It does not run bridged, differential (`-Dproofstone.differential`) or
  repeated (`-Dproofstone.repeat`), and `plugins` on a loader cell is an `INVALID test declaration`; use `mods`.
- The mod agent serves the same wire protocol as the Bukkit agent, with fewer capabilities. `Agent.command`, player state, the
  event probe and the log are there. A read of a player's state **while a window is open** is `501 NOT_SUPPORTED` on both
  loaders (see [troubleshooting](troubleshooting.md#loader-cells-player-state-read-with-a-window-open-is-501)).
- Your build needs a **Java 25 toolchain** for the mod agent (a build of the agent, not of your test). Gradle finds it; it does
  not download it.
- **First start needs the network.** A cold start of a loader cell reaches Mojang's CDN from inside the container (see
  [troubleshooting](troubleshooting.md#cold-start-of-a-loader-cell-reaches-mojang)). Later starts reuse what was downloaded.
- Versions: `26.3` only. Fabric on 1.21.11 refuses the 777 client, so there is no second row.

## Data packs

```java
@Proofstone(datapacks = "src/test/datapacks/example-pack")
@Matrix(platform = Platform.FABRIC, versions = {"26.3"})
class DataPackFunctionE2E { ... }
```

- Each glob matches exactly one `.zip` with `pack.mcmeta` at its top, or one directory that holds `pack.mcmeta`. A directory is
  zipped deterministically for you.
- The pack is copied into the server's world `datapacks` **before the server starts**. Nothing reloads it while a test runs.
  Test what the pack does (run a function, craft a recipe), not a reload.
- Declaring a pack implies the capability `DATAPACKS_VANILLA`. A cell without it is `SKIPPED` with the capability named, never
  a pass. `neoforge@26.3` skips this way today.
- **Loaded or loud.** A declared pack that the server did not enable makes the class's tests `INVALID`, naming the pack
  (`file/<name>`) with the log tail. A pack that loads but logs a WARN or worse line naming one of its namespaces (for example a
  function that does not parse) is `INVALID` as well, with the server's line quoted. Both ran in the bad-test `metaModDataPack`
  run (`paper@26.3`).
- A control run installs no data pack. A topology cell refuses a declared data pack.
- The pack format must match the server. For `26.3` the data format is `121`: the conformance pack's `pack.mcmeta` has
  `"min_format": 121` and `"max_format": 121`.
- Run counts: C-D01 and C-D02 on `paper@26.3` 3 of 3 and on `fabric@26.3` 3 of 3 (fresh set after a test fix; the verifier's
  first set had one red, a world-generation WARN, see
  [troubleshooting](troubleshooting.md#fabric-a-zombie_villager-warn-fails-a-test)).

## Resource packs

```java
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"26.3"})
@ServerResourcePack(value = "src/test/resourcepacks/my-pack.zip", required = true, prompt = "{\"text\":\"Please\"}")
@ResourcePackAnswer(ResourcePackAnswer.Answer.ACCEPT)
class PackE2E {
    @CellTest
    void accepted(Client client) {
        ResourcePackPush push = client.resourcePacks().get(0);
        // push.statuses() is [ACCEPTED, DOWNLOADED, SUCCESSFULLY_LOADED]
    }
}
```

- `@ServerResourcePack(value, required, prompt, sha1)`. `value` is a glob matching exactly one `.zip`. The harness serves the
  file from the test JVM on `127.0.0.1` for the life of the server, and writes the URL, hash and `require-resource-pack` into
  `server.properties`. No port reaches a container. `prompt` must be a JSON text component: a plain string is an
  `INVALID test declaration`, because the server drops it.
- `sha1` is the hash the server pushes. **Leave it empty** (the default, the file's own sha1) unless you are testing a wrong
  hash: a pushed hash that is not the file's makes a client that accepts report `hash mismatch: expected <h>, got <h>`.
- `@ResourcePackAnswer(ACCEPT | DECLINE | FAIL_DOWNLOAD)` on the class or the method (the method wins). **There is no default:**
  a push with no declared answer ends the join at once with an error naming the URL. The driver never accepts silently. The
  push arrives during the join, before the test body, which is why the answer is an annotation and not a call.
- `Client.resourcePacks()` lists the pushes this client saw: `id`, `url`, `hash`, `required`, `prompt`, `answer`, `statuses`,
  `problem`, `popped`. Read it again for later statuses. `problem` is information; the test decides what fails.
- What a vanilla client does, as the driver answers it, on a required pack:

  | Answer | Statuses sent | Result |
  |---|---|---|
  | `ACCEPT`, true hash | `ACCEPTED`, `DOWNLOADED`, `SUCCESSFULLY_LOADED` | Joins. |
  | `ACCEPT`, wrong hash | `ACCEPTED`, `FAILED_DOWNLOAD` | Joins. Paper does **not** kick. `problem` names the mismatch. |
  | `DECLINE` | `DECLINED` | Kicked: `multiplayer.requiredTexturePrompt.disconnect`. |
  | `FAIL_DOWNLOAD` | `ACCEPTED`, `FAILED_DOWNLOAD` | Not downloaded. Covered by the driver unit test only; no real-server run. |

- The capability is `RESOURCE_PACKS`: native `paper@26.3` only. It is not held on a bridged cell, a topology or a loader row.
  A test that declares a pack there is `SKIPPED` with the capability named.
- **No rendering.** The harness checks the prompt, the hash, the download and the status the client sends. It does not unpack a
  texture or check what the pack looks like.
- **The server's view.** Paper 26.3 fires no `PlayerResourcePackStatusEvent` for the configuration-phase push that
  `server.properties` makes (see [troubleshooting](troubleshooting.md#paper-fires-no-resource-pack-status-event-in-the-configuration-phase)).
  To read the server's side, have a plugin push the pack in play, as the conformance fixture does. For the configuration-phase
  push, the client's record and the outcome of the join are the evidence.
- Run counts: C-R01 and C-R02 on `paper@26.3`, 3 of 3 green.

## A data pack on `fabric@26.3`

[`examples/loader-datapack`](../../examples/loader-datapack/README.md) is one test class on one loader row. It uses only what the
M8 conformance ran (C-D01):

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:loader-datapack:loaderExample \
  -Dproofstone.cells=fabric@26.3 --no-parallel --max-workers=1
```

Read <https://aka.ms/MinecraftEULA> first. See the example's README for what it checks and what was run.

## What was not run

- A user mod other than the repository's example mod. The example mod registers nothing, so what a mod does at runtime
  (commands, events) has not been tested through this API.
- A content mod through any driver. Fabric kicks the vanilla client; on NeoForge one added item joined 3 of 3 in the spike and
  nothing more was run.
- Resource packs on Fabric, NeoForge, a bridged cell or a topology.
- Data packs on `neoforge@26.3` (skipped), and any loader row other than `26.3`.
- Any flake rate for a loader row beyond the counted runs in the ROADMAP.
