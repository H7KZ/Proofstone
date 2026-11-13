package io.github.h7kz.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundCustomQueryPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundCustomQueryAnswerPacket;
import org.junit.jupiter.api.Test;

/**
 * T-M7-11: a backend with Velocity forwarding asks every login for {@code velocity:player_info} (docs/platforms/velocity.md,
 * Direct join). A vanilla client answers a login query it does not know with an empty answer, and the backend then refuses
 * it with its own message. A client that stays silent only stalls the login, which hides the refusal as a timeout.
 */
class McplLoginQueryTest {

    private final List<Packet> sent = new ArrayList<>();
    private final Session network = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(),
            new Class<?>[] {Session.class}, (proxy, method, args) -> {
                if (method.getName().equals("send")) {
                    sent.add((Packet) args[0]);
                }
                return null;
            });
    private final McplSession session = new McplSession(network, "Bot");

    @Test
    void aLoginQueryIsAnsweredWithTheSameIdAndNoData() {
        session.listener().packetReceived(network,
                new ClientboundCustomQueryPacket(7, Key.key("velocity", "player_info"), new byte[] {1}));

        assertThat(sent).hasSize(1);
        ServerboundCustomQueryAnswerPacket answer = (ServerboundCustomQueryAnswerPacket) sent.get(0);
        assertThat(answer.getTransactionId()).isEqualTo(7);
        assertThat(answer.getData()).isNull();
    }

    @Test
    void theQueryAndItsAnswerAreInTheTranscriptAndTheSessionStaysJoining() {
        session.listener().packetReceived(network,
                new ClientboundCustomQueryPacket(7, Key.key("velocity", "player_info"), new byte[0]));

        assertThat(session.transcript()).anyMatch(line -> line.contains("ClientboundCustomQueryPacket"));
        assertThat(session.state()).isEqualTo(McplSession.State.JOINING);
    }
}
