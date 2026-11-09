package io.github.h7kz.proofstone.core.versions;

import java.util.List;

/**
 * One version row of a platform. Pins that are still {@code TODO} in the file are {@code null} here.
 *
 * @param mc Minecraft version, for example {@code 1.21.11}
 * @param build platform build number, or {@code null} while {@code TODO}
 * @param sha256 server jar checksum, or {@code null} while {@code TODO}
 * @param status {@code required}, {@code experimental} or {@code retired}
 * @param java Java image override, or {@code null} when the {@code java-rules} decide
 * @param protocol protocol number, or {@code null} when automation has not filled it in
 * @param nativeDrivers drivers that speak this version natively
 * @param observed free-text last observation
 * @param notes free-text notes
 */
public record Row(
        String mc,
        Integer build,
        String sha256,
        Status status,
        String java,
        Integer protocol,
        List<String> nativeDrivers,
        String observed,
        String notes) {
}
