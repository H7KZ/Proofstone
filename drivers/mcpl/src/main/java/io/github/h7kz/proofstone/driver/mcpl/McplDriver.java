package io.github.h7kz.proofstone.driver.mcpl;

import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import io.github.h7kz.proofstone.core.driver.ClientDriver;
import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;

/**
 * The primary driver (ADR-0004): MCProtocolLib on the newest protocol only, offline auth.
 *
 * <p>The library's default listeners run login, the configuration phase (registries, known packs, finish
 * configuration) and keep-alive replies. {@link McplSession} only observes packets and sends chat and commands,
 * which is the whole packet surface this card needs (client-driver spec §3).
 */
public final class McplDriver implements ClientDriver {

    /** The Minecraft version of the pinned MCProtocolLib; it equals the {@code mcpl} protocol in versions.yml. */
    static final String PROTOCOL_VERSION = "26.3";

    @Override
    public String id() {
        return "mcpl";
    }

    @Override
    public String protocolVersion() {
        return PROTOCOL_VERSION;
    }

    @Override
    public ClientSession connect(ConnectSpec spec) {
        ClientNetworkSession network = ClientNetworkSessionFactory.factory()
                .setAddress(spec.host(), spec.port())
                .setProtocol(new MinecraftProtocol(spec.username()))
                .create();
        McplSession session = new McplSession(network, spec.username());
        network.addListener(session.listener());
        network.connect(false);
        String where = spec.username() + " -> " + spec.host() + ":" + spec.port();
        try {
            Await.until("play state for " + where, session::state, state -> state != McplSession.State.JOINING,
                    spec.connectTimeout());
        } catch (AwaitTimeoutException e) {
            session.close();
            throw new ClientDriverException("timed out joining " + where + " after " + spec.connectTimeout()
                    + "; transcript: " + session.transcript(), e);
        }
        if (session.state() == McplSession.State.DISCONNECTED) {
            session.close();
            throw new ClientDriverException("could not join " + where + ": " + session.disconnectReason());
        }
        return session;
    }
}
