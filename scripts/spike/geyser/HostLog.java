// T-M9-01 measurement tool, not production code. A DNS logging forwarder: the container under test uses it as its only DNS
// server (docker --dns), so every host name the server's code looks up is written to the log with a timestamp, then the
// query is forwarded unchanged to the docker embedded resolver (127.0.0.11) and the answer relayed. A host cannot be
// contacted by name without a lookup, so the log is the complete list of names tried. A literal IP address would not show;
// the join phase of the spike removes the container's default route (see geyser-spike.sh), so that path is closed there.
// Usage: java HostLog <log file> [<stub keystore.p12> <own ip>]   (compiled with javac --release 21 on the host and copied in,
// as T-M8-07 did). With the optional stub arguments it also answers the two Microsoft discovery names with its own address
// and serves invented discovery, OpenID and JWKS documents over TLS on 443 (see geyser.md, "stub route"): a stand-in so the
// Bedrock login code finds its start-up documents without any packet reaching Microsoft. The documents are made up here;
// they carry no Microsoft key.
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class HostLog {
    private static Path log;
    private static byte[] stubIp;
    private static final java.util.Set<String> STUB_NAMES =
            java.util.Set.of("client.discovery.minecraft-services.net", "authorization.franchise.minecraft-services.net");

    public static void main(String[] args) throws Exception {
        log = Path.of(args[0]);
        if (args.length >= 3) {
            stubIp = InetAddress.getByName(args[2]).getAddress();
            startStub(Path.of(args[1]));
        }
        InetAddress upstream = InetAddress.getByName("127.0.0.11");
        try (DatagramSocket dns = new DatagramSocket(53)) {
            record("dns up");
            while (true) {
                byte[] buf = new byte[1500];
                DatagramPacket in = new DatagramPacket(buf, buf.length);
                dns.receive(in);
                byte[] q = java.util.Arrays.copyOf(in.getData(), in.getLength());
                Thread.ofVirtual().start(() -> serve(dns, in.getSocketAddress(), q, upstream));
            }
        }
    }

    private static void serve(DatagramSocket dns, java.net.SocketAddress from, byte[] q, InetAddress upstream) {
        try {
            int pos = 12;
            StringBuilder name = new StringBuilder();
            while (pos < q.length && q[pos] != 0) {
                int len = q[pos++] & 0xff;
                if (name.length() > 0) name.append('.');
                name.append(new String(q, pos, len, StandardCharsets.ISO_8859_1));
                pos += len;
            }
            int qtype = ((q[pos + 1] & 0xff) << 8) | (q[pos + 2] & 0xff);
            record("DNS " + (qtype == 1 ? "A" : qtype == 28 ? "AAAA" : "type" + qtype) + " " + name + " from " + from);
            // Block mode: while /hl/block exists every name gets NXDOMAIN except the stub names and the name suffixes listed in the
            // file (one per line), so a start that needs the network fails the way an offline machine would. The spike script
            // creates the file before the warm start.
            if (Files.exists(Path.of("/hl/block")) && !allowed(name.toString())) {
                int end = pos + 5;
                byte[] r = java.util.Arrays.copyOf(q, end);
                r[2] = (byte) 0x81; r[3] = (byte) 0x83; r[6] = r[7] = r[8] = r[9] = r[10] = r[11] = 0; // NXDOMAIN
                dns.send(new DatagramPacket(r, r.length, from));
                record("DNS BLOCKED " + (qtype == 1 ? "A" : qtype == 28 ? "AAAA" : "type" + qtype) + " " + name);
                return;
            }
            if (stubIp != null && qtype == 1 && STUB_NAMES.contains(name.toString())) {
                int end = pos + 5;
                byte[] r = new byte[end + 16];
                System.arraycopy(q, 0, r, 0, end);
                r[2] = (byte) 0x81; r[3] = (byte) 0x80; r[6] = 0; r[7] = 1; r[8] = r[9] = r[10] = r[11] = 0;
                int o = end;
                r[o++] = (byte) 0xc0; r[o++] = 0x0c; r[o++] = 0; r[o++] = 1; r[o++] = 0; r[o++] = 1;
                r[o++] = 0; r[o++] = 0; r[o++] = 0; r[o++] = 0; r[o++] = 0; r[o++] = 4;
                System.arraycopy(stubIp, 0, r, o, 4);
                dns.send(new DatagramPacket(r, r.length, from));
                record("STUB answered " + name);
                return;
            }
            try (DatagramSocket up = new DatagramSocket()) {
                up.setSoTimeout(5000);
                up.send(new DatagramPacket(q, q.length, upstream, 53));
                byte[] rb = new byte[1500];
                DatagramPacket r = new DatagramPacket(rb, rb.length);
                up.receive(r);
                dns.send(new DatagramPacket(rb, r.getLength(), from));
            }
        } catch (Exception e) {
            record("DNS error " + e);
        }
    }

    private static boolean allowed(String name) {
        if (stubIp != null && STUB_NAMES.contains(name)) return true;
        try {
            for (String suffix : Files.readAllLines(Path.of("/hl/block"))) {
                if (!suffix.isBlank() && name.endsWith(suffix.trim())) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void startStub(Path keystore) throws Exception {
        char[] pw = "changeit".toCharArray();
        java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
        try (java.io.InputStream in = Files.newInputStream(keystore)) { ks.load(in, pw); }
        javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, pw);
        javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        com.sun.net.httpserver.HttpsServer https = com.sun.net.httpserver.HttpsServer.create(new java.net.InetSocketAddress(443), 0);
        https.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(ctx));
        java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("EC");
        g.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        java.security.interfaces.ECPublicKey pub = (java.security.interfaces.ECPublicKey) g.generateKeyPair().getPublic();
        java.util.Base64.Encoder b64 = java.util.Base64.getUrlEncoder().withoutPadding();
        String jwks = "{\"keys\":[{\"kty\":\"EC\",\"crv\":\"P-256\",\"use\":\"sig\",\"kid\":\"proofstone-spike\",\"x\":\""
                + b64.encodeToString(unsigned(pub.getW().getAffineX())) + "\",\"y\":\"" + b64.encodeToString(unsigned(pub.getW().getAffineY())) + "\"}]}";
        String base = "https://authorization.franchise.minecraft-services.net";
        String discovery = "{\"result\":{\"serviceEnvironments\":{\"auth\":{\"prod\":{\"serviceUri\":\"" + base + "\"}}}}}";
        String openid = "{\"issuer\":\"" + base + "\",\"jwks_uri\":\"" + base + "/.well-known/keys\"}";
        https.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            record("STUB GET " + ex.getRequestHeaders().getFirst("Host") + path);
            String body = path.startsWith("/api/v1.0/discovery") ? discovery : path.endsWith("openid-configuration") ? openid : path.endsWith("keys") ? jwks : null;
            byte[] out = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(body == null ? 404 : 200, out.length);
            try (java.io.OutputStream o = ex.getResponseBody()) { o.write(out); }
        });
        https.start();
        record("stub https up");
    }

    private static byte[] unsigned(java.math.BigInteger v) {
        byte[] b = v.toByteArray();
        byte[] out = new byte[32];
        System.arraycopy(b, Math.max(0, b.length - 32), out, 32 - Math.min(32, b.length), Math.min(32, b.length));
        return out;
    }

    private static synchronized void record(String line) {
        try {
            Files.writeString(log, Instant.now() + " " + line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
