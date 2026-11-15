package io.github.h7kz.proofstone.core.host;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * The resource-pack source of one cell (ADR-0017 section 7): an HTTP server in the harness JVM, bound to {@code 127.0.0.1}
 * on a free port, serving only the declared pack's bytes. The server never fetches the pack, only the client does, and the
 * client runs in this JVM, so loopback is enough (T-M8-01, docs/platforms/fabric.md): no port is exposed to a container.
 */
public final class ResourcePackSource implements AutoCloseable {

    private static final String HOST = "127.0.0.1";
    private static final Pattern SHA1 = Pattern.compile("[0-9a-fA-F]{40}");

    private final HttpServer server;
    private final ExecutorService handler;
    private final byte[] bytes;
    private final String sha1;
    private final String path;
    private final AtomicInteger requests = new AtomicInteger();

    private ResourcePackSource(byte[] bytes) throws IOException {
        this.bytes = bytes;
        this.sha1 = sha1(bytes);
        this.path = "/proofstone-pack/" + sha1 + ".zip";
        this.server = HttpServer.create(new InetSocketAddress(HOST, 0), 0);
        // Daemon threads, so a run that never closes the source still ends its JVM.
        this.handler = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "proofstone-pack-source");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(handler);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            boolean match = "GET".equals(exchange.getRequestMethod()) && path.equals(exchange.getRequestURI().getPath());
            byte[] body = match ? this.bytes : "not the declared pack".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(match ? 200 : 404, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    /**
     * Serves the pack the glob names.
     *
     * @param glob relative to {@code base}; it must match exactly one {@code .zip} file
     * @throws PaperHostException naming the glob when it matches none, several or a non-zip file
     */
    public static ResourcePackSource serve(Path base, String glob) {
        List<Path> found = Artefacts.resolve(base, List.of(glob));
        if (found.size() != 1) {
            throw new PaperHostException("resource pack glob \"" + glob + "\" matched " + found.size() + " files under "
                    + base + " " + found + "; it must match exactly one .zip");
        }
        Path file = found.get(0);
        if (!file.getFileName().toString().endsWith(".zip")) {
            throw new PaperHostException("resource pack glob \"" + glob + "\" matched " + file + ", which is not a .zip");
        }
        try {
            return new ResourcePackSource(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot serve the resource pack " + file, e);
        }
    }

    /** The source's base URL; the driver downloads only from under it. */
    public String base() {
        return "http://" + HOST + ":" + server.getAddress().getPort() + "/";
    }

    /** The pack's URL, which the server pushes. */
    public String url() {
        return base() + path.substring(1);
    }

    /** The sha1 of the served bytes, in lowercase hex. */
    public String sha1() {
        return sha1;
    }

    /** How many requests reached the source, for the run's evidence of who fetched the pack. */
    public int requests() {
        return requests.get();
    }

    /**
     * The image's variables that write the pack into {@code server.properties} ({@code property-definitions.json} of the
     * pinned image): the URL, the sha1, an id (set, so the server does not derive one and warn), the required flag and the
     * prompt.
     *
     * @param prompt a JSON text component, or empty for none
     * @param pushedSha1 the sha1 to push instead of the file's (a test of a wrong hash), or empty for the file's own
     * @throws IllegalArgumentException as an INVALID test declaration for a plain-string prompt or a malformed sha1
     */
    public Map<String, String> env(boolean required, String prompt, String pushedSha1) {
        if (!prompt.isEmpty() && !prompt.strip().startsWith("{")) {
            throw new IllegalArgumentException("INVALID test declaration: the resource-pack prompt \"" + prompt + "\" is not"
                    + " a JSON text component such as {\"text\":\"...\"}; the server drops a plain string"
                    + " (docs/platforms/fabric.md, Gotchas)");
        }
        if (!pushedSha1.isEmpty() && !SHA1.matcher(pushedSha1).matches()) {
            throw new IllegalArgumentException("INVALID test declaration: the resource-pack sha1 \"" + pushedSha1
                    + "\" is not 40 hex digits");
        }
        Map<String, String> env = new LinkedHashMap<>();
        env.put("RESOURCE_PACK", url());
        env.put("RESOURCE_PACK_SHA1", pushedSha1.isEmpty() ? sha1 : pushedSha1);
        env.put("RESOURCE_PACK_ID", UUID.nameUUIDFromBytes(sha1.getBytes(StandardCharsets.US_ASCII)).toString());
        env.put("RESOURCE_PACK_ENFORCE", required ? "TRUE" : "FALSE");
        if (!prompt.isEmpty()) {
            env.put("RESOURCE_PACK_PROMPT", prompt);
        }
        return env;
    }

    @Override
    public void close() {
        server.stop(0);
        handler.shutdownNow();
    }

    private static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("the JDK has no SHA-1", e);
        }
    }

    @Override
    public String toString() {
        return "ResourcePackSource[" + url() + "]";
    }
}
