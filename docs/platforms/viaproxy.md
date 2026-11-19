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
