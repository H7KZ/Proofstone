package dev.proofstone.agent.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MinecraftVersionTest {

    @Test
    void stripsTheSnapshotSuffix() {
        assertThat(MinecraftVersion.parse("1.8.8-R0.1-SNAPSHOT")).isEqualTo("1.8.8");
        assertThat(MinecraftVersion.parse("1.21.11-R0.1-SNAPSHOT")).isEqualTo("1.21.11");
    }

    @Test
    void stripsPaperBuildSuffixSeenOn26x() {
        assertThat(MinecraftVersion.parse("26.3.build.238-R0.1-SNAPSHOT")).isEqualTo("26.3");
    }

    @Test
    void anEmptyVersionIsAnError() {
        assertThatThrownBy(() -> MinecraftVersion.parse("-R0.1-SNAPSHOT"))
                .isInstanceOf(IllegalStateException.class);
    }
}
