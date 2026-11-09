package io.github.h7kz.proofstone.core.driver;

/** A driver failure. The message always carries the reason (kick text, timeout, protocol error). */
public class ClientDriverException extends RuntimeException {

    public ClientDriverException(String message) {
        super(message);
    }

    public ClientDriverException(String message, Throwable cause) {
        super(message, cause);
    }
}
