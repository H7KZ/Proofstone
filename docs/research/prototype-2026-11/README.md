# Prototype (2026-11-03): a throwaway round trip

This is the evidence behind the ADRs. **It is not part of the product.** It is a shell script and a
single Node file.

## What it does
- `run.sh <mcVersion> <javaTag> [botVersion]` starts `itzg/minecraft-server` with:
  - Paper or Folia;
  - offline mode;
  - RCON;
  - a flat world;
  - the plugin jar from `./plugins/`.
- `e2e.js` then runs these steps:
  1. A Mineflayer bot joins.
  2. It runs `/version Disenchantment`.
  3. RCON gives the bot a Sharpness V and Unbreaking III sword, a book and 30 levels, and places an anvil.
  4. The bot opens the anvil, places both items, waits for result slot 2 and takes the result.
  5. The script asserts both the client view and the server view (RCON `data get` and `xp query`).

## Results

| Server | Java | Bot | Boot | Checks |
|---|---|---|---|---|
| Paper 1.18.2 | 17 | 1.18.2 | 19–20 s | 8/8 |
| Paper 1.21.11 | 21 | 1.21.11 | 27–30 s | 8/8 |
| Paper 26.1.2 | 25 | 26.1 | 24–25 s | 8/8 |
| Paper 26.3 | 25 | 26.1 | 24 s | bot refused (`ECONNRESET`) |
| Paper 26.3 + ViaVersion/ViaBackwards | 25 | 26.1 | 27 s | 8/8 |
| Folia 1.21.11 | 21 | 1.21.11 | 29 s | client 6/6; server checks failed (no `/data`, empty `xp query`) |

The test itself takes about 2–4 s. XP went from 30 to 17 levels (cost 13, default Disenchantment config).

## Lessons that became ADRs
- RCON truncates output, its syntax changes per version, and it fails on Folia
  ([ADR-0002](../../adr/0002-server-truth-via-agent.md)).
- The client item model lags the protocol ([ADR-0003](../../adr/0003-dual-oracle.md)).
- Mineflayer lags the newest release, and bridging works
  ([ADR-0004](../../adr/0004-client-driver-latest-protocol-plus-viaproxy.md)).
- A Git Bash path made a bind mount silently empty
  ([ADR-0005](../../adr/0005-containers-testcontainers-no-bind-mounts.md)).
- A fixed sleep caused a stale inventory, and polling fixed it
  ([ADR-0007](../../adr/0007-no-sleeps-no-retries.md)).

## Running it
Requires Docker and Node 18+.

```bash
npm install
mkdir -p plugins && cp /path/to/Disenchantment-6.5.12.jar plugins/
./run.sh 1.21.11 java21
```
