package dev.proofstone.core.versions;

/**
 * A container image pinned by digest, as the {@code drivers} section of {@code versions.yml} names them.
 *
 * @param image image repository, for example {@code ghcr.io/viaversion/viaproxy}
 * @param digest content digest, {@code sha256:...}
 */
public record ImagePin(String image, String digest) {

    /** @return the reference Docker pulls, {@code image@digest} */
    public String reference() {
        return image + "@" + digest;
    }
}
