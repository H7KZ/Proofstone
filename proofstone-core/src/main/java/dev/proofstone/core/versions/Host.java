package dev.proofstone.core.versions;

/**
 * The host image for a platform.
 *
 * @param image image repository, for example {@code itzg/minecraft-server}
 * @param digest image digest, or {@code null} while it is still {@code TODO}
 */
public record Host(String image, String digest) {
}
