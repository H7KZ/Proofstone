package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.host.LoaderEnvironment.Download;
import io.github.h7kz.proofstone.core.host.LoaderEnvironment.PinnedFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoaderPinsTest {

    private static final byte[] BYTES = "pinned bytes".getBytes(StandardCharsets.UTF_8);
    // sha256 of "pinned bytes", computed by LoaderPins itself below so the test does not repeat a literal.
    private static final String ZERO = "0".repeat(64);

    private static Download download(String sha256) {
        return new Download("loader.jar", "https://example.test/loader.jar", sha256, "/pins/loader.jar");
    }

    private static String sha256Of(Path dir) throws IOException {
        Path file = dir.resolve("probe");
        Files.write(file, BYTES);
        return LoaderPins.sha256(file);
    }

    @Test
    void aMatchingDownloadIsKeptInTheCache(@TempDir Path dir) throws IOException {
        String sha = sha256Of(dir);
        List<String> urls = new ArrayList<>();

        List<PinnedFile> pins = LoaderPins.resolve(List.of(download(sha)), dir.resolve("cache"), (url, target) -> {
            urls.add(url);
            Files.write(target, BYTES);
        });

        assertThat(urls).containsExactly("https://example.test/loader.jar");
        assertThat(pins.get(0).file()).hasBinaryContent(BYTES);
        assertThat(dir.resolve("cache")).isDirectoryContaining(path -> path.getFileName().toString().equals("loader.jar"))
                .isDirectoryNotContaining(path -> path.getFileName().toString().endsWith(".part"));
    }

    @Test
    void aCachedFileWithTheRightSha256IsReusedWithoutAFetch(@TempDir Path dir) throws IOException {
        String sha = sha256Of(dir);
        Files.createDirectories(dir.resolve("cache"));
        Files.write(dir.resolve("cache/loader.jar"), BYTES);

        LoaderPins.resolve(List.of(download(sha)), dir.resolve("cache"), (url, target) -> {
            throw new IOException("must not fetch " + url);
        });
    }

    @Test
    void aCachedFileWithTheWrongSha256IsNotTrustedAndIsFetchedAgain(@TempDir Path dir) throws IOException {
        String sha = sha256Of(dir);
        Files.createDirectories(dir.resolve("cache"));
        Files.writeString(dir.resolve("cache/loader.jar"), "tampered");

        List<PinnedFile> pins = LoaderPins.resolve(List.of(download(sha)), dir.resolve("cache"),
                (url, target) -> Files.write(target, BYTES));

        assertThat(pins.get(0).file()).hasBinaryContent(BYTES);
    }

    @Test
    void aShaMismatchFailsNamingTheFileAndCopiesNothing(@TempDir Path dir) {
        assertThatThrownBy(() -> LoaderPins.resolve(List.of(download(ZERO)), dir.resolve("cache"),
                (url, target) -> Files.write(target, BYTES)))
                .isInstanceOf(PaperHostException.class)
                .hasMessageContaining("loader.jar").hasMessageContaining(ZERO)
                .hasMessageContaining("https://example.test/loader.jar").hasMessageContaining("Nothing was copied in");
        assertThat(dir.resolve("cache/loader.jar")).doesNotExist();
        assertThat(dir.resolve("cache/loader.jar.part")).doesNotExist();
    }

    @Test
    void aFailedFetchIsAnErrorNotAnEmptyPin(@TempDir Path dir) {
        assertThatThrownBy(() -> LoaderPins.resolve(List.of(download(ZERO)), dir.resolve("cache"), (url, target) -> {
            throw new IOException("GET " + url + " answered 503");
        })).isInstanceOf(PaperHostException.class).hasMessageContaining("503");
    }
}
