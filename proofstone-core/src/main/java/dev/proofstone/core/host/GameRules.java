package dev.proofstone.core.host;

import java.util.List;

/**
 * The deterministic-world setup commands. Minecraft 26.1 renamed game rules from camelCase to snake_case
 * (docs/platforms/paper.md), so the names depend on the cell's Minecraft version.
 */
final class GameRules {

    private static final int FIRST_SNAKE_CASE_MAJOR = 26;

    private GameRules() {
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

    /** The rule that {@code commands} sets to 0 first; tests read it back to prove the effect. */
    static String randomTickSpeed(String mc) {
        return usesSnakeCase(mc) ? "random_tick_speed" : "randomTickSpeed";
    }

    /** Unparsable versions fail loudly: guessing the naming would apply the wrong rules silently. */
    static boolean usesSnakeCase(String mc) {
        String major = mc.split("\\.", 2)[0];
        try {
            return Integer.parseInt(major) >= FIRST_SNAKE_CASE_MAJOR;
        } catch (NumberFormatException e) {
            throw new PaperHostException("cannot read the Minecraft major version from \"" + mc + "\"", e);
        }
    }
}
