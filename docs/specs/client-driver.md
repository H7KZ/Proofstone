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

## 3. Packet surface for mcpl v1 (keep it minimal)

| Direction | Packets |
|---|---|
| Login and configuration | handshake, login start, the configuration phase (registries, known packs), finish configuration |
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
