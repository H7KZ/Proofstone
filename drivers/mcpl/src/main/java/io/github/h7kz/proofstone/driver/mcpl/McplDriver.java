package io.github.h7kz.proofstone.driver.mcpl;

import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import io.github.h7kz.proofstone.core.driver.ClientDriver;
import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;

/**
 * The primary driver (ADR-0004): MCProtocolLib on the newest protocol only, offline auth.
 *
 * <p>The library's default listeners run login, the configuration phase (registries, known packs, finish
 * configuration) and keep-alive replies. {@link McplSession} observes packets, sends chat and commands, and answers
 * resource-pack pushes as {@link ConnectSpec#resourcePacks()} says (client-driver spec §2b, §3).
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
        McplSession session = new McplSession(network, spec.username(), spec.resourcePacks(), ResourcePacks.http(),
                download -> Thread.ofVirtual().name("mcpl-pack-download").start(download));
        network.addListener(session.listener());
        network.connect(false);
        String where = spec.username() + " -> " + spec.host() + ":" + spec.port();
        Instant started = Instant.now();
        try {
            Await.until("play state for " + where, session::state, state -> state != McplSession.State.JOINING,
                    spec.connectTimeout());
            // The join is not done while the teleport acknowledgement is held for the server's first tick (T-M7-19).
            // Same budget, not a new one: a server that never sends its first SetHealth is a named join timeout.
            Duration left = spec.connectTimeout().minus(Duration.between(started, Instant.now()));
            Await.until("first tick (SetHealth) for " + where, () -> session.joinAcknowledgementHeld() && session.state() != McplSession.State.DISCONNECTED,
                    held -> !held,
                    left.isNegative() || left.isZero() ? Duration.ofMillis(1) : left);
        } catch (AwaitTimeoutException e) {
            session.close();
            // The stage comes from what the client saw (T-M6-14); the harness adds the server's side.
            List<String> transcript = session.transcript();
            JoinDiagnosis diagnosis = JoinDiagnosis.classify(transcript);
            throw new JoinTimeoutException(diagnosis.message(where, spec.connectTimeout().toString(), transcript), e,
                    spec.username(), diagnosis.stage().name(), diagnosis.protocolOffered(),
                    diagnosis.lastReceived());
        }
        if (session.state() == McplSession.State.DISCONNECTED) {
            session.close();
            // A pack the client could not take is part of why: the server's kick alone would hide it (T-M8-10).
            String packs = session.resourcePacks().stream().filter(push -> push.problem() != null)
                    .map(push -> push.url() + ": " + push.problem()).collect(Collectors.joining("; "));
            throw new ClientDriverException("could not join " + where + ": " + session.disconnectReason()
                    + (packs.isEmpty() ? "" : "; resource packs: " + packs));
        }
        return session;
    }
}
