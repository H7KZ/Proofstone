# Platform support plan

"Practically everything" is delivered as **platform packs** behind one SPI and one wire protocol.
Adding a platform means writing a pack and passing conformance. Core code does not change.

Each platform is at one of these status levels:
- **planned**;
- **spike** (being explored);
- **experimental** (conformance passes on some cells);
- **supported** (conformance passes on every `required` cell, plus docs and an example).

## Java Edition: server plugins

| Platform | Pack | Agent | Host | Versions (target) | Milestone | Notes |
|---|---|---|---|---|---|---|
| Paper | `bukkit/paper` | Bukkit agent (Java 8 bytecode) | itzg `TYPE=PAPER` | 1.8.8 → newest | M1–M3 | Proven in the prototype (1.18.2, 1.21.11, 26.1.2, 26.3). |
| Folia | `bukkit/folia` | Bukkit agent + region schedulers | itzg `TYPE=FOLIA` | 1.19.4 → newest | M5 | **Required**: rows 1.21.11, 26.1.2, 26.2, 26.3 (promoted 2026-11-08, ADR-0015). C01 to C08, C-F01, C-F02 passed in at least two full runs per row; C06 repeat 10 of 10 on 26.3 and 1.21.11 (`docs/platforms/folia.md`). The agent is required; RCON is unusable as an oracle. |
| Purpur | `bukkit/purpur` | Bukkit agent | itzg `TYPE=PURPUR` (confirmed, T-M5-04) | 1.20.6 → newest (rows so far; 1.14 → newest per Purpur API) | M5 | Paper fork. **Required** (ADR-0015, M5 follow-up): rows 1.20.6 (bridged), 1.21.11 (bridged), 26.3 (native). 1.14 to 1.19.x not run. `docs/platforms/purpur.md`. |
| Spigot | `bukkit/spigot` | Bukkit agent | BuildTools image (`images/spigot-buildtools`), jar cached by SHA-256, `TYPE=CUSTOM` | 1.8.8 → newest | M5 | Slow to build, so each row is a manual per-row run (no schedule). **Required** (ADR-0015, M5 follow-up): rows 1.8.8 (bridged), 1.21.11 (bridged), 26.3 (native). An earlier C06 red on 1.21.11 is recorded and unexplained. `docs/platforms/spigot.md`. |
| Other Paper forks (Pufferfish, Leaves, …) | community packs | Bukkit agent | custom | — | M10 | Pack template plus conformance. |
| Hybrids (Mohist, Arclight, …) | community packs | Bukkit agent + mod agent | custom | — | M10+ | Low priority. Behaviour differs a lot between them. |

## Java Edition: proxies

| Platform | Agent | Topology | Milestone | Notes |
|---|---|---|---|---|
| Velocity | Velocity plugin agent (Java 17 bytecode) | proxy + N backends (Paper with the Bukkit agent) | M7 | Modern forwarding. **Status: required** (`velocity@4.3.1+paper@26.3`, promoted 2026-11-14 under ADR-0015 on the ROADMAP "M7 review" evidence; guide: [topologies](user/topologies.md)) ([ADR-0016](adr/0016-topology-cells.md)). Velocity 3.x rejects the newest client, so it is not a row. |
| BungeeCord | Bungee plugin agent | proxy + N backends | M7 | Legacy forwarding. **Status: required** (`bungeecord@2111+paper@26.3`, promoted 2026-11-14, same evidence). |
| Waterfall | none | — | out | End-of-life; its newest build (1.21 build 615) rejects the newest client. Not a row, not tested ([ADR-0016](adr/0016-topology-cells.md)). |
| Gate, others | community | — | M10 | — |

## Java Edition: mods and data

