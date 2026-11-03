package dev.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtefactsTest {

    @TempDir
    Path dir;

    @Test
    void globMatchesFilesRelativeToTheBase() throws IOException {
        Files.createDirectories(dir.resolve("build/libs"));
        Path jar = Files.createFile(dir.resolve("build/libs/plugin-1.0.jar"));
        Files.createFile(dir.resolve("build/libs/plugin-1.0-sources.txt"));

        assertThat(Artefacts.resolve(dir, List.of("build/libs/*.jar"))).containsExactly(jar);
    }

    @Test
    void missingGlobFailsAndNamesTheGlob() {
        assertThatThrownBy(() -> Artefacts.resolve(dir, List.of("build/libs/*.jar")))
                .isInstanceOf(PaperHostException.class)
                .hasMessageContaining("build/libs/*.jar");
    }

    @Test
    void eulaIsAcceptedOnlyByAnExplicitTrue() {
        assertThat(Eula.accepted(Map.of())).isFalse();
        assertThat(Eula.accepted(Map.of(Eula.VARIABLE, "yes"))).isFalse();
        assertThat(Eula.accepted(Map.of(Eula.VARIABLE, "true"))).isTrue();
    }

    @Test
    void logBufferKeepsLinesAndTails() {
        LogBuffer logs = new LogBuffer();
        logs.append("one\ntwo\r\n");
        logs.append("three\n");

        assertThat(logs.contains("two")).isTrue();
        assertThat(logs.tail(2)).isEqualTo("two\nthree");
    }
}
