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

/** A mod that crashes at load is named (ADR-0017 section 4, T-M8-11), without a server. */
class ModLoadTest {

    @TempDir
    Path dir;

    private Path jar(String name, String entry, String content) throws IOException {
        Path file = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return file;
    }

    @Test
    void readsAFabricModIdFromItsJson() throws IOException {
        Path mod = jar("a.jar", "fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"proofstone_example\",\"version\":\"1\"}");

        assertThat(ModLoad.ids(mod)).containsExactly("proofstone_example");
    }

    @Test
    void readsEveryNeoForgeModIdFromItsToml() throws IOException {
        Path mod = jar("b.jar", "META-INF/neoforge.mods.toml", "modLoader = \"javafml\"\n[[mods]]\nmodId = \"one\"\n"
                + "version = \"1\"\n[[mods]]\n  modId=\"two\"\n");

        assertThat(ModLoad.ids(mod)).containsExactly("one", "two");
    }

    @Test
    void aJarWithNoModDescriptorIsAnErrorNotAnEmptyList() throws IOException {
        Path mod = jar("c.jar", "readme.txt", "not a mod");

        assertThatThrownBy(() -> ModLoad.ids(mod)).isInstanceOf(PaperHostException.class)
                .hasMessageContaining("c.jar").hasMessageContaining("fabric.mod.json");
    }

    @Test
    void theModTheLoaderNamedInItsFailureIsNamedInTheVerdict() throws IOException {
        Path mod = jar("proofstone-example-mod.jar", "fabric.mod.json", "{\"id\":\"proofstone_example\"}");
        Path other = jar("other.jar", "fabric.mod.json", "{\"id\":\"other_mod\"}");
        String log = "[04:11:04] [main/ERROR] (FabricLoader) Could not execute entrypoint stage 'server' due to errors, "
                + "provided by 'proofstone_example' at 'proofstone-example-mod.jar'!\n"
                + "java.lang.IllegalStateException: example mod was asked to fail by config\n"
                + "[04:11:04] [main/ERROR] (Minecraft) Failed to start the minecraft server";

        String message = ModLoad.invalid("fabric@26.3", List.of(mod, other), "Failed to start the minecraft server", log);

        assertThat(message).startsWith("INVALID").contains("proofstone_example").contains("proofstone-example-mod.jar")
                .contains("fabric@26.3").contains("Failed to start the minecraft server")
                .contains("example mod was asked to fail").doesNotContain("other_mod");
    }

    @Test
    void aFailedStartThatNamesNoDeclaredModIsNotBlamedOnOne() throws IOException {
        Path mod = jar("a.jar", "fabric.mod.json", "{\"id\":\"quiet_mod\"}");

        assertThat(ModLoad.invalid("fabric@26.3", List.of(mod), "Failed to start the minecraft server",
                "Failed to start the minecraft server\nCould not reach launchermeta.mojang.com")).isNull();
        assertThat(ModLoad.invalid("fabric@26.3", List.of(), "Failed to start the minecraft server",
                "proofstone_example")).isNull();
    }
}
