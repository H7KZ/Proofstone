package dev.proofstone.core.versions;

import java.util.Map;

/**
 * The host image for a platform. The digest differs per Java tag (ADR-0013), so a cell asks for the one of its tag.
 *
 * @param image image repository, for example {@code itzg/minecraft-server}
 * @param digests digest by Java tag; a tag whose pin is still {@code TODO} is absent
 */
public record Host(String image, Map<String, String> digests) {

    public Host {
        digests = Map.copyOf(digests);
    }

    /** @return the digest pinned for {@code javaTag}, or {@code null} when it is missing or still {@code TODO} */
    public String digest(String javaTag) {
        return digests.get(javaTag);
    }
}
