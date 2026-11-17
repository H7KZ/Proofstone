package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.host.standin.StandIn;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The stand-in's DNS answers, forwarding, name log and TLS documents, on loopback (T-M9-06). No Docker. */
class StandInTest {

    private static final byte[] OWN = {10, 1, 2, 3};

    @TempDir
    Path dir;

    /** A DNS query for {@code name} of {@code type} (a minimal question, as a resolver sends it). */
    static byte[] query(String name, int type) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0});
        for (String label : name.split("\\.")) {
            out.write(label.length());
            out.writeBytes(label.getBytes(StandardCharsets.ISO_8859_1));
        }
        out.write(0);
        out.writeBytes(new byte[] {0, (byte) type, 0, 1});
        return out.toByteArray();
    }

    @Test
    void anAQueryForEitherMicrosoftNameGetsTheStandInsOwnAddress() {
        for (String name : StandIn.NAMES) {
            byte[] query = query(name, 1);
            byte[] reply = StandIn.answer(query, StandIn.queryName(query), 1, OWN);
            assertThat(reply).isNotNull();
            assertThat(reply[0]).isEqualTo((byte) 0x12);
            assertThat(reply[3] & 0x0f).as("NOERROR").isZero();
            assertThat(reply[7]).as("one answer").isEqualTo((byte) 1);
            assertThat(Arrays.copyOfRange(reply, reply.length - 4, reply.length)).containsExactly(10, 1, 2, 3);
        }
    }

    @Test
    void anAaaaQueryForAMicrosoftNameGetsAnEmptySuccessAndIsNeverForwarded() {
        byte[] query = query(StandIn.DISCOVERY_NAME, 28);
        byte[] reply = StandIn.answer(query, StandIn.queryName(query), 28, OWN);
        assertThat(reply).isNotNull();
        assertThat(reply[3] & 0x0f).isZero();
        assertThat(reply[7]).as("no answer").isZero();
    }

    @Test
    void anyOtherNameIsNotAnsweredHereAndNamesAreLowerCased() {
        byte[] query = query("fill.papermc.io", 1);
        assertThat(StandIn.answer(query, StandIn.queryName(query), 1, OWN)).isNull();
        assertThat(StandIn.queryName(query("Login.Live.COM", 1))).isEqualTo("login.live.com");
        assertThat(StandIn.queryType(query("a.b", 28))).isEqualTo(28);
    }

    @Test
    void aMalformedQueryIsRejectedNotGuessed() {
        byte[] truncated = {0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 5, 'a', 'b'};
        assertThatThrownBy(() -> StandIn.queryName(truncated)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runsOnLoopbackAnswersTheNamesForwardsTheRestLogsEveryNameAndServesTlsDocuments() throws Exception {
        List<String> upstreamAsked = new CopyOnWriteArrayList<>();
        try (DatagramSocket upstream = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            // One upstream answer: the query echoed back as a reply, which is all the forwarding test needs.
            Thread thread = new Thread(() -> {
                try {
                    byte[] buffer = new byte[512];
                    DatagramPacket in = new DatagramPacket(buffer, buffer.length);
                    upstream.receive(in);
                    byte[] q = Arrays.copyOf(in.getData(), in.getLength());
                    upstreamAsked.add(StandIn.queryName(q));
                    upstream.send(new DatagramPacket(q, q.length, in.getSocketAddress()));
                } catch (Exception e) {
                    upstreamAsked.add("error " + e);
                }
            });
            thread.setDaemon(true);
            thread.start();

            CellTrust trust = CellTrust.generate();
            Files.write(dir.resolve("stand-in.p12"), trust.stubKeystore());
            List<String> log = new CopyOnWriteArrayList<>();
            StandIn standIn = new StandIn(OWN, new InetSocketAddress(InetAddress.getLoopbackAddress(), upstream.getLocalPort()),
                    log::add);
            try (DatagramSocket client = new DatagramSocket()) {
                standIn.start(new DatagramSocket(0, InetAddress.getLoopbackAddress()),
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), dir.resolve("stand-in.p12"));
                client.setSoTimeout(5000);
                InetSocketAddress dns = new InetSocketAddress(InetAddress.getLoopbackAddress(), standIn.dnsPort());

                byte[] own = ask(client, dns, query(StandIn.AUTH_NAME, 1));
                assertThat(own[own.length - 1]).isEqualTo((byte) 3);
                byte[] forwarded = ask(client, dns, query("fill.papermc.io", 1));
                assertThat(forwarded).isNotEmpty();
                assertThat(upstreamAsked).containsExactly("fill.papermc.io");

                // TLS with the generated certificate only (no image trust store), verified against the Microsoft name.
                KeyStore store = KeyStore.getInstance("PKCS12");
                store.load(null, null);
                store.setCertificateEntry("stand-in", java.security.cert.CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(trust.certificate())));
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(store);
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, tmf.getTrustManagers(), null);
                String name = StandIn.DISCOVERY_NAME;
                try (Socket plain = new Socket(InetAddress.getLoopbackAddress(), standIn.tlsPort());
                        SSLSocket tls = (SSLSocket) context.getSocketFactory().createSocket(plain, name, standIn.tlsPort(), true)) {
                    SSLParameters parameters = tls.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    tls.setSSLParameters(parameters);
                    tls.setSoTimeout(5000);
                    tls.getOutputStream().write(("GET /api/v1.0/discovery/MinecraftPE/builds/1.0.0.0 HTTP/1.1\r\nHost: " + name
                            + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    String response = new String(tls.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    assertThat(response).startsWith("HTTP/1.1 200").contains("serviceUri").contains(StandIn.AUTH_NAME);
                }
            } finally {
                standIn.close();
            }
            assertThat(log).anyMatch(line -> line.startsWith(StandIn.READY_PREFIX));
            assertThat(log).contains("DNS A " + StandIn.AUTH_NAME, "STUB answered " + StandIn.AUTH_NAME,
                    "DNS A fill.papermc.io", "STUB GET " + StandIn.DISCOVERY_NAME + "/api/v1.0/discovery/MinecraftPE/builds/1.0.0.0");
        }
    }

    private static byte[] ask(DatagramSocket client, InetSocketAddress server, byte[] query) throws Exception {
        client.send(new DatagramPacket(query, query.length, server));
        byte[] buffer = new byte[1500];
        DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
        client.receive(reply);
        return Arrays.copyOf(reply.getData(), reply.getLength());
    }
}
