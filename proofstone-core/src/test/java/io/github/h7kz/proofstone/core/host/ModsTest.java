package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A declared mod resolves like a data pack does: one match per glob, and never the Fabric API (T-M8-13), without Docker. */
class ModsTest {

    @TempDir
    Path dir;

    private Path jar(String relative, String id) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(("{\"schemaVersion\":1,\"id\":\"" + id + "\"}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return file;
    }

    @Test
    void eachGlobResolvesToItsOneJar() throws IOException {
        Path a = jar("build/libs/my-mod-1.0.jar", "my_mod");
        Path b = jar("other/second.jar", "second_mod");

        assertThat(Mods.resolve(dir, List.of("build/libs/my-mod-*.jar", "other/second.jar"))).containsExactly(a, b);
    }

    @Test
    void aGlobThatMatchesNothingFailsNamingTheGlob() {
        assertThatThrownBy(() -> Mods.resolve(dir, List.of("build/libs/my-mod-*.jar")))
                .isInstanceOf(PaperHostException.class).hasMessageStartingWith("INVALID test declaration")
                .hasMessageContaining("build/libs/my-mod-*.jar").hasMessageContaining("matched no file");
    }

    @Test
    void aGlobThatMatchesTwoJarsFailsNamingTheGlobAndBothFiles() throws IOException {
        jar("build/libs/my-mod-1.0.jar", "my_mod");
        jar("build/libs/my-mod-1.1.jar", "my_mod");

        assertThatThrownBy(() -> Mods.resolve(dir, List.of("build/libs/my-mod-*.jar")))
                .isInstanceOf(PaperHostException.class).hasMessageStartingWith("INVALID test declaration")
                .hasMessageContaining("build/libs/my-mod-*.jar").hasMessageContaining("exactly one")
                .hasMessageContaining("my-mod-1.0.jar").hasMessageContaining("my-mod-1.1.jar");
    }

    @Test
    void aModWithTheIdFabricApiIsRefusedBecauseTheCellProvidesIt() throws IOException {
        jar("libs/some-fabric-api.jar", "fabric-api");

        assertThatThrownBy(() -> Mods.resolve(dir, List.of("libs/some-fabric-api.jar")))
                .isInstanceOf(PaperHostException.class).hasMessageStartingWith("INVALID test declaration")
                .hasMessageContaining("fabric-api").hasMessageContaining("some-fabric-api.jar")
                .hasMessageContaining("provides the pinned");
    }

    @Test
    void noDeclaredModIsNoJar() {
        assertThat(Mods.resolve(dir, List.of())).isEmpty();
    }
}
