# Spec: client driver SPI

Status: draft. Decision: [ADR-0004](../adr/0004-client-driver-latest-protocol-plus-viaproxy.md).

## 1. SPI

```java
public interface ClientDriver {
    String id();                         // "mcpl", "mineflayer-ref", "bedrock"
    Edition edition();                   // JAVA | BEDROCK
    ProtocolInfo protocol();             // the version this driver speaks natively
    Set<Capability> capabilities();
    ClientSession connect(ConnectSpec spec);   // host, port, username, timeouts; offline auth
}

public interface ClientSession extends AutoCloseable {
    void chat(String message);
    void command(String commandWithoutSlash);
    ChatMessage awaitMessage(Pattern p, Duration timeout);
    void useBlock(BlockPos pos, Face face);              // look + interact
    WindowSession awaitWindow(Duration timeout);
    InventoryView inventory();                            // last server-synced view
    ExperienceView experience();
    PositionView position();
    void awaitServerSwitch(Duration timeout);             // proxy server switch, see §2a
    TranscriptView transcript();                          // for repro bundles
}

public interface WindowSession extends AutoCloseable {
    String type(); int id(); List<ItemView> slots(); ItemView cursor();
    void click(int slot, Click click);                    // returns when server has acknowledged (see §2)
    void close();                                         // awaits server close + inventory resync
}
```

## 2. Server-authoritative click protocol (mcpl)

1. Send the click packet with the window's current `stateId`.
   - On protocols that need predicted changed slots (hashed stacks from 1.21.5 onward), send an empty
     prediction list and an empty or unknown carried item.
   - The server detects the mismatch and resyncs, which is exactly what we want.
2. Wait until a `ContainerSetContent` or `ContainerSetSlot` arrives with a `stateId` newer than the one
   sent, **or** the spec timeout expires, which is an error.
3. Expose only server-sent state. The driver never applies local predictions.


## 2a. Server switch on a live connection (proxies, T-M7-09)

A proxy moves a connected client to another backend ([ADR-0016](../adr/0016-topology-cells.md) section 5). The client sees the
configuration phase again and then a new join: `ClientboundStartConfigurationPacket`, the acknowledgement, finish
configuration (answered), then `ClientboundLoginPacket`. BungeeCord adds `ClientboundRespawnPacket` after the login; the driver
ignores it, and it ignores the Velocity and BungeeCord difference otherwise.

- **`ClientSession.awaitServerSwitch(Duration)`** (additive, default throws, so a driver without proxy support fails loudly).
  It returns when a switch completed, meaning the new server's login arrived. Each completed switch is handed out once, so a
  switch that finished before the call is not lost and a second call waits for the next.
- **State:** `PLAYING`, `SWITCHING` from start configuration, `PLAYING` again at the new login. Commands and chat while
  `SWITCHING` fail with the state named.
- **Views reset at the new login, never kept.** Windows are closed (a held `WindowSession` fails with "closed"), window 0 and the
  cursor are empty, position and experience are unset, until the new server syncs them. The server is authoritative (section 2),
  and a view is the new server's or empty. The join burst repeats: the driver accepts the new teleport and says it is loaded.
- **Failures, all loud:** a kick during the switch, or before it, is a `ClientDriverException` with the reason and (during a
  switch) the stage. A timeout is a `JoinTimeoutException` whose stage is one of `SWITCH_NOT_STARTED` (the proxy did not move
  the client: an unreachable target, or the command reached a backend; the chat since the command is quoted),
  `SWITCH_START_NOT_ACKNOWLEDGED`, `SWITCH_CONFIGURATION_STALLED`, `SWITCH_FINISH_NOT_ANSWERED`, `SWITCH_NO_JOIN_PACKET`. Its message
  starts `timed out joining`, so the flake accounting counts it. When the proxy cannot reach the target it keeps the client on the
  first server: the session stays usable and its views are untouched.
- **The hop** comes from the harness: `TopologyHost.describeSwitch` adds it from the logs (ADR-0016 section 9).

