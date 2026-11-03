package dev.proofstone.core.versions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VersionsCatalogTest {

    @TempDir
    Path dir;

    @Test
    void loadsValidDocumentAndExposesRows() throws IOException {
        VersionsCatalog catalog = VersionsFixture.load(dir, VersionsFixture.valid());

        assertThat(catalog.platformIds()).containsExactly("paper");
        Platform paper = catalog.platform("paper");
        assertThat(paper.host().image()).isEqualTo("itzg/minecraft-server");
        assertThat(paper.rows()).hasSize(9);
        assertThat(paper.rows().get(0).mc()).isEqualTo("1.9.4");
        assertThat(paper.rows().get(0).build()).isEqualTo(1);
        assertThat(paper.rows().get(0).status()).isEqualTo(Status.REQUIRED);
    }

    @Test
    void rejectsDocumentThatFailsSchema() throws IOException {
        String yaml = VersionsFixture.valid().replace("schema: 1", "schema: 2");

        assertThatThrownBy(() -> VersionsFixture.load(dir, yaml))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("schema");
    }

    @Test
    void rejectsDuplicateMcWithinPlatform() throws IOException {
        String yaml = VersionsFixture.valid().replace("mc: \"26.3\"", "mc: \"1.9.4\"");

        assertThatThrownBy(() -> VersionsFixture.load(dir, yaml))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("1.9.4")
                .hasMessageContaining("appears twice");
    }

    @Test
    void rejectsRequiredRowWithTodoPin() throws IOException {
        String yaml = VersionsFixture.valid().replace("build: 1,", "build: TODO,");

        assertThatThrownBy(() -> VersionsFixture.load(dir, yaml))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("required row must not contain TODO");
    }

    @Test
    void loadsSeedFileWhoseExperimentalRowsStillHaveTodoPins() throws IOException {
        Path seed = VersionsFixture.REPO_ROOT.resolve("versions.yml");

        VersionsCatalog catalog = VersionsCatalog.load(seed, VersionsFixture.schema());

        assertThat(catalog.platformIds()).contains("paper");
        assertThat(catalog.platform("paper").host().digest()).isNull();
    }

    @Test
    void failsWhenFileIsMissing() {
        assertThatThrownBy(() -> VersionsCatalog.load(dir.resolve("absent.yml"), VersionsFixture.schema()))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("absent.yml");
    }
}
