package dev.proofstone.agent.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response envelope (spec §1). Exactly one of {@code data} and {@code error} is set, matching {@code ok}.
 *
 * @param <T> the success payload type
 */
public final class Envelope<T> {

    private final boolean ok;
    private final T data;
    private final ProtocolError error;

    @JsonCreator
    public Envelope(
            @JsonProperty("ok") boolean ok,
            @JsonProperty("data") T data,
            @JsonProperty("error") ProtocolError error) {
        this.ok = ok;
        this.data = data;
        this.error = error;
    }

    public boolean isOk() {
        return ok;
    }

    public T getData() {
        return data;
    }

    public ProtocolError getError() {
        return error;
    }
}
