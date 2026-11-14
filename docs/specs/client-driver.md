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

Design; no driver code yet. What ran (T-M8-01, Fabric 26.3, [fabric.md](../platforms/fabric.md) Capabilities): the server
pushes `ClientboundResourcePackPushPacket(id, url, hash (sha1), required, prompt)` in the **configuration phase**; accept
(`ACCEPTED`, `DOWNLOADED`, `SUCCESSFULLY_LOADED`) reached play, `DECLINED` was kicked with
`multiplayer.requiredTexturePrompt.disconnect`, and no answer stalled the join at `CONFIGURATION_STALLED`.

- **Answer policy, set before connect.** `ConnectSpec` carries the test's answer (`ACCEPT`, `DECLINE`, `FAIL_DOWNLOAD`) or
  none. A push arrives during the join, before the test body, so the answer cannot be given later.
- **No answer declared:** on the first push the driver disconnects and throws `ClientDriverException` with the URL and
  `no resource-pack answer declared`. It never accepts silently and never lets the join stall into a timeout.
- **Record every push and pop**, in configuration and in play, in order: `id`, `url`, `hash` (empty if the server sent
  none), `required`, `prompt` (the text component flattened to plain text, or null), the answer given and the statuses
  sent. A pop (`ClientboundResourcePackPopPacket`, one id or all) is recorded too. The session exposes the records
  (`ClientSession.resourcePacks()`, additive, default throws so a driver without support fails loudly); the test reads
  them as `Client.resourcePacks()`.
- **Statuses sent**, as a vanilla client sends them:
  - `ACCEPT`: `ACCEPTED`, then the download, then `DOWNLOADED` and `SUCCESSFULLY_LOADED` when the sha1 of the bytes equals
    the pushed hash. On a mismatch the record says `hash mismatch: expected <h>, got <h>` and the driver sends
    `FAILED_DOWNLOAD`; the test, not the driver, decides whether that is a failure.
  - `DECLINE`: `DECLINED`.
  - `FAIL_DOWNLOAD`: `ACCEPTED`, then `FAILED_DOWNLOAD`, without downloading.
  - T-M8-10 checks each sequence against a real server and records what the server logs for it.
- **Source rule: never the internet.** The driver downloads only from the harness pack source (the loopback URL the harness
  serves for the cell). A push with any other URL is recorded; `DECLINE` and `FAIL_DOWNLOAD` are sent as declared, and
  `ACCEPT` on it is a `ClientDriverException` naming the URL and this rule. Nothing is unpacked or rendered.
- **Several packs** (one push per id since 1.20.3) are answered one by one with the same policy.
- Bridged cells: not run. `RESOURCE_PACKS` is held on native cells only until a bridged run shows ViaProxy carries the push.

## 3. Packet surface for mcpl v1 (keep it minimal)

| Direction | Packets |
|---|---|
| Login and configuration | handshake, login start, the configuration phase (registries, known packs, ping answered, resource-pack push and pop, §2b), finish configuration |
| Play, outbound | chat, chat command, keep-alive response, use item on, container click, container close, player position or rotation (only when needed) |
| Play, inbound | system and player chat, open screen, container set content, container set slot, set experience, player position, keep-alive, disconnect |

Anything else is ignored, unless a later card adds it with a conformance scenario.

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
