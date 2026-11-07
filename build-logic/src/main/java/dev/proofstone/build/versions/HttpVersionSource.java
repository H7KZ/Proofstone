package dev.proofstone.build.versions;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** The network {@link VersionSource}, used by {@code ./gradlew updateVersions} and the version-watch workflow. */
public final class HttpVersionSource implements VersionSource {

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String getText(URI uri) throws IOException {
        return send(uri, BodyHandlers.ofString()).body();
    }

    @Override
    public Path download(URI uri) throws IOException {
        Path file = Files.createTempFile("proofstone-server", ".jar");
        try {
            send(uri, BodyHandlers.ofFile(file));
            return file;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(file);
            throw e;
        }
    }

    private <T> HttpResponse<T> send(URI uri, BodyHandler<T> handler) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).build();
        HttpResponse<T> response;
        try {
            response = client.send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while fetching " + uri, e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("GET " + uri + " returned HTTP " + response.statusCode());
        }
        return response;
    }
}
