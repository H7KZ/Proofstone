package dev.proofstone.driver.mineflayer;

import dev.proofstone.core.driver.ClientDriver;
import dev.proofstone.core.driver.ClientSession;
import dev.proofstone.core.driver.ConnectSpec;
import java.net.URI;

/**
 * The reference driver (ADR-0004): Mineflayer in a sidecar container, reached over JSON-RPC on a WebSocket. It is
 * used only for differential runs on rows where it is native, never as the primary driver.
 *
 * <p>The sidecar runs on the cell network, so {@link ConnectSpec#host()} and the port are as the sidecar sees them
 * (the server's alias, or ViaProxy's on a bridged cell). The sidecar's own address is given to the constructor.
 */
public final class MineflayerRefDriver implements ClientDriver {

    /**
     * The native row: Mineflayer 4.39.0 speaks protocol 775, which versions.yml lists as row 26.1.2
     * (drivers.mineflayer-ref max-native 26.1). Core bridges any other row, because it compares this string with
     * the row's Minecraft version.
     */
    static final String PROTOCOL_VERSION = "26.1.2";

    private final URI sidecar;

    /** @param sidecar the sidecar's WebSocket address as this JVM sees it, for example {@code ws://localhost:8765} */
    public MineflayerRefDriver(URI sidecar) {
        this.sidecar = sidecar;
    }

    @Override
    public String id() {
        return "mineflayer-ref";
    }

    @Override
    public String protocolVersion() {
        return PROTOCOL_VERSION;
    }

    @Override
    public ClientSession connect(ConnectSpec spec) {
        return MineflayerRefSession.open(sidecar, spec);
    }
}
