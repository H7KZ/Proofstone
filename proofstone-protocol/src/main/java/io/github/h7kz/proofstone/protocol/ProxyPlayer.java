package io.github.h7kz.proofstone.protocol;

/**
 * Reply of {@code GET /v1/players/{name}} on a proxy agent (proxy.json#/$defs/player). A proxy has no inventory or
 * location, so this is not a {@link PlayerState}. Protocol 1.2.
 */
public record ProxyPlayer(String name, String uuid, Position proxy) {

    /** {@code previousServer} is null until the player has switched; it is written as an explicit null. */
    public record Position(String server, String previousServer) {
    }
}