## 2b. Resource packs (ADR-0017 §7, built by T-M8-10)

What ran first (T-M8-01, Fabric 26.3, [fabric.md](../platforms/fabric.md) Capabilities): the server pushes
`ClientboundResourcePackPushPacket(id, url, hash (sha1), required, prompt)` in the **configuration phase**; accept
(`ACCEPTED`, `DOWNLOADED`, `SUCCESSFULLY_LOADED`) reached play, `DECLINED` was kicked with
`multiplayer.requiredTexturePrompt.disconnect`, and no answer stalled the join at `CONFIGURATION_STALLED`. As built
(`drivers/mcpl` `ResourcePacks`, T-M8-10), checked on `paper@26.3` by C-R01 and C-R02 ([self-tests.md](self-tests.md) §1c):

- **Answer policy, set before connect.** `ConnectSpec.resourcePacks()` is a `ResourcePackPolicy(answer, source)`: the test's
  answer (`ACCEPT`, `DECLINE`, `FAIL_DOWNLOAD`, from `@ResourcePackAnswer` on the method, else the class) or none, and the
  harness source's base URL (`http://127.0.0.1:<port>/`) or none. The 4-argument `ConnectSpec` means no answer and no source.
  The per-cell conformance check's own clients accept the cell's declared pack (the harness serves it); with no pack
  declared they declare nothing either.
- **No answer declared:** on the first push the driver disconnects, and the join fails at once with `ClientDriverException`
  naming the URL and `no resource-pack answer declared`. It never accepts silently and never lets the join stall into a
  timeout (C-R01: ended well inside the join timeout, no `timed out` message).
- **Record every push and pop**, in configuration and in play (and during a proxy switch), in order, as
  `io.github.h7kz.proofstone.api.ResourcePackPush`: `id`, `url`, `hash` (empty if the server sent none), `required`, `prompt`
  (the text component flattened to plain text, or null), the declared `answer`, the `statuses` sent, a `problem` (or null)
  and `popped`. A pop (`ClientboundResourcePackPopPacket`, one id or all) marks the records; the driver sends no status for a
  pop. The transcript has one line per push, pop and status sent. `ClientSession.resourcePacks()` (additive, default throws)
  exposes the records; the test reads them as `Client.resourcePacks()`.
- **Statuses sent**, as a vanilla client sends them, each recorded before it is sent:
  - `ACCEPT`: `ACCEPTED`, then the download (on its own thread, so keep-alives still flow), then `DOWNLOADED` and
    `SUCCESSFULLY_LOADED` when the sha1 of the bytes equals the pushed hash. On a mismatch the record's problem is
    `hash mismatch: expected <h>, got <h>` and the driver sends `FAILED_DOWNLOAD`; a failed download (not HTTP 200, an I/O
    error) is `download failed: ...` and `FAILED_DOWNLOAD`. The test, not the driver, decides whether that is a failure. A
    push with no hash is loaded unchecked, as vanilla does, and the problem says it was not checked.
  - `DECLINE`: `DECLINED`.
  - `FAIL_DOWNLOAD`: `ACCEPTED`, then `FAILED_DOWNLOAD`, without downloading.
  - What `paper@26.3` did with each (C-R01, C-R02): `ACCEPT` reached play; `DECLINED` on a required pack was kicked with
    `multiplayer.requiredTexturePrompt.disconnect` (server log: `Disconnecting <player> due to resource pack <id> rejection`);
    `FAILED_DOWNLOAD` after a wrong hash on a required pack still reached play. `FAIL_DOWNLOAD` itself was run in unit tests
    only.
- **A join that fails** says why the pack was not taken: the `could not join` message adds `resource packs: <url>: <problem>`.
- **Source rule: never the internet.** The driver downloads only from under the source base URL, with one GET that follows
  no redirect. A push with any other URL is recorded; `DECLINE` and `FAIL_DOWNLOAD` are sent as declared, and `ACCEPT` on it
  disconnects with a `ClientDriverException` naming the URL and this rule. Nothing is unpacked or rendered.
