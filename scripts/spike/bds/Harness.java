// T-M10-01 spike tool, not production code. The harness end of the "inverted transport": a BDS behaviour pack cannot listen,
// so it polls this server for requests and posts the answers and events back. JDK classes only (com.sun.net.httpserver).
// Runs in a container of the pinned image on the cell network (alias "harness"); the spike script drives it with curl
// inside that container, so no port is published.
//
// Pack-facing endpoints (bearer token):
//   GET  /pack/ping                  -> {"pong":<server ms>}                  (raw HTTP round trip, timed by the pack)
//   GET  /pack/poll                  -> {"id":n,"op":"players"} or 204        (long poll: held until a request or 5 s)
//   POST /pack/answer   {"id":n,...} -> 200                                   (the answer to request n)
//   POST /pack/event    {...}        -> 200                                   (an event the pack saw)
//   POST /pack/metric   {...}        -> 200                                   (the pack's own timing figures)
// Control endpoints (loopback only, used by the spike script through docker exec):
//   POST /ctl/request?op=players&timeoutMs=n -> the answer body plus the harness-side round trip, or 504
//   GET  /ctl/await-event?type=playerJoin&timeoutMs=n -> the first event of that type, or 504
//   GET  /ctl/log                    -> every line this harness recorded
// Waits are bounded waits on a condition (AGENTS.md section 3), never a fixed sleep.
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class Harness {
    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> PENDING = new ArrayDeque<>();
    private static final Map<Long, String> ANSWERS = new HashMap<>();
    private static final List<String> EVENTS = new ArrayList<>();
    private static final List<String> LOG = new ArrayList<>();
    private static long nextId = 1;
    private static String token;

    public static void main(String[] args) throws Exception {
        token = args[0];
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 64);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", Harness::handle);
        server.start();
        record("harness up on 8080");
    }

    private static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (path.startsWith("/ctl/")) {
                if (!ex.getRemoteAddress().getAddress().isLoopbackAddress()) {
                    reply(ex, 403, "{\"error\":\"control is loopback only\"}");
                    return;
                }
                control(ex, path);
                return;
            }
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            record("pack " + ex.getRequestMethod() + " " + path + " from " + ex.getRemoteAddress().getAddress().getHostAddress()
                    + (auth == null ? " (no auth)" : "") + (body.isEmpty() ? "" : " body=" + body));
            if (!("Bearer " + token).equals(auth)) {
                reply(ex, 401, "{\"error\":\"UNAUTHORIZED\"}");
                return;
            }
            switch (path) {
                case "/pack/ping" -> reply(ex, 200, "{\"pong\":" + System.currentTimeMillis() + "}");
                case "/pack/poll" -> {
                    String req = takeRequest(5_000);
                    if (req == null) reply(ex, 204, null); else reply(ex, 200, req);
                }
                case "/pack/answer" -> {
                    long id = Long.parseLong(body.replaceAll("(?s).*\"id\"\\s*:\\s*(\\d+).*", "$1"));
                    synchronized (LOCK) { ANSWERS.put(id, body); LOCK.notifyAll(); }
                    reply(ex, 200, "{}");
                }
                case "/pack/event" -> {
                    synchronized (LOCK) { EVENTS.add(body); LOCK.notifyAll(); }
                    reply(ex, 200, "{}");
                }
                case "/pack/metric" -> reply(ex, 200, "{}");
                default -> reply(ex, 404, "{\"error\":\"NOT_FOUND\"}");
            }
        } catch (RuntimeException | InterruptedException e) {
            record("error " + e);
            reply(ex, 500, "{\"error\":\"" + e.toString().replace('"', '\'') + "\"}");
        }
    }

    private static void control(HttpExchange ex, String path) throws IOException, InterruptedException {
        Map<String, String> q = query(ex.getRequestURI().getRawQuery());
        long timeoutMs = Long.parseLong(q.getOrDefault("timeoutMs", "10000"));
        switch (path) {
            case "/ctl/request" -> {
                long id;
                long t0 = System.nanoTime();
                synchronized (LOCK) {
                    id = nextId++;
                    PENDING.add("{\"id\":" + id + ",\"op\":\"" + q.get("op") + "\"}");
                    LOCK.notifyAll();
                }
                String answer = awaitValue(() -> ANSWERS.get(id), timeoutMs);
                double ms = (System.nanoTime() - t0) / 1e6;
                record("ctl request " + id + " op=" + q.get("op") + " rtt_ms=" + String.format("%.1f", ms) + " answer=" + answer);
                if (answer == null) reply(ex, 504, "{\"error\":\"TIMEOUT\",\"id\":" + id + "}");
                else reply(ex, 200, "{\"rttMs\":" + String.format("%.1f", ms) + ",\"answer\":" + answer + "}");
            }
            case "/ctl/await-event" -> {
                String type = "\"type\":\"" + q.get("type") + "\"";
                String ev = awaitValue(() -> EVENTS.stream().filter(e -> e.contains(type)).findFirst().orElse(null), timeoutMs);
                if (ev == null) reply(ex, 504, "{\"error\":\"TIMEOUT\"}"); else reply(ex, 200, ev);
            }
            case "/ctl/log" -> {
                synchronized (LOCK) { reply(ex, 200, String.join("\n", LOG) + "\n"); }
            }
            default -> reply(ex, 404, "{\"error\":\"NOT_FOUND\"}");
        }
    }

    private static String takeRequest(long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        synchronized (LOCK) {
            while (PENDING.isEmpty()) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) return null;
                LOCK.wait(left);
            }
            return PENDING.poll();
        }
    }

    private static String awaitValue(java.util.function.Supplier<String> probe, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        synchronized (LOCK) {
            String v;
            while ((v = probe.get()) == null) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) return null;
                LOCK.wait(left);
            }
            return v;
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) m.put(kv.substring(0, i), kv.substring(i + 1));
        }
        return m;
    }

    private static void record(String line) {
        String s = Instant.now() + " " + line;
        synchronized (LOCK) { LOG.add(s); }
        System.out.println(s);
    }

    private static void reply(HttpExchange ex, int status, String body) throws IOException {
        if (body == null) {
            ex.sendResponseHeaders(status, -1);
            ex.close();
            return;
        }
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
    }
}
