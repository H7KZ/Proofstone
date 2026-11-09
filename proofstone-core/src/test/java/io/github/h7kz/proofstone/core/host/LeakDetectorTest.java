package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeakDetectorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String DUMP = """
            {"players":[{"name":"Alex","level":2,"exp":0.5,"inventory":{"0":{"type":"minecraft:stone","amount":1}}}],
             "plugins":[{"name":"proofstone-fixture","version":"1.0","enabled":true,"config":{"greeting":"hi"}}]}
            """;

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
    void aRuleThatIsGoneAfterwardIsALeak() {
        assertThat(LeakDetector.changed(List.of("a: 1"), List.of()))
                .containsExactly("a: 1 -> gone");
    }

    @Test
    void aKeyHeldTwiceIsAnError() {
        assertThatThrownBy(() -> LeakDetector.changed(List.of("a: 1", "a: 2"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theAgentDumpBecomesOneEntryPerPluginSettingAndPlayerSlot() throws Exception {
        List<String> entries = LeakDetector.state(JSON.readTree(DUMP));

        assertThat(entries).containsExactly(
                "plugin proofstone-fixture enabled: true",
                "plugin proofstone-fixture config greeting: \"hi\"",
                "player Alex xp: level 2 exp 0.5",
                "player Alex slot 0: {\"type\":\"minecraft:stone\",\"amount\":1}");
    }

    @Test
    void aDumpWithoutItsListsIsAnError() throws Exception {
        JsonNode dump = JSON.readTree("{\"players\":[]}");
        assertThatThrownBy(() -> LeakDetector.state(dump)).isInstanceOf(PaperHostException.class);
    }

    @Test
    void aChangedPlayerXpIsALeakWhileThePlayerIsOnlineOnBothSides() {
        List<String> before = List.of("player Alex xp: level 2 exp 0.5");
        List<String> after = List.of("player Alex xp: level 0 exp 0.0");

        assertThat(LeakDetector.changed(before, after))
                .containsExactly("player Alex xp: level 2 exp 0.5 -> player Alex xp: level 0 exp 0.0");
    }

    @Test
    void aPlayerOnlineOnOneSideOnlyIsNotALeak() {
        assertThat(LeakDetector.changed(List.of(), List.of("player T1 xp: level 0 exp 0.0"))).isEmpty();
        assertThat(LeakDetector.changed(List.of("player T1 xp: level 0 exp 0.0"), List.of())).isEmpty();
    }

    @Test
    void aChangedPluginConfigOrDisabledPluginIsALeak() {
        List<String> before = List.of("plugin p enabled: true", "plugin p config greeting: \"hi\"");
        List<String> after = List.of("plugin p enabled: false", "plugin p config greeting: \"bye\"");

        assertThat(LeakDetector.changed(before, after)).containsExactly(
                "plugin p enabled: true -> plugin p enabled: false",
                "plugin p config greeting: \"hi\" -> plugin p config greeting: \"bye\"");
    }
}
