package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.cell.GeyserCell;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * The pinned Geyser-Spigot and Floodgate-Spigot jars of a cell (ADR-0018 section 8). They are downloaded here, in the
 * harness JVM, by version and build from the official API, verified against the row's sha256 and copied into the
 * server's {@code /plugins}. Geyser never fetches a jar itself (its mappings are inside its jar, so the sha256 pins them).
 * A mismatch fails the cell and removes the file; a removed build is a loud failure at download.
 */
final class GeyserJars {

    private static final String API = "https://download.geysermc.org/v2/projects/";

    /** Where the bytes of a URL come from; the seam the tests replace. */
    interface Fetcher {
        InputStream open(URI uri) throws IOException;
    }

    private GeyserJars() {
    }

    /** The pinned Geyser-Spigot jar, in {@code cache} (checked, downloaded when absent). */
    static Path geyser(GeyserCell cell, Path cache, Fetcher fetcher) {
        return fetch(url("geyser", cell.version(), cell.build()), "Geyser-Spigot-" + cell.version() + "-b" + cell.build()
                + ".jar", cell.sha256(), cache, fetcher);
    }

    /** The pinned Floodgate-Spigot jar. */
    static Path floodgate(GeyserCell cell, Path cache, Fetcher fetcher) {
        GeyserCell.Floodgate pin = cell.floodgate();
        return fetch(url("floodgate", pin.version(), pin.build()), "floodgate-spigot-" + pin.version() + "-b" + pin.build()
                + ".jar", pin.sha256(), cache, fetcher);
    }

    static URI url(String project, String version, int build) {
        return URI.create(API + project + "/versions/" + version + "/builds/" + build + "/downloads/spigot");
    }

    /** The default source: an HTTPS GET that follows the API's redirect to the file. */
    static Fetcher http() {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30)).build();
        return uri -> {
            try {
                HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofMinutes(5)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    response.body().close();
                    throw new IOException("GET " + uri + " answered " + response.statusCode());
                }
                return response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("GET " + uri + " was interrupted", e);
            }
        };
    }

    static Path fetch(URI uri, String fileName, String sha256, Path cache, Fetcher fetcher) {
        Path file = cache.resolve(fileName);
        try {
            Files.createDirectories(cache);
            if (Files.isRegularFile(file) && sha256Of(file).equals(sha256)) {
                return file;
            }
            // A cached file that differs is a stale or damaged download: it is replaced, then checked like a fresh one.
            Path partial = cache.resolve(fileName + ".part");
            String actual;
            try (InputStream in = fetcher.open(uri)) {
                actual = copyAndDigest(in, partial);
            }
            if (!actual.equals(sha256)) {
                Files.deleteIfExists(partial);
                throw new PaperHostException("sha256 of " + uri + " is " + actual + ", the row pins " + sha256
                        + " (the build changed or the download is damaged); not used");
            }
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING);
            return file;
        } catch (IOException e) {
            throw new PaperHostException("cannot download " + fileName + " from " + uri + ": " + e.getMessage(), e);
        }
    }

    static String sha256Of(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return copyAndDigest(in, null);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private static String copyAndDigest(InputStream in, Path target) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this JVM", e);
        }
        try (DigestInputStream stream = new DigestInputStream(in, digest)) {
            if (target == null) {
                stream.transferTo(OutputStream.nullOutputStream());
            } else {
                try (OutputStream out = Files.newOutputStream(target)) {
                    stream.transferTo(out);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