- **Several packs** (one push per id since 1.20.3) are answered one by one with the same policy.
- Bridged cells: not run. `RESOURCE_PACKS` is held on native cells only until a bridged run shows ViaProxy carries the push.

## 3. Packet surface for mcpl v1 (keep it minimal)

| Direction | Packets |
|---|---|
| Login and configuration | handshake, login start, the configuration phase (registries, known packs, ping answered, resource-pack push and pop, §2b), finish configuration |
| Play, outbound | chat, chat command, keep-alive response, use item on, container click, container close, player position or rotation (only when needed) |
| Play, inbound | system and player chat, open screen, container set content, container set slot, set experience, player position, keep-alive, disconnect |

Anything else is ignored, unless a later card adds it with a conformance scenario.

### 3a. Join acknowledgement (T-M7-19)

On a **native** join (`ConnectSpec.bridged` false) the mcpl driver applies every `ClientboundPlayerPositionPacket` to `position()` at once, but it **holds the join teleport's
`ServerboundAcceptTeleportationPacket` (and `ServerboundPlayerLoadedPacket`) until the first `ClientboundSetHealthPacket` of that
login**, then sends the newest held position's acknowledgement. Later positions are acknowledged at once. A new
`ClientboundLoginPacket` (a proxy switch) starts the hold again. This differs from the vanilla client, which acknowledges at
once; the reason is that Paper and Purpur handle the acknowledgement's position before the player's first tick, when their last
good position is still `0, 0, 0`, and log `moved too quickly!` for the whole distance to the join spot (ROADMAP "M7 follow-up
findings (T-M7-18)"). The first `SetHealth` is sent in that first tick.

It is event-driven on the netty thread: no sleep, no timer, no retry. `connect` waits, within the same connect timeout, until
the held acknowledgement is released. A server that never sends `SetHealth` after its join position fails the join as a
`JoinTimeoutException` at stage `JOIN_TELEPORT_HELD_NO_HEALTH`, never a silent hang.

**Bridged joins do not hold.** `ConnectSpec.bridged` (set by the harness from `Cell.bridged()`, default false) makes the driver
acknowledge at once, as before: bridged `paper@1.8.8` and `spigot@1.8.8` send no `SetHealth` before the client acknowledges
(ViaProxy resends the position), so holding deadlocked their join (ROADMAP "T-M7-19"), and they never logged the WARN. A native row
that never sends the first `SetHealth` still fails as `JOIN_TELEPORT_HELD_NO_HEALTH`.

## 4. Reference driver (mineflayer-ref)

- A Node and Mineflayer sidecar image exposes this SPI over JSON-RPC (WebSocket). A Java proxy class
  implements `ClientDriver`.
- It is used only in differential runs, on cells where `versions.yml` lists it as native.
- Its item parsing must handle the component era. The prototype's `stored_enchantments` fix is the
  minimum, plus mapping numeric registry IDs to names.

## 5. Bridging (ViaProxy)

- A cell is *bridged* when the driver's protocol (`driver.protocolVersion()`) is not the row's Minecraft version.
- Core puts the server and ViaProxy on one Docker network (the server has the alias `server`) and starts
  `ghcr.io/viaversion/viaproxy@<digest>` (`drivers.viaproxy` in `versions.yml`; a `TODO` digest is an error):
  - in `cli` mode, with the image entrypoint overridden, so no file is copied or mounted;
  - `--target-address server:25565`, `--target-version <row mc>`, `--auth-method NONE` (offline), and
    `--bind-address 0.0.0.0:25568`;
  - ready when the bind port is in the container's listening socket table.
- The driver connects to ViaProxy's mapped port, and `Cell.bridged()` returns true. Reports and display names say
  "bridged".
- The flags were verified against the pinned image in card T-M3-03 and are recorded in
  [viaproxy](../platforms/viaproxy.md).
