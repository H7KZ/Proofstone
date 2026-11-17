package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Geyser bundle never holds the Floodgate key in any form, or anything from plugins/floodgate (ADR-0018 section 5). */
class GeyserBundleTest {

    private static final byte[] KEY = "0123456789abcdef".getBytes(StandardCharsets.ISO_8859_1);

    @TempDir
    Path dir;

    /** The forms the host hands the writer; the same set FloodgateKey produces. */
    private static List<String> forms() {
        return List.of(HexFormat.of().formatHex(KEY), HexFormat.of().withUpperCase().formatHex(KEY),
                Base64.getEncoder().encodeToString(KEY), Base64.getUrlEncoder().encodeToString(KEY),
                Base64.getEncoder().withoutPadding().encodeToString(KEY),
                Base64.getUrlEncoder().withoutPadding().encodeToString(KEY), new String(KEY, StandardCharsets.ISO_8859_1));
    }

    @Test
    void everyBundleFileIsFreeOfTheKeyInRawHexAndBase64FormAndOfBothTokens() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("server/server.log", "key raw " + forms().get(6) + "\nhex " + forms().get(0) + "\nHEX " + forms().get(1)
                + "\nb64 " + forms().get(2) + "\nurl " + forms().get(3) + "\ntoken-a tok1\n");
        files.put("stand-in/stand-in.log", "b64 no pad " + forms().get(4) + " tok2\n");
        files.put("driver-host/driver-host.log", "x " + forms().get(5) + "\n");
        files.put("cell.json", "{\"bedrockAuth\":\"offline-stub\"}\n");

        GeyserBundle.write(dir, files, List.of("tok1", "tok2"), forms());

        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> written = walk.filter(Files::isRegularFile).toList();
            assertThat(written).hasSize(4);
            for (Path file : written) {
                String text = Files.readString(file);
                for (String form : forms()) {
                    assertThat(text).as(file + " holds " + form.length() + "-char form").doesNotContain(form);
                }
                assertThat(text).doesNotContain("tok1").doesNotContain("tok2");
            }
        }
        assertThat(Files.readString(dir.resolve("cell.json"))).contains("offline-stub");
    }

    @Test
    void nothingFromPluginsFloodgateIsEverWritten() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("server/server.log", "log\n");
        files.put("server/plugins/floodgate/key.pem", "binary key");
        files.put("server/plugins/floodgate/config.yml", "key-file-name: key.pem");
        files.put("plugins/floodgate/key.pem", "x");
        files.put("server\\plugins\\floodgate\\key.pem", "x");
        GeyserBundle.write(dir, files, List.of("tok"), forms());
        try (Stream<Path> walk = Files.walk(dir)) {
            assertThat(walk.filter(Files::isRegularFile).map(p -> dir.relativize(p).toString().replace('\\', '/')).toList())
                    .containsExactly("server/server.log");
        }
    }

    @Test
    void aBlankTokenOrKeyFormIsRefusedBecauseItWouldMatchEverywhere() {
        assertThatThrownBy(() -> GeyserBundle.write(dir, Map.of("a", "b"), List.of(" "), forms()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> GeyserBundle.write(dir, Map.of("a", "b"), List.of("t"), List.of("")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void floodgatePathsAreRecognisedWithAnySeparatorAndPrefix() {
        assertThat(GeyserBundle.isFloodgateFile("plugins/floodgate")).isTrue();
        assertThat(GeyserBundle.isFloodgateFile("a/plugins/floodgate/key.pem")).isTrue();
        assertThat(GeyserBundle.isFloodgateFile("a\\plugins\\floodgate\\key.pem")).isTrue();
        assertThat(GeyserBundle.isFloodgateFile("plugins/floodgate-other.txt")).isFalse();
        assertThat(GeyserBundle.isFloodgateFile("server/server.log")).isFalse();
    }
}
