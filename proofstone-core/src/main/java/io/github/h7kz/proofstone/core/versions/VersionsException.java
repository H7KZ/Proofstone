package io.github.h7kz.proofstone.core.versions;

/** Any problem with the version matrix or a request against it. Always fails the run, never skips. */
public final class VersionsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public VersionsException(String message) {
        super(message);
    }

    public VersionsException(String message, Throwable cause) {
        super(message, cause);
    }
}
