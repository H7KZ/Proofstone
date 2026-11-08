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
| Velocity | Velocity plugin agent | proxy + N backends (Paper with the Bukkit agent) | M7 | Modern forwarding. The client connects to the proxy. |
| BungeeCord / Waterfall | Bungee plugin agent | proxy + N backends | M7 | Waterfall is end-of-life, but still widely used. |
| Gate, others | community | — | M10 | — |

## Java Edition: mods and data

| Platform | Agent | Host | Milestone | Notes |
|---|---|---|---|---|
| Fabric (server-side mods) | Fabric mod agent, multi-version via Stonecutter (to verify) | itzg `TYPE=FABRIC` (verify) | M8 | Agent per MC version, because mods compile against mappings. 26.1+ ships unobfuscated, which makes this easier. |
| NeoForge | NeoForge mod agent | itzg (verify) | M8 | Same approach as Fabric. |
| Forge (legacy) | Forge mod agent | itzg | M10 | Older versions only. |
| Quilt | Fabric agent (compatible) | — | M10 | — |
| Sponge | Sponge plugin agent | SpongeVanilla | M10 | — |
| Data packs | Bukkit agent (Paper host) **or** Fabric agent (vanilla-like host) | as above | M8 | Use the Fabric host when Paper's behaviour differs from vanilla. |
| Resource packs (server-sent) | the driver checks the pack prompt and hash | any | M8 | No rendering checks. |
| Client-side mods (rendering, UI) | out of scope for core | Fabric client gametest + XVFB | later | Only through integration with Fabric's own client gametest. |

## Bedrock Edition

| Platform | Agent | Client driver | Milestone | Notes |
|---|---|---|---|---|
| Geyser + Floodgate on a Java server | Bukkit agent | Bedrock driver (CloudburstMC Protocol in Java, or the `bedrock-protocol` sidecar; spike decides) | M9 | Unproven: whether offline Bedrock clients can join through Geyser without Xbox auth. **Spike first.** |
| Simulated Bedrock players (prefix detection) | Bukkit agent | Java driver with prefixed name (`Clients.create(".Name")`) | M5 | Not Bedrock support: a Java client under a `.` name, so a plugin's Bedrock-only branch can run (`SIMULATED_BEDROCK`: native Paper only, C-B01; a bridged prefixed login stalled on 1.21.11). UUID detection is not simulated. |
| Bedrock Dedicated Server add-ons (behaviour packs, Script API) | Script-API behaviour pack agent using `@minecraft/server-net` (beta, BDS-only) | Bedrock driver | M9 | The Script API changes often. Pin per BDS version. |
| Third-party Bedrock servers (PocketMine-MP, Nukkit, Endstone, …) | community agents implementing the wire protocol | Bedrock driver | M10 | The wire protocol is language-neutral by design. |

## Per-platform knowledge files

Each supported platform gets `docs/platforms/<id>.md` with these sections:
- **Host**;
- **Agent notes**;
- **Capabilities**;
- **Gotchas**, which agents append to as they learn.

Written so far: [paper](platforms/paper.md), [folia](platforms/folia.md), [purpur](platforms/purpur.md), [spigot](platforms/spigot.md). Bridged-row facts: [viaproxy](platforms/viaproxy.md).

## Status evidence (M5, 2026-11-08; M5 follow-up, same day)
M5 itself promoted nothing: each row had one full local run. The M5 follow-up (`fix/m5-evidence-promotion`) applies
ADR-0015 with the evidence table in [ROADMAP, "M5 follow-up evidence"](plan/ROADMAP.md):
- **Promoted to `required`:** purpur 1.20.6, 1.21.11, 26.3; spigot 1.8.8, 1.21.11, 26.3. Each has at least two full
  passing per-row runs, and the platform's C06 repeat (10 of 10 on purpur@26.3 and spigot@1.21.11) shows no flake.
- **Folia promoted later the same day:** the four pinned Folia rows, after a C06 repeat of 10 of 10 on folia@26.3 and folia@1.21.11.
  folia@1.20.6 is not added: it failed twice on different tests (`docs/platforms/folia.md`, M5 follow-up).
- Paper rows keep their M3 `required` status.
