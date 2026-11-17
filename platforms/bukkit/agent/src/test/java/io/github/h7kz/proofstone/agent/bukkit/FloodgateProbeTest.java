package io.github.h7kz.proofstone.agent.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The {@code floodgate} token (ADR-0018 section 5): by plugin name, read-only, once, only when the plugin is enabled. */
class FloodgateProbeTest {

    @Test
    void addsTheTokenWhenThePluginNamedFloodgateIsEnabled() {
        List<String> tokens = new ArrayList<>(java.util.Collections.singletonList("command"));
        new FloodgateProbe(tokens, "floodgate"::equals).run();
        assertThat(tokens).containsExactly("command", "floodgate");
    }

    @Test
    void addsNothingWhenThePluginIsNotEnabled() {
        List<String> tokens = new ArrayList<>(java.util.Collections.singletonList("command"));
        new FloodgateProbe(tokens, name -> false).run();
        assertThat(tokens).containsExactly("command");
    }

    @Test
    void asksOnceAndNeverDuplicates() {
        List<String> tokens = new ArrayList<>();
        int[] asked = {0};
        FloodgateProbe probe = new FloodgateProbe(tokens, name -> {
            asked[0]++;
            return true;
        });
        probe.run();
        probe.run();
        assertThat(asked[0]).isEqualTo(1);
        assertThat(tokens).containsExactly("floodgate");
    }
}