| Platform | Agent | Host | Milestone | Notes |
|---|---|---|---|---|
| Fabric (server-only mods) | Fabric mod agent (needs the pinned Fabric API for its ready signal), newest native row only (Stonecutter out for M8, T-M8-01) | itzg `TYPE=FABRIC` with a pinned server launcher (`FABRIC_LAUNCHER`), `java25` digest (confirmed, T-M8-01) | M8 | **Status: experimental** ([fabric.md](platforms/fabric.md), [guide](user/mods-and-packs.md)). Row `fabric@26.3`, native 777, not bridged. `:conformance:run` C01 to C08: 3 of 3 green (T-M8-08); C-D01 and C-D02: 3 of 3 green after a scenario fix, and 2 of 3 in the T-M8-09 verifier set (1 red, a `zombie_villager` worldgen WARN: peaceful difficulty plus a zombie village, cause found by T-M8-16, fixed by T-M8-21: the seed is pinned). M8 review (T-M8-99): `:conformance:run` 3 of 3 green again (11 tests, 0 skips). Not `required`: ADR-0015 is not applied to it. A content mod gets the client kicked, so content mods are out of M8, and `@Proofstone(mods)` does not exist yet, so a test cannot declare a mod under test (card T-M8-13). Capabilities held: `CONSOLE`, `DATAPACKS_VANILLA` ([ADR-0017](adr/0017-mod-platform-packs.md)). 26.x is unobfuscated: no mappings needed. |
| NeoForge | NeoForge mod agent | itzg `TYPE=NEOFORGE` with a pinned installer (`NEOFORGE_INSTALLER`), `java25` digest (confirmed, T-M8-01) | M8 | **Status: experimental** ([neoforge.md](platforms/neoforge.md), [guide](user/mods-and-packs.md)). Row `neoforge@26.3`, native 777. `:conformance:run` C01 to C08: 3 of 3 green, 0 join timeouts (T-M8-08). C-D01 and C-D02 skip with `DATAPACKS_VANILLA` named. M8 review (T-M8-99): 3 of 3 green again. With one added item the client joined in the spike (3 of 3), but content mods stay unverified. Not `required` (ADR-0015 is not applied). No user-declared mod, as on Fabric. |
| Forge (legacy) | Forge mod agent | itzg | M10 | Older versions only. |
| Quilt | Fabric agent (compatible) | — | M10 | — |
| Sponge | Sponge plugin agent | SpongeVanilla | M10 | — |
| Data packs | Bukkit agent (Paper host) **or** Fabric agent (vanilla-like host) | as above | M8 | Use the Fabric host when Paper's behaviour differs from vanilla. One pack (function, recipe) worked on Paper 26.3, Fabric and NeoForge in the spike. `@Proofstone(datapacks)`, capability `DATAPACKS_VANILLA` (token `datapacks`; Paper and Fabric packs declare it, ADR-0017). **Status: experimental.** C-D01 and C-D02 passed 3 of 3 on `paper@26.3` and 3 of 3 on `fabric@26.3` (a fresh set after a scenario fix, T-M8-09; the verifier's set on Fabric had 1 red from a worldgen WARN, open). `neoforge@26.3` skips with `DATAPACKS_VANILLA` named. Guide: [mods-and-packs](user/mods-and-packs.md). |
| Resource packs (server-sent) | the driver checks the pack prompt and hash; the Bukkit agent reads the status | Paper (native cells) | M8 | **Status: experimental.** C-R01 and C-R02 passed 3 of 3 on `paper@26.3` (T-M8-10). No rendering checks. The harness serves the pack from its JVM on loopback (no extra container); the driver answers as the test declares (`@ResourcePackAnswer`), never silently. Capability `RESOURCE_PACKS` (ADR-0017). |
| Client-side mods (rendering, UI) | out of scope for core | Fabric client gametest + XVFB | later | Only through integration with Fabric's own client gametest. |

## Bedrock Edition

