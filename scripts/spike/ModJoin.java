// SPIKE (T-M8-01). Throwaway client for the mod-loader spike, not production code and not part of the build.
// Joins one server with the real mcpl driver (offline) a given number of times and counts every join. A failed join is
// printed with its message (a join timeout names its stalled stage, T-M6-14); nothing is retried.
//
// Optional, before the counted joins (SPIKE_PROBE=1): one raw MCProtocolLib "probe" connection that prints every login
// and configuration packet with its fields (channels, known packs, resource-pack push), because the driver's transcript
// keeps only packet class names. The probe answers a login query with no data, as the driver does, and answers a
// resource-pack push as SPIKE_PACK_ANSWER says (accept or decline); the counted driver joins never answer one.
//
// Optional (SPIKE_PACK=<zip>): serves that file at http://127.0.0.1:<SPIKE_PACK_PORT>/pack.zip from this JVM (the
// harness host) and logs every request, so the run shows who fetched the pack.
//
// Steps after each counted join (args after <outDir>), each "<kind>:<text>[=><regex>]":
//   console:<command>   sent to the server console with `docker exec <SPIKE_CONSOLE> mc-send-to-console`
//   cmd:<command>       sent by the client as a chat command
// With "=>", the step waits up to 20 s for a chat line matching the regex and prints it.
//
// Run through scripts/spike/mod-spike.sh, which builds the classpath:
//   java -cp <mcpl runtime classpath> scripts/spike/ModJoin.java <host> <port> <joins> <outDir> [steps...]

import com.sun.net.httpserver.HttpServer;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.driver.ChatMessage;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.driver.mcpl.McplDriver;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPushPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundResourcePackPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundCustomQueryPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundCustomQueryAnswerPacket;

public class ModJoin {

