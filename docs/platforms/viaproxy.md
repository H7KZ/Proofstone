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
| N1 to N14 (100 each) | paper@1.21.11, **bridged** (client 777, server 774) | 1400 | 13 of 1400: runs 62, 74, 78 (N1); 74, 98 (N3); 94 (N4); 61, 64 (N7); 67 (N8); 76 (N9); 87 (N11); 74, 85 (N14) | below |
| D1 to D5 (100 each, login acknowledgement held 10 ms) | paper@1.21.11, bridged | 500 | 1 of 500 (D2 run 74, ack written 19 ms after `LoginFinished`) | below |
| S1 to S10 (50 each) | paper@1.21.11, bridged | 500 | 0 of 500 | below |

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

**Bridged paper@1.21.11 (client protocol 777 through ViaProxy to Paper 774), 13 of 1400 joins: after `LoginFinished` the server sends nothing more.** The client logs `CONFIGURATION_STALLED` (login
  acknowledged, configuration never finished), the harness shows `could not join ... disconnect.endOfStream` at +30 s. This is the stall of Disenchantment's hosted runs (its cells below 26.3 are all bridged). Counted loops, both sides, of one stalled join:
  - **Client** (raw reads and writes at the head of the Netty pipeline, `client-raw-io-diagnostic.patch`): writes 17 B (intention) and 28 B (hello) at +1 ms, **reads exactly 50 B** (login compression and
    `LoginFinished`) at +42 ms, **writes the 3 B `LoginAcknowledged` and the write completes** (+42 ms), then nothing for 29.6 s until the connection closes. The reads were recorded in 11 stalls: all 11 are exactly 50 B (in two reads of 4 B and 46 B in 5 of them); the writes in the last 3 (N14 twice, D2): all 3 show the acknowledgement written and flushed.
  - **Server:** the log has `UUID of player X is ...` and no `logged in`; 29 s later `lost connection: Timed out`. Paper's `eth0` sends about 800 B for the join and then only 77 B background pulses, where a clean join
    puts 36 to 70 KB on the wire. Socket queues are empty both ways. **A thread dump 19 s into the stall shows every thread idle**: the server thread parked between ticks, all four Netty IO threads in `epollWait`,
    the login thread pool idle. No deadlock, no busy thread. Paper is waiting and the client is waiting.
  - **Count and timing.** All 13 stalls fall at join 61 to 98 of a 100-join server (71 to 148 s after boot, so it is the join count, not the uptime). Ten loops of 50 joins gave **0 stalls in 500** (about 4.6 expected
    if the count did not matter, p about 0.01). One identity repeats beyond chance: repeat 74 (`Tc8a136e9`) stalled in 4 of 14 loops.
  - **Where it holds:** ViaProxy logs `Connected successfully! Switching to CONFIGURATION state` for the stalled join and never `Configuration finished!`, as for 1.8.8. The three hops are client, ViaProxy, Paper: the bytes
    the client got and what Paper sent were measured, ViaProxy's own state was not. A Paper that is idle and silent is what a server waiting for a `LoginAcknowledged` that ViaProxy never forwarded looks like; so is a stalled ViaProxy.
  - **Excluded:** the driver not sending the acknowledgement (it is written and flushed); the client not reading (the bytes never arrive); a busy or blocked server (idle threads, empty queues); load (the loops ran idle, and
    a stressor gave the same rate in T-M6-109); an acknowledgement that arrives too early (holding it 10 ms gave 1 stall in 500, with the ack written 19 ms after `LoginFinished`: not prevented).
  - **Not shown:** why the hop between the client's acknowledgement and Paper stops there, and why it needs about 60 earlier joins on one ViaProxy. Next experiments: ViaProxy's debug log for the stalled join (`-Dviaproxy...` or the CLI debug flag, not tried), a loop with a fresh ViaProxy every 40 joins (the count is per proxy or per server?), a native 26.3 loop for the control.
    _paper@1.21.11 (bridged) · T-M12-02_

### The mechanism: an unsynchronised auto-read stack in ViaProxy (T-M12-15, 2026-11-26)

**Result: the stall is a data race in ViaProxy's `ChannelUtil` (pinned 3.4.15-SNAPSHOT, `3e81750`). It leaves the proxy's server channel with auto-read off for good, so ViaProxy never reads Paper again.**
Upstream `master` has the same code (checked 2026-11-26). Nothing in Proofstone, the driver or Paper is involved.

The code: `disableAutoRead` pushes the channel's current auto-read flag on a per-channel `Stack` and sets it false; `restoreAutoRead` throws if the flag is already true, else pops the stack and sets the flag to the popped value.
Two independent pairs of calls use the **server channel** during one login, on two different event loops:
1. `CompressionPacketHandler`: disable on the server channel's loop (S2C `LoginCompression`); restore in the write-completion listener of the forwarded packet, which runs on the **client** channel's loop.
2. `LoginPacketHandler` (S2C `LoginFinished`): disable on the server channel's loop; restore in the write listener of the forwarded `LoginAcknowledged` (`ConfigurationPacketHandler`).

