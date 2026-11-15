// SPIKE (T-M8-16, diagnosis only). THROWAWAY, not production code. Run by scripts/spike/loader-hand.sh (RACE=...).
// Joins and leaves <cycles> times through the real mcpl driver (the same connect and close the harness uses), closing
// after a random 0..<maxJitterMs> ms after the join finishes. The sleeps are the experiment (they spread the close over
// the server's 50 ms tick), they are not synchronisation. Prints how many cycles joined.
//
//   java -cp <mcpl runtime classpath> scripts/spike/DisconnectRace.java <host> <port> <cycles> <maxJitterMs> [help]
// With "help" each cycle sends /help, waits for the reply and closes at once (what ConformanceSmokeTest C02 does).
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.driver.mcpl.McplDriver;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.Random;

public class DisconnectRace {
    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        int cycles = Integer.parseInt(args[2]);
        int jitter = Integer.parseInt(args[3]);
        Random random = new Random(42);
        McplDriver driver = new McplDriver();
        int ok = 0;
        int fail = 0;
        for (int i = 1; i <= cycles; i++) {
            ClientSession session;
            try {
                session = driver.connect(new ConnectSpec(host, port, "Race" + (i % 1000), Duration.ofSeconds(30)));
            } catch (RuntimeException e) {
                fail++;
                System.out.println("JOIN " + i + " FAIL " + e.getClass().getSimpleName());
                continue;
            }
            ok++;
            if (args.length > 4 && "help".equals(args[4])) {
                session.command("help");
                session.awaitMessage(Pattern.compile(Pattern.quote("/help [<command>]")), Duration.ofSeconds(10));
            }
            if (jitter > 0) {
                Thread.sleep(random.nextInt(jitter + 1));
            }
            session.close();
        }
        System.out.println("RACE cycles=" + cycles + " joined=" + ok + " joinFailed=" + fail);
    }
}