    private static final Duration JOIN = Duration.ofSeconds(30);
    private static final Duration WAIT = Duration.ofSeconds(20);

    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        int joins = Integer.parseInt(args[2]);
        Path out = Path.of(args[3]);
        List<String> steps = List.of(args).subList(4, args.length);
        Files.createDirectories(out);
        HttpServer packServer = servePack();
        if ("1".equals(System.getenv("SPIKE_PROBE"))) {
            probe(host, port, out);
        }
        McplDriver driver = new McplDriver();
        int joinOk = 0;
        int joinFail = 0;
        int stepOk = 0;
        int stepFail = 0;
        for (int i = 1; i <= joins; i++) {
            String name = "Spike" + i;
            ClientSession session;
            long started = System.nanoTime();
            try {
                session = driver.connect(new ConnectSpec(host, port, name, JOIN));
            } catch (RuntimeException e) {
                joinFail++;
                System.out.println("JOIN " + i + " FAIL " + e.getClass().getSimpleName() + ": " + oneLine(e.getMessage()));
                continue;
            }
            joinOk++;
            System.out.println("JOIN " + i + " OK " + name + " in " + (System.nanoTime() - started) / 1_000_000 + " ms");
            try (session) {
                for (String step : steps) {
                    String expanded = step.replace("{player}", name);
                    try {
                        runStep(session, i, expanded);
                        stepOk++;
                    } catch (RuntimeException e) {
                        stepFail++;
                        System.out.println("  [" + i + "] STEP FAIL " + expanded + " " + e.getClass().getSimpleName() + ": "
                                + oneLine(e.getMessage()));
                    }
                }
            } finally {
                Files.write(out.resolve("transcript-" + name + ".txt"), session.transcript());
            }
        }
        System.out.println("SUMMARY joins=" + joins + " joinOk=" + joinOk + " joinFail=" + joinFail + " stepOk=" + stepOk
                + " stepFail=" + stepFail);
        if (packServer != null) {
            packServer.stop(0);
        }
        System.exit(0);
    }

    private static void runStep(ClientSession session, int i, String step) throws Exception {
        String[] parts = step.split("=>", 2);
        String action = parts[0];
        if (action.startsWith("console:")) {
            String container = System.getenv("SPIKE_CONSOLE");
            Process p = new ProcessBuilder("docker", "exec", "--user", "1000", container, "mc-send-to-console",
                    action.substring("console:".length())).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            int code = p.waitFor();
            if (code != 0) {
                throw new IllegalStateException("mc-send-to-console exit " + code + ": " + output);
            }
        } else if (action.startsWith("cmd:")) {
            session.command(action.substring("cmd:".length()));
        } else {
            throw new IllegalArgumentException("unknown step kind: " + step);
        }
        if (parts.length == 2) {
            ChatMessage message = session.awaitMessage(Pattern.compile(parts[1]), WAIT);
            System.out.println("  [" + i + "] " + action + " -> " + message.text());
        } else {
            System.out.println("  [" + i + "] " + action + " sent");
        }
    }

    /** One raw connection that prints the fields of every login and configuration packet, then leaves once in play. */
    private static void probe(String host, int port, Path out) throws Exception {
        List<String> lines = new ArrayList<>();
        AtomicReference<String> end = new AtomicReference<>();
        String answer = System.getenv().getOrDefault("SPIKE_PACK_ANSWER", "accept");
        ClientNetworkSession network = ClientNetworkSessionFactory.factory().setAddress(host, port)
                .setProtocol(new MinecraftProtocol("SpikeProbe")).create();
        network.addListener(new SessionAdapter() {
            private volatile boolean playing;

            @Override
            public void packetReceived(Session session, Packet packet) {
                if (packet instanceof ClientboundLoginPacket) {
                    playing = true;
                    log(lines, "<- ClientboundLoginPacket (play)");
                    end.compareAndSet(null, "PLAY");
                    return;
                }
                String name = packet.getClass().getSimpleName();
                if (!playing || name.contains("CustomPayload") || name.contains("ResourcePack") || name.contains("Disconnect")) {
                    log(lines, "<- " + describe(packet));
                }
                if (packet instanceof ClientboundCustomQueryPacket query) {
                    session.send(new ServerboundCustomQueryAnswerPacket(query.getMessageId(), null));
                } else if (packet instanceof ClientboundPingPacket ping) {
                    // As the driver does (T-M3-03): a vanilla client answers every ping. Fabric API and NeoForge
                    // send one in the configuration phase and wait for the pong (spike phase A).
                    session.send(new ServerboundPongPacket(ping.getId()));
                } else if (packet instanceof ClientboundResourcePackPushPacket push) {
                    // A vanilla client: accepted, then downloaded and checked, then loaded (or declined).
                    Thread.ofVirtual().start(() -> answerPack(session, push, answer, lines));
                }
            }

            @Override
            public void packetSent(Session session, Packet packet) {
                if (!playing) {
                    log(lines, "-> " + cut(packet.toString()));
                }
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                log(lines, "disconnected: " + event.getReason() + (event.getCause() == null ? "" : " " + event.getCause()));
                end.compareAndSet(null, "DISCONNECTED");
            }
        });
        network.connect(false);
        try {
            Await.until("probe play or disconnect", end::get, v -> v != null, JOIN);
        } catch (RuntimeException e) {
            log(lines, "PROBE TIMEOUT " + e.getMessage());
        }
        System.out.println("PROBE end=" + end.get());
        network.disconnect("spike probe done");
        synchronized (lines) {
            Files.write(out.resolve("probe.txt"), lines);
            for (String line : lines) {
                System.out.println("  probe " + line);
            }
        }
    }

    private static void answerPack(Session session, ClientboundResourcePackPushPacket push, String answer, List<String> lines) {
        try {
            if (answer.equals("decline")) {
                session.send(new ServerboundResourcePackPacket(push.getId(), ResourcePackStatus.DECLINED));
                return;
            }
            session.send(new ServerboundResourcePackPacket(push.getId(), ResourcePackStatus.ACCEPTED));
            HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(push.getUrl())).timeout(WAIT).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(response.body()));
            boolean match = sha1.equalsIgnoreCase(push.getHash());
            log(lines, "pack fetched HTTP " + response.statusCode() + " " + response.body().length + " bytes sha1=" + sha1
                    + " matches push hash: " + match);
            session.send(new ServerboundResourcePackPacket(push.getId(), ResourcePackStatus.DOWNLOADED));
            session.send(new ServerboundResourcePackPacket(push.getId(),
                    match ? ResourcePackStatus.SUCCESSFULLY_LOADED : ResourcePackStatus.FAILED_DOWNLOAD));
        } catch (Exception e) {
            log(lines, "pack answer error " + e);
            session.send(new ServerboundResourcePackPacket(push.getId(), ResourcePackStatus.FAILED_DOWNLOAD));
        }
    }

    private static HttpServer servePack() throws Exception {
        String pack = System.getenv("SPIKE_PACK");
        if (pack == null || pack.isEmpty()) {
            return null;
        }
        byte[] body = Files.readAllBytes(Path.of(pack));
        int port = Integer.parseInt(System.getenv().getOrDefault("SPIKE_PACK_PORT", "28765"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/pack.zip", exchange -> {
            System.out.println("PACK HTTP " + exchange.getRequestMethod() + " " + exchange.getRequestURI() + " from "
                    + exchange.getRemoteAddress());
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        System.out.println("PACK serving " + body.length + " bytes at http://127.0.0.1:" + port + "/pack.zip");
        return server;
    }

    private static void log(List<String> lines, String line) {
        synchronized (lines) {
            lines.add(java.time.Instant.now() + " " + line);
        }
    }

    /** Registry data as id and entry count; a custom payload with its channel and the payload as text (channel lists). */
    private static String describe(Packet packet) {
        if (packet instanceof ClientboundRegistryDataPacket registry) {
            return "ClientboundRegistryDataPacket(registry=" + registry.getRegistry() + ", entries="
                    + registry.getEntries().size() + ")";
        }
        if (packet instanceof ClientboundCustomPayloadPacket payload) {
            String text = new String(payload.getData(), StandardCharsets.UTF_8).replaceAll("[\\x00-\\x1f]", "|");
            return "ClientboundCustomPayloadPacket(channel=" + payload.getChannel() + ", " + payload.getData().length
                    + " bytes, text=" + cut(text) + ")";
        }
        return cut(packet.toString());
    }

    private static String cut(String s) {
        return s.length() > 600 ? s.substring(0, 600) + "...(" + s.length() + " chars)" : s;
    }

    private static String oneLine(String s) {
        return s == null ? "" : cut(s.replace('\n', ' '));
    }
}
