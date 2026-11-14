// SPIKE (T-M8-01). The agent's shape, nothing more: an HTTP port opened from the environment at mod init, and a
// "ready" flag set by the earliest server-started event the loader offers. JDK only (com.sun.net.httpserver).
package proofstone.spike;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public final class SpikeHttp {
    private static final long INIT = System.nanoTime();
    private static volatile String loader = "?";
    private static volatile String readyBy;
    private static volatile long readyMs = -1;

    private SpikeHttp() {
    }

    public static void start(String loaderName) {
        loader = loaderName;
        int port = Integer.parseInt(System.getenv().getOrDefault("PROOFSTONE_SPIKE_PORT", "25590"));
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            server.createContext("/health", exchange -> {
                String body = "{\"loader\":\"" + loader + "\",\"ready\":" + (readyBy != null) + ",\"readyBy\":\""
                        + readyBy + "\",\"readyMsAfterInit\":" + readyMs + "}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            System.out.println("[proofstone-spike] " + loader + " mod init, HTTP on port " + port + " (thread "
                    + Thread.currentThread().getName() + ")");
        } catch (Exception e) {
            throw new IllegalStateException("[proofstone-spike] could not open port " + port, e);
        }
    }

    public static void ready(String event) {
        readyMs = (System.nanoTime() - INIT) / 1_000_000;
        readyBy = event;
        System.out.println("[proofstone-spike] ready by " + event + " " + readyMs + " ms after mod init (thread "
                + Thread.currentThread().getName() + ")");
    }

    public static void mark(String event) {
        System.out.println("[proofstone-spike] event " + event + " " + (System.nanoTime() - INIT) / 1_000_000
                + " ms after mod init (thread " + Thread.currentThread().getName() + ")");
    }
}
