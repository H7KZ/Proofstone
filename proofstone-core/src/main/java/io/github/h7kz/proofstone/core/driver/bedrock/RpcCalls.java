package io.github.h7kz.proofstone.core.driver.bedrock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Envelope;
import java.time.Duration;

/** One typed call of the driver RPC: sends, parses the envelope, and turns an error reply into the right exception. */
final class RpcCalls {

    /** Added to the host's own timeout so the host's TIMEOUT reply arrives before the transport gives up. */
    static final Duration MARGIN = Duration.ofSeconds(10);

    private final HostTransport transport;

    RpcCalls(HostTransport transport) {
        this.transport = transport;
    }

    <T> T call(String method, String path, Object request, Class<T> reply, Duration hostTimeout) {
        String body;
        try {
            body = request == null ? null : BedrockRpc.JSON.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new ClientDriverException("cannot encode the " + path + " request", e);
        }
        String raw = transport.call(method, path, body, hostTimeout.plus(MARGIN));
        JavaType type = BedrockRpc.JSON.getTypeFactory().constructParametricType(Envelope.class, reply);
        Envelope<T> envelope;
        try {
            envelope = BedrockRpc.JSON.readValue(raw, type);
        } catch (JsonProcessingException e) {
            // An unknown field or an unparsable reply is an error, never a default (AGENTS.md section 3).
            throw new ClientDriverException("driver host reply to " + method + " " + path + " is not RPC v1: " + raw, e);
        }
        if (envelope.ok()) {
            if (envelope.data() == null) {
                throw new ClientDriverException("driver host reply to " + method + " " + path + " has no data: " + raw);
            }
            return envelope.data();
        }
        if (envelope.error() == null) {
            throw new ClientDriverException("driver host reply to " + method + " " + path + " failed without an error: " + raw);
        }
        throw new HostError(envelope.error());
    }

    /** An error reply of the host; the caller maps it, because a join timeout needs the player and address. */
    static final class HostError extends ClientDriverException {
        private final transient BedrockRpc.RpcError error;

        HostError(BedrockRpc.RpcError error) {
            super(error.code() + ": " + error.message());
            this.error = error;
        }

        BedrockRpc.RpcError error() {
            return error;
        }
    }
}
