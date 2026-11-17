package io.github.h7kz.proofstone.core.driver.bedrock;

import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.api.ResourcePackPush;
import io.github.h7kz.proofstone.core.driver.ChatMessage;
import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.Face;
import io.github.h7kz.proofstone.core.driver.WindowSession;
import io.github.h7kz.proofstone.core.driver.WindowView;
import io.github.h7kz.proofstone.core.driver.bedrock.RpcCalls.HostError;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.AwaitRequest;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.ChatRequest;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.CommandRequest;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Inventory;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Message;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Position;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Transcript;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * One joined Bedrock player, read through the driver host. The v1 surface is chat, command, {@link #awaitMessage},
 * inventory and position read, and disconnect; the rest fails loudly naming the edition (ADR-0018 section 2).
 */
final class BedrockSession implements ClientSession {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);

    private final RpcCalls rpc;
    private final String path;
    private final String name;
    private volatile boolean closed;

    BedrockSession(RpcCalls rpc, String sessionId, String name) {
        this.rpc = rpc;
        this.path = BedrockRpc.SESSIONS + "/" + sessionId;
        this.name = name;
    }

    @Override
    public void chat(String message) {
        call("POST", "/chat", new ChatRequest(message), BedrockRpc.Ack.class, CALL_TIMEOUT);
    }

    @Override
    public void command(String commandWithoutSlash) {
        call("POST", "/command", new CommandRequest(commandWithoutSlash), BedrockRpc.Ack.class, CALL_TIMEOUT);
    }

    @Override
    public ChatMessage awaitMessage(Pattern pattern, Duration timeout) {
        Message message = call("POST", "/await-message", new AwaitRequest(pattern.pattern(), timeout.toMillis()),
                Message.class, timeout);
        return new ChatMessage(message.text(), message.system());
    }

    @Override
    public WindowView inventory() {
        return new BedrockInventory(call("GET", "/inventory", null, Inventory.class, CALL_TIMEOUT).slots());
    }

    @Override
    public double[] position() {
        Position position = call("GET", "/position", null, Position.class, CALL_TIMEOUT);
        return new double[] {position.x(), position.y(), position.z()};
    }

    @Override
    public List<String> transcript() {
        return call("GET", "/transcript", null, Transcript.class, CALL_TIMEOUT).lines();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        call("DELETE", "", null, BedrockRpc.Ack.class, CALL_TIMEOUT);
    }

    @Override
    public void useBlock(BlockPos pos, Face face) {
        throw unsupported("useBlock");
    }

    @Override
    public WindowSession awaitWindow(Duration timeout) {
        throw unsupported("windows and clicks");
    }

    @Override
    public int experienceLevel() {
        throw unsupported("experienceLevel");
    }

    @Override
    public void awaitServerSwitch(Duration timeout) {
        throw unsupported("awaitServerSwitch");
    }

    @Override
    public List<ResourcePackPush> resourcePacks() {
        throw unsupported("resource packs");
    }

    private <T> T call(String method, String suffix, Object request, Class<T> reply, Duration timeout) {
        try {
            return rpc.call(method, path + suffix, request, reply, timeout);
        } catch (HostError e) {
            throw new ClientDriverException("Bedrock session " + name + " " + method + " " + suffix + ": " + e.getMessage());
        }
    }

    private static UnsupportedOperationException unsupported(String what) {
        return new UnsupportedOperationException(what + " is not supported by the Bedrock edition driver in v1 "
                + "(ADR-0018 section 2)");
    }

}
