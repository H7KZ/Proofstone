package io.github.h7kz.proofstone.driver.mcpl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the client knows about a join that timed out: the stalled {@link JoinStage}, the protocol version it offered,
 * the last packet it received and any packet error. It reads only {@link McplSession}'s transcript, so it states what
 * the client saw and nothing about the server (the harness adds the server's view, T-M6-14).
 */
record JoinDiagnosis(JoinStage stage, String protocolOffered, String lastReceived, String packetError) {

    /** A transcript line: an optional timestamp, then {@code ->} or {@code <-}, the packet class and any detail. */
    private static final Pattern PACKET = Pattern.compile("^(?:\\S+ )?(->|<-) (\\S+)(?: (.*))?$");
    private static final Pattern PROTOCOL = Pattern.compile("protocol=(\\d+)");
    private static final String PACKET_ERROR = "packet error: ";

    static JoinDiagnosis classify(List<String> transcript) {
        List<String> sent = new ArrayList<>();
        List<String> received = new ArrayList<>();
        String protocol = null;
        String error = null;
        boolean connected = false;
        for (String line : transcript) {
            Matcher packet = PACKET.matcher(line);
            if (packet.matches()) {
                (packet.group(1).equals("->") ? sent : received).add(packet.group(2));
                Matcher version = PROTOCOL.matcher(packet.group(3) == null ? "" : packet.group(3));
                if (packet.group(2).equals("ClientIntentionPacket") && version.find()) {
                    protocol = version.group(1);
                }
            } else if (line.endsWith(" connected") || line.equals("connected")) {
                connected = true;
            } else if (line.contains(PACKET_ERROR)) {
                error = line.substring(line.indexOf(PACKET_ERROR) + PACKET_ERROR.length());
            }
        }
        boolean handshake = sent.contains("ClientIntentionPacket");
        String offered = !handshake ? "none (no handshake sent)" : protocol == null ? "unknown" : protocol;
        String last = received.isEmpty() ? "none" : received.get(received.size() - 1);
        return new JoinDiagnosis(stage(sent, received, connected || !sent.isEmpty() || !received.isEmpty()), offered,
                last, error);
    }

    private static JoinStage stage(List<String> sent, List<String> received, boolean connected) {
        if (!connected) {
            return JoinStage.NO_TCP_CONNECTION;
        }
        if (!sent.contains("ClientIntentionPacket")) {
            return JoinStage.CONNECTED_NO_HANDSHAKE;
        }
        if (!sent.contains("ServerboundHelloPacket")) {
            return JoinStage.HANDSHAKE_SENT_NO_LOGIN_START;
        }
        if (received.isEmpty()) {
            return JoinStage.LOGIN_START_SENT_NO_REPLY;
        }
        if (!received.contains("ClientboundLoginFinishedPacket")) {
            if (received.contains("ClientboundHelloPacket")) {
                return JoinStage.ENCRYPTION_REQUESTED_NO_LOGIN_SUCCESS;
            }
            return received.contains("ClientboundLoginCompressionPacket")
                    ? JoinStage.COMPRESSION_SET_NO_LOGIN_SUCCESS : JoinStage.UNKNOWN;
        }
        if (!received.contains("ClientboundFinishConfigurationPacket")) {
            return sent.contains("ServerboundLoginAcknowledgedPacket")
                    ? JoinStage.CONFIGURATION_STALLED : JoinStage.LOGIN_SUCCESS_NOT_ACKNOWLEDGED;
        }
        return sent.contains("ServerboundFinishConfigurationPacket")
                ? JoinStage.PLAY_NO_JOIN_PACKET : JoinStage.CONFIGURATION_FINISH_NOT_ANSWERED;
    }

    /**
     * The stage a server switch stopped at, from the transcript lines since the awaited switch began (T-M7-09). Like
     * {@link #classify}, it reads only what the client saw.
     */
    static JoinDiagnosis classifySwitch(List<String> sinceSwitch) {
        List<String> sent = new ArrayList<>();
        List<String> received = new ArrayList<>();
        for (String line : sinceSwitch) {
            Matcher packet = PACKET.matcher(line);
            if (packet.matches()) {
                (packet.group(1).equals("->") ? sent : received).add(packet.group(2));
            }
        }
        String last = received.isEmpty() ? "none" : received.get(received.size() - 1);
        JoinStage stage;
        if (!received.contains("ClientboundStartConfigurationPacket")) {
            stage = JoinStage.SWITCH_NOT_STARTED;
        } else if (!sent.contains("ServerboundConfigurationAcknowledgedPacket")) {
            stage = JoinStage.SWITCH_START_NOT_ACKNOWLEDGED;
        } else if (!received.contains("ClientboundFinishConfigurationPacket")) {
            stage = JoinStage.SWITCH_CONFIGURATION_STALLED;
        } else if (!sent.contains("ServerboundFinishConfigurationPacket")) {
            stage = JoinStage.SWITCH_FINISH_NOT_ANSWERED;
        } else {
            stage = JoinStage.SWITCH_NO_JOIN_PACKET;
        }
        return new JoinDiagnosis(stage, "n/a (server switch on a live connection)", last, null);
    }

    /**
     * The failure message. It starts {@code timed out joining}, which scripts/flake-loop.sh and the troubleshooting
     * guide classify on, then names the stage, then keeps the full transcript as before.
     */
    String message(String where, String timeout, List<String> transcript) {
        return "timed out joining " + where + " after " + timeout + "; stalled at " + stage + " (" + stage.summary()
                + "); protocol offered: " + protocolOffered + "; last packet received: " + lastReceived
                + (packetError == null ? "" : "; client packet error: " + packetError)
                + "; transcript: " + transcript;
    }

    /**
     * The message of a switch that did not complete (T-M7-09). It starts {@code timed out joining} like a join timeout,
     * so the flake accounting counts it, then names the stage. {@code replies} are the chat lines the client got since
     * the switch was awaited: a backend answering the command is the loud red the ADR asks for.
     */
    String switchMessage(String where, String timeout, List<String> replies, List<String> transcript) {
        return "timed out joining " + where + " during a server switch after " + timeout + "; stalled at " + stage
                + " (" + stage.summary() + "); last packet received: " + lastReceived
                + "; chat received while waiting: " + (replies.isEmpty() ? "none" : replies)
                + "; transcript: " + transcript;
    }
}
