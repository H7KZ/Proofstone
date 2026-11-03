package dev.proofstone.agent.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Error body of a failed {@link Envelope}. {@code code} is an UPPER_SNAKE token such as {@code TIMEOUT}. */
public final class ProtocolError {

    private final String code;
    private final String message;
    private final Object detail;

    @JsonCreator
    public ProtocolError(
            @JsonProperty("code") String code,
            @JsonProperty("message") String message,
            @JsonProperty("detail") Object detail) {
        this.code = code;
        this.message = message;
        this.detail = detail;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Object getDetail() {
        return detail;
    }
}
