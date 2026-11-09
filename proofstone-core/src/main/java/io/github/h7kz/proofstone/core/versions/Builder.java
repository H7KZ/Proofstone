package io.github.h7kz.proofstone.core.versions;

import java.util.Map;

/**
 * How a platform's server jar is built from source instead of downloaded (Spigot, T-M5-05). The builder image is
 * built locally from {@code images/spigot-buildtools}; nothing it produces is committed or published.
 *
 * @param buildToolsBuild the BuildTools Jenkins build number (the jar URL names it)
 * @param buildToolsSha256 SHA-256 of that BuildTools.jar; the image build fails on a mismatch
 * @param jdkImages JDK image by Java tag, pinned by digest (for example {@code eclipse-temurin@sha256:…}); a tag whose
 *     pin is still {@code TODO} is absent
 */
public record Builder(int buildToolsBuild, String buildToolsSha256, Map<String, String> jdkImages) {

    public Builder {
        jdkImages = Map.copyOf(jdkImages);
    }

    /** @return the JDK image pinned for {@code javaTag}, or {@code null} when it is missing or still {@code TODO} */
    public String jdkImage(String javaTag) {
        return jdkImages.get(javaTag);
    }
}
