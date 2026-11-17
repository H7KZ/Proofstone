package io.github.h7kz.proofstone.core.driver.bedrock;

import io.github.h7kz.proofstone.core.driver.ClientDriver;
import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.Edition;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import io.github.h7kz.proofstone.core.driver.bedrock.RpcCalls.HostError;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.ConnectRequest;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Connected;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Health;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Bedrock client driver (ADR-0018, client-driver spec section 6): a thin client of the driver host, a separate
 * process on the cell network that holds the real Bedrock connection. State is the server's, never predicted.
 */
public final class BedrockDriver implements ClientDriver {

    public static final String ID = "bedrock";
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);
    /** Floodgate reads the XUID as a number; the base looks like an Xbox one. The counter makes each join's unique. */
    private static final AtomicLong NEXT_XUID = new AtomicLong(2_535_400_000_000_000L
            + new SecureRandom().nextInt(1_000_000_000));

    private final RpcCalls rpc;

    public BedrockDriver(HostTransport transport) {
        this.rpc = new RpcCalls(transport);
    }

    @Override
    public String id() {
        return ID;
    }

    /** The Bedrock game version of protocol {@link BedrockRpc#PROTOCOL}; never compared with a Java protocol. */
    @Override
    public String protocolVersion() {
        return BedrockRpc.GAME_VERSION;
    }

    @Override
    public Edition edition() {
        return Edition.BEDROCK;
    }

    /** A numeric XUID no earlier join of this JVM used (a repeated one is refused while the first is online). */
    static String nextXuid() {
        return Long.toString(NEXT_XUID.getAndIncrement());
    }

    @Override
    public ClientSession connect(ConnectSpec spec) {
        if (spec.bridged()) {
            throw new ClientDriverException("the bedrock driver never joins through a bridge (ADR-0018 section 3)");
        }
        BedrockRpc.requireName(spec.username());
        Health health = rpc.call("GET", BedrockRpc.HEALTH, null, Health.class, HEALTH_TIMEOUT);
        if (health.rpc() != BedrockRpc.VERSION || health.protocol() != BedrockRpc.PROTOCOL) {
            throw new ClientDriverException("driver host speaks RPC " + health.rpc() + " and Bedrock protocol "
                    + health.protocol() + ", this driver needs RPC " + BedrockRpc.VERSION + " and "
                    + BedrockRpc.PROTOCOL);
        }
        ConnectRequest request = new ConnectRequest(spec.host(), spec.port(), spec.username(), nextXuid(),
                spec.connectTimeout().toMillis());
        try {
            Connected connected = rpc.call("POST", BedrockRpc.SESSIONS, request, Connected.class, spec.connectTimeout());
            return new BedrockSession(rpc, connected.sessionId(), connected.name());
        } catch (HostError e) {
            throw joinFailure(e, spec);
        }
    }

    private static ClientDriverException joinFailure(HostError e, ConnectSpec spec) {
        BedrockRpc.RpcError error = e.error();
        if (BedrockRpc.JOIN_TIMEOUT.equals(error.code())) {
            String stage = error.stage() == null ? "unknown" : error.stage();
            return new JoinTimeoutException("timed out joining " + spec.host() + ":" + spec.port() + " as "
                    + spec.username() + " at stage " + stage + " after " + spec.connectTimeout().toMillis() + " ms: "
                    + error.message(), null, spec.username(), stage, "bedrock " + BedrockRpc.PROTOCOL,
                    error.lastPacket() == null ? "none" : error.lastPacket());
        }
        // A server's own text, such as the Xbox refusal (login validation is on), is quoted and never called a timeout.
        return new ClientDriverException("Bedrock join of " + spec.username() + " failed: " + error.code() + ": "
                + error.message());
    }
}
