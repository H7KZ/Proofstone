# ViaProxy

The bridge between the newest-protocol driver and an older server (ADR-0004,
[client-driver spec](../specs/client-driver.md) section 5). Core starts it only for *bridged* cells, on the cell
network, and never installs Via plugins in the server under test.

## Image
- `ghcr.io/viaversion/viaproxy`, pinned by index digest in `versions.yml` (`drivers.viaproxy`). GPL-3.0, run as a
  separate container and never linked (ADR-0011).
- Pinned build: ViaProxy 3.4.15 (`latest` on 2026-11-07), Java 25, supports servers 1.7+ and clients up to 26.3.
- No newer stable release exists (T-M9-14, 2026-11-19): GitHub's newest is v3.4.14 (2026-09-27); the pin is a main snapshot 28 commits past it.
  `:latest` has since moved to 3.5.0-SNAPSHOT (`sha256:5a1ed8d1...`, main `23719ec`) with renamed CLI flags, so it is not a drop-in pin.
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

## Bridged rows observed (M5, 2026-11-08)
Local real-server runs, one full `:conformance:run` per row, through this ViaProxy image:
- Folia 1.21.11, 26.1.2 and 26.2 (C01 to C08, C-F01, C-F02 pass). Folia 26.3 is native.
- Purpur 1.20.6 and 1.21.11 (C01 to C08 pass, two runs on 1.20.6). Purpur 26.3 is native.
- Spigot 1.8.8 and 1.21.11 (C01 to C08 pass, except one red C06 on 1.21.11, see [spigot.md](spigot.md)). Spigot 26.3 is native.
- Bridged `SIMULATED_BEDROCK` is not held: a `.`-prefixed login stalls on 1.21.11 at this proxy, with no `[AUTH]` line.
  The cause is not identified. Details in [paper.md](paper.md), "Simulated Bedrock". No Bedrock client was joined through
  ViaProxy, so this image is not a Bedrock bridge.

## Gotchas
- **Bridged joins acknowledge the join teleport at once (T-M7-19).** The mcpl driver holds that acknowledgement for the first
  `SetHealth` on native joins only (client-driver.md section 3a). Behind ViaProxy, `paper@1.8.8` and `spigot@1.8.8` send no
  `SetHealth` before the acknowledgement and ViaProxy resends the position, so holding deadlocked the join; `ConnectSpec.bridged`
  turns the hold off. _1.8.8 (bridged) · T-M7-19_

