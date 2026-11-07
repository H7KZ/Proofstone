# ViaProxy

The bridge between the newest-protocol driver and an older server (ADR-0004,
[client-driver spec](../specs/client-driver.md) section 5). Core starts it only for *bridged* cells, on the cell
network, and never installs Via plugins in the server under test.

## Image
- `ghcr.io/viaversion/viaproxy`, pinned by index digest in `versions.yml` (`drivers.viaproxy`). GPL-3.0, run as a
  separate container and never linked (ADR-0011).
- Pinned build: ViaProxy 3.4.15 (`latest` on 2026-11-07), Java 25, supports servers 1.7+ and clients up to 26.3.
- The image entrypoint is `java -jar /app/ViaProxy.jar config viaproxy.yml` in `/app/run`. That mode needs a config
  file. Core overrides the entrypoint with `java -jar /app/ViaProxy.jar` and passes `cli` plus flags, so no file is
  copied or mounted (ADR-0005).

## CLI flags (verified by running the pinned image with `cli --help`)
| Need | Flag | Value core passes |
|---|---|---|
| Bind address and port | `--bind-address <host:port>` | `0.0.0.0:25568` (default is the same) |
| Target server | `--target-address <host:port>` | `server:25565` (the server's network alias) |
| Target version | `--target-version <name>` | the row's `mc`, for example `1.8.8` |
| Offline auth | `--auth-method <NONE\|ACCOUNT\|OPENAUTHMOD>` | `NONE` (the default, set explicitly) |

- `--target-version` accepts a plain version inside a ViaProxy range name: `1.8.8` matches the range `1.8.x`
  (`cli --list-versions` prints the names). The default, `Auto Detect (1.7+ servers)`, is never used: the target is
  always explicit.
- `--proxy-online-mode` defaults to false, so the proxy does not authenticate clients against Mojang.

## Topology
- One Docker network per bridged cell. The server joins it with the alias `server`, ViaProxy with `viaproxy`.
- The driver connects to ViaProxy's mapped port on the Docker host. The server's own game port stays published, but a
  bridged cell's client never uses it.
- Ready means ViaProxy's bind port is in the container's listening socket table. The log line "Binding proxy
  server" is printed before the bind, so it is not used.

## Gotchas

> Version-span entries (1.8.8 to 26.3, bridged cells) were observed on local real-server runs through the Docker lock.
> CI smoke is manual since [PR #40](https://github.com/H7KZ/Proofstone/pull/40) (billed Actions minutes), so no CI run
> backs them. Each entry links its merged PR (the PR description holds the real-server output) and the commit. No repro
> bundle was kept: bundles are written to `build/proofstone/`, which is not committed, so none is linked.

### Version span, pre-1.17 servers

Dates are 2026-11-07 unless stated.

- **A server before 1.17 has no state id.** ViaProxy sends a constant one, so "the state id moved" is never the sign
  that a click was answered. The mcpl driver counts full `ContainerSetContent` resyncs instead. _Pre-1.17 rows (bridged)
  · [PR #39](https://github.com/H7KZ/Proofstone/pull/39),
  [12ad323](https://github.com/H7KZ/Proofstone/commit/12ad323)_
- **A server before 1.17 locks a window after a rejected click until the client acks it.** ViaVersion turns the server's
  rejection (the claimed stack differs from the real one) into a clientbound ping, and sends the ack only when the pong
  comes back. A client that never pongs gets every later click ignored, silently. The driver answers every ping and
  waits for the full resync, which follows the ping, before it sends the next click. _1.8.8 to 1.16.5 (bridged) ·
  [PR #39](https://github.com/H7KZ/Proofstone/pull/39), [265328c](https://github.com/H7KZ/Proofstone/commit/265328c)_
- **The claimed stack of a click must never equal the real one.** The driver relies on a rejection to get the server's
  resync. Air with a count of one reads as an empty stack on a pre-1.17 server, so it matched an empty slot: the server
  accepted the click and sent no echo. The claim is a barrier x1. _1.8.8 (bridged) ·
  [PR #39](https://github.com/H7KZ/Proofstone/pull/39), [265328c](https://github.com/H7KZ/Proofstone/commit/265328c)_
- **Pre-1.17 servers answer an accepted click with no echo** (they expect the client's prediction). A driver that waits
  for the server's answer must therefore make every click be rejected, which is why the claim above is a barrier.
  _1.8.8 to 1.16.5 (bridged) · [PR #39](https://github.com/H7KZ/Proofstone/pull/39),
  [265328c](https://github.com/H7KZ/Proofstone/commit/265328c)_
- **C06 flaked on 1.8.8 before the "wait for the whole answer" fix.** It failed in 1 of about 4 runs. After the fix it
  passed 5 of 5 isolated runs and the final run. Not proven flake-free: one run per row set, no repeats.
  _1.8.8 (bridged) · [PR #39](https://github.com/H7KZ/Proofstone/pull/39), [265328c](https://github.com/H7KZ/Proofstone/commit/265328c)_

### Version span, 1.17 and newer (source notes)

- ViaProxy turns 1.17+ item components into hashes in click packets, and an old server sees the claimed carried item as
  the clicked item. Source: ViaVersion `ItemPacketRewriter1_17`, read but not observed on a run. Both are why the claim
  above is a plain id and count. _Source note, not run-observed · [PR #39](https://github.com/H7KZ/Proofstone/pull/39)_

### Host and tooling

- Docker on Windows under Git Bash rewrites `/app/...` arguments into `C:/Program Files/Git/app/...`. Use PowerShell (or
  `MSYS_NO_PATHCONV=1`) when running the image by hand. _2026-11-07, Windows host · [PR #39](https://github.com/H7KZ/Proofstone/pull/39)_
