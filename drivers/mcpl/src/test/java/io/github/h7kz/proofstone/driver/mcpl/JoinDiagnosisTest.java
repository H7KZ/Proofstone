package io.github.h7kz.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Stage classification of a join that timed out (T-M6-14). Lines are shaped like McplSession's transcript. */
class JoinDiagnosisTest {

    private static final String T = "2026-11-09T10:00:00Z ";

    private static List<String> transcript(String... events) {
        List<String> lines = new ArrayList<>();
        for (String event : events) {
            lines.add(T + event);
        }
        return lines;
    }

    private static final String CONNECTED = "connected";
    private static final String HANDSHAKE = "-> ClientIntentionPacket protocol=775";
    private static final String LOGIN_START = "-> ServerboundHelloPacket";
    private static final String COMPRESSION = "<- ClientboundLoginCompressionPacket";
    private static final String ENCRYPTION = "<- ClientboundHelloPacket";
    private static final String LOGIN_FINISHED = "<- ClientboundLoginFinishedPacket";
    private static final String LOGIN_ACK = "-> ServerboundLoginAcknowledgedPacket";
    private static final String REGISTRY = "<- ClientboundRegistryDataPacket";
    private static final String FINISH_CONFIG_IN = "<- ClientboundFinishConfigurationPacket";
    private static final String FINISH_CONFIG_OUT = "-> ServerboundFinishConfigurationPacket";

    @Test
    void nothingAtAllMeansTheTcpConnectNeverCompleted() {
        JoinDiagnosis d = JoinDiagnosis.classify(transcript());
        assertThat(d.stage()).isEqualTo(JoinStage.NO_TCP_CONNECTION);
        assertThat(d.protocolOffered()).isEqualTo("none (no handshake sent)");
        assertThat(d.lastReceived()).isEqualTo("none");
    }

    @Test
    void connectedButNoHandshakeSent() {
        assertThat(JoinDiagnosis.classify(transcript(CONNECTED)).stage()).isEqualTo(JoinStage.CONNECTED_NO_HANDSHAKE);
    }

    @Test
    void aSentPacketImpliesTheConnectionEvenWithoutAConnectedLine() {
        // The library's own listener may send the handshake before ours sees the connected event.
        assertThat(JoinDiagnosis.classify(transcript(HANDSHAKE)).stage())
                .isEqualTo(JoinStage.HANDSHAKE_SENT_NO_LOGIN_START);
    }

    @Test
    void handshakeSentButTheLoginStartWasNeverSent() {
        JoinDiagnosis d = JoinDiagnosis.classify(transcript(CONNECTED, HANDSHAKE));
        assertThat(d.stage()).isEqualTo(JoinStage.HANDSHAKE_SENT_NO_LOGIN_START);
        assertThat(d.protocolOffered()).isEqualTo("775");
    }

    @Test
    void loginStartSentAndTheServerSaidNothing() {
        // The shape of the three unexplained paper@26.3 reds: ClientIntentionPacket, ServerboundHelloPacket, then silence.
        JoinDiagnosis d = JoinDiagnosis.classify(transcript(CONNECTED, HANDSHAKE, LOGIN_START));
        assertThat(d.stage()).isEqualTo(JoinStage.LOGIN_START_SENT_NO_REPLY);
        assertThat(d.lastReceived()).isEqualTo("none");
        assertThat(d.stage().summary()).contains("login start sent").contains("no reply");
    }

    @Test
    void compressionSetButNoLoginSuccess() {
        JoinDiagnosis d = JoinDiagnosis.classify(transcript(CONNECTED, HANDSHAKE, LOGIN_START, COMPRESSION));
        assertThat(d.stage()).isEqualTo(JoinStage.COMPRESSION_SET_NO_LOGIN_SUCCESS);
        assertThat(d.lastReceived()).isEqualTo("ClientboundLoginCompressionPacket");
    }

    @Test
    void anEncryptionRequestOfAnOnlineModeServerIsNamedAsSuch() {
        JoinDiagnosis d = JoinDiagnosis.classify(transcript(CONNECTED, HANDSHAKE, LOGIN_START, ENCRYPTION));
        assertThat(d.stage()).isEqualTo(JoinStage.ENCRYPTION_REQUESTED_NO_LOGIN_SUCCESS);
    }

