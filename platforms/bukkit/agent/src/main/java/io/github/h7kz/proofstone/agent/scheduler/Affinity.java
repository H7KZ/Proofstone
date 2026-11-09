package io.github.h7kz.proofstone.agent.scheduler;

/**
 * What an agent operation touches, so the scheduler can run it on the thread that owns that data (ADR-0002,
 * docs/platforms/folia.md). Paper has one main thread and ignores the affinity. Folia has one thread per region:
 * a player's data belongs to the player's entity scheduler, a block belongs to the region that holds its chunk,
 * and everything else (commands, plugin registration, config) belongs to the global region scheduler.
 */
public final class Affinity {

    /** Which owner an operation needs. */
    public enum Kind {
        /** Server-wide work: commands, plugin config and reload, the online list, event probes. */
        GLOBAL,
        /** A player's state: the player's entity scheduler owns it. */
        PLAYER,
        /** A block: the region that owns its chunk. */
        BLOCK
    }

    private static final Affinity GLOBAL = new Affinity(Kind.GLOBAL, null, null, 0, 0, 0);

    private final Kind kind;
    private final String world;
    private final String player;
    private final int x;
    private final int y;
    private final int z;

    private Affinity(Kind kind, String world, String player, int x, int y, int z) {
        this.kind = kind;
        this.world = world;
        this.player = player;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Affinity global() {
        return GLOBAL;
    }

    public static Affinity player(String name) {
        return new Affinity(Kind.PLAYER, null, name, 0, 0, 0);
    }

    public static Affinity block(String world, int x, int y, int z) {
        return new Affinity(Kind.BLOCK, world, null, x, y, z);
    }

    public Kind kind() {
        return kind;
    }

    /** The world of a {@link Kind#BLOCK} affinity. */
    public String world() {
        return world;
    }

    /** The player name of a {@link Kind#PLAYER} affinity. */
    public String player() {
        return player;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }
}
