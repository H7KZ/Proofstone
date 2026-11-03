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
| Folia | `bukkit/folia` | Bukkit agent + region schedulers | itzg `TYPE=FOLIA` | 1.19.4 → newest | M5 | Prototype: plugin OK, RCON unusable. The agent is required. |
| Purpur | `bukkit/purpur` | Bukkit agent | itzg (verify `TYPE=PURPUR`) | per Purpur API | M5 | A Paper fork, so low cost. |
| Spigot | `bukkit/spigot` | Bukkit agent | BuildTools image, jar cached by checksum | 1.8.8 → newest | M5 | Slow to build, so it runs nightly. |
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
| Simulated Bedrock players (prefix or UUID detection) | Bukkit agent | Java driver with prefixed name | M5 | A cheap tier for "Bedrock-only code paths" in Java plugins. |
| Bedrock Dedicated Server add-ons (behaviour packs, Script API) | Script-API behaviour pack agent using `@minecraft/server-net` (beta, BDS-only) | Bedrock driver | M9 | The Script API changes often. Pin per BDS version. |
| Third-party Bedrock servers (PocketMine-MP, Nukkit, Endstone, …) | community agents implementing the wire protocol | Bedrock driver | M10 | The wire protocol is language-neutral by design. |

## Per-platform knowledge files

Each supported platform gets `docs/platforms/<id>.md` with these sections:
- **Host**;
- **Agent notes**;
- **Capabilities**;
- **Gotchas**, which agents append to as they learn.

Seeded now: [paper](platforms/paper.md) and [folia](platforms/folia.md).
