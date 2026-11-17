package io.github.h7kz.proofstone.core.host.standin;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * The discovery stand-in of a Geyser cell (ADR-0018 section 4), the spike's {@code HostLog} as production code. It runs in
 * a container of the pinned image ({@code java -cp}), so it is JDK classes only, Java 17 bytecode, and one class: the
 * harness copies the single {@code .class} file in. It does two things:
 * <ol>
 *   <li>DNS on UDP 53: the two Microsoft discovery names get this container's own address, every other name is forwarded
 *       unchanged to Docker's resolver, and every name asked is logged;
 *   <li>TLS on 443 with the certificate the harness generated for the run, serving invented discovery, OpenID and JWKS
 *       documents (they carry no Microsoft key, which is fine only because login validation is off).
 * </ol>
 * It logs to standard output, one line per event, which the harness reads as the container's log. The name guard that
 * fails a cell lives in the harness ({@code NameGuard}), not here.
 */
public final class StandIn {

    /** The Microsoft name the Cloudburst login code fetches its discovery document from (geyser.md, condition 3). */
    public static final String DISCOVERY_NAME = "client.discovery.minecraft-services.net";
    /** The Microsoft name whose OpenID configuration and JWKS it fetches next. */
    public static final String AUTH_NAME = "authorization.franchise.minecraft-services.net";
    /** The two names the stand-in answers itself. */
    public static final Set<String> NAMES = Set.of(DISCOVERY_NAME, AUTH_NAME);
    /** The log line that says DNS and TLS are both up; the harness waits for it. */
    public static final String READY_PREFIX = "stand-in ready: ";
    /** Docker's embedded resolver, inside every container on a user network. */
    public static final String DOCKER_RESOLVER = "127.0.0.11";
    /** The password of the keystore the harness copies in and of the trust store it builds; a public test value. */
    public static final String STORE_PASSWORD = "changeit";

    private static final int DNS_PORT = 53;
    private static final int TLS_PORT = 443;
    private static final int UPSTREAM_TIMEOUT_MILLIS = 5000;
    private static final int TYPE_A = 1;
    private static final int TYPE_AAAA = 28;

    private final byte[] ownAddress;
    private final InetSocketAddress upstream;
    private final Consumer<String> log;
    private DatagramSocket dns;
    private HttpsServer https;

