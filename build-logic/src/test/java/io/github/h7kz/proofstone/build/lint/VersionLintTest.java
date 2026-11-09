package io.github.h7kz.proofstone.build.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

// Each lint rule has one failing and one passing fixture (AGENTS.md §3).
class VersionLintTest {

    @Test
    void latestFails() throws IOException, URISyntaxException {
        assertThat(violations("latest.fixture")).isNotEmpty();
    }

    @Test
    void plusFails() throws IOException, URISyntaxException {
        assertThat(violations("plus.fixture")).isNotEmpty();
    }

    @Test
    void snapshotFails() throws IOException, URISyntaxException {
        assertThat(violations("snapshot.fixture")).isNotEmpty();
    }

    @Test
    void rangeFails() throws IOException, URISyntaxException {
        assertThat(violations("range.fixture")).isNotEmpty();
    }

    @Test
    void pinnedVersionsAndCommentsPass() throws IOException, URISyntaxException {
        assertThat(violations("clean.fixture")).isEmpty();
    }

    private static List<String> violations(String name) throws IOException, URISyntaxException {
        Path file = Path.of(VersionLintTest.class.getResource("/lint/" + name).toURI());
        return VersionLint.violations(file);
    }
}
