package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.versions.VersionNumber;
import java.util.List;
import java.util.function.Consumer;

/**
 * The deterministic-world setup commands. Minecraft 1.21.11 renamed game rules from camelCase to snake_case
 * (docs/platforms/paper.md), so the names depend on the cell's Minecraft version.
 */
final class GameRules {

    // Observed on Paper 1.21.11 (T-M3-02): the rename landed there, not at 26.1 as first assumed.
    private static final String FIRST_SNAKE_CASE = "1.21.11";

    private GameRules() {
    }

    /**
     * Sets the deterministic world through the host's console-command function, the one call for every host (T-M8-27: a
     * loader cell held the vanilla default, {@code random_tick_speed} 3, while a Paper cell held 0). The commands are
     * built, and an unparsable version fails, before the first one is sent.
     */
    static void apply(String mc, Consumer<String> command) {
        commands(mc).forEach(command);
    }

    static List<String> commands(String mc) {
        if (usesSnakeCase(mc)) {
            return List.of(
                    "gamerule random_tick_speed 0",
                    "gamerule advance_time false",
                    "gamerule advance_weather false",
                    "gamerule spawn_mobs false");
        }
        return List.of(
                "gamerule randomTickSpeed 0",
                "gamerule doDaylightCycle false",
                "gamerule doWeatherCycle false",
                "gamerule doMobSpawning false");
    }

    /** The rule names the deterministic world sets, in the cell's naming: the leak detector reads these back. */
    static List<String> names(String mc) {
        return commands(mc).stream().map(command -> command.split(" ")[1]).toList();
    }

    /** The rule that {@code commands} sets to 0 first; tests read it back to prove the effect. */
    static String randomTickSpeed(String mc) {
        return usesSnakeCase(mc) ? "random_tick_speed" : "randomTickSpeed";
    }

    /** Unparsable versions fail loudly: guessing the naming would apply the wrong rules silently. */
    static boolean usesSnakeCase(String mc) {
        try {
            return VersionNumber.compare(mc, FIRST_SNAKE_CASE) >= 0;
        } catch (IllegalArgumentException e) {
            throw new PaperHostException("cannot read the Minecraft version from \"" + mc + "\"", e);
        }
    }
}
