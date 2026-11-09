package io.github.h7kz.proofstone.core.driver;

/**
 * The join did not reach the play state in time (T-M6-14). Its message starts {@code timed out joining} and names the
 * stalled handshake stage; the harness adds what the server's log shows. It carries the client's view in fields so
 * the harness can write it to the repro bundle.
 */
public class JoinTimeoutException extends ClientDriverException {

    private final String player;
    private final String stage;
    private final String protocolOffered;
    private final String lastReceived;

    /**
     * @param player the player name the client logged in as, which the server log names
     * @param stage the stalled handshake stage, as the driver names it
     * @param protocolOffered the protocol version the client offered in the handshake, or why there is none
     * @param lastReceived the class name of the last packet the client received, or {@code none}
     */
    public JoinTimeoutException(String message, Throwable cause, String player, String stage, String protocolOffered,
            String lastReceived) {
        super(message, cause);
        this.player = player;
        this.stage = stage;
        this.protocolOffered = protocolOffered;
        this.lastReceived = lastReceived;
    }

    public String player() {
        return player;
    }

    public String stage() {
        return stage;
    }

    public String protocolOffered() {
        return protocolOffered;
    }

    public String lastReceived() {
        return lastReceived;
    }

    /** The same timeout with more text after its message, for example the server's view. */
    public JoinTimeoutException withSuffix(String suffix) {
        return new JoinTimeoutException(getMessage() + suffix, getCause(), player, stage, protocolOffered,
                lastReceived);
    }
}
