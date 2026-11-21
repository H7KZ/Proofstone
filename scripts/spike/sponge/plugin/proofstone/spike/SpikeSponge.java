// SPIKE (T-M10-11). Throwaway SpongeVanilla plugin, not production code and not part of the build. It shows the agent's shape on
// Sponge API 21 and nothing more: an HTTP port opened at plugin construction (JDK com.sun.net.httpserver only), a "ready" flag set
// by StartedEngineEvent<Server>, and two read-only routes in the wire protocol's envelope:
//   GET /v1/health   {agentVersion, protocol, platform, mcVersion, capabilities} plus spike fields (ready, readyBy)
//   GET /v1/players  {players: [{name, uuid}]}, read on the server thread through the plugin's scheduler executor
// Every request needs "Authorization: Bearer $PROOFSTONE_AGENT_TOKEN" (401 UNAUTHORIZED otherwise), as agent-protocol.md section 1.
// Listeners are Order.POST (Sponge's last order) and only log: observe, never interfere (ADR-0002).
// No @Inject: the instance is created by Sponge's plain-Java loader and is registered as a listener automatically.
package proofstone.spike;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;

@Plugin("proofstone_spike")
public final class SpikeSponge {
    private static final String ID = "proofstone_spike";
    private static final long INIT = System.nanoTime();
    private volatile String readyBy;
    private volatile long readyMs = -1;

    public SpikeSponge() {
        int port = Integer.parseInt(System.getenv().getOrDefault("PROOFSTONE_SPIKE_PORT", "25590"));
        String token = System.getenv().getOrDefault("PROOFSTONE_AGENT_TOKEN", "");
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            server.createContext("/v1/health", ex -> handle(ex, token, this::health));
            server.createContext("/v1/players", ex -> handle(ex, token, this::players));
            server.start();
            log("plugin constructed, HTTP on port " + port + (token.isEmpty() ? " (no token set: every call is 401)" : ""));
        } catch (Exception e) {
            throw new IllegalStateException("[proofstone-spike] could not open port " + port, e);
        }
    }

    @Listener(order = Order.POST)
    public void onStarted(StartedEngineEvent<Server> event) {
        readyMs = (System.nanoTime() - INIT) / 1_000_000;
        readyBy = "StartedEngineEvent<Server>";
        log("ready by " + readyBy + " " + readyMs + " ms after construction");
    }

    @Listener(order = Order.POST)
    public void onJoin(ServerSideConnectionEvent.Join event) {
        log("event Join " + event.player().name());
    }

    @Listener(order = Order.POST)
    public void onLeave(ServerSideConnectionEvent.Disconnect event) {
        log("event Disconnect " + event.profile().map(p -> p.name().orElse("?")).orElse("?"));
    }

    private String health() {
        String mc = Sponge.platform().minecraftVersion().name();
        String version = container().metadata().version().toString();
        return "{\"agentVersion\":\"" + version + "-spike\",\"protocol\":\"1.3\",\"platform\":\"sponge\",\"mcVersion\":\"" + mc
                + "\",\"capabilities\":[\"players\"],\"ready\":" + (readyBy != null) + ",\"readyBy\":\"" + readyBy + "\"}";
    }

    private String players() throws Exception {
        if (readyBy == null) {
            throw new IllegalStateException("server not started");
        }
        // Read on the server thread, never from the HTTP thread; a bounded wait, never a partial answer.
        CompletableFuture<String> out = new CompletableFuture<>();
        Sponge.server().scheduler().executor(container()).execute(() -> {
            try {
                List<String> rows = new ArrayList<>();
                for (ServerPlayer p : Sponge.server().onlinePlayers()) {
                    rows.add("{\"name\":\"" + p.name() + "\",\"uuid\":\"" + p.uniqueId() + "\"}");
                }
                log("players read on thread " + Thread.currentThread().getName() + " onMainThread="
                        + Sponge.server().onMainThread());
                out.complete("{\"players\":[" + String.join(",", rows) + "]}");
            } catch (Throwable t) {
                out.completeExceptionally(t);
            }
        });
        return out.get(10_000, TimeUnit.MILLISECONDS);
    }

    private static PluginContainer container() {
        return Sponge.pluginManager().plugin(ID).orElseThrow(() -> new IllegalStateException("no container " + ID));
    }

    private interface Route {
        String data() throws Exception;
    }

    private static void handle(HttpExchange ex, String token, Route route) {
        int status;
        String body;
        try {
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            if (token.isEmpty() || !("Bearer " + token).equals(auth)) {
                status = 401;
                body = error("UNAUTHORIZED", "missing or wrong token");
            } else if (!"GET".equals(ex.getRequestMethod())) {
                status = 405;
                body = error("METHOD_NOT_ALLOWED", ex.getRequestMethod());
            } else {
                status = 200;
                body = "{\"ok\":true,\"data\":" + route.data() + "}";
            }
        } catch (Exception e) {
            status = 500;
            body = error("INTERNAL", String.valueOf(e));
        }
        log("HTTP " + ex.getRequestMethod() + " " + ex.getRequestURI() + " -> " + status + " id="
                + ex.getRequestHeaders().getFirst("X-Proofstone-Request-Id"));
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
        } catch (Exception ignored) {
            // The client went away; nothing to answer.
        } finally {
            ex.close();
        }
    }

    private static String error(String code, String message) {
        return "{\"ok\":false,\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message.replace("\"", "'") + "\"}}";
    }

    private static void log(String message) {
        System.out.println("[proofstone-spike] " + message + " (thread " + Thread.currentThread().getName() + ")");
    }
}
