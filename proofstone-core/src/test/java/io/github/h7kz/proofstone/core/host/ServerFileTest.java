package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

/** A copied config file is owned and writable by the server user, and a permission error at start fails the cell (T-M8-02). */
class ServerFileTest {

    private record Packed(TarArchiveEntry entry, byte[] body) { }

    private static Packed packed(ServerFile file, String path) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            file.transferTo(tar, path);
        }
        try (TarArchiveInputStream in = new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            TarArchiveEntry entry = in.getNextEntry();
            return new Packed(entry, in.readAllBytes());
        }
    }

    @Test
    void aCopiedFileIsOwnedByTheServerUserWithAnOwnerWritableMode() throws IOException {
        Packed packed = packed(ServerFile.of("settings:\n  a: 1\n"), "/data/bukkit.yml");

        assertThat(packed.entry().getName()).isEqualTo("data/bukkit.yml"); // tar strips the leading slash
        assertThat(packed.entry().getLongUserId()).isEqualTo(1000);
        assertThat(packed.entry().getLongGroupId()).isEqualTo(1000);
        assertThat(packed.entry().getMode() & 0777).isEqualTo(0644);
        assertThat(new String(packed.body(), StandardCharsets.UTF_8)).isEqualTo("settings:\n  a: 1\n");
    }

    @Test
    void everyConfigCopyOfAPaperBackendAndAProxyIsOwnedByTheServerUser() throws IOException {
        Map<String, String> topology = Map.of("/data/spigot.yml", "a: 1\n", "/config/paper-global.yml", "b: 2\n");
        Map<String, ServerFile> backend = PaperHost.configCopies(topology);
        Map<String, ServerFile> proxy = ProxyHost.configCopies(Map.of("/config/velocity.toml", "c = 3\n"));

        assertThat(backend.keySet()).containsExactlyInAnyOrder("/data/bukkit.yml", "/data/spigot.yml",
                "/config/paper-global.yml");
        assertThat(proxy.keySet()).containsExactly("/config/velocity.toml");
        for (Map<String, ServerFile> copies : List.of(backend, proxy)) {
            for (Map.Entry<String, ServerFile> copy : copies.entrySet()) {
                TarArchiveEntry entry = packed(copy.getValue(), copy.getKey()).entry();
                assertThat(entry.getLongUserId()).as(copy.getKey()).isEqualTo(1000);
                assertThat(entry.getLongGroupId()).as(copy.getKey()).isEqualTo(1000);
                assertThat(entry.getMode() & 0200).as(copy.getKey() + " owner-writable").isEqualTo(0200);
            }
        }
    }

    @Test
    void aMultiByteFileKeepsItsByteSize() throws IOException {
        String text = "motd: éé\n";
        Packed packed = packed(ServerFile.of(text), "/config/paper-global.yml");

        assertThat(packed.entry().getSize()).isEqualTo(text.getBytes(StandardCharsets.UTF_8).length);
        assertThat(packed.body()).hasSize((int) packed.entry().getSize());
    }

    @Test
    void aPermissionErrorForACopiedFileFailsAndNamesTheFile() {
        String log = "[12:00:00 INFO]: Starting\n"
                + "[12:00:01 ERROR]: Could not save spigot.yml (Permission denied)\n[12:00:02 INFO]: Done (3s)!";

        assertThatThrownBy(() -> PermissionCheck.require("paper@26.3", log,
                List.of("/data/bukkit.yml", "/data/spigot.yml")))
                .isInstanceOf(PaperHostException.class)
                .hasMessageContaining("copied file /data/spigot.yml")
                .hasMessageContaining("Could not save spigot.yml")
                .hasMessageNotContaining("bukkit.yml");
    }

    @Test
    void aPermissionErrorForAnUnknownFileStillFails() {
        assertThatThrownBy(() -> PermissionCheck.require("velocity", "java.io.IOException: /server/x (Permission denied)",
                List.of("/config/velocity.toml")))
                .isInstanceOf(PaperHostException.class).hasMessageContaining("did not copy by name");
    }

    @Test
    void aLogWithoutAPermissionErrorPasses() {
        PermissionCheck.require("paper@26.3", "Done (3s)!\nPlayer joined", List.of("/data/bukkit.yml"));
    }
}
