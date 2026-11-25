package io.github.h7kz.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T-M12-01: a server that closes the connection while the join is stalled must not hide the stage. Before, a fast
 * disconnect ended as {@code could not join ...: disconnect.endOfStream} with no stage, so the join-stall class (T-M6-109)
 * could not be told from a plain kick. Lines are shaped like McplSession's transcript.
 */
class McplJoinDisconnectTest {

    private static final String T = "2026-11-24T10:00:00Z ";

    private static List<String> transcript(String... events) {
        List<String> lines = new ArrayList<>();
        for (String event : events) {
            lines.add(T + event);
        }
        return lines;
    }

    @Test
    void aDisconnectAfterTheLoginAcknowledgementNamesTheConfigurationStall() {
        String message = McplDriver.disconnectMessage("Bot -> localhost:25565", "disconnect.endOfStream", "", transcript(
                "connected", "-> ClientIntentionPacket protocol=775", "-> ServerboundHelloPacket",
                "<- ClientboundLoginFinishedPacket", "-> ServerboundLoginAcknowledgedPacket",
                "disconnected: disconnect.endOfStream"));

        assertThat(message).startsWith("could not join Bot -> localhost:25565: disconnect.endOfStream")
                .contains("the join ended at CONFIGURATION_STALLED").contains("last packet received: ClientboundLoginFinishedPacket");
    }

    @Test
    void aKickAtTheLoginStartNamesThatStageAndKeepsTheReasonAndThePackProblems() {
        String message = McplDriver.disconnectMessage("Bot -> localhost:25565", "Outdated server!", "http://x/p.zip: 404",
                transcript("connected", "-> ClientIntentionPacket protocol=775", "-> ServerboundHelloPacket",
                        "disconnected: Outdated server!"));

        assertThat(message).contains("Outdated server!").contains("LOGIN_START_SENT_NO_REPLY")
                .contains("resource packs: http://x/p.zip: 404");
    }

    @Test
    void aRefusedConnectionNamesThatNothingConnected() {
        String message = McplDriver.disconnectMessage("Bot -> localhost:1", "Connection refused", "",
                transcript("disconnected: Connection refused"));

        assertThat(message).startsWith("could not join Bot -> localhost:1: Connection refused")
                .contains("the join ended at NO_TCP_CONNECTION").contains("last packet received: none");
    }

    @Test
    void aKickAfterTheFirstHealthPacketAddsNoStageButKeepsTheLastPacket() {
        String message = McplDriver.disconnectMessage("Bot -> localhost:25565", "Kicked for flying", "", transcript(
                "connected", "-> ClientIntentionPacket protocol=775", "-> ServerboundHelloPacket",
                "<- ClientboundLoginFinishedPacket", "-> ServerboundLoginAcknowledgedPacket",
                "<- ClientboundFinishConfigurationPacket", "-> ServerboundFinishConfigurationPacket",
                "<- ClientboundLoginPacket", "<- ClientboundSetHealthPacket", "disconnected: Kicked for flying"));

        assertThat(message).isEqualTo("could not join Bot -> localhost:25565: Kicked for flying; last packet received: "
                + "ClientboundSetHealthPacket");
    }
}
