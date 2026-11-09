package io.github.h7kz.proofstone.agent.server;

import com.sun.net.httpserver.HttpExchange;
import io.github.h7kz.proofstone.agent.game.Game;
import java.util.Collections;

/**
 * Routes of the proxy-only endpoints (spec §2, protocol 1.2): the backends a proxy knows. A backend {@link Game} does
 * not override the two reads, so there they are a 404 and a backend agent never serves {@code /v1/proxy/*}.
 */
final class ProxyRoutes {

    private static final String SERVERS = "/v1/proxy/servers";
    private static final String SERVERS_PREFIX = SERVERS + "/";

    private final Game game;

    ProxyRoutes(Game game) {
        this.game = game;
    }

    /** True when the path belongs to the proxy endpoints. */
    static boolean handles(String path) {
        return SERVERS.equals(path) || path.startsWith(SERVERS_PREFIX);
    }

    Object dispatch(HttpExchange exchange, String method, String path) {
        AgentServer.requireMethod(method, "GET");
        if (SERVERS.equals(path)) {
            return Collections.singletonMap("servers", game.proxyServers(AgentServer.DEFAULT_TIMEOUT_MS));
        }
        return game.proxyServer(path.substring(SERVERS_PREFIX.length()), AgentServer.DEFAULT_TIMEOUT_MS);
    }
}
