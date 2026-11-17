package io.github.h7kz.proofstone.core.driver.bedrock.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The driver RPC v1 contract between {@code BedrockDriver} (harness) and the Bedrock driver host in
 * {@code drivers/bedrock} (client-driver spec section 6, ADR-0018 section 2). Both sides compile against this one
 * class, so they cannot drift. It is the only part of core the host's jar carries, which is why it holds nothing but
 * JDK and Jackson types. It is internal: not the agent wire protocol, and no schema under {@code proofstone-protocol}.
 */
public final class BedrockRpc {

    /** The RPC version; a host that answers another one is an error, never a guess. */
    public static final int VERSION = 1;
    /** The Bedrock protocol number of codec {@code Bedrock_v2225} (game version 1.26.60). */
    public static final int PROTOCOL = 2225;
    public static final String GAME_VERSION = "1.26.60";
    /** The port the host listens on inside its container; never published (ADR-0018 section 2). */
    public static final int PORT = 25590;
    public static final String BASE = "/rpc/v1";
    public static final String REQUEST_ID_HEADER = "X-Proofstone-Request-Id";
    public static final String HEALTH = BASE + "/health";
    public static final String SESSIONS = BASE + "/sessions";

    /** Error codes of {@link RpcError#code()}. */
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String JOIN_TIMEOUT = "JOIN_TIMEOUT";
    /** The server ended the session; the message quotes its text. */
    public static final String DISCONNECTED = "DISCONNECTED";
    /** An await ran out; a partial result is never returned. */
    public static final String TIMEOUT = "TIMEOUT";
    /** A packet the driver reads had an unknown or malformed field: an error, never a default. */
    public static final String PROTOCOL_ERROR = "PROTOCOL_ERROR";
    public static final String INTERNAL = "INTERNAL";

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,15}");
    private static final Pattern XUID = Pattern.compile("[1-9][0-9]{0,18}");

    /** Fails on an unknown field in a reply or request, and leaves absent optional fields out (ADR-0018: no defaults). */
    public static final ObjectMapper JSON = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    private BedrockRpc() {
    }

    /** A Bedrock join stage, named by the stalled one in a timeout (ADR-0018 section 7). Each is entered at its event. */
    public enum JoinStage {
        /** Connecting over RakNet (protocol 11). */
        RAKNET_CONNECT,
        /** RakNet is up; waiting for the server's network settings. */
        NETWORK_SETTINGS,
        /** The unsigned login is sent; waiting for the server's encryption handshake. */
        LOGIN_SENT,
        /** Encryption is on; waiting for the login success. */
        ENCRYPTION_HANDSHAKE,
        /** Login succeeded; answering the resource pack exchange. */
        RESOURCE_PACKS,
        /** The pack exchange is answered; waiting for the start game packet. */
        START_GAME,
        /** Start game is in; waiting for the spawn status. */
        SPAWN_STATUS
    }

    /** The player name rule: 1 to 15 characters from {@code A-Za-z0-9_} (Floodgate prepends one {@code .}). */
    public static String requireName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Bedrock player name '" + name + "' must be 1 to 15 characters of A-Za-z0-9_");
        }
        return name;
    }

    /** A numeric XUID without a leading zero: Floodgate parses it as a number, an empty one fails (geyser.md). */
    public static String requireXuid(String xuid) {
        if (xuid == null || !XUID.matcher(xuid).matches()) {
            throw new IllegalArgumentException("XUID '" + xuid + "' must be a positive decimal number");
        }
        return xuid;
    }

    /** The reply envelope, as the agent's: {@code {ok, data}} or {@code {ok:false, error}}. */
    public record Envelope<T>(boolean ok, T data, RpcError error) {
    }

    /**
     * @param stage the stalled {@link JoinStage} name for {@link #JOIN_TIMEOUT}, else {@code null}
     * @param lastPacket the class name of the last packet received, for {@link #JOIN_TIMEOUT}, else {@code null}
     */
    public record RpcError(String code, String message, String stage, String lastPacket) {
    }

    /** The data of a call that only acknowledges (chat, command, close). */
    public record Ack(boolean done) {
    }

    public record Health(int rpc, int protocol, String gameVersion) {
    }

    /** @param joinTimeoutMillis how long the host may take to reach the spawn status */
    public record ConnectRequest(String host, int port, String name, String xuid, long joinTimeoutMillis) {
    }

    public record Connected(String sessionId, String name) {
    }

    public record ChatRequest(String message) {
    }

    public record CommandRequest(String command) {
    }

    public record AwaitRequest(String pattern, long timeoutMillis) {
    }

    /** One received line. {@code seq} counts from 0 per session. */
    public record Message(long seq, String text, boolean system) {
    }

    public record Messages(List<Message> messages) {
    }

    /** One inventory slot as the server synced it; {@code id} is the item's identifier, {@code count} 0 when empty. */
    public record Slot(int slot, String id, int runtimeId, int count) {
    }

    public record Inventory(List<Slot> slots) {
    }

    /** The last server-synced position: the start game packet carries the first, a move packet for this player the next. */
    public record Position(double x, double y, double z) {
    }

    public record Transcript(List<String> lines) {
    }
}
