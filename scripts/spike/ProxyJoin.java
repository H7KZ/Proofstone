// SPIKE (T-M7-01). Throwaway client for the proxy spike, not production code and not part of the build.
// Joins a proxy with the mcpl driver (offline), switches backend with the client's own `/server <name>` command and
// back, and prints every outcome. Each join is counted; a failed join is reported with its message (a join timeout
// names its stalled stage, T-M6-14) and the next join starts fresh. Nothing is retried.
//
// Run through scripts/spike/proxy-spike.sh, which builds the classpath:
//   java -cp <mcpl runtime classpath> scripts/spike/ProxyJoin.java <host> <port> <joins> <outDir> <backendA> <backendB>

import io.github.h7kz.proofstone.core.driver.ChatMessage;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.driver.mcpl.McplDriver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

public class ProxyJoin {

    private static final Duration JOIN = Duration.ofSeconds(30);
    private static final Duration WAIT = Duration.ofSeconds(20);

    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        int joins = Integer.parseInt(args[2]);
        Path out = Path.of(args[3]);
        String first = args[4];
        String second = args[5];
        Files.createDirectories(out);
        McplDriver driver = new McplDriver();
        int joinOk = 0;
        int joinFail = 0;
        int switchOk = 0;
        int switchFail = 0;
        for (int i = 1; i <= joins; i++) {
            String name = "Spike" + i;
            ClientSession session;
            try {
                session = driver.connect(new ConnectSpec(host, port, name, JOIN));
            } catch (RuntimeException e) {
                joinFail++;
                System.out.println("JOIN " + i + " FAIL " + e.getClass().getSimpleName() + ": " + e.getMessage());
                continue;
            }
            joinOk++;
            System.out.println("JOIN " + i + " OK " + name);
            try (session) {
                print(i, "join message", session.awaitMessage(Pattern.compile("joined"), WAIT));
                session.command("server");
                print(i, "/server reply", session.awaitMessage(Pattern.compile("."), WAIT));
                for (String target : new String[] {second, first}) {
                    session.command("server " + target);
                    try {
                        print(i, "after /server " + target, session.awaitMessage(Pattern.compile("joined"), WAIT));
                        switchOk++;
                        System.out.println("SWITCH " + i + " OK -> " + target);
                    } catch (RuntimeException e) {
                        switchFail++;
                        System.out.println("SWITCH " + i + " FAIL -> " + target + " " + e.getClass().getSimpleName()
                                + ": " + e.getMessage());
                        break;
                    }
                }
            } catch (RuntimeException e) {
                System.out.println("SESSION " + i + " ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                Files.write(out.resolve("transcript-" + name + ".txt"), session.transcript());
            }
        }
        System.out.println("SUMMARY joins=" + joins + " joinOk=" + joinOk + " joinFail=" + joinFail
                + " switchOk=" + switchOk + " switchFail=" + switchFail);
        System.exit(0);
    }

    private static void print(int i, String what, ChatMessage message) {
        System.out.println("  [" + i + "] " + what + ": " + message.text());
    }
}
