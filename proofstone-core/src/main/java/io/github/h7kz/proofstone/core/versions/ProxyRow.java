package io.github.h7kz.proofstone.core.versions;

import java.util.List;

/**
 * One proxy row. Pins that are still {@code TODO} in the file are {@code null} here.
 *
 * @param version the proxy's own version as a string; for BungeeCord the Jenkins build number (ADR-0016 section 1)
 * @param build the build the image downloads, or {@code null} while {@code TODO}
 * @param sha256 jar checksum, or {@code null} while {@code TODO}
 * @param java Java tag of the host image; required, because {@code java-rules} are keyed on Minecraft versions
 * @param status {@code required}, {@code experimental} or {@code retired}
 * @param backends the backend rows this proxy row is paired with, each with its own status
 * @param observed free-text last observation
 * @param notes free-text notes
 */
public record ProxyRow(String version, Integer build, String sha256, String java, Status status,
        List<Pairing> backends, String observed, String notes) {

    /**
     * One backend row a proxy row is paired with.
     *
     * @param platform backend platform id, a key of {@code platforms}
     * @param mc the backend row's Minecraft version
     * @param status the pairing's own status
     */
    public record Pairing(String platform, String mc, Status status) {
    }
}
