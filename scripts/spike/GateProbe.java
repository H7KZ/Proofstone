// SPIKE (T-M10-12). Throwaway client for the Gate spike, not production code and not part of the build.
// Joins a proxy with the mcpl driver (offline) and records, by hand, what P01 to P05 would assert, using the backends'
// agents as the only oracle (Gate has no proxy agent). Every observation is printed as `P0x <what>: <value>`; nothing is
// retried and nothing is asserted: the spike records outcomes, the maintainer reads them.
//
// Run through scripts/spike/gate-spike.sh:
//   java -cp <mcpl runtime classpath> scripts/spike/GateProbe.java <host> <port> <name> <lobbyAgentPort> <survivalAgentPort> <token> <outDir>

import io.github.h7kz.proofstone.core.driver.ChatMessage;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.driver.mcpl.McplDriver;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

public class GateProbe {

    private static final Duration JOIN = Duration.ofSeconds(30);
    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static String token;

    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String name = args[2];
        int lobby = Integer.parseInt(args[3]);
        int survival = Integer.parseInt(args[4]);
        token = args[5];
        Path out = Path.of(args[6]);
        Files.createDirectories(out);
        McplDriver driver = new McplDriver();
        ClientSession session;
        try {
            session = driver.connect(new ConnectSpec(host, port, name, JOIN));
        } catch (RuntimeException e) {
            System.out.println("P01 join: FAIL " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(1);
            return;
        }
        try (session) {
            System.out.println("P01 join: OK " + name);
            try {
                System.out.println("P01 first message: " + session.awaitMessage(Pattern.compile("."), WAIT).text());
            } catch (RuntimeException e) {
                System.out.println("P01 first message: none (" + e.getMessage() + ")");
            }
            System.out.println("P01 lobby /v1/players: " + get(lobby, "/v1/players"));
            System.out.println("P01 survival /v1/players: " + get(survival, "/v1/players"));

            String state = get(lobby, "/v1/players/" + name);
            String uuid = state.replaceAll(".*\"uuid\":\"([0-9a-f-]{36})\".*", "$1");
            String offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
            System.out.println("P02 backend uuid: " + uuid + " offline uuid of name: " + offline + " equal=" + uuid.equals(offline));

            System.out.println("P04 fixture command: " + post(lobby, "/v1/command", "{\"command\":\"fixture-getserver " + name + "\"}"));

            session.command("server");
            try {
                System.out.println("P05 /server reply: " + session.awaitMessage(Pattern.compile("."), WAIT).text());
            } catch (RuntimeException e) {
                System.out.println("P05 /server reply: none (" + e.getMessage() + ")");
            }

            for (String target : new String[] {"survival", "lobby"}) {
                int to = target.equals("survival") ? survival : lobby;
                int from = target.equals("survival") ? lobby : survival;
                session.command("server " + target);
                boolean arrived = false;
                long deadline = System.nanoTime() + WAIT.toNanos();
                while (System.nanoTime() < deadline && !arrived) {
                    arrived = get(to, "/v1/players").contains("\"" + name + "\"");
                    if (!arrived) {
                        try {
                            Thread.sleep(250); // spike poll, bounded by the deadline
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                System.out.println("P03 -> " + target + " arrived on target agent: " + arrived);
                System.out.println("P03 -> " + target + " target /v1/players: " + get(to, "/v1/players"));
                System.out.println("P03 -> " + target + " source /v1/players: " + get(from, "/v1/players"));
                System.out.println("P03 -> " + target + " still in play on client: "
                        + describe(session));
            }
            Files.write(out.resolve("transcript-" + name + ".txt"), session.transcript());
        }
        System.exit(0);
    }

    private static String describe(ClientSession session) {
        try {
            session.chat("probe");
            return "chat sent without error";
        } catch (RuntimeException e) {
            return "ERROR " + e.getMessage();
        }
    }

    private static String get(int port, String path) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> resp = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() + " " + resp.body();
    }

    private static String post(int port, String path, String body) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> resp = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() + " " + resp.body();
    }
}
