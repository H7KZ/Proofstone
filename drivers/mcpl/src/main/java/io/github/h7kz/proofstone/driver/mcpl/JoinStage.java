package io.github.h7kz.proofstone.driver.mcpl;

/**
 * The handshake step a join was stuck at when it timed out (T-M6-14). Each value says what the client saw, so the
 * step the server never answered is named in the failure instead of a bare timeout. {@link #UNKNOWN} is only for a
 * server packet that no step expects.
 */
enum JoinStage {
    NO_TCP_CONNECTION("no TCP connection completed: nothing was sent or received"),
    CONNECTED_NO_HANDSHAKE("connected, but the handshake (ClientIntentionPacket) was never sent"),
    HANDSHAKE_SENT_NO_LOGIN_START("handshake sent, but the login start (ServerboundHelloPacket) was never sent"),
    LOGIN_START_SENT_NO_REPLY("login start sent, no reply: no compression, encryption request or login success"),
    COMPRESSION_SET_NO_LOGIN_SUCCESS("compression was set, but no login success followed"),
    ENCRYPTION_REQUESTED_NO_LOGIN_SUCCESS(
            "the server asked for encryption (online mode?), and no login success followed"),
    LOGIN_SUCCESS_NOT_ACKNOWLEDGED("login success received, but the client never acknowledged it"),
    CONFIGURATION_STALLED("login acknowledged, configuration never finished"),
    CONFIGURATION_FINISH_NOT_ANSWERED("the server finished configuration, but the client never answered"),
    PLAY_NO_JOIN_PACKET("play state reached, but no join packet (ClientboundLoginPacket) arrived"),
    // A server switch on a live connection (T-M7-09): the same steps again, after the proxy moves the client.
    SWITCH_NOT_STARTED("a switch was awaited, but no configuration phase began: the proxy did not move the client"
            + " (an unreachable target, or the command reached a backend instead of the proxy)"),
    SWITCH_START_NOT_ACKNOWLEDGED("the proxy started a new configuration, but the client never acknowledged it"),
    SWITCH_CONFIGURATION_STALLED("switch configuration acknowledged, never finished"),
    SWITCH_FINISH_NOT_ANSWERED("the switch configuration finished, but the client never answered"),
    SWITCH_NO_JOIN_PACKET("switch configuration finished and answered, but the new server's join packet"
            + " (ClientboundLoginPacket) never arrived"),
    UNKNOWN("unknown: the server sent a packet that no handshake step expects here");

    private final String summary;

    JoinStage(String summary) {
        this.summary = summary;
    }

    String summary() {
        return summary;
    }
}
