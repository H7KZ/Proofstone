# M7–M10 epics (to be refined into cards)

These are not executable cards yet. Each epic is refined by its milestone-review card. A Sonnet or Opus
session turns it into `T-Mx-NN` cards in the standard format, in a file `Mx-<name>.md`.

## M7: Proxies (Velocity, BungeeCord)

**Refined into cards on 2026-11-09: see [M7-proxies.md](M7-proxies.md) (awaiting maintainer approval).** The text below is the original epic.

- **Topology:** a proxy container plus N backend Paper containers, each with the Bukkit agent. Clients
  connect to the proxy. Velocity uses modern forwarding and BungeeCord uses legacy forwarding.
- **Proxy agents:** a Velocity plugin and a Bungee plugin that implement the protocol subset
  (`health`, `players`, `command`, `events`, `log`) plus `/v1/proxy/servers`.
- **API:** `Topology.server("lobby")`, `client.transferTo("survival")`, and a `@Topology` annotation.
- **Conformance:** join through the proxy, switch servers, plugin messaging round trip, and a forwarding
  check (UUID and name match the backend).
- **Risks:**
  - the forwarding secret and modern-forwarding setup;
  - Waterfall being end-of-life;
  - BungeeCord's Java baseline.

## M8: Mods and data (Fabric, NeoForge, data packs, resource packs)

**Refined into cards on 2026-11-14 by T-M7-99: see [M8-mods.md](M8-mods.md) (awaiting maintainer approval).** The text below is the original epic.

- **Agents:**
  - a Fabric mod agent and a NeoForge mod agent, built for multiple versions with Stonecutter (to verify);
  - a per-version matrix that follows the Fabric and NeoForge metadata;
  - 26.1+ ships unobfuscated, so later versions are simpler.
- **Host:** the itzg Fabric and NeoForge types (to verify), with the mod under test copied into `/mods`.
- **Data packs:** a data-pack artefact type, hosted on Paper (Bukkit agent) or vanilla-like Fabric, with
  a capability `DATAPACKS_VANILLA`.
- **Resource packs:** the driver checks the pack prompt, URL and hash, then accepts or declines and reports
  status. No rendering checks.
- **Conformance:** C01–C08 on mod hosts, plus a data pack function result and a recipe check.
- **Risks:**
  - agent API churn per loader version;
  - Loom and ModDevGradle build times;
  - mappings before 26.1.

## M9: Bedrock
- **Spike (Opus, time-boxed to 2 days).**
  - Can a Java-hosted Bedrock client connect offline to Geyser (`auth-type: offline` or the Floodgate
    setup)? Try both CloudburstMC Protocol (Java) and `bedrock-protocol` (Node).
  - Can a BDS Script-API behaviour pack use `@minecraft/server-net`, which is beta and BDS-only, to act
    as an agent?
  - Output: an ADR choosing the Bedrock driver and the agent approach, or "not feasible" with evidence.
- **Then:** a Bedrock driver, a Geyser topology (Java server + Geyser + Floodgate + Bukkit agent) and a
  BDS pack (BDS host image, script agent, wire-protocol subset).
- **Risks:**
  - Xbox auth requirements;
  - the Bedrock protocol lagging the Bedrock release;
  - Script API beta churn.

## M10: Ecosystem
- A community pack template repository, plus a "write a platform pack" guide with a conformance checklist.
- More platforms: Paper forks, Sponge, PocketMine-MP, Nukkit, Endstone and Gate.
- An optional TypeScript test front end for Bedrock add-on developers, over the same wire protocol.
- A docs site, a showcase, and a compatibility dashboard generated from nightly reports.
- An optional integration with Fabric client gametest, for client-side mods.
