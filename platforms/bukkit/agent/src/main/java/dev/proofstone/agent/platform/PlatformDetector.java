package dev.proofstone.agent.platform;

import java.util.function.Predicate;

/**
 * Names the server platform from class probes (M1 supports Paper, Spigot, Purpur and Folia). Probes are
 * plain class-presence checks, so the agent never links against a platform it is not running on.
 */
public final class PlatformDetector {

    private PlatformDetector() {
    }

    /** Most specific first: Purpur and Paper both ship Spigot's classes, so Spigot is checked last. */
    public static String detect(Predicate<String> classPresent) {
        if (classPresent.test("io.papermc.paper.threadedregions.RegionizedServer")) {
            return "folia";
        }
        if (classPresent.test("org.purpurmc.purpur.PurpurConfig")) {
            return "purpur";
        }
        if (classPresent.test("com.destroystokyo.paper.PaperConfig")
                || classPresent.test("io.papermc.paper.configuration.Configuration")
                // Paper 1.8.8 is still called PaperSpigot and has neither of the classes above.
                || classPresent.test("org.github.paperspigot.PaperSpigotConfig")) {
            return "paper";
        }
        if (classPresent.test("org.spigotmc.SpigotConfig")) {
            return "spigot";
        }
        return "bukkit";
    }
}
