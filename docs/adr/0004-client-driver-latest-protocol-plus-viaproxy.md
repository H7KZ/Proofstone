# ADR-0004: Primary driver is MCProtocolLib on the newest protocol only, with ViaProxy for older servers

- Status: Accepted
- Date: 2026-11-03
- Supersedes: the Mineflayer-sidecar recommendation in the founding survey

## Context
- **Protocol churn is the largest long-term maintenance cost.**
- **Mineflayer lags.** 4.39.0, released 2026-09-06, is tested up to 26.1, while 26.2 (June 2026) and 26.3
  (September 2026) were out. A 26.3 server refused it with `ECONNRESET`.
- **MCProtocolLib is current.** `26.3-2` is in the opencollab repo. It is MIT-licensed and maintained by
  GeyserMC, whose product needs support for the newest Java version.
- **ViaProxy is a standalone proxy** for clients 1.7.2 → 26.3 and servers 1.0 → 26.3, with an official
  Docker image (`ghcr.io/viaversion/viaproxy`). It is GPL-3.0.
- **The prototype showed a bridge works.** With ViaBackwards on the server, a 26.1 client passed all 8
  checks against a 26.3 server.

## Decision
1. The primary driver `mcpl` is a Java implementation on MCProtocolLib that speaks **only the newest
   protocol** (one dependency version).
2. On cells where the server is older than the driver, core inserts a **ViaProxy container** between the
   driver and the server. The server stays clean: no Via plugins inside it. The cell is marked *bridged*.
3. The driver is **server-authoritative**. It never predicts slot contents. After each click it waits for
   the server's `SetContent`/`SetSlot` and sends whatever prediction fields the protocol requires as
   "unknown" or empty. The server then resyncs.
4. The driver touches a minimal packet surface (about 10 packet types) behind one adapter class.
5. **Mineflayer stays as an independent reference driver** for differential runs on versions where it is
   native ([ADR-0003](0003-dual-oracle.md), [self-tests](../specs/self-tests.md)).

## Consequences
- There is one protocol implementation to maintain, and it is always the newest.
- Old versions are free through ViaProxy.
- New Minecraft releases are a pinned dependency bump.
- Translation risk is mitigated by native newest cells, differential runs and conformance (R1 in the
  [risk register](../robustness.md)).
- The GPL ViaProxy runs as a separate container and isn't linked, so it doesn't affect Proofstone's
  licence.
- Our driver is Java, so the core and the driver share one runtime, which keeps debugging simple.

## Alternatives considered
- **Mineflayer as primary:** lags the newest version, and adds a Node runtime. Kept as the reference driver.
- **MCProtocolLib with N shaded versions:** N times the maintenance. Rejected.
- **Via plugins inside the server under test:** modifies the system under test. Rejected; the
  ViaBackwards-in-server approach was only for the prototype.
- **Headless real client (Fabric client + XVFB):** heaviest option and needs a per-version mod port.
  Possible later for client-mod testing, not as the primary driver.
