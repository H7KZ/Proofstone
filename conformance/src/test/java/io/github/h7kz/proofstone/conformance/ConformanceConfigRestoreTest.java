package io.github.h7kz.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * C-C03 (T-M12-04): a patch made after a restore reaches a plugin that holds its config object since {@code onEnable}.
 * The fixture reads {@code greeting} from that held object. Test one patches and the lifecycle restores after it; test two
 * patches again and must see its value. A restore that replaced the config object ({@code reloadConfig()}) left the plugin
 * on the old object, so every later patch was invisible to it (Disenchantment's repeat runs, 2026-11-24). The order is
 * fixed: without it a pass could mean "the patching test ran first".
 */
@Proofstone(plugins = "build/fixture-plugins/proofstone-fixture.jar")
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConformanceConfigRestoreTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    /** Set by test one. Test two fails loudly without it: run alone it would pass on the old restore too. */
    private static boolean testOneRan;

    @CellTest
    @Order(1)
    void aPatchReachesTheHeldConfigObject(Agent agent) {
        agent.plugin("proofstone-fixture").patchConfig("greeting", "one");
        awaitGreeting(agent, "one");
        testOneRan = true;
    }

    @CellTest
    @Order(2)
    void aPatchAfterARestoreStillReachesTheHeldConfigObject(Agent agent) {
        assertThat(testOneRan).as("test one ran first on this server, so a restore has happened").isTrue();
        awaitGreeting(agent, "hi");
        agent.plugin("proofstone-fixture").patchConfig("greeting", "two");
        awaitGreeting(agent, "two");
    }

    /** Asks the fixture for the greeting in its held config object and waits for the log line with {@code expected}. */
    private static void awaitGreeting(Agent agent, String expected) {
        agent.command("fixture-cached-greeting");
        List<String> lines = agent.logLines(Pattern.compile("fixture cached greeting=" + Pattern.quote(expected) + "$"), WAIT);
        assertThat(lines).as("the held config object reads greeting=" + expected).isNotEmpty();
    }
}
