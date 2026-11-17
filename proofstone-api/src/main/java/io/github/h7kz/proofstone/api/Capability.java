package io.github.h7kz.proofstone.api;

/**
 * A feature a cell can offer (glossary: Capability). A test declares the ones it needs with {@link Requires}, and a cell
 * that lacks one is SKIPPED with a reason, never a pass (test-api spec section 10).
 *
 * <p>A cell holds a capability only when its platform pack declares it and the agent reports the matching evidence.
 * Versions never imply a capability.
 */
public enum Capability {

    /** Bukkit events fire on the server, so {@link Agent#events()} can observe a plugin's events. */
    BUKKIT_EVENTS,

    /** The server has a console, so {@link Agent#command(String)} runs commands on it. */
    CONSOLE,

    /** Folia's regionised threading: there is no main thread, and each region owns its own data. */
    FOLIA_REGIONS,

    /**
     * A Java client logs in under a prefixed name (such as {@code .Name}, through {@link Clients#create}), so a plugin's
     * name-prefix Bedrock detection can run. This is a simulation, not a Bedrock client: it speaks the Java protocol and
     * proves nothing about the Bedrock protocol (docs/platforms.md, Bedrock Edition). A cell holds it only where the
     * prefixed login works.
     */
    SIMULATED_BEDROCK,

    /**
     * A proxy runs in front of the server and its agent answers ({@link Topology#proxy()}). Declared by the proxy packs
     * only (ADR-0016 section 10); a single-server cell never holds it.
     */
    PROXY,

    /**
     * The cell is a topology: a proxy and named backends, with {@link Topology} and {@link Client#transferTo(String)}.
     * Declared by the proxy packs only; a single-server cell never holds it, so a test that needs it skips there.
     */
    TOPOLOGY,

    /**
     * The cell loads vanilla data packs from the world's {@code datapacks} folder and its agent lists them
     * ({@code GET /v1/datapacks}, token {@code datapacks}), so {@link Proofstone#datapacks()} can ship one. Declared by the
     * Paper and Fabric packs (ADR-0017 section 8).
     */
    DATAPACKS_VANILLA,

    /**
     * The server pushes a {@link ServerResourcePack} to the client and the agent reads the client's answer through the
     * event probe (token {@code resource-pack-status}). Declared by the Paper pack, on native cells only (ADR-0017
     * sections 7 and 8).
     */
    RESOURCE_PACKS,

    /**
     * The cell loads mods from {@code /mods} and its agent lists the loaded ones ({@code GET /v1/mods}, token {@code mods}),
     * so {@link Proofstone#mods()} can put a mod under test. Declared by the Fabric and NeoForge packs (ADR-0017 section 8).
     */
    MODS,

    /**
     * A real Bedrock client joins through Geyser, so {@link Clients#bedrock(String)} works. Declared by the Geyser pack and
     * held only when the Bukkit agent reports {@code floodgate} (ADR-0018 section 5); a Geyser cell is native, never bridged,
     * and not a topology or loader cell. It is not {@link #SIMULATED_BEDROCK}: that is a Java client under a prefixed name.
     */
    BEDROCK_CLIENT
}
