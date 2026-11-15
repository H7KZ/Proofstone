package io.github.h7kz.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.junit.jupiter.api.Test;

/**
 * T-M7-19: the join teleport is acknowledged after the server's first player tick, which the first SetHealth marks.
 * Paper and Purpur handle the acknowledgement's position before that tick, when their last good position is still
 * 0, 0, 0, and log "moved too quickly!" (ROADMAP "M7 follow-up findings (T-M7-18)"). No server: scripted packets.
 */
class McplJoinAckTest {

    private final List<Packet> sent = new ArrayList<>();
    private final Session network = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(),
            new Class<?>[] {Session.class}, (proxy, method, args) -> {
                if (method.getName().equals("send")) {
                    sent.add((Packet) args[0]);
                }
                return null;
            });
    private final McplSession session = new McplSession(network, "Bot");

    private void received(Packet packet) {
        session.listener().packetReceived(network, packet);
    }

    private static ClientboundLoginPacket login() {
        return new ClientboundLoginPacket(1, false, new Key[0], 20, 8, 8, false, true, false, null, false, false);
    }

    private static ClientboundPlayerPositionPacket position(int id, double y) {
        return new ClientboundPlayerPositionPacket(id, Vector3d.from(0.5, y, 0.5), Vector3d.ZERO, 0, 0, List.of());
    }

    private static ClientboundSetHealthPacket health() {
        return new ClientboundSetHealthPacket(20f, 20, 5f);
    }

    private List<Integer> acknowledgedIds() {
        return sent.stream().filter(ServerboundAcceptTeleportationPacket.class::isInstance)
                .map(p -> ((ServerboundAcceptTeleportationPacket) p).getId()).toList();
    }

    private long loadedCount() {
        return sent.stream().filter(ServerboundPlayerLoadedPacket.class::isInstance).count();
    }

    @Test
    void theJoinTeleportAndPlayerLoadedWaitForTheFirstHealthPacket() {
        received(login());
        received(position(7, 64));

        assertThat(acknowledgedIds()).as("not before the first tick").isEmpty();
        assertThat(loadedCount()).isZero();
        assertThat(session.position()).as("the position is known at once").containsExactly(0.5, 64, 0.5);
        assertThat(session.joinAcknowledgementHeld()).isTrue();

        received(health());

        assertThat(acknowledgedIds()).containsExactly(7);
        assertThat(loadedCount()).isEqualTo(1);
        assertThat(session.joinAcknowledgementHeld()).isFalse();
    }

    @Test
    void theNewestHeldPositionIsTheOneAcknowledged() {
        received(login());
        received(position(7, 64));
        received(position(8, 65));
        received(health());

        assertThat(acknowledgedIds()).containsExactly(8);
        assertThat(loadedCount()).isEqualTo(1);
    }

    @Test
    void laterPositionsAreAcknowledgedAtOnce() {
        received(login());
        received(position(7, 64));
        received(health());
        received(position(9, 70));

        assertThat(acknowledgedIds()).containsExactly(7, 9);
        assertThat(loadedCount()).as("player loaded is once per join").isEqualTo(1);
        received(health());
        assertThat(acknowledgedIds()).as("a later health packet changes nothing").containsExactly(7, 9);
    }

    @Test
    void aNewLoginHoldsTheNextJoinTeleportAgain() {
        received(login());
        received(position(7, 64));
        received(health());
        received(login());
        received(position(1, 80));

        assertThat(acknowledgedIds()).as("the second server's teleport is held").containsExactly(7);
        received(health());
        assertThat(acknowledgedIds()).containsExactly(7, 1);
        assertThat(loadedCount()).isEqualTo(2);
    }

    @Test
    void aJoinThatNeverGetsAHealthPacketIsNamedByItsStage() {
        List<String> transcript = List.of("connected", "-> ClientIntentionPacket protocol=775", "-> ServerboundHelloPacket",
                "<- ClientboundLoginFinishedPacket", "-> ServerboundLoginAcknowledgedPacket",
                "<- ClientboundFinishConfigurationPacket", "-> ServerboundFinishConfigurationPacket",
                "<- ClientboundLoginPacket", "<- ClientboundPlayerPositionPacket");

        JoinDiagnosis diagnosis = JoinDiagnosis.classify(transcript);

        assertThat(diagnosis.stage()).isEqualTo(JoinStage.JOIN_TELEPORT_HELD_NO_HEALTH);
        assertThat(diagnosis.message("Bot -> h:1", "10s", transcript)).contains("JOIN_TELEPORT_HELD_NO_HEALTH");
    }
}
