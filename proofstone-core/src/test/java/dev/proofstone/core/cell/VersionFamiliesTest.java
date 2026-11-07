package dev.proofstone.core.cell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.proofstone.core.versions.VersionsCatalog;
import dev.proofstone.core.versions.VersionsException;
import dev.proofstone.core.versions.VersionsFixture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VersionFamiliesTest {

    @TempDir
    Path dir;

    private static final String FAMILIES = """
            version-families:
              paper:
                netty: ["1.9.4", "1.10.2"]
                modern: ["1.20.4", "26.3"]
            """;

    private VersionsCatalog catalog() throws IOException {
        return VersionsFixture.load(dir, VersionsFixture.valid());
    }

    @Test
    void noFamiliesMeansNothingExtra() {
        assertThat(VersionFamilies.none().versions("paper", Scope.PR)).isEmpty();
        assertThat(VersionFamilies.none().versions("paper", Scope.NIGHTLY)).isEmpty();
    }

    @Test
    void prScopeTakesTheNewestVersionOfEachFamily() throws IOException {
        VersionFamilies families = VersionFamilies.parse(FAMILIES, catalog());

        assertThat(families.versions("paper", Scope.PR)).containsExactly("1.10.2", "26.3");
    }

    @Test
    void nightlyScopeTakesEveryFamilyVersion() throws IOException {
        VersionFamilies families = VersionFamilies.parse(FAMILIES, catalog());

        assertThat(families.versions("paper", Scope.NIGHTLY))
                .containsExactlyInAnyOrder("1.9.4", "1.10.2", "1.20.4", "26.3");
    }

    @Test
    void platformWithoutFamiliesHasNone() throws IOException {
        VersionFamilies families = VersionFamilies.parse(FAMILIES, catalog());

        assertThat(families.versions("folia", Scope.NIGHTLY)).isEmpty();
    }

    @Test
    void emptyDocumentAndEmptyFamilyMapAreNone() throws IOException {
        assertThat(VersionFamilies.parse("", catalog()).versions("paper", Scope.NIGHTLY)).isEmpty();
        assertThat(VersionFamilies.parse("version-families:\n", catalog()).versions("paper", Scope.NIGHTLY))
                .isEmpty();
    }

    @Test
    void unknownPlatformKeyIsAnError() throws IOException {
        String yaml = "version-families:\n  nope:\n    a: [\"1.9.4\"]\n";

        assertThatThrownBy(() -> VersionFamilies.parse(yaml, catalog()))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("unknown platform \"nope\"");
    }

    @Test
    void versionThatIsNotExactIsAnError() throws IOException {
        String yaml = "version-families:\n  paper:\n    a: [\"latest\"]\n";

        assertThatThrownBy(() -> VersionFamilies.parse(yaml, catalog()))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("latest")
                .hasMessageContaining("exact version");
    }

    @Test
    void unknownTopLevelKeyIsAnError() throws IOException {
        assertThatThrownBy(() -> VersionFamilies.parse("version-familes:\n  paper: {}\n", catalog()))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("version-familes");
    }

    @Test
    void familyThatIsNotAListIsAnError() throws IOException {
        String yaml = "version-families:\n  paper:\n    netty: 1.9.4\n";

        assertThatThrownBy(() -> VersionFamilies.parse(yaml, catalog()))
                .isInstanceOf(VersionsException.class)
                .hasMessageContaining("netty");
    }

    @Test
    void loadReadsTheFileAndTreatsAMissingFileAsNone() throws IOException {
        Path file = dir.resolve("proofstone.yml");
        Files.writeString(file, FAMILIES);

        assertThat(VersionFamilies.load(file, catalog()).versions("paper", Scope.PR))
                .containsExactly("1.10.2", "26.3");
        assertThat(VersionFamilies.load(dir.resolve("absent.yml"), catalog()).versions("paper", Scope.PR))
                .isEmpty();
    }
}