    @Test
    void loginSuccessNotAcknowledgedByTheClient() {
        assertThat(JoinDiagnosis.classify(transcript(CONNECTED, HANDSHAKE, LOGIN_START, LOGIN_FINISHED)).stage())
                .isEqualTo(JoinStage.LOGIN_SUCCESS_NOT_ACKNOWLEDGED);
    }

    @Test
    void configurationStalledAfterTheAcknowledgement() {
        JoinDiagnosis d = JoinDiagnosis.classify(
                transcript(CONNECTED, HANDSHAKE, LOGIN_START, LOGIN_FINISHED, LOGIN_ACK, REGISTRY));
        assertThat(d.stage()).isEqualTo(JoinStage.CONFIGURATION_STALLED);
        assertThat(d.lastReceived()).isEqualTo("ClientboundRegistryDataPacket");
    }

    @Test
    void configurationFinishedByTheServerButNotAnsweredByTheClient() {
        assertThat(JoinDiagnosis.classify(transcript(CONNECTED, HANDSHAKE, LOGIN_START, LOGIN_FINISHED, LOGIN_ACK,
                FINISH_CONFIG_IN)).stage()).isEqualTo(JoinStage.CONFIGURATION_FINISH_NOT_ANSWERED);
    }

    @Test
    void playStageWithNoJoinPacket() {
        JoinDiagnosis d = JoinDiagnosis.classify(transcript(CONNECTED, HANDSHAKE, LOGIN_START, LOGIN_FINISHED,
                LOGIN_ACK, FINISH_CONFIG_IN, FINISH_CONFIG_OUT, "<- ClientboundSetTimePacket"));
        assertThat(d.stage()).isEqualTo(JoinStage.PLAY_NO_JOIN_PACKET);
        assertThat(d.lastReceived()).isEqualTo("ClientboundSetTimePacket");
    }

    @Test
    void anUnrecognisedServerPacketBeforeLoginSuccessIsUnknownAndNamed() {
        JoinDiagnosis d = JoinDiagnosis.classify(
                transcript(CONNECTED, HANDSHAKE, LOGIN_START, "<- ClientboundCustomQueryPacket"));
        assertThat(d.stage()).isEqualTo(JoinStage.UNKNOWN);
        assertThat(d.lastReceived()).isEqualTo("ClientboundCustomQueryPacket");
    }

    @Test
    void theLastReceivedPacketKeepsItsNameWithoutTheTimestampOrTheDetail() {
        JoinDiagnosis d = JoinDiagnosis.classify(transcript(CONNECTED, HANDSHAKE, LOGIN_START, LOGIN_FINISHED,
                LOGIN_ACK, "<- ClientboundSystemChatPacket hello there"));
        assertThat(d.lastReceived()).isEqualTo("ClientboundSystemChatPacket");
    }

    @Test
    void aPacketErrorIsCarriedIntoTheMessage() {
        List<String> lines = transcript(CONNECTED, HANDSHAKE, LOGIN_START, LOGIN_FINISHED, LOGIN_ACK,
                "packet error: java.lang.IllegalStateException: bad registry");
        JoinDiagnosis d = JoinDiagnosis.classify(lines);
        assertThat(d.packetError()).isEqualTo("java.lang.IllegalStateException: bad registry");
        assertThat(d.message("Bot -> h:1", "PT30S", lines)).contains("bad registry");
    }

    @Test
    void theMessageKeepsTheTimedOutJoiningPrefixAndNamesTheStage() {
        List<String> lines = transcript(CONNECTED, HANDSHAKE, LOGIN_START);
        String message = JoinDiagnosis.classify(lines).message("Bot -> 127.0.0.1:25565", "PT30S", lines);
        // scripts/flake-loop.sh and troubleshooting.md classify on this prefix.
        assertThat(message).startsWith("timed out joining Bot -> 127.0.0.1:25565 after PT30S");
        assertThat(message).contains("stalled at LOGIN_START_SENT_NO_REPLY")
                .contains("protocol offered: 775")
                .contains("last packet received: none")
                .contains("transcript: ");
    }
}
