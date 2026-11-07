package dev.proofstone.core.versions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes small {@code versions.yml} files for tests and points at the real schema (ADR-0008). */
public final class VersionsFixture {

    /** Set by {@code proofstone-core/build.gradle.kts}: the repository root that holds the seed file. */
    public static final Path REPO_ROOT = Path.of(System.getProperty("proofstone.repoRoot"));

    static final String JAVA_RULES = """
            java-rules:
              - { range: "<=1.16.5", image: java8 }
              - { range: "1.17.x", image: java16 }
              - { range: ">=1.18 <1.20.5", image: java17 }
              - { range: ">=1.20.5 <26", image: java21 }
              - { range: ">=26", image: java25 }
            """;

    static final String PLATFORM_HEADER = """
            platforms:
              paper:
                host:
                  image: "itzg/minecraft-server"
                  digests: { java8: "sha256:08", java11: "sha256:11", java16: "sha256:16", java17: "sha256:17", java21: "sha256:21", java25: "sha256:25" }
                rows:
            """;

    /** Pinned rows, one per interesting version and Java edge. */
    static final String PINNED_ROWS = """
                  - { mc: "1.9.4", build: 1, sha256: "a1", status: required }
                  - { mc: "1.10.2", build: 2, sha256: "a2", status: required }
                  - { mc: "1.16.5", build: 3, sha256: "a3", status: experimental, java: java11 }
                  - { mc: "1.17.1", build: 4, sha256: "a4", status: experimental }
                  - { mc: "1.20.4", build: 5, sha256: "a5", status: required }
                  - { mc: "1.20.5", build: 6, sha256: "a6", status: experimental }
                  - { mc: "26.1", build: 7, sha256: "a7", status: experimental }
                  - { mc: "26.3", build: 8, sha256: "a8", status: experimental }
                  - { mc: "1.8.8", build: 9, sha256: "a9", status: retired }
            """;

    private VersionsFixture() {
    }

    /** A valid document: the pinned rows plus the standard Java rules. */
    public static String valid() {
        return "schema: 2\n" + JAVA_RULES
                + "drivers:\n  mcpl: { library: \"g:a\", version: \"1.0\", protocol: \"26.3\" }\n"
                + PLATFORM_HEADER + PINNED_ROWS;
    }

    static Path write(Path dir, String yaml) throws IOException {
        Path file = dir.resolve("versions.yml");
        Files.writeString(file, yaml);
        return file;
    }

    public static Path schema() {
        return REPO_ROOT.resolve("proofstone-protocol/schemas/versions.schema.json");
    }

    public static VersionsCatalog load(Path dir, String yaml) throws IOException {
        return VersionsCatalog.load(write(dir, yaml), schema());
    }
}
