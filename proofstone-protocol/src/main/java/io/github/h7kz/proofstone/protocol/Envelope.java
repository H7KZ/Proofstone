package io.github.h7kz.proofstone.protocol;

/**
 * Response envelope (spec §1). Exactly one of {@code data} and {@code error} is set, matching {@code ok}.
 *
 * @param <T> the success payload type
 */
public record Envelope<T>(boolean ok, T data, ProtocolError error) {
}
