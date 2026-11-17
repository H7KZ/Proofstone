package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameRulesTest {

    @Test
    void camelCaseBeforeTheRenameAt12111() {
        assertThat(GameRules.usesSnakeCase("1.8.8")).isFalse();
        assertThat(GameRules.usesSnakeCase("1.20.6")).isFalse();
        assertThat(GameRules.usesSnakeCase("1.21.10")).isFalse();
        assertThat(GameRules.randomTickSpeed("1.21.10")).isEqualTo("randomTickSpeed");
        assertThat(GameRules.commands("1.20.4")).contains("gamerule randomTickSpeed 0");
    }

    @Test
    void snakeCaseFrom12111() {
        assertThat(GameRules.usesSnakeCase("1.21.11")).isTrue();
        assertThat(GameRules.usesSnakeCase("26.1.2")).isTrue();
        assertThat(GameRules.usesSnakeCase("26.3")).isTrue();
        assertThat(GameRules.randomTickSpeed("1.21.11")).isEqualTo("random_tick_speed");
        assertThat(GameRules.commands("1.21.11")).contains("gamerule random_tick_speed 0");
    }

    @Test
    void unparsableVersionFailsLoudly() {
        assertThatThrownBy(() -> GameRules.usesSnakeCase("banana"))
                .isInstanceOf(PaperHostException.class)
                .hasMessageContaining("banana");
    }

    @Test
    void levelTypeIsNamespacedFrom118() {
        assertThat(PaperHost.levelType("1.8.8")).isEqualTo("flat");
        assertThat(PaperHost.levelType("1.16.5")).isEqualTo("flat");
        assertThat(PaperHost.levelType("1.17.1")).isEqualTo("flat");
        assertThat(PaperHost.levelType("1.18.2")).isEqualTo("minecraft:flat");
        assertThat(PaperHost.levelType("26.3")).isEqualTo("minecraft:flat");
    }

    /** One entry point for every host (T-M8-27): a loader cell gets the rules a Paper cell gets, in the cell's naming. */
    @Test
    void applyRunsTheSameCommandsInOrderForTheCellsNaming() {
        List<String> sent = new ArrayList<>();
        GameRules.apply("26.3", sent::add);
        assertThat(sent).isEqualTo(GameRules.commands("26.3"))
                .contains("gamerule random_tick_speed 0", "gamerule advance_time false", "gamerule advance_weather false",
                        "gamerule spawn_mobs false");

        sent.clear();
        GameRules.apply("1.20.4", sent::add);
        assertThat(sent).isEqualTo(GameRules.commands("1.20.4")).contains("gamerule randomTickSpeed 0");
    }

    @Test
    void applyFailsLoudlyOnAnUnparsableVersionBeforeSendingAnything() {
        List<String> sent = new ArrayList<>();
        assertThatThrownBy(() -> GameRules.apply("banana", sent::add)).isInstanceOf(PaperHostException.class);
        assertThat(sent).isEmpty();
    }
}
