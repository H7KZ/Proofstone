package io.github.h7kz.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.api.Face;
import io.github.h7kz.proofstone.api.ResourcePackAnswer.Answer;
import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.ResourcePackPolicy;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.junit.jupiter.api.Test;

/**
 * T-M11-04: {@code placeBlock} and {@code breakBlock} send the packets of a real player and return on the server's
 * block-change acknowledgement of their own sequence, driven by scripted packets with no server.
 */
class McplBlockActionTest {

    private static final Duration WAIT = Duration.ofMillis(300);

    private final List<Packet> sent = new ArrayList<>();
    // The scripted server: silent never answers, acksStartOnly answers a start but not the finish, disconnectOnSend kicks.
    private boolean silent;
    private boolean acksStartOnly;
    private boolean disconnectOnSend;
    private McplSession bot;
    private final Session network = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(),
            new Class<?>[] {Session.class}, (proxy, method, args) -> {
                if (method.getName().equals("send")) {
                    Packet packet = (Packet) args[0];
                    sent.add(packet);
                    if (disconnectOnSend && (packet instanceof ServerboundUseItemOnPacket
                            || packet instanceof ServerboundPlayerActionPacket)) {
                        bot.listener().disconnected(
                                new DisconnectedEvent((Session) proxy, Component.text("Kicked: flying"), null));
                    } else if (acksStartOnly && packet instanceof ServerboundPlayerActionPacket action
                            && action.getAction() == PlayerAction.START_DIGGING) {
                        ack(action.getSequence());
                    } else if (silent || acksStartOnly) {
                        return null;
                    } else if (packet instanceof ServerboundUseItemOnPacket use) {
                        bot.listener().packetReceived(null, new ClientboundBlockChangedAckPacket(use.getSequence()));
                    } else if (packet instanceof ServerboundPlayerActionPacket action
                            && action.getAction() == PlayerAction.FINISH_DIGGING) {
                        bot.listener().packetReceived(null, new ClientboundBlockChangedAckPacket(action.getSequence()));
                    }
                }
                return null;
            });

    private void ack(int sequence) {
        bot.listener().packetReceived(null, new ClientboundBlockChangedAckPacket(sequence));
    }

    private void join() {
        bot = new McplSession(network, "Bot", new ResourcePackPolicy(Answer.DECLINE, "http://127.0.0.1:40000/"),
                url -> new byte[0], Runnable::run);
        bot.listener().packetReceived(null,
                new ClientboundLoginPacket(1, false, new Key[0], 20, 8, 8, false, true, false, null, false, false));
        bot.listener().packetReceived(null,
                new ClientboundPlayerPositionPacket(1, Vector3d.from(0.5, 64, 0.5), Vector3d.ZERO, 0, 0, List.of()));
        sent.clear();
    }

    @Test
    void placeBlockLooksThenUsesTheFaceOfTheBlockAndReturnsOnItsOwnAcknowledgement() {
        join();

        bot.placeBlock(new BlockPos(0, 63, 0), Face.UP);

        assertThat(sent).hasSize(2);
        assertThat(sent.get(0)).isInstanceOf(ServerboundMovePlayerRotPacket.class);
        ServerboundUseItemOnPacket use = (ServerboundUseItemOnPacket) sent.get(1);
        assertThat(use.getPosition()).isEqualTo(Vector3i.from(0, 63, 0));
        assertThat(use.getFace()).isEqualTo(Direction.UP);
        assertThat(use.getCursorY()).isEqualTo(1.0f);
    }

    @Test
    void breakBlockLooksStartsThenFinishesWithAscendingSequencesAndReturnsOnTheFinish() {
        join();

        bot.breakBlock(new BlockPos(0, 64, 1));

        assertThat(sent).hasSize(3);
        assertThat(sent.get(0)).isInstanceOf(ServerboundMovePlayerRotPacket.class);
        ServerboundPlayerActionPacket start = (ServerboundPlayerActionPacket) sent.get(1);
        ServerboundPlayerActionPacket finish = (ServerboundPlayerActionPacket) sent.get(2);
        assertThat(start.getAction()).isEqualTo(PlayerAction.START_DIGGING);
        assertThat(finish.getAction()).isEqualTo(PlayerAction.FINISH_DIGGING);
        assertThat(start.getPosition()).isEqualTo(Vector3i.from(0, 64, 1)).isEqualTo(finish.getPosition());
        assertThat(finish.getSequence()).isGreaterThan(start.getSequence());
    }

    @Test
    void aStaleAckOfAnEarlierPlaceDoesNotReleaseALaterOne() {
        join();
        silent = true;
        bot.blockWait(WAIT);

        assertThatThrownBy(() -> bot.placeBlock(new BlockPos(0, 63, 0), Face.UP)).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("did not answer placing against");
        ServerboundUseItemOnPacket use = (ServerboundUseItemOnPacket) sent.get(sent.size() - 1);
        ack(use.getSequence());
        assertThatThrownBy(() -> bot.placeBlock(new BlockPos(0, 63, 0), Face.UP)).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("did not answer placing against");
    }

    @Test
    void aStaleAckOfAnEarlierBreakDoesNotReleaseALaterOne() {
        join();
        silent = true;
        bot.blockWait(WAIT);

        assertThatThrownBy(() -> bot.breakBlock(new BlockPos(0, 64, 1))).isInstanceOf(ClientDriverException.class);
        ServerboundPlayerActionPacket finish = (ServerboundPlayerActionPacket) sent.get(sent.size() - 1);
        ack(finish.getSequence());
        assertThatThrownBy(() -> bot.breakBlock(new BlockPos(0, 64, 1))).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("did not answer breaking");
    }

    @Test
    void breakBlockWaitsForTheFinishAckAndNotTheStartAck() {
        join();
        acksStartOnly = true;
        bot.blockWait(WAIT);

        assertThatThrownBy(() -> bot.breakBlock(new BlockPos(0, 64, 1))).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("did not answer breaking");
        assertThat(sent).as("look, start and finish were all sent; only the start was acked").hasSize(3);
    }

    @Test
    void placeBlockTimesOutNamingTheActionWhenTheServerNeverAnswers() {
        join();
        silent = true;
        bot.blockWait(WAIT);

        assertThatThrownBy(() -> bot.placeBlock(new BlockPos(0, 63, 0), Face.UP)).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("did not answer placing against " + new BlockPos(0, 63, 0))
                .hasMessageContaining("within " + WAIT).hasMessageContaining("transcript tail");
    }

    @Test
    void aDisconnectDuringThePlaceFailsWithItsReasonInsteadOfWaiting() {
        join();
        disconnectOnSend = true;

        assertThatThrownBy(() -> bot.placeBlock(new BlockPos(0, 63, 0), Face.UP)).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("disconnected while placing against").hasMessageContaining("Kicked: flying");
    }

    @Test
    void aDisconnectDuringTheBreakFailsWithItsReason() {
        join();
        disconnectOnSend = true;

        assertThatThrownBy(() -> bot.breakBlock(new BlockPos(0, 64, 1))).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("disconnected while breaking").hasMessageContaining("Kicked: flying");
    }

    @Test
    void aRejectedFinishIsNeverFollowedByAbortDigging() {
        join();

        bot.breakBlock(new BlockPos(0, 64, 1));

        assertThat(sent).filteredOn(p -> p instanceof ServerboundPlayerActionPacket)
                .extracting(p -> ((ServerboundPlayerActionPacket) p).getAction())
                .containsExactly(PlayerAction.START_DIGGING, PlayerAction.FINISH_DIGGING);
    }

    @Test
    void beforeTheJoinIsCompleteTheCallsFailLoudly() {
        bot = new McplSession(network, "Bot", new ResourcePackPolicy(Answer.DECLINE, "http://127.0.0.1:40000/"),
                url -> new byte[0], Runnable::run);

        assertThatThrownBy(() -> bot.placeBlock(new BlockPos(0, 63, 0), Face.UP)).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("cannot use a block");
        assertThatThrownBy(() -> bot.breakBlock(new BlockPos(0, 63, 0))).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("cannot break a block");
        assertThat(sent).isEmpty();
    }
}
