// T-M9-01 spike client, not production code. Joins the Geyser/Floodgate server over its Java port (TCP) with the real mcpl driver
// under the names given: the question "what does a Java client under a plain or a '.' prefixed name look like to Floodgate".
// A failed join is printed with its message (a join timeout names its stalled stage); nothing is retried.
// Usage: java -cp <mcpl runtime classpath> JavaJoin.java <host> <tcp port> <name>... ; the hook (HOOK env) runs once joined.
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.driver.mcpl.McplDriver;
import java.time.Duration;

public final class JavaJoin {
    public static void main(String[] a) throws Exception {
        McplDriver driver = new McplDriver();
        int ok = 0, bad = 0;
        for (int i = 2; i < a.length; i++) {
            long t = System.nanoTime();
            try (ClientSession s = driver.connect(new ConnectSpec(a[0], Integer.parseInt(a[1]), a[i], Duration.ofSeconds(30)))) {
                ok++;
                System.out.println("JAVA JOIN OK " + a[i] + " in " + (System.nanoTime() - t) / 1_000_000 + " ms");
                String hook = System.getenv("HOOK");
                if (hook != null) {
                    Process p = new ProcessBuilder(System.getenv().getOrDefault("PS_BASH", "bash"), "-c", hook.replace("{name}", a[i]))
                            .redirectErrorStream(true).start();
                    System.out.println("HOOK " + new String(p.getInputStream().readAllBytes()).trim().replace("\n", " | "));
                    p.waitFor();
                }
            } catch (RuntimeException e) {
                bad++;
                System.out.println("JAVA JOIN FAIL " + a[i] + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        System.out.println("JAVA SUMMARY ok=" + ok + " fail=" + bad);
        System.exit(0);
    }
}