| Platform | Agent | Client driver | Milestone | Notes |
|---|---|---|---|---|
| Geyser + Floodgate on a Java server | Bukkit agent | Bedrock driver (CloudburstMC Protocol in Java, run as a driver host on the cell network, [ADR-0018](adr/0018-bedrock-driver-geyser-cells.md)) | M9 | Spike done: feasible offline on `paper@26.3` with `validate-bedrock-login: false`, `transport: both` and a stand-in for two Microsoft discovery names ([geyser](platforms/geyser.md)). Built in M9 (ADR-0018): an *offline-stub* cell, not production auth (Xbox login validation is off). **Status: experimental**, row `geyser@2.12.1+paper@26.3`, selected by name only. Recorded on one Windows machine: `:conformance:run` 3 of 3 green (`tests=7 skipped=3`, the 3 skips reasoned: window clicks and `useBlock`, forms, resource packs), the cell host test 3 of 3, the Bedrock negative controls 3 of 3, 0 Bedrock join timeouts; the example `examples/geyser-bedrock` 3 of 3 green (2 tests, 0 skips). The driver is on a pinned **snapshot** of CloudburstMC Protocol, so ADR-0015 cannot be applied yet. Re-run by the M9 review (T-M9-99): conformance 3 of 3, `metaBedrockTest`, the example and the host tests once each, all as expected, 0 Bedrock join timeouts, 0 log-guard reds. No flake rate beyond those runs is known. The *offline-stub* label is in `cell.json`, the report (`bedrockAuth`) and the display name (T-M10-06). Client v1 does chat, command, inventory (id and count) and position only. Guide: [Bedrock through Geyser](user/bedrock.md). |
| Simulated Bedrock players (prefix detection) | Bukkit agent | Java driver with prefixed name (`Clients.create(".Name")`) | M5 | Not Bedrock support: a Java client under a `.` name, so a plugin's Bedrock-only branch can run (`SIMULATED_BEDROCK`: native Paper only, C-B01; a bridged prefixed login stalled on 1.21.11). UUID detection is not simulated. |
| Bedrock Dedicated Server add-ons (behaviour packs, Script API) | Script-API behaviour pack agent using `@minecraft/server-net` (beta, BDS-only) | Bedrock driver | M10 (was M9) | Out of M9 by maintainer decision (2026-11-17, ADR-0018): a Mojang binary with its own EULA and a new image. The Script API changes often. Pin per BDS version. |
| Third-party Bedrock servers (PocketMine-MP, Nukkit, Endstone, …) | community agents implementing the wire protocol | Bedrock driver | M10 | The wire protocol is language-neutral by design. |

## Per-platform knowledge files

Each supported platform gets `docs/platforms/<id>.md` with these sections:
- **Host**;
- **Agent notes**;
- **Capabilities**;
- **Gotchas**, which agents append to as they learn.

Written so far: [paper](platforms/paper.md), [folia](platforms/folia.md), [purpur](platforms/purpur.md), [spigot](platforms/spigot.md). Bridged-row facts: [viaproxy](platforms/viaproxy.md).
Proxy facts (T-M7-01 spike, then the M7 cards; both rows `required` since 2026-11-14, P01 to P05 green 3 of 3 per row again in the M7 review): [velocity](platforms/velocity.md), [bungeecord](platforms/bungeecord.md).
Mod loader facts (T-M8-01 spike, then the M8 cards; design in [ADR-0017](adr/0017-mod-platform-packs.md); both rows `experimental`): [fabric](platforms/fabric.md), [neoforge](platforms/neoforge.md). How to test mods, data packs and resource packs: [user guide](user/mods-and-packs.md).
A loader cell's first start fetches the vanilla server jar from Mojang and libraries from the loader's maven: a listed,
content-pinned exception to "no Mojang dependency in runs" (ADR-0017 §6). Logins never reach Mojang (offline mode).
Bedrock facts (spike and M9 runs, a Bedrock client through Geyser and Floodgate on `paper@26.3`): [geyser](platforms/geyser.md); guide: [Bedrock through Geyser](user/bedrock.md).

## Status evidence (M5, 2026-11-08; M5 follow-up, same day)
M5 itself promoted nothing: each row had one full local run. The M5 follow-up (`fix/m5-evidence-promotion`) applies
ADR-0015 with the evidence table in [ROADMAP, "M5 follow-up evidence"](plan/ROADMAP.md):
- **Promoted to `required`:** purpur 1.20.6, 1.21.11, 26.3; spigot 1.8.8, 1.21.11, 26.3. Each has at least two full
  passing per-row runs, and the platform's C06 repeat (10 of 10 on purpur@26.3 and spigot@1.21.11) shows no flake.
- **Folia promoted later the same day:** the four pinned Folia rows, after a C06 repeat of 10 of 10 on folia@26.3 and folia@1.21.11.
  folia@1.20.6 is not added: it failed twice on different tests (`docs/platforms/folia.md`, M5 follow-up).
- Paper rows keep their M3 `required` status.