- **On a pre-1.17 server the cursor is a separate packet after the full resync.** `ContainerSetContent` has no carried stack on 1.8.8,
  so it says "empty cursor" and the real cursor follows 0 to 9 ms later as `SetCursorItem`. `McplSession.click` returns on the content,
  so a cursor read right after a pick-up click can see an empty cursor (C04 "cursor after picking up slot A", C05 "the renamed sword
  on the cursor", about 3 in 10 full runs). Await the cursor, do not read it at once: C04 and C05 do
  (`ConformanceFixture.awaitCursor`, T-M7-21). _1.8.8 (bridged) · T-M7-20, T-M7-21_

- **Bridged join stalls after `LoginFinished`, about 1 in 250 joins on 1.8.8 (T-M6-109).** The client gets `LoginFinished`, acks it
  1 ms later, then nothing for 30 s. ViaProxy logs `Connected successfully! Switching to CONFIGURATION state` and never `Configuration
  finished!`. Paper 1.8.8 is already in play (`logged in with entity id`). It is on the ViaProxy side of the bridge, not the server
  and not the driver (packets are in the failure message when it is a `JoinTimeoutException`). Source read, not run-proven: ViaProxy's
  `LoginPacketHandler` calls `ChannelUtil.disableAutoRead` on the server channel at login success and, for a 1.20.2+ client, only
  `ConfigurationPacketHandler` (on the client's `LoginAcknowledged`) calls `restoreAutoRead`; a lost restore leaves the server socket
  unread. Not shown: that this is what happens. The harness can do nothing: the client cannot see the proxy's state, and a wait,
  retry or re-join is forbidden. A second shape of the same bridged 1.8.8 join, 2 of 530 joins: ViaProxy kicks the client with
  `IndexOutOfBoundsException` in `S2CLoginCompressionPacket.read` (and `ERROR IN Protocol1_20_2To1_20_3 IN REMAP OF DISGUISED_CHAT`);
  seen both times at join 42 or 43 of a 100-join cell. _1.8.8 (bridged) · ROADMAP "T-M6-109 differential runs"_

### The join stall after `LoginFinished`: counted loops and both sides (T-M12-02, 2026-11-26)

Tooling: [scripts/spike/join-stall/](../../scripts/spike/join-stall/) (`loop.sh`, `viawatch.sh`, `client-raw-io-diagnostic.patch`). Each loop is one locked invocation, Windows 11, Docker Desktop:

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/spike/join-stall/loop.sh build/spike/join-stall/<run> 100 paper@1.8.8 ViaProxy.jar
PROOFSTONE_ACCEPT_EULA=true bash scripts/spike/join-stall/loop.sh build/spike/join-stall/<run> 100 paper@1.21.11 itzg/minecraft-server dump
```

It runs `:conformance:run -Dproofstone.repeat=<n>` on smoke c02 (one cell boot, n joins one after another) and a read-only watcher of the container's sockets
(`/proc/net/tcp` and `tcp6`), its `eth0` byte counters and, with `dump`, one JVM thread dump of the Paper server when a join sits at `UUID of player ...`.

| Loops | Cell | Joins | Stalls (k of n) | Where |
|---|---|---|---|---|
| L1 to L5 (100 each) | paper@1.8.8, bridged | 500 | 7 of 500: L1 runs 92, 97; L2 36, 87; L4 40; L5 9, 100 (L3 none) | below |
| N1 to N14 (100 each) | paper@1.21.11, native | 1400 | 13 of 1400: runs 62, 74, 78 (N1); 74, 98 (N3); 94 (N4); 61, 64 (N7); 67 (N8); 76 (N9); 87 (N11); 74, 85 (N14) | [paper.md](paper.md) |
| D1 to D5 (100 each, login acknowledgement held 10 ms) | paper@1.21.11, native | 500 | 1 of 500 (D2 run 74, ack written 19 ms after `LoginFinished`) | [paper.md](paper.md) |
| S1 to S10 (50 each) | paper@1.21.11, native | 500 | 0 of 500 | [paper.md](paper.md) |

**Bridged paper@1.8.8, 7 stalls, two shapes (both: ViaProxy logs `Connected successfully! Switching to CONFIGURATION state` and never `Configuration finished!`, Paper has the player in):**
- **Shape Q, quiet, 4 of 7 (L1 runs 92, 97; L5 runs 9, 100).** No error in ViaProxy's log; Paper logs `lost connection: Timed out` about 29 s later. In L5, where the watcher worked
  (the L1 watcher saw nothing: it read IPv4 only), both stalls show the same picture: **ViaProxy's socket to Paper holds an unread receive queue from the first second (about 3.8 KB) that
  grows for the whole stall (3.7 to 4.4 KB over 29 s)**, with an empty send queue. Paper kept sending (its keepalives) and ViaProxy read none of it. So ViaProxy stops reading its server
  connection: that is the lost `restoreAutoRead` the source read suggested (`LoginPacketHandler` disables auto-read at login success; only the client's `LoginAcknowledged` handler restores it). Shown: the
  proxy does not read the server socket. Not shown: the line of code that fails to restore it.
- **Shape E, exception, 3 of 7 (L2 runs 36, 87; L4 run 40).** ViaProxy logs `Caught unhandled netty exception`: `DecoderException: IndexOutOfBoundsException: readerIndex(1) + length(1) exceeds writerIndex(1)` on the proxy to server
  leg (the `S2CLoginCompressionPacket.read` shape of T-M6-109), and Paper logs `Connection reset by peer` in the same second. The proxy closes its server channel and says nothing to the client, which waits.
- Clean controls: 493 of 500 joins. The client's own transcript is empty on a failed repeat (an evidence gap of the harness, not fixed here).
- The watcher of L1 was broken (IPv4 only, the container match truncated); L1 is counted for the stalls, not for the queue evidence.

### Bridged login stall (M5 follow-up, 2026-11-08)
- **The image's CLI has no username or Bedrock option.** `cli --help` on the pinned image (ViaProxy 3.4.15-SNAPSHOT,
  Java 25) lists `--auth-method` (default NONE), `--proxy-online-mode` (default false), `--chat-signing`,
  `--rewrite-handshake-packet`, `--ignore-protocol-translation-errors`, `--suppress-client-protocol-errors`, and no
  flag that names a Bedrock prefix. The image bundles ViaBedrock (`BedrockProxyConnection`, `BedrockAccount`), which is
  a likely place for a `.`-prefixed login to wait for Bedrock auth. Not verified: the `viaproxy.yml` in the jar root
  is absent, so its option comments could not be read.
- **Not reproduced in this follow-up.** Reproducing the `.Name` stall needs C-B01 on a bridged cell, which the
  capability model does not allow (`SIMULATED_BEDROCK` is native-only), so a run needs a temporary override. Not
  done: the time-box was used up. No fix, and the bridged prefixed login stays unsupported.
- **A plain-name bridged join also stalls, once.** On folia@1.20.6 (run 1 of 2), `ConformanceFoliaTeleportTest` sent
  `ClientIntention` and `Hello`, and then nothing for 30 s: no server login and no ViaProxy connection line. The
  other 10 tests on that row joined. Read as the same family, but only seen once, so not proven. Folia 1.20.6 is not
  in `versions.yml` (see `folia.md`). _folia 1.20.6 · 2026-11-08_

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
