package io.github.h7kz.proofstone.build.versions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// T-M3-01. The API answers come from responses recorded on 2026-11-07 (src/test/resources/versions/api). The
// server jars are synthetic zips built here, so unit tests never touch the network and no server binary is
// committed. The recorded checksums belong to the real jars, so the stub swaps in its own (see StubSource).
class VersionsUpdaterTest {

    private static final Path SCHEMA = Path.of("../proofstone-protocol/schemas/versions.schema.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    // protocol_version the stub jars carry. 777 for 26.3 is the value read from the real Paper 26.3 build 244 jar.
    // The others are test values, not observations.
    private static final Map<String, Integer> PROTOCOLS = Map.of(
            "26.3", 777, "26.1.2", 775, "26.2", 776, "1.21.11", 774, "1.18.2", 758);

    @TempDir
    Path tmp;

    @Test
    void fillsStaleRowsAndAddsNewerFoliaRows() throws Exception {
        StubSource source = new StubSource(PROTOCOLS, true);
        VersionsUpdater.Result result = VersionsUpdater.update(read("update-input.yml"), source);
        String text = result.text();

        // Paper 26.3 moves from the seed build 238 to 244, with the jar's checksum and protocol.
        // (The seed aligns its columns, so the assertions match from "build:" onwards.)
        assertThat(text).contains("build: 244, sha256: \"" + source.sha("26.3")
                + "\", protocol: 777, status: experimental, observed: \"prototype 8/8 via ViaBackwards\" }");
        // TODO pins become real values.
        assertThat(text).contains("build: 388, sha256: \"" + source.sha("1.18.2") + "\", protocol: 758, status:");
        // Folia gets every stable version newer than its newest row (1.21.11), in ascending order, after that row.
        assertThat(text).containsSubsequence(
                "mc: \"1.21.11\", build: 14,",
                "mc: \"26.1.2\", build: 8,",
                "mc: \"26.2\", build: 7,",
                "mc: \"26.3\", build: 6, sha256: \"" + source.sha("26.3") + "\", protocol: 777, status: experimental }");
        assertThat(text).doesNotContain("26.3-rc-3");
        assertThat(rowLines(text)).hasSize(8);
        // Comments survive the rewrite.
        assertThat(text).contains("# SEED DATA").contains("java-rules:");
        assertThat(result.log()).anySatisfy(line -> assertThat(line).contains("folia 26.3").contains("new row"));
    }

    // T-M5-04. Purpur's API gives an md5, not a sha256: the jar must match the md5, and the row pins its sha256.
    @Test
    void purpurRowsCheckTheMd5AndPinTheSha256() throws Exception {
        byte[] jar1 = StubSource.jar(774);
        byte[] jar2 = StubSource.jar(777);
        String yaml = """
                java-rules:
                  - { range: ">=1.20.5 <26", image: java21 }
                  - { range: ">=26", image: java25 }
                platforms:
                  purpur:
                    host:
                      image: "itzg/minecraft-server"
                      digests:
                        java21: "sha256:04b5ee189f5f2c1992fa557e366cf2e4d09fe2abfbd57d2d1f977d8c038c1b98"
                    rows:
                      - { mc: "1.21.11", build: 1, sha256: "TODO", protocol: 774, status: experimental }
                """;
        PurpurSource source = new PurpurSource(jar1, jar2, md5Hex(jar1), md5Hex(jar2), "1.21.11", "26.3");
        VersionsUpdater.Result result = VersionsUpdater.update(yaml, source);

        assertThat(result.text()).contains("mc: \"1.21.11\", build: 2568, sha256: \"" + sha256Hex(jar1)
                + "\", protocol: 774, status: experimental }");
        assertThat(result.text()).contains("mc: \"26.3\", build: 2657, sha256: \"" + sha256Hex(jar2)
                + "\", protocol: 777, status: experimental }");
        assertThat(source.downloads).containsExactly("https://api.purpurmc.org/v2/purpur/1.21.11/2568/download",
                "https://api.purpurmc.org/v2/purpur/26.3/2657/download");
    }

    @Test
    void purpurJarWithTheWrongMd5FailsLoudly() throws Exception {
        byte[] jar = StubSource.jar(774);
        PurpurSource source = new PurpurSource(jar, jar, "0".repeat(32), md5Hex(jar), "1.21.11", "26.3");
        String yaml = "java-rules:\n  - { range: \">=1.20.5 <26\", image: java21 }\n"
                + "platforms:\n  purpur:\n    host:\n      image: \"itzg/minecraft-server\"\n      digests:\n"
                + "        java21: \"sha256:04b5ee189f5f2c1992fa557e366cf2e4d09fe2abfbd57d2d1f977d8c038c1b98\"\n"
                + "    rows:\n      - { mc: \"1.21.11\", build: 1, sha256: \"TODO\", protocol: 774, status: experimental }\n";
        assertThatThrownBy(() -> VersionsUpdater.update(yaml, source))
                .hasMessageContaining("md5 mismatch");
    }

    /** Serves the Purpur API shapes for two versions, with the jars and md5s given. */
    static final class PurpurSource implements VersionSource {
        private final byte[] jarOld;
        private final byte[] jarNew;
        private final String md5Old;
        private final String md5New;
        private final String oldMc;
        private final String newMc;
        final List<String> downloads = new ArrayList<>();

        PurpurSource(byte[] jarOld, byte[] jarNew, String md5Old, String md5New, String oldMc, String newMc) {
            this.jarOld = jarOld;
            this.jarNew = jarNew;
            this.md5Old = md5Old;
            this.md5New = md5New;
            this.oldMc = oldMc;
            this.newMc = newMc;
        }

        @Override
        public String getText(URI uri) throws IOException {
            String path = uri.toString();
            if (path.equals("https://api.purpurmc.org/v2/purpur")) {
                return "{\"project\":\"purpur\",\"versions\":[\"" + oldMc + "\",\"" + newMc + "\"]}";
            }
            if (path.equals("https://api.purpurmc.org/v2/purpur/" + oldMc + "/latest")) {
                return "{\"build\":\"2568\",\"md5\":\"" + md5Old + "\"}";
            }
            if (path.equals("https://api.purpurmc.org/v2/purpur/" + newMc + "/latest")) {
                return "{\"build\":\"2657\",\"md5\":\"" + md5New + "\"}";
            }
            throw new IOException("stub has no answer for " + uri);
        }

        @Override
        public Path download(URI uri) throws IOException {
            downloads.add(uri.toString());
            byte[] jar = uri.toString().contains("/" + oldMc + "/") ? jarOld : jarNew;
            Path file = Files.createTempFile("purpur-stub", ".jar");
            Files.write(file, jar);
            return file;
        }
    }

    private static String md5Hex(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static String sha256Hex(byte[] bytes) throws IOException {
        return StubSource.sha256Hex(bytes);
    }

    @Test
    void secondRunIsIdempotentAndDownloadsNothing() throws Exception {
        String first = VersionsUpdater.update(read("update-input.yml"), new StubSource(PROTOCOLS, true)).text();

        StubSource again = new StubSource(PROTOCOLS, true);
        String second = VersionsUpdater.update(first, again).text();

        assertThat(second).isEqualTo(first);
        assertThat(again.downloads).isEmpty();
    }

    @Test
    void dryRunPrintsTheDiffAndWritesNothing() throws Exception {
        Path file = tmp.resolve("versions.yml");
        Files.writeString(file, read("update-input.yml"));

        VersionsUpdater.Result result = VersionsUpdater.run(file, SCHEMA, true, new StubSource(PROTOCOLS, true));

        assertThat(Files.readString(file)).isEqualTo(read("update-input.yml"));
        String diff = result.diff();
        assertThat(diff).contains("-      - { mc: \"26.3\",    build: 238, sha256: \"989f93c2")
                .contains("+      - { mc: \"26.3\",    build: 244, sha256: \"" + new StubSource(PROTOCOLS, true).sha("26.3"))
                .contains("+      - { mc: \"26.3\", build: 6,");
    }

    @Test
    void realRunWritesAValidFile() throws Exception {
        Path file = tmp.resolve("versions.yml");
        Files.writeString(file, read("update-input.yml"));

        VersionsUpdater.Result result = VersionsUpdater.run(file, SCHEMA, false, new StubSource(PROTOCOLS, true));

        assertThat(Files.readString(file)).isEqualTo(result.text());
        assertThat(VersionsValidator.validate(file, SCHEMA)).isEmpty();
    }

    @Test
    void checksumMismatchFailsLoudly() {
        // Without the substitution the recorded checksum (of the real jar) does not match the stub download.
        assertThatThrownBy(() -> VersionsUpdater.update(read("update-input.yml"), new StubSource(PROTOCOLS, false)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("sha256 mismatch");
    }

    @Test
    void jarWithoutProtocolVersionFailsLoudly() {
        assertThatThrownBy(() -> VersionsUpdater.update(read("update-input.yml"), new StubSource(Map.of(), true)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("version.json");
    }

    @Test
    void oldJarWithoutVersionJsonKeepsTheRowsOwnProtocol() throws Exception {
        // Paperclip jars before 1.17 carry no version.json; the row's protocol stays and the log says so.
        String input = read("update-input.yml").replace("{ mc: \"1.18.2\",  build: 388,", "{ mc: \"1.18.2\",  protocol: 758, build: 388,");
        Map<String, Integer> others = Map.of("26.3", 777, "26.1.2", 775, "26.2", 776, "1.21.11", 774);

        VersionsUpdater.Result result = VersionsUpdater.update(input, new StubSource(others, true));

        assertThat(result.log()).anySatisfy(line -> assertThat(line)
                .contains("1.18.2").contains("protocol 758 kept from versions.yml"));
        assertThat(result.text()).contains("protocol: 758");
    }

    private static List<String> rowLines(String text) {
        return text.lines().filter(line -> line.matches("^\\s*- \\{ mc: .*")).toList();
    }

    private static String read(String name) throws IOException {
        try {
            return Files.readString(Path.of(VersionsUpdaterTest.class.getResource("/versions/" + name).toURI()));
        } catch (java.net.URISyntaxException e) {
            throw new IOException(e);
        }
    }

    /**
     * Serves the recorded fill responses by URL. With {@code substituteChecksums}, the recorded checksum is replaced
     * by the stub jar's own, so the download is verified against a jar we built. Each stub jar carries a
     * version.json with the protocol given for its Minecraft version, or none when the map has no entry.
     */
    static final class StubSource implements VersionSource {
        private final Map<String, Integer> protocols;
        private final boolean substituteChecksums;
        private final Map<String, byte[]> jars = new HashMap<>();
        final List<String> downloads = new ArrayList<>();

        StubSource(Map<String, Integer> protocols, boolean substituteChecksums) {
            this.protocols = protocols;
            this.substituteChecksums = substituteChecksums;
        }

        String sha(String mc) throws IOException {
            return sha256Hex(jar(protocols.get(mc)));
        }

        @Override
        public String getText(URI uri) throws IOException {
            // /v3/projects/<platform>  or  /v3/projects/<platform>/versions/<mc>/builds/latest
            String[] parts = uri.getPath().split("/");
            if (parts.length == 4) {
                return fixture("fill-" + parts[3] + "-project.json");
            }
            String text = fixture("fill-" + parts[3] + "-" + parts[5] + "-latest.json");
            JsonNode server = JSON.readTree(text).path("downloads").path("server:default");
            String recorded = server.path("checksums").path("sha256").asText();
            byte[] jar = jar(protocols.get(parts[5]));
            jars.put(server.path("url").asText(), jar);
            // Only the checksums field: the download URL carries the same hash in its path.
            return substituteChecksums
                    ? text.replace("\"sha256\":\"" + recorded + "\"", "\"sha256\":\"" + sha256Hex(jar) + "\"")
                    : text;
        }

        @Override
        public Path download(URI uri) throws IOException {
            downloads.add(uri.toString());
            byte[] jar = jars.get(uri.toString());
            if (jar == null) {
                throw new IOException("stub has no jar for " + uri);
            }
            Path file = Files.createTempFile("stub-jar", ".jar");
            Files.write(file, jar);
            return file;
        }

        private static String fixture(String name) throws IOException {
            try {
                return Files.readString(Path.of(VersionsUpdaterTest.class.getResource("/versions/api/" + name).toURI()));
            } catch (java.net.URISyntaxException e) {
                throw new IOException(e);
            }
        }

        private static byte[] jar(Integer protocol) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                ZipEntry entry = new ZipEntry(protocol == null ? "META-INF/MANIFEST.MF" : "version.json");
                entry.setLastModifiedTime(FileTime.from(Instant.parse("2026-01-01T00:00:00Z")));
                zip.putNextEntry(entry);
                String body = protocol == null
                        ? "Manifest-Version: 1.0\n"
                        : "{\"id\": \"stub\", \"protocol_version\": " + protocol + "}";
                zip.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return bytes.toByteArray();
        }

        private static String sha256Hex(byte[] bytes) throws IOException {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
        }
    }
}
