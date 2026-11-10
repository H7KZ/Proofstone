package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.cell.TopologyCell;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * The pins and inputs of one {@link ProxyHost} (ADR-0016 section 2). The pins come from the planned
 * {@link TopologyCell}, that is from {@code versions.yml} schema 3 ({@link #of(TopologyCell, Path)}).
 *
 * @param kind which proxy
 * @param image the {@code itzg/mc-proxy} image as {@code name@sha256:...}, by digest for the proxy's Java tag
 * @param version Velocity's release version; for BungeeCord, the build number again (it publishes no other)
 * @param build the build the image downloads ({@code VELOCITY_BUILD_ID} or {@code BUNGEE_JOB_ID})
 * @param jarSha256 the downloaded jar's sha256, checked in the container
 * @param javaMajor the Java major the image must run
 * @param agentJar the shaded proxy agent jar
 * @param bootTimeout how long to wait for the ready line
 * @param agentTimeout how long to wait for agent health after it
 */
public record ProxyHostOptions(ProxyKind kind, String image, String version, String build, String jarSha256,
        int javaMajor, Path agentJar, Duration bootTimeout, Duration agentTimeout) {

    public ProxyHostOptions {
        Objects.requireNonNull(kind, "kind");
        if (!image.contains("@sha256:")) {
            throw new IllegalArgumentException("proxy image must be pinned by digest: " + image);
        }
        if (!jarSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("proxy jar sha256 is not 64 hex digits: " + jarSha256);
        }
    }

    /**
     * The options of the proxy half of a planned topology cell: its host image by digest, build, jar checksum and Java
     * tag, exactly as {@code versions.yml} pins them. BungeeCord's version and build are both its build number.
     *
     * @throws IllegalArgumentException on an unknown proxy, a forwarding mode other than the proxy's one, or a Java tag
     *     that is not {@code java<major>}
     */
    public static ProxyHostOptions of(TopologyCell cell, Path agentJar) {
        TopologyCell.Proxy proxy = cell.proxy();
        ProxyKind kind = ProxyKind.of(proxy.id());
        if (!kind.forwarding().equals(proxy.forwarding())) {
            throw new IllegalArgumentException(cell.identity() + ": " + kind.id() + " runs " + kind.forwarding()
                    + " forwarding in M7 but versions.yml says " + proxy.forwarding());
        }
        if (!proxy.java().matches("java[0-9]+")) {
            throw new IllegalArgumentException(cell.identity() + ": Java tag \"" + proxy.java() + "\" is not java<major>");
        }
        return of(kind, proxy.hostImage() + "@" + proxy.hostDigest(), proxy.version(), Integer.toString(proxy.build()),
                proxy.sha256(), Integer.parseInt(proxy.java().substring("java".length())), agentJar);
    }

    public static ProxyHostOptions of(ProxyKind kind, String image, String version, String build, String jarSha256,
            int javaMajor, Path agentJar) {
        return new ProxyHostOptions(kind, image, version, build, jarSha256, javaMajor, agentJar,
                Duration.ofMinutes(3), Duration.ofSeconds(30));
    }
}
