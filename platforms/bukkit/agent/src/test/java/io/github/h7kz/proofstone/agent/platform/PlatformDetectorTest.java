package io.github.h7kz.proofstone.agent.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class PlatformDetectorTest {

    @Test
    void namesPaperWhenPaperClassesArePresent() {
        assertThat(PlatformDetector.detect(present("com.destroystokyo.paper.PaperConfig", "org.spigotmc.SpigotConfig")))
                .isEqualTo("paper");
    }

    @Test
    void namesPaperOnTheOldPaperSpigotFork() {
        // Paper 1.8.8 is "PaperSpigot": it has no com.destroystokyo.paper.PaperConfig (observed on build 445).
        assertThat(PlatformDetector.detect(present("org.github.paperspigot.PaperSpigotConfig",
                "org.spigotmc.SpigotConfig"))).isEqualTo("paper");
    }

    @Test
    void purpurWinsOverPaperBecauseItShipsPaperClassesToo() {
        assertThat(PlatformDetector.detect(present(
                "org.purpurmc.purpur.PurpurConfig", "com.destroystokyo.paper.PaperConfig")))
                .isEqualTo("purpur");
    }

    @Test
    void foliaWinsOverEverything() {
        assertThat(PlatformDetector.detect(present(
                "io.papermc.paper.threadedregions.RegionizedServer", "com.destroystokyo.paper.PaperConfig")))
                .isEqualTo("folia");
    }

    @Test
    void spigotAndPlainBukkitFallBack() {
        assertThat(PlatformDetector.detect(present("org.spigotmc.SpigotConfig"))).isEqualTo("spigot");
        assertThat(PlatformDetector.detect(present())).isEqualTo("bukkit");
    }

    private static Predicate<String> present(String... names) {
        Set<String> set = new HashSet<>(Arrays.asList(names));
        return set::contains;
    }
}
