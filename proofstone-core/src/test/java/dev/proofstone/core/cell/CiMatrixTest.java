package dev.proofstone.core.cell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.proofstone.core.versions.VersionsCatalog;
import dev.proofstone.core.versions.VersionsException;
import dev.proofstone.core.versions.VersionsFixture;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The CI matrix JSON against golden fixtures (T-M3-07). The fixture matrix has one platform per case: paper with
 * retired and experimental rows, folia with a {@code TODO} host digest, and spigot with no required row.
 */
class CiMatrixTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path dir;

    /** A matrix with Java rules as in the seed file; the unknown-rule test removes the {@code >=26} rule. */
    private static final String FIXTURE = """
            schema: 1
            java-rules:
              - { range: "<=1.16.5", image: java8 }
              - { range: "1.17.x", image: java16 }
              - { range: ">=1.18 <1.20.5", image: java17 }
              - { range: ">=1.20.5 <26", image: java21 }
              - { range: ">=26", image: java25 }
            drivers:
              mcpl: { library: "g:a", version: "1.0", protocol: "26.3" }
            platforms:
              paper:
                host: { image: "itzg/minecraft-server", digest: "sha256:0123" }
                rows:
                  - { mc: "1.9.4", build: 1, sha256: "a1", status: required }
                  - { mc: "1.20.4", build: 5, sha256: "a5", status: required }
                  - { mc: "1.21.11", build: 132, sha256: "a6", status: experimental }
                  - { mc: "26.3", build: 244, sha256: "a7", status: experimental }
                  - { mc: "1.8.8", build: 9, sha256: "a9", status: retired }
              folia:
                host: { image: "itzg/minecraft-server", digest: "TODO" }
                rows:
                  - { mc: "26.3", build: 6, sha256: "c1", status: required }
              spigot:
                host: { image: "itzg/minecraft-server", digest: "sha256:0456" }
                rows:
                  - { mc: "1.12.2", build: 1, sha256: "d1", status: experimental }
            """;

    private VersionsCatalog catalog(String yaml) throws IOException {
        return VersionsFixture.load(dir, yaml);
    }

    private static JsonNode golden(String name) throws IOException {
        try (InputStream in = CiMatrixTest.class.getResourceAsStream("/ci/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture /ci/" + name);
            }
            return JSON.readTree(in);
        }
    }

    @Test
    void prScopeRunsRequiredRowsOnlyAndSkipsWithReasons() throws IOException {
        CiMatrix.Plan plan = CiMatrix.plan(catalog(FIXTURE), Scope.PR);

        assertThat(CiMatrix.matrix(plan.cells())).isEqualTo(golden("matrix-pr.json"));
        assertThat(plan.skipped()).containsOnlyKeys("folia", "spigot");
        assertThat(plan.skipped().get("folia")).contains(CiMatrix.TODO_PINS).contains("host digest");
        assertThat(plan.skipped().get("spigot")).contains("no required row");
    }

    @Test
    void nightlyScopeRunsEveryNonRetiredRowAndSkipsOnlyTheUnpinnedPlatform() throws IOException {
        CiMatrix.Plan plan = CiMatrix.plan(catalog(FIXTURE), Scope.NIGHTLY);

        assertThat(CiMatrix.matrix(plan.cells())).isEqualTo(golden("matrix-nightly.json"));
        assertThat(plan.skipped()).containsOnlyKeys("folia");
    }

    @Test
    void addingARowAddsACellWithoutAnyOtherChange() throws IOException {
        String withNewRow = FIXTURE.replace(
                "- { mc: \"26.3\", build: 244, sha256: \"a7\", status: experimental }",
                "- { mc: \"26.3\", build: 244, sha256: \"a7\", status: experimental }\n"
                        + "      - { mc: \"26.2\", build: 240, sha256: \"a8\", status: experimental }");

        List<String> cells = new ArrayList<>();
        CiMatrix.matrix(CiMatrix.plan(catalog(withNewRow), Scope.NIGHTLY).cells()).get("include")
                .forEach(entry -> cells.add(entry.get("cell").asText()));
        assertThat(cells).contains("paper@26.2");
    }

    @Test
    void unknownJavaRuleFailsInsteadOfBeingSkipped() throws IOException {
        String noNewestRule = FIXTURE.replace("  - { range: \">=26\", image: java25 }\n", "");

        assertThatThrownBy(() -> CiMatrix.plan(catalog(noNewestRule), Scope.NIGHTLY))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("no java-rules entry matches");
    }
}
