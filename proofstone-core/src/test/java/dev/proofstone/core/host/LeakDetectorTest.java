package dev.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class LeakDetectorTest {

    @Test
    void theRulesAreTheOnesTheWorldSetsInTheCellsNaming() {
        assertThat(GameRules.names("1.8.8")).containsExactly("randomTickSpeed", "doDaylightCycle",
                "doWeatherCycle", "doMobSpawning");
        assertThat(GameRules.names("26.1.2")).containsExactly("random_tick_speed", "advance_time",
                "advance_weather", "spawn_mobs");
    }

    @Test
    void onlyTheRulesThatChangedAreReported() {
        List<String> before = List.of("random_tick_speed: 0", "advance_time: false");
        List<String> after = List.of("random_tick_speed: 3", "advance_time: false");

        assertThat(LeakDetector.changed(before, after))
                .containsExactly("random_tick_speed: 0 -> random_tick_speed: 3");
        assertThat(LeakDetector.changed(before, before)).isEmpty();
    }

    @Test
    void snapshotsOfDifferentRulesAreAnError() {
        assertThatThrownBy(() -> LeakDetector.changed(List.of("a: 1"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
