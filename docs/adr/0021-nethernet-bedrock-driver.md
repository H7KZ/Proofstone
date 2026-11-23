# ADR-0021: NetherNet transport for the Bedrock driver

- Status: Accepted (2026-11-23)
- Date: 2026-11-23
- Deciders: maintainer delegated the decision ("decide yourself", 2026-11-23); decided by the orchestrating agent from spike
  T-M10-19 ([nethernet.md](../platforms/nethernet.md)). Adds to [ADR-0018](0018-bedrock-driver-geyser-cells.md); amends nothing.

## Context
- The driver speaks RakNet only. Bedrock 26.60 clients, BDS 1.26.66.1, PowerNukkitX 3.0.6, Nukkit master and Geyser's default
  transport use NetherNet; Geyser keeps RakNet only with `bedrock.transport: both`.
- The spike joined PowerNukkitX 3.0.6 over NetherNet twice with a throwaway client (verdict Go, 1 to 2 weeks).
- The transport needs two snapshot-only dependencies (`netty-transport-nethernet`, `libdatachannel-java` with a native library).

## Decision
1. **Add NetherNet to the Bedrock driver** as a second transport beside RakNet, chosen per cell in `versions.yml`.
2. **Scope:** driver host (frame codec, signalling client, self-made offline identity) and the Geyser cell with
   `bedrock.transport: nethernet`. Linux glibc x86_64 and aarch64 natives only, inside the pinned container image (ADR-0018 placement).
3. **Dependencies:** pin the two snapshots to exact timestamped versions and keep them in a dedicated source set so the published
   artefacts (`proofstone-api`, `-core`, `-protocol`) never depend on them. Revisit when upstream publishes releases.
4. **Not in scope:** BDS (needs a fresh maintainer yes for the Mojang binary), PowerNukkitX as a promoted row (separate track),
   Windows or macOS natives, TLS signalling.
5. **Promotion** of any new row follows ADR-0015 (local per-row evidence).

## Consequences
- New cards (not started in this change): driver NetherNet transport; shading and pinning the native; Geyser NetherNet cell;
  docs. PNX and BDS rows stay behind their own decisions.
- Snapshot dependencies can disappear: the cards must vendor the resolved jars in the pinned image build, not at test time.

## Alternatives considered
- **Stay on RakNet:** stops working against Bedrock 26.60+ clients and Geyser once it drops RakNet.
- **Wait for releases of the NetherNet library:** unknown date; the spike shows the transport is small.
