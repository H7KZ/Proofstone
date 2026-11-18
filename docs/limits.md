# Limits: what Proofstone does not do

Read this before you build on Proofstone. Each limit says what you will see and what to do. Platform detail is in
[platforms](platforms.md); known failures are in [troubleshooting](user/troubleshooting.md).

## What a test needs

- **Docker with Linux containers.** Without it every test is `SKIPPED (not a pass)` with the reason printed.
- **You accept the Minecraft EULA** (`PROOFSTONE_ACCEPT_EULA=true` in the command environment, or `acceptEula = true` in the test).
  Without it every test is skipped. Proofstone never accepts it for you and never writes it to a file.
- **Network access** to download the pinned server jars at runtime and pull images. Mojang and Microsoft binaries are never
  shipped in the Proofstone artefacts.
- **JDKs:** 17, 21 and 26 as Gradle toolchains for a full build ([compatibility](compatibility.md)).

## Speed and scale

- A cell boots a real server: expect tens of seconds per cell, minutes for Spigot (BuildTools) the first time. Jars and builds are cached.
- **One Docker run at a time on a developer machine.** Parallel Docker Gradle runs break Docker Desktop. Use
  `scripts/dockerlock.sh` and `--no-parallel --max-workers=1` ([troubleshooting](user/troubleshooting.md)).
- A matrix multiplies cells. `@Matrix(versions = {"1.8.8", ..., "latest"})` over four platforms is a long run. Select one cell while
  developing: `-Dproofstone.cells=paper@26.3`.
- Hosted CI runners are slower than a workstation. Start with one cell per PR and the wider matrix on a schedule you own.

## Correctness limits

- **No sleeps and no automatic retries.** A slow cell fails by timeout; it is not retried. Use the await helpers.
- **A known join-timeout flake exists** on `paper@26.3` (3 of 61 recorded runs, cause unexplained, diagnosed at the handshake stage
  since T-M6-14). It surfaces as `INVALID` with the stalled stage, never as a silent pass.
- **The agent observes only.** Tests that must change server behaviour through the agent are out of scope; use the client or
  console commands the test API offers.
- **Two oracles, not three.** The server state (agent) and the client view. Anything neither can see, such as a rendered texture,
  is not checked.

## Platforms and rows

| Area | State |
|---|---|
| Paper, Folia, Purpur, Spigot | `required` rows ([platforms](platforms.md)). Older versions run bridged through ViaProxy. |
| Velocity, BungeeCord topologies | `required` rows. Velocity 3.x and Waterfall are not rows. |
| Fabric, NeoForge (server-only mods) | `experimental`. Newest version row only. |
| **Content mods** | Not supported. A content mod gets the client kicked, because the vanilla driver lacks the registry entries. |
| **Declaring a mod under test** | `@Proofstone(mods = "...")` works for **server-only** mods on `fabric@26.3` and `neoforge@26.3` ([mods guide](user/mods-and-packs.md)). Content mods stay unsupported. |
| Data packs, resource packs | `experimental`. Paper 26.3 fires no resource-pack status event for the configuration-phase push ([troubleshooting](user/troubleshooting.md)). |
| Bedrock through Geyser | `experimental`. An *offline-stub* cell: Xbox login validation is **off** on purpose. Client does join, chat, command, inventory (id and count) and position only; window clicks, `useBlock`, forms and resource packs are not implemented. The driver is pinned to a CloudburstMC Protocol **snapshot**. |
| Bedrock Dedicated Server | Not available (spike deferred). |
| Sponge, PocketMine, others | Not available (see the [roadmap](plan/ROADMAP.md)). |

An `experimental` row can change or be removed in a minor release ([compatibility](compatibility.md)).

## Platform and OS

- Developed and verified on Windows 11 with Docker Desktop and Git Bash. Linux and macOS are expected to work and are covered by
  the hosted `gates` workflow only for the no-Docker gates. Real-server runs on Linux and macOS are **not** continuously verified.
- The helper scripts are Bash. On Windows use Git Bash or WSL.

## Security limits

- Test cells run untrusted-by-default server jars and your plugin in containers on your machine. Do not run a cell for a plugin
  you do not trust on a machine with sensitive data.
- Never put secrets in a test or its config: repro bundles collect server logs and configs.
