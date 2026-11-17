package io.github.h7kz.proofstone.driver.bedrock;

import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.concurrent.CountDownLatch;

/**
 * The Bedrock driver host program (ADR-0018 section 2): {@code java -jar bedrock-host-all.jar <token>} in a container
 * on the cell network. It serves driver RPC v1 on loopback port {@link BedrockRpc#PORT}, which the harness reaches with
 * curl inside the container, so no port is published. The token is the only argument: it is a per-cell secret that is
 * never logged.
 */
public final class BedrockHost {

    private BedrockHost() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || args[0].isBlank()) {
            System.err.println("usage: bedrock-host-all.jar <bearer token>");
            System.exit(2);
        }
        NioEventLoopGroup group = new NioEventLoopGroup(2);
        RpcServer server = new RpcServer(BedrockRpc.PORT, args[0], (id, request) -> HostSession.join(id, request, group));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            group.shutdownGracefully(0, 1, java.util.concurrent.TimeUnit.SECONDS);
        }));
        server.start();
        // The log line the harness waits for (ADR-0018 section 6, readiness).
        System.out.println("bedrock driver host ready: RPC v" + BedrockRpc.VERSION + ", Bedrock protocol "
                + BedrockRpc.PROTOCOL + " (" + BedrockRpc.GAME_VERSION + ")");
        new CountDownLatch(1).await();
    }
}
