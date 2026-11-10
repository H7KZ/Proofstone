package io.github.h7kz.proofstone.core.host;

/**
 * The proxies a topology can run (ADR-0016). Each has one forwarding mode in M7: Velocity modern, BungeeCord legacy
 * (section 8). Waterfall is not one (section 4).
 */
public enum ProxyKind {
    /** Ready at {@code Done (}, after {@code Listening on} (docs/platforms/velocity.md). */
    VELOCITY("velocity", "VELOCITY", "modern", "Done (", "/server/velocity-*.jar"),
    /** Ready at {@code Listening on} (docs/platforms/bungeecord.md). */
    BUNGEECORD("bungeecord", "BUNGEECORD", "legacy", "Listening on", "/server/BungeeCord-*.jar");

    private final String id;
    private final String imageType;
    private final String forwarding;
    private final String readyLine;
    private final String jarGlob;

    ProxyKind(String id, String imageType, String forwarding, String readyLine, String jarGlob) {
        this.id = id;
        this.imageType = imageType;
        this.forwarding = forwarding;
        this.readyLine = readyLine;
        this.jarGlob = jarGlob;
    }

    /** The platform id the proxy's agent reports in {@code health}. */
    public String id() {
        return id;
    }

    /** The one forwarding mode this proxy runs in M7 (ADR-0016 section 8), as {@code versions.yml} spells it. */
    public String forwarding() {
        return forwarding;
    }

    /**
     * @throws IllegalArgumentException on an id that is not a proxy Proofstone can start, naming the known ones
     */
    public static ProxyKind of(String id) {
        for (ProxyKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown proxy \"" + id + "\"; known: velocity, bungeecord");
    }

    String imageType() {
        return imageType;
    }

    String readyLine() {
        return readyLine;
    }

    String jarGlob() {
        return jarGlob;
    }
}