    /**
     * @param ownAddress the IPv4 address given for the two names
     * @param upstream where every other name is forwarded
     * @param log one line per event
     */
    public StandIn(byte[] ownAddress, InetSocketAddress upstream, Consumer<String> log) {
        if (ownAddress.length != 4) {
            throw new IllegalArgumentException("the stand-in answers an IPv4 address, got " + ownAddress.length + " bytes");
        }
        this.ownAddress = ownAddress.clone();
        this.upstream = upstream;
        this.log = log;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: StandIn <keystore.p12>");
            System.exit(2);
        }
        StandIn standIn = new StandIn(ownAddress(), new InetSocketAddress(DOCKER_RESOLVER, DNS_PORT), line -> {
            System.out.println(line);
            System.out.flush();
        });
        standIn.start(new DatagramSocket(DNS_PORT), new InetSocketAddress(TLS_PORT), Path.of(args[0]));
        Thread.currentThread().join();
    }

    /**
     * Starts DNS on {@code dnsSocket} and TLS on {@code tlsAddress}, then logs {@link #READY_PREFIX}. The DNS thread is a
     * daemon, so the program ends with {@link #close()} or the container's stop.
     */
    public void start(DatagramSocket dnsSocket, InetSocketAddress tlsAddress, Path keystore) throws Exception {
        https = tls(tlsAddress, keystore);
        https.start();
        dns = dnsSocket;
        Thread loop = new Thread(this::dnsLoop, "stand-in-dns");
        loop.setDaemon(true);
        loop.start();
        log.accept(READY_PREFIX + "dns " + dnsSocket.getLocalPort() + ", tls " + https.getAddress().getPort()
                + ", address " + format(ownAddress));
    }

    /** The port the DNS socket listens on. */
    public int dnsPort() {
        return dns.getLocalPort();
    }

    /** The port the TLS server listens on. */
    public int tlsPort() {
        return https.getAddress().getPort();
    }

    public void close() {
        if (dns != null) {
            dns.close();
        }
        if (https != null) {
            https.stop(0);
        }
    }

    // ---- DNS ----

    private void dnsLoop() {
        while (!dns.isClosed()) {
            byte[] buffer = new byte[1500];
            DatagramPacket in = new DatagramPacket(buffer, buffer.length);
            try {
                dns.receive(in);
            } catch (IOException e) {
                if (!dns.isClosed()) {
                    log.accept("DNS error receiving: " + e);
                }
                return;
            }
            byte[] query = Arrays.copyOf(in.getData(), in.getLength());
            SocketAddress from = in.getSocketAddress();
            Thread worker = new Thread(() -> serve(from, query), "stand-in-query");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void serve(SocketAddress from, byte[] query) {
        try {
            String name = queryName(query);
            int type = queryType(query);
            log.accept("DNS " + typeName(type) + " " + name);
            byte[] reply = answer(query, name, type, ownAddress);
            if (reply != null) {
                log.accept((type == TYPE_A ? "STUB answered " : "STUB empty " + typeName(type) + " ") + name);
            } else {
                reply = forward(query);
            }
            dns.send(new DatagramPacket(reply, reply.length, from));
        } catch (IOException | RuntimeException e) {
            // A malformed or unanswerable query is dropped, never answered with a guess; the client times out.
            log.accept("DNS error " + e);
        }
    }

    /** One upstream round trip on a fresh socket, so concurrent queries never read each other's replies. */
    private byte[] forward(byte[] query) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(UPSTREAM_TIMEOUT_MILLIS);
            socket.send(new DatagramPacket(query, query.length, upstream));
            byte[] buffer = new byte[4096];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);
            return Arrays.copyOf(reply.getData(), reply.getLength());
        }
    }

    /**
     * The reply for one of the two stand-in names, or {@code null} for any other name (forward it). An {@code A} query gets
     * {@code ownAddress}; every other type gets an empty, successful answer, so the name is never looked up elsewhere
     * (the spike forwarded {@code AAAA}, which would have asked the real resolver for a Microsoft name).
     */
    public static byte[] answer(byte[] query, String name, int type, byte[] ownAddress) {
        if (!NAMES.contains(name)) {
            return null;
        }
        int end = questionEnd(query);
        boolean address = type == TYPE_A;
        byte[] reply = new byte[end + (address ? 16 : 0)];
        System.arraycopy(query, 0, reply, 0, end);
        reply[2] = (byte) 0x81; // QR, RD
        reply[3] = (byte) 0x80; // RA, NOERROR
        reply[4] = 0;
        reply[5] = 1; // one question
        reply[6] = 0;
        reply[7] = (byte) (address ? 1 : 0);
        reply[8] = 0;
        reply[9] = 0;
        reply[10] = 0;
        reply[11] = 0;
        if (address) {
            int o = end;
            reply[o] = (byte) 0xc0; // pointer to the question name
            reply[o + 1] = 0x0c;
            reply[o + 3] = TYPE_A;
            reply[o + 5] = 1; // class IN
            // TTL 0 (bytes o+6 to o+9): nothing caches the stand-in's answer.
            reply[o + 11] = 4;
            System.arraycopy(ownAddress, 0, reply, o + 12, 4);
        }
        return reply;
    }

    /** The lower-case name of the first question. */
    public static String queryName(byte[] query) {
        StringBuilder name = new StringBuilder();
        int pos = 12;
        while (true) {
            if (pos >= query.length) {
                throw new IllegalArgumentException("DNS query ends inside its name");
            }
            int len = query[pos++] & 0xff;
            if (len == 0) {
                return name.toString().toLowerCase(Locale.ROOT);
            }
            if ((len & 0xc0) != 0 || pos + len > query.length) {
                throw new IllegalArgumentException("DNS query name is compressed or truncated");
            }
            if (name.length() > 0) {
                name.append('.');
            }
            name.append(new String(query, pos, len, StandardCharsets.ISO_8859_1));
            pos += len;
        }
    }

    /** The type of the first question. */
    public static int queryType(byte[] query) {
        int pos = questionEnd(query) - 4;
        return ((query[pos] & 0xff) << 8) | (query[pos + 1] & 0xff);
    }

    /** The offset after the first question: its name, then 2 bytes of type and 2 of class. */
    private static int questionEnd(byte[] query) {
        int pos = 12;
        while (pos < query.length && query[pos] != 0) {
            pos += (query[pos] & 0xff) + 1;
        }
        int end = pos + 5;
        if (end > query.length) {
            throw new IllegalArgumentException("DNS query is shorter than its question");
        }
        return end;
    }

    private static String typeName(int type) {
        return type == TYPE_A ? "A" : type == TYPE_AAAA ? "AAAA" : "type" + type;
    }

    // ---- TLS ----

    private HttpsServer tls(InetSocketAddress address, Path keystore) throws Exception {
        char[] password = STORE_PASSWORD.toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keystore)) {
            store.load(in, password);
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);
        HttpsServer server = HttpsServer.create(address, 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context));
        String jwks = jwks();
        String base = "https://" + AUTH_NAME;
        String discovery = "{\"result\":{\"serviceEnvironments\":{\"auth\":{\"prod\":{\"serviceUri\":\"" + base + "\"}}}}}";
        String openid = "{\"issuer\":\"" + base + "\",\"jwks_uri\":\"" + base + "/.well-known/keys\"}";
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            log.accept("STUB GET " + exchange.getRequestHeaders().getFirst("Host") + path);
            String body = path.startsWith("/api/v1.0/discovery") ? discovery
                    : path.endsWith("openid-configuration") ? openid
                    : path.endsWith("keys") ? jwks : null;
            byte[] out = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(body == null ? 404 : 200, out.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(out);
            }
        });
        return server;
    }

    /** A JWKS with a key made up now: it signs nothing, and login validation is off, so nothing checks a signature. */
    private static String jwks() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        ECPublicKey key = (ECPublicKey) generator.generateKeyPair().getPublic();
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        return "{\"keys\":[{\"kty\":\"EC\",\"crv\":\"P-256\",\"use\":\"sig\",\"kid\":\"proofstone-stand-in\",\"x\":\""
                + base64.encodeToString(unsigned(key.getW().getAffineX())) + "\",\"y\":\""
                + base64.encodeToString(unsigned(key.getW().getAffineY())) + "\"}]}";
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(32, bytes.length);
        System.arraycopy(bytes, bytes.length - copy, out, 32 - copy, copy);
        return out;
    }

    // ---- address ----

    /** The one non-loopback IPv4 address of this container; none or several is an error, never a guess. */
    static byte[] ownAddress() throws SocketException {
        List<Inet4Address> found = new ArrayList<>();
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nic.isUp() || nic.isLoopback()) {
                continue;
            }
            for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                if (address instanceof Inet4Address v4) {
                    found.add(v4);
                }
            }
        }
        if (found.size() != 1) {
            throw new IllegalStateException("the stand-in needs exactly one IPv4 address on the cell network, found " + found);
        }
        return found.get(0).getAddress();
    }

    private static String format(byte[] address) {
        return (address[0] & 0xff) + "." + (address[1] & 0xff) + "." + (address[2] & 0xff) + "." + (address[3] & 0xff);
    }
}
