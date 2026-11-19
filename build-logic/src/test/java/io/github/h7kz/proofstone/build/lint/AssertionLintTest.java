package io.github.h7kz.proofstone.build.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

// Each rule has one failing and one passing fixture (AGENTS.md section 3).
class AssertionLintTest {

    @Test
    void negativeOnlyClassFails() throws IOException, URISyntaxException {
        List<String> violations = violations("negative-only.fixture");
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("NegativeOnlyE2E").contains("isEmpty", "isFalse", "noneMatch");
    }

    @Test
    void controlRunNegativePasses() throws IOException, URISyntaxException {
        assertThat(violations("control-run-negative.fixture")).isEmpty();
    }

    @Test
    void mixedClassPasses() throws IOException, URISyntaxException {
        assertThat(violations("mixed.fixture")).isEmpty();
    }

    private static List<String> violations(String name) throws IOException, URISyntaxException {
        Path file = Path.of(AssertionLintTest.class.getResource("/assertlint/" + name).toURI());
        return AssertionLint.violations(file);
    }
}