`Stack` methods are synchronised one by one, but "read the flag, push, set the flag" and "check the flag, pop, set the flag" are not atomic, and the two pairs overlap in a window of about a millisecond. The wrong interleaving (the compression restore pops `true` before the login disable pushes, the login disable reads the flag before the compression restore sets it,
and the login disable sets false after the compression restore sets true) ends with flag `false` and stack `[false]`. The acknowledgement's restore then pops `false` and sets false again: **auto-read stays off, ViaProxy reads nothing more from Paper, Paper waits for the acknowledgement that it already got through ViaProxy's closed read side,**
and the client times out after 30 s at `CONFIGURATION_STALLED`. The two shapes of the 1.8.8 loops are the same fault seen from outside: the quiet one is the unread receive queue (auto-read off); the `IndexOutOfBoundsException` is a half-read compression packet on the same channel (not shown separately).
The other outcome of the same race is harmless and common: the acknowledgement's restore finds the flag already true and throws `Race condition detected` inside a write listener, where Netty swallows it.

Evidence (diagnostic build: log lines in `ChannelUtil` and `ConfigurationPacketHandler` compiled into two classes of the pinned jar, `viaproxy-diag-image.sh`; a full rebuild floats the ViaVersion snapshot and does not start):
- **A stalled join caught with the diagnostic proxy** (loop of 100, bridged paper@1.21.11, 1 stall at join 28, `JoinTimeoutException ... CONFIGURATION_STALLED`). The server channel's calls, in the log's order: `disable (was=true)`, `restore`, `disable (was=false)`, the acknowledgement's `restore`, then **no `Configuration finished!`** and no further auto-read call. A healthy join shows `disable`, `disable (was=false)`, `restore`, `restore`, `disable`, `restore`.
  The `restore` and the second `disable` swapped places, and the second `disable` read `was=false` although the restore had just run: the lost update.
- **The same race without the stall, 14 times in 700 joins** (loops of 100 with the popped value logged): the sequence `disable, disable (was=false), restore (popped true)` and then the acknowledgement's restore throwing `Race condition detected` (14 `restore-race` lines, one per such join, every join completed). The flag was `true` in these, because the second `disable`'s set came before the restore's set. The stalled outcome is the narrower interleaving of the two. The 700 joins gave no second stall (1 stall in 800 joins with the diagnostic proxy against 13 in 1400 without; the log lines shift the timing, so the rates are not comparable).
- **The unmodified class reproduces it alone** (`AutoReadRaceStress.java`: the same call pattern, one thread for the server loop and one for the client loop, an `EmbeddedChannel`, the pinned jar's `ChannelUtil`, no Paper, no network): 3 runs of 200000 iterations left **0, 1 and 7 channels with auto-read false** after the acknowledgement's restore. That is the stall's final state, produced without a server.
- The earlier counts fit: the race needs two events about a millisecond apart, so it is a per-join probability (a few in a thousand), not a count of joins. The "join 61 to 98" and "0 of 500 in 50-join loops" in the table above are a small-sample pattern; the stress run has no join count at all.
- **Not shown:** the flag value of the stalled join after its last restore (that stall was caught before the popped value was logged, and no second stall came in 700 joins). The stall is therefore shown as the logged call order plus the stress reproduction of the final state, not as a logged `popped=false`.

- **The two pairs, named by their callers (2026-11-28, diagnostic proxy with stack frames, 200 joins, bridged paper@1.21.11, no stall in these 200).** On the server channel, every join shows `disableAutoRead` from `CompressionPacketHandler.handleP2S:47` and from `LoginPacketHandler.handleP2S:191` (both on the server channel's loop) and `restoreAutoRead` from `CompressionPacketHandler.lambda$handleP2S$0:51` (on the **client** channel's loop) and from `ConfigurationPacketHandler.lambda$handleC2P$0:49` (the acknowledgement's write listener). That is the pairing the mechanism above assumes; it is now read from the frames, not inferred.
- **Mitigation tried and rejected: no server compression.** `NETWORK_COMPRESSION_THRESHOLD=-1` on the Paper container (confirmed in `/data/server.properties` and the container env) does not remove the first pair: `CompressionPacketHandler.handleP2S` still ran in 101 of 101 joins (ViaProxy or the server still sends a compression packet, threshold -1 included), and 1 stall came in the first 100 joins with the setting. The change was discarded; nothing in the repository sets it.

**What this means for the cells.** Every bridged cell (paper@1.21.11 and every cell below 26.3) runs a ViaProxy that can stall a join at about 1 in 100 (1.8.8: 7 in 500; 1.21.11: 13 in 1400). It is not fixable in Proofstone without a retry (forbidden by AGENTS.md section 3), a change of the ViaProxy pin, or a patched ViaProxy.
Open for the maintainer: report it upstream (ViaVersion/ViaProxy; the report text is in T-M12-15 of [M12-followups.md](../plan/tasks/M12-followups.md)), and decide on a ViaProxy pin newer than `3e81750` once upstream fixes it (ADR-0008, `updateVersions`).
Tooling: `scripts/spike/join-stall/` (`viaproxy-diag-image.sh`, `viaproxy-autoread-diagnostic.patch`, `proofstone-viaproxy-image-override.patch`, `AutoReadRaceStress.java`). Not part of any runtime path; `versions.yml`, the pin and the driver are unchanged.

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
