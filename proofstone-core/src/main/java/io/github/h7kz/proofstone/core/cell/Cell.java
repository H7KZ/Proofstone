package io.github.h7kz.proofstone.core.cell;

import io.github.h7kz.proofstone.core.versions.Status;
import java.util.List;

/**
 * One concrete environment a test runs in (glossary: Cell), resolved from {@code versions.yml}. Every pin is
 * set: the planner refuses rows that still say {@code TODO}.
 *
 * @param platform platform id, for example {@code paper}
 * @param mc Minecraft version
 * @param build platform build number, or {@code null} for a platform built from source (Spigot has no build number)
 * @param sha256 server jar checksum
 * @param java Java image, for example {@code java21}
 * @param status row status
 * @param hostImage host image repository
 * @param hostDigest host image digest
 * @param notes warnings, for example that {@code latest} fell back to an experimental row
 */
public record Cell(
        String platform,
        String mc,
        Integer build,
        String sha256,
        String java,
        Status status,
        String hostImage,
        String hostDigest,
        List<String> notes) {
}
