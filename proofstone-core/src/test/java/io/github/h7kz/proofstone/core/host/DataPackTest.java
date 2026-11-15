package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The data-pack artefact (ADR-0017 section 4): what a glob may match, and what is copied into the container. */
class DataPackTest {

    private static final String MCMETA = "{\"pack\":{\"description\":\"t\",\"min_format\":121,\"max_format\":121}}";

    @TempDir
    Path dir;

    @Test
    void aZipWithPackMcmetaAtItsRootIsOnePack() throws IOException {
        writeZip(dir.resolve("packs/my.zip"), "pack.mcmeta", MCMETA, "data/x/function/f.mcfunction", "say hi");

        List<DataPack> packs = DataPack.resolve(dir, List.of("packs/*.zip"));

        assertThat(packs).hasSize(1);
        assertThat(packs.get(0).id()).isEqualTo("file/my.zip");
        assertThat(packs.get(0).containerPath()).isEqualTo("/datapacks-in/my.zip");
    }

    @Test
    void aDirectoryHoldingPackMcmetaIsZippedUnderItsNameWithTheFilesInside() throws IOException {
        Path pack = dir.resolve("src/e2e/datapacks/my-pack");
        Files.createDirectories(pack.resolve("data/x/function"));
        Files.writeString(pack.resolve("pack.mcmeta"), MCMETA);
        Files.writeString(pack.resolve("data/x/function/f.mcfunction"), "say hi");

        List<DataPack> packs = DataPack.resolve(dir, List.of("src/e2e/datapacks/my-pack"));

        assertThat(packs).hasSize(1);
        assertThat(packs.get(0).id()).isEqualTo("file/my-pack.zip");
        assertThat(entries(packs.get(0).zip())).containsExactly("data/x/function/f.mcfunction", "pack.mcmeta");
    }

    @Test
    void thePackKnowsTheNamespacesItDefines() throws IOException {
        writeZip(dir.resolve("my.zip"), "pack.mcmeta", MCMETA, "data/alpha/function/f.mcfunction", "say hi",
                "data/beta/recipe/r.json", "{}", "data/alpha/recipe/r.json", "{}");

        assertThat(DataPack.resolve(dir, List.of("my.zip")).get(0).namespaces()).containsExactly("alpha", "beta");
    }

    @Test
    void theZipOfADirectoryIsTheSameBytesEveryTime() throws IOException {
        Path pack = dir.resolve("p");
        Files.createDirectories(pack.resolve("data/x"));
        Files.writeString(pack.resolve("pack.mcmeta"), MCMETA);
        Files.writeString(pack.resolve("data/x/a.json"), "{}");

        assertThat(DataPack.resolve(dir, List.of("p")).get(0).zip())
                .isEqualTo(DataPack.resolve(dir, List.of("p")).get(0).zip());
    }

    @Test
    void aGlobThatMatchesNothingNamesTheGlob() {
        assertThatThrownBy(() -> DataPack.resolve(dir, List.of("packs/*.zip")))
                .isInstanceOf(PaperHostException.class).hasMessageContaining("packs/*.zip").hasMessageContaining("no ");
    }

    @Test
    void aGlobThatMatchesTwoPacksIsAnErrorNamingBoth() throws IOException {
        writeZip(dir.resolve("packs/a.zip"), "pack.mcmeta", MCMETA);
        writeZip(dir.resolve("packs/b.zip"), "pack.mcmeta", MCMETA);

        assertThatThrownBy(() -> DataPack.resolve(dir, List.of("packs/*.zip")))
                .isInstanceOf(PaperHostException.class).hasMessageContaining("exactly one")
                .hasMessageContaining("a.zip").hasMessageContaining("b.zip");
    }

    @Test
    void aDirectoryWithoutPackMcmetaIsRefusedNamingIt() throws IOException {
        Files.createDirectories(dir.resolve("empty-pack/data"));

        assertThatThrownBy(() -> DataPack.resolve(dir, List.of("empty-pack")))
                .isInstanceOf(PaperHostException.class).hasMessageContaining("empty-pack")
                .hasMessageContaining("pack.mcmeta");
    }

    @Test
    void aZipWithoutPackMcmetaAtItsRootIsRefusedNamingIt() throws IOException {
        writeZip(dir.resolve("wrapped.zip"), "inner/pack.mcmeta", MCMETA);

        assertThatThrownBy(() -> DataPack.resolve(dir, List.of("wrapped.zip")))
                .isInstanceOf(PaperHostException.class).hasMessageContaining("wrapped.zip")
                .hasMessageContaining("pack.mcmeta");
    }

    @Test
    void aFileThatIsNotAZipIsRefused() throws IOException {
        Files.writeString(dir.resolve("notes.txt"), "x");

        assertThatThrownBy(() -> DataPack.resolve(dir, List.of("notes.txt")))
                .isInstanceOf(PaperHostException.class).hasMessageContaining("notes.txt").hasMessageContaining(".zip");
    }

    @Test
    void twoPacksWithTheSameNameAreRefused() throws IOException {
        writeZip(dir.resolve("one/p.zip"), "pack.mcmeta", MCMETA);
        writeZip(dir.resolve("two/p.zip"), "pack.mcmeta", MCMETA);

        assertThatThrownBy(() -> DataPack.resolve(dir, List.of("one/p.zip", "two/p.zip")))
                .isInstanceOf(PaperHostException.class).hasMessageContaining("file/p.zip");
    }

    @Test
    void theContainerEnvListsEveryPackByItsContainerPath() throws IOException {
        writeZip(dir.resolve("a.zip"), "pack.mcmeta", MCMETA);
        writeZip(dir.resolve("b.zip"), "pack.mcmeta", MCMETA);

        assertThat(DataPack.env(DataPack.resolve(dir, List.of("a.zip", "b.zip"))))
                .isEqualTo("/datapacks-in/a.zip,/datapacks-in/b.zip");
        assertThat(DataPack.env(List.of())).isNull();
    }

    private static List<String> entries(byte[] zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static void writeZip(Path file, String... nameAndContent) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (int i = 0; i < nameAndContent.length; i += 2) {
                out.putNextEntry(new ZipEntry(nameAndContent[i]));
                out.write((i + 1 < nameAndContent.length ? nameAndContent[i + 1] : "").getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        Files.write(file, bytes.toByteArray());
    }
}
