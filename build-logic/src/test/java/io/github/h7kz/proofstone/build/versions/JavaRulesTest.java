package io.github.h7kz.proofstone.build.versions;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// The java-rules table of versions.yml (ADR-0008, docs/versioning.md §3). First match wins.
class JavaRulesTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private static JsonNode seedRules() throws Exception {
        return YAML.readTree(Path.of(JavaRulesTest.class.getResource("/versions/update-input.yml").toURI()).toFile())
                .path("java-rules");
    }

    @ParameterizedTest
    @CsvSource({
            "1.8.8, java8",
            "1.16.5, java8",
            "1.17.1, java16",
            "1.17, java16",
            "1.18.2, java17",
            "1.20.4, java17",
            "1.20.5, java21",
            "1.21.11, java21",
            "26.1.2, java25",
            "26.3, java25",
    })
    void seedRulesAssignTheObservedTag(String mc, String tag) throws Exception {
        assertThat(JavaRules.tagFor(seedRules(), mc)).contains(tag);
    }

    @Test
    void noMatchIsEmptyNotADefault() throws Exception {
        JsonNode onlyNewest = YAML.readTree("[ { range: \">=26\", image: java25 } ]");
        assertThat(JavaRules.tagFor(onlyNewest, "1.8.8")).isEmpty();
    }

    @Test
    void versionOrderIsNumericPerSegment() {
        assertThat(McVersions.compare("1.20.10", "1.20.9")).isPositive();
        assertThat(McVersions.compare("26.3", "1.21.11")).isPositive();
        assertThat(McVersions.compare("1.21", "1.21.0")).isZero();
    }

    @Test
    void prereleasesAreNotStable() {
        assertThat(McVersions.isStable("26.3")).isTrue();
        assertThat(McVersions.isStable("26.3-rc-3")).isFalse();
        assertThat(McVersions.isStable("1.13-pre7")).isFalse();
    }
}
