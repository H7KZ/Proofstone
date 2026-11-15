package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T-M8-10: the harness-JVM pack source on loopback (ADR-0017 section 7). */
class ResourcePackSourceTest {

    @TempDir
    Path base;

    private Path pack(String name, byte[] bytes) throws Exception {
        Path file = base.resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        return file;
    }

    private static HttpResponse<byte[]> get(String url) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void itServesExactlyTheDeclaredBytesOnLoopbackAndNothingElse() throws Exception {
        byte[] bytes = {1, 2, 3, 4};
        pack("packs/one.zip", bytes);
        try (ResourcePackSource source = ResourcePackSource.serve(base, "packs/*.zip")) {
            assertThat(source.base()).startsWith("http://127.0.0.1:").endsWith("/");
            assertThat(source.url()).startsWith(source.base()).endsWith(".zip");
            assertThat(source.sha1()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes)));

            HttpResponse<byte[]> served = get(source.url());
            assertThat(served.statusCode()).isEqualTo(200);
            assertThat(served.body()).isEqualTo(bytes);
            assertThat(get(source.base() + "other.zip").statusCode()).isEqualTo(404);
            assertThat(source.requests()).isEqualTo(2);
        }
    }

    @Test
    void theServerPropertiesPointAtTheSourceWithTheFilesHashAndAFixedId() throws Exception {
        pack("one.zip", new byte[] {9});
        try (ResourcePackSource source = ResourcePackSource.serve(base, "one.zip")) {
            Map<String, String> env = source.env(true, "{\"text\":\"Please\"}", "");
            assertThat(env).containsEntry("RESOURCE_PACK", source.url())
                    .containsEntry("RESOURCE_PACK_SHA1", source.sha1())
                    .containsEntry("RESOURCE_PACK_ENFORCE", "TRUE")
                    .containsEntry("RESOURCE_PACK_PROMPT", "{\"text\":\"Please\"}")
                    .containsKey("RESOURCE_PACK_ID");
            assertThat(source.env(false, "", "").get("RESOURCE_PACK_ID")).as("stable for the same bytes")
                    .isEqualTo(env.get("RESOURCE_PACK_ID"));
            assertThat(source.env(false, "", "")).containsEntry("RESOURCE_PACK_ENFORCE", "FALSE")
                    .doesNotContainKey("RESOURCE_PACK_PROMPT");
        }
    }

    @Test
    void aDeclaredWrongHashIsPushedInsteadOfTheFilesOwn() throws Exception {
        pack("one.zip", new byte[] {9});
        String wrong = "0123456789abcdef0123456789abcdef01234567";
        try (ResourcePackSource source = ResourcePackSource.serve(base, "one.zip")) {
            assertThat(source.env(true, "", wrong)).containsEntry("RESOURCE_PACK_SHA1", wrong);
            assertThatThrownBy(() -> source.env(true, "", "not-a-hash")).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INVALID test declaration").hasMessageContaining("sha1");
        }
    }

    @Test
    void aPlainStringPromptIsRefusedBecauseTheServerDropsIt() throws Exception {
        pack("one.zip", new byte[] {9});
        try (ResourcePackSource source = ResourcePackSource.serve(base, "one.zip")) {
            assertThatThrownBy(() -> source.env(true, "Please", "")).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INVALID test declaration").hasMessageContaining("text component");
        }
    }

    @Test
    void theGlobMustMatchExactlyOneZip() throws Exception {
        pack("a.zip", new byte[] {1});
        pack("b.zip", new byte[] {2});
        pack("c.txt", new byte[] {3});
        assertThatThrownBy(() -> ResourcePackSource.serve(base, "*.zip")).isInstanceOf(PaperHostException.class)
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> ResourcePackSource.serve(base, "c.txt")).isInstanceOf(PaperHostException.class)
                .hasMessageContaining(".zip");
        assertThatThrownBy(() -> ResourcePackSource.serve(base, "none/*.zip")).isInstanceOf(PaperHostException.class)
                .hasMessageContaining("matched no file");
    }
}
