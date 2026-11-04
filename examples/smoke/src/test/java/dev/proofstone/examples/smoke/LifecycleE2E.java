package dev.proofstone.examples.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.api.Agent;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Client;
import dev.proofstone.api.Item;
import dev.proofstone.api.ItemView;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.PlayerHandle;
import dev.proofstone.api.Platform;
import dev.proofstone.api.PluginHandle;
import dev.proofstone.api.Proofstone;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * T-M2-08 on a real server: tests that share one server do not see each other's player state or config, and each
 * gets its own client. Test one dirties everything and test two must find it clean, so the order is fixed;
 * without it a pass could mean "the dirty test ran second".
 */
@Proofstone(plugins = "build/fixture-plugins/proofstone-fixture.jar")
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LifecycleE2E {

    private static final Set<String> NAMES = new HashSet<>();

    @CellTest
    @Order(1)
    void firstTestStartsCleanThenDirtiesPlayerAndConfig(Agent agent, Client client) {
        PlayerHandle player = agent.player(client);
        PluginHandle fixture = agent.plugin("proofstone-fixture");
        assertThat(NAMES.add(client.name())).isTrue();
        assertThat(player.state().inventory()).isEmpty();
        assertThat(fixture.config("greeting")).isEqualTo("hi");

        player.give(Item.of("diamond_sword")).level(17);
        fixture.patchConfig("greeting", "changed by test one");
        agent.command("scoreboard objectives add lifecycleOne dummy");

        assertThat(player.awaitState("sword and level", state -> state.level() == 17
                && state.inventory().values().stream().anyMatch(item -> item.is("diamond_sword")))).isNotNull();
        assertThat(fixture.config("greeting")).isEqualTo("changed by test one");
    }

    @CellTest
    @Order(2)
    void secondTestFindsPlayerAndConfigResetButTheServerIsTheSame(Agent agent, Client client) {
        PlayerHandle player = agent.player(client);
        // A different client on the same server: its name is new, and the first test's objective is still there.
        assertThat(NAMES.add(client.name())).as("client name %s was reused", client.name()).isTrue();
        assertThat(NAMES).hasSize(2);
        assertThat(agent.command("scoreboard objectives list")).contains("lifecycleOne");

        assertThat(player.state().inventory()).as("inventory of the first test must be gone").isEmpty();
        assertThat(player.state().level()).as("level of the first test must be gone").isZero();
        assertThat(client.inventory()).as("client sees an empty inventory").allMatch(ItemView::isEmpty);
        assertThat(agent.plugin("proofstone-fixture").config("greeting"))
                .as("config patched by the first test must be restored").isEqualTo("hi");
    }
}
