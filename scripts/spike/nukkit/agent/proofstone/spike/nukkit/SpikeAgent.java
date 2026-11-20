// SPIKE (T-M10-13). Not production code. A throwaway PowerNukkitX plugin that serves two agent-protocol v1 reads,
// GET /v1/health and GET /v1/players, in the envelope of docs/specs/agent-protocol.md section 1, so the spike can show whether a
// Java plugin agent works on a Nukkit-family server. JDK HttpServer only (no dependency to shade). Observe-only: it reads
// Server#getOnlinePlayers and Player#getName on the main thread and changes nothing. Token from PROOFSTONE_AGENT_TOKEN,
// port from PROOFSTONE_AGENT_PORT (default 25580), as the spec says.
package proofstone.spike.nukkit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.plugin.PluginBase;

public final class SpikeAgent extends PluginBase {
    private HttpServer http;

    @Override
    public void onEnable() {
        String token = System.getenv("PROOFSTONE_AGENT_TOKEN");
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("PROOFSTONE_AGENT_TOKEN is not set");
        }
        int port = Integer.parseInt(System.getenv().getOrDefault("PROOFSTONE_AGENT_PORT", "25580"));
        try {
            http = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        } catch (IOException e) {
            throw new IllegalStateException("agent cannot listen on " + port, e);
        }
        http.setExecutor(Executors.newFixedThreadPool(2));
        http.createContext("/", ex -> handle(ex, token));
        http.start();
        getLogger().info("proofstone spike agent listening on " + port + " (protocol 1.3 subset: health, players)");
    }

    @Override
    public void onDisable() {
        if (http != null) {
            http.stop(0);
        }
    }

    private void handle(HttpExchange ex, String token) throws IOException {
        String rid = ex.getRequestHeaders().getFirst("X-Proofstone-Request-Id");
        String path = ex.getRequestURI().getPath();
        getLogger().info("request " + ex.getRequestMethod() + " " + path + " id=" + rid);
        if (!("Bearer " + token).equals(ex.getRequestHeaders().getFirst("Authorization"))) {
            send(ex, 401, "{\"ok\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"missing or wrong bearer token\"}}");
            return;
        }
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 404, err("NOT_FOUND", "spike agent serves GET only"));
            return;
        }
        try {
            if ("/v1/health".equals(path)) {
                String[] v = onMain(() -> new String[] {Server.getInstance().getNukkitVersion(), Server.getInstance().getVersion()});
                send(ex, 200, "{\"ok\":true,\"data\":{\"agentVersion\":\"0.0.1-spike\",\"protocol\":\"1.3\",\"platform\":\"powernukkitx\","
                        + "\"mcVersion\":" + q(v[1]) + ",\"capabilities\":[\"players\"]}}");
                getLogger().info("health read: server " + v[0] + ", mc " + v[1]);
            } else if ("/v1/players".equals(path)) {
                List<String> names = onMain(() -> {
                    List<String> out = new ArrayList<>();
                    for (Player p : Server.getInstance().getOnlinePlayers().values()) {
                        out.add(p.getName());
                    }
                    out.sort(null);
                    return out;
                });
                StringBuilder sb = new StringBuilder("{\"ok\":true,\"data\":{\"players\":[");
                for (int i = 0; i < names.size(); i++) {
                    sb.append(i == 0 ? "" : ",").append(q(names.get(i)));
                }
                send(ex, 200, sb.append("]}}").toString());
            } else {
                send(ex, 404, err("NOT_FOUND", "spike agent serves /v1/health and /v1/players only"));
            }
        } catch (Exception e) {
            send(ex, 500, err("INTERNAL", String.valueOf(e)));
        }
    }

    // Runs the read on the server's main thread and waits for it with a bound (await with timeout, AGENTS.md section 3).
    private <T> T onMain(java.util.function.Supplier<T> read) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Server.getInstance().getScheduler().scheduleTask(this, () -> {
            try {
                f.complete(read.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f.get(10, TimeUnit.SECONDS);
    }

    private static String err(String code, String message) {
        return "{\"ok\":false,\"error\":{\"code\":" + q(code) + ",\"message\":" + q(message) + "}}";
    }

    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : String.valueOf(s).toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(b);
        }
    }
}
