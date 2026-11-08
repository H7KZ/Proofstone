package dev.proofstone.core.host;

import dev.proofstone.core.cell.Cell;
import dev.proofstone.core.versions.Builder;
import dev.proofstone.core.versions.Platform;
import dev.proofstone.core.versions.VersionsCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * The Spigot server jar of one cell (T-M5-05, ADR-0005, ADR-0006). Spigot is not downloaded: BuildTools builds it for
 * the cell's {@code --rev} inside the local builder image {@code images/spigot-buildtools}, and the result is cached
 * under its SHA-256. A cached jar is used only when its {@link JarDigest} matches the row's pin, and a fresh build
 * that digests differently fails loudly. No Spigot jar is committed or pushed (docs/platforms/spigot.md).
 */
final class SpigotServerJar {

    /** Where the builder image and the BuildTools pins live: {@code images/spigot-buildtools} and {@code platforms.spigot}. */
    static final String PLATFORM = "spigot";
    /** The cache directory, relative to the repository root unless {@code proofstone.spigotCache} overrides it. */
    static final String CACHE_PROPERTY = "proofstone.spigotCache";
    private static final String REPO_ROOT_PROPERTY = "proofstone.repoRoot";
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(60);
    private static final int LOG_TAIL_LINES = 80;

    private SpigotServerJar() {
    }

    /**
     * Returns the cell's jar, from the cache when it is there and matches the pin, else built now.
     *
     * @throws PaperHostException when the pins are missing, the build fails or the built jar differs from the pin
     */
    static Path resolve(Cell cell) {
        if (!PLATFORM.equals(cell.platform())) {
            throw new PaperHostException("SpigotServerJar only builds spigot, not " + cell.platform());
        }
        Path repoRoot = repoRoot();
        Path cacheDir = cacheDir(repoRoot);
        Path cached = cacheDir.resolve(cell.sha256() + ".jar");
        if (Files.isRegularFile(cached) && cell.sha256().equals(JarDigest.of(cached))) {
            return cached;
        }
        VersionsCatalog catalog = VersionsCatalog.load(repoRoot.resolve("versions.yml"),
                repoRoot.resolve("proofstone-protocol/schemas/versions.schema.json"));
        Platform platform = catalog.platform(PLATFORM);
        Builder builder = platform.builder();
        if (builder == null) {
            throw new PaperHostException("versions.yml has no builder for " + PLATFORM);
        }
        String jdk = builder.jdkImage(cell.java());
        if (jdk == null) {
            throw new PaperHostException("versions.yml has no builder JDK for " + cell.java()
                    + " (platforms.spigot.builder.jdk), needed by spigot@" + cell.mc());
        }
        Path built = build(cell, repoRoot.resolve("images/spigot-buildtools"), builder, jdk, cacheDir);
        requireMatch(built, cell.sha256(), cell.mc());
        publish(built, cached);
        return cached;
    }

    /** Fails loudly, and removes the partial file, when the built jar's {@link JarDigest} is not the row's pin. */
    static void requireMatch(Path built, String pin, String mc) {
        String actual;
        try {
            actual = JarDigest.of(built);
        } catch (RuntimeException e) {
            discard(built);
            throw e;
        }
        if (!pin.equals(actual)) {
            discard(built);
            throw new PaperHostException("BuildTools built spigot@" + mc + " with content digest " + actual
                    + ", but versions.yml pins " + pin + ". The source or the builder changed: check the BuildTools"
                    + " build and the JDK, then re-pin the row (docs/platforms/spigot.md).");
        }
    }

    /** Runs BuildTools in the builder image and copies its jar out. The output is named by the rev. */
    private static Path build(Cell cell, Path context, Builder builder, String jdk, Path cacheDir) {
        ImageFromDockerfile image = new ImageFromDockerfile()
                .withFileFromPath(".", context)
                .withBuildArg("JDK_IMAGE", jdk)
                .withBuildArg("BUILDTOOLS_BUILD", Integer.toString(builder.buildToolsBuild()))
                .withBuildArg("BUILDTOOLS_SHA256", builderSha(builder));
        GenericContainer<?> buildTools = new GenericContainer<>(image)
                .withCommand("--rev", cell.mc())
                // One run to completion: the container exits when BuildTools is done. A non-zero exit fails here.
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(BUILD_TIMEOUT));
        Path out = null;
        try {
            buildTools.start();
            out = Files.createTempFile(cacheDir, "spigot-" + cell.mc() + "-", ".jar.part");
            Path target = out;
            buildTools.copyFileFromContainer("/out/spigot-" + cell.mc() + ".jar", in -> {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                return target;
            });
            return out;
        } catch (Exception e) {
            discard(out);
            throw new PaperHostException("BuildTools failed for spigot@" + cell.mc() + " on " + cell.java()
                    + ": " + e.getMessage() + "\n--- BuildTools log tail ---\n" + tail(buildTools), e);
        } finally {
            buildTools.stop();
        }
    }

    private static String builderSha(Builder builder) {
        if (builder.buildToolsSha256() == null) {
            throw new PaperHostException("versions.yml has no BuildTools sha256 (platforms.spigot.builder.buildtools)");
        }
        return builder.buildToolsSha256();
    }

    private static void publish(Path built, Path cached) {
        try {
            Files.move(built, cached, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot cache the built jar at " + cached, e);
        }
    }

    private static Path repoRoot() {
        String root = System.getProperty(REPO_ROOT_PROPERTY);
        if (root == null) {
            throw new PaperHostException("spigot needs -D" + REPO_ROOT_PROPERTY + " to find versions.yml and the"
                    + " builder image");
        }
        return Path.of(root);
    }

    private static Path cacheDir(Path repoRoot) {
        String override = System.getProperty(CACHE_PROPERTY);
        Path dir = override == null ? repoRoot.resolve("build/proofstone/spigot-cache") : Path.of(override);
        try {
            return Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create the Spigot cache " + dir, e);
        }
    }

    /** The plain SHA-256 of the file's bytes, to check that the copy inside the container is the file resolved here. */
    static String fileSha256(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot hash " + file, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this JVM", e);
        }
    }

    private static void discard(Path partial) {
        if (partial == null) {
            return;
        }
        try {
            Files.deleteIfExists(partial);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot remove the partial jar " + partial, e);
        }
    }

    private static String tail(GenericContainer<?> container) {
        try {
            String[] lines = container.getLogs().split("\n");
            return String.join("\n", Arrays.copyOfRange(lines, Math.max(0, lines.length - LOG_TAIL_LINES), lines.length));
        } catch (RuntimeException e) {
            return "(no log: " + e.getMessage() + ")";
        }
    }

    /** The name the jar has inside the server container; PaperHost checks its sha256 against the pin. */
    static String containerName(Cell cell) {
        return PLATFORM + "-" + cell.mc().toLowerCase(Locale.ROOT) + ".jar";
    }
}
