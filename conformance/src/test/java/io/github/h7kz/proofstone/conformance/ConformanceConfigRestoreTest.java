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

    @CellTest
    @Order(1)
    void aPatchReachesTheHeldConfigObject(Agent agent) {
        agent.plugin("proofstone-fixture").patchConfig("greeting", "one");
        assertThat(cachedGreeting(agent)).isEqualTo("fixture cached greeting=one");
    }

    @CellTest
    @Order(2)
    void aPatchAfterARestoreStillReachesTheHeldConfigObject(Agent agent) {
        assertThat(cachedGreeting(agent)).as("the restore put the original back in the held object")
                .isEqualTo("fixture cached greeting=hi");
        agent.plugin("proofstone-fixture").patchConfig("greeting", "two");
        assertThat(cachedGreeting(agent)).isEqualTo("fixture cached greeting=two");
    }

    private static String cachedGreeting(Agent agent) {
        agent.command("fixture-cached-greeting");
        List<String> lines = agent.logLines(Pattern.compile("fixture cached greeting=\\S+$"), WAIT);
        assertThat(lines).isNotEmpty();
        String last = lines.get(lines.size() - 1);
        return last.substring(last.indexOf("fixture cached greeting="));
    }
}
