package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.host.LoaderEnvironment.Download;
import io.github.h7kz.proofstone.core.host.LoaderEnvironment.PinnedFile;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Downloads each pinned file on the host and checks its sha256 before it is copied in (ADR-0017 section 3). A cached file
 * is reused only when its sha256 matches. A mismatch fails the cell naming the file, never a silent new input.
 */
final class LoaderPins {

    /** Fetches {@code url} into {@code target}; a test supplies its own. */
    @FunctionalInterface
    interface Fetcher {
        void fetch(String url, Path target) throws IOException;
    }

    private LoaderPins() {
    }

    static List<PinnedFile> resolve(List<Download> downloads, Path cache, Fetcher fetcher) {
        try {
            Files.createDirectories(cache);
            List<PinnedFile> files = new ArrayList<>();
            for (Download download : downloads) {
                files.add(new PinnedFile(download, pin(download, cache, fetcher)));
            }
            return files;
        } catch (IOException e) {
            throw new PaperHostException("cannot fetch a loader pin: " + e.getMessage(), e);
        }
    }

    private static Path pin(Download download, Path cache, Fetcher fetcher) throws IOException {
        Path file = cache.resolve(download.name());
        if (Files.isRegularFile(file) && sha256(file).equals(download.sha256())) {
            return file;
        }
        Path part = cache.resolve(download.name() + ".part");
        fetcher.fetch(download.url(), part);
        String actual = sha256(part);
        if (!actual.equals(download.sha256())) {
            Files.deleteIfExists(part);
            throw new PaperHostException(download.name() + " from " + download.url() + " has sha256 " + actual
                    + ", versions.yml pins " + download.sha256() + ". Nothing was copied in.");
        }
        return Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** The JDK's HTTP client; redirects followed, any status but 200 is an error naming the URL. */
    static Fetcher http() {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        return (url, target) -> {
            try {
                HttpResponse<InputStream> reply = client.send(HttpRequest.newBuilder(URI.create(url)).build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = reply.body()) {
                    if (reply.statusCode() != 200) {
                        throw new IOException("GET " + url + " answered " + reply.statusCode());
                    }
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while fetching " + url, e);
            }
        };
    }

    static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
