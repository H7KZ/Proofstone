package io.github.h7kz.proofstone.core.host;

/** A cell host failed to start or become ready. The message always says why and carries the log tail. */
public final class PaperHostException extends RuntimeException {

    public PaperHostException(String message) {
        super(message);
    }

    public PaperHostException(String message, Throwable cause) {
        super(message, cause);
    }
}
