package io.github.h7kz.proofstone.protocol;

/** Error body of a failed {@link Envelope}. {@code code} is an UPPER_SNAKE token such as {@code TIMEOUT}. */
public record ProtocolError(String code, String message, Object detail) {
}
