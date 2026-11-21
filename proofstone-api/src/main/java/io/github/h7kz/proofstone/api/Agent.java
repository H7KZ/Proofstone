package io.github.h7kz.proofstone.api;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/** The server oracle and setup channel (test-api spec §3). Every call blocks until the in-server agent replies. */
public interface Agent {

    /** Runs a console command. Setup only: the reply is best effort, so never assert on it (ADR-0002). */
    String command(String command);

    /** The handle for the player a client controls. */
    default PlayerHandle player(Client client) {
        return player(client.name());
    }

    /** The handle for an online player by name. */
    PlayerHandle player(String name);

    /** The config handle of an installed plugin. Patches are undone after each test. */
    PluginHandle plugin(String name);

    /** Read-only listeners on server events, such as a plugin's own API events. */
    Events events();

    /** The capabilities the agent reported at readiness. */
    List<String> capabilities();

    /**
     * The server log lines logged since the test started, oldest first, as plain text read from the server's logger by
     * the agent. A record becomes one line, {@code LEVEL logger: message}, for example
     * {@code INFO net.minecraft.server.MinecraftServer: System chat: Steve joined the game} (the logger and wording are the
     * server's own and differ per platform and version); a message or stack trace that spans lines adds one line per line
     * of text. Lines logged before the test
     * (the server boot, earlier tests) are not included. Read-only: it never changes the server.
     *
     * <p>Not a barrier: a line the server has not logged yet is absent, so wait for one with
     * {@link #logLines(Pattern, Duration)}.
     *
     * @throws IllegalStateException when the agent's log buffer dropped records since the test started, because the
     *                               result would then be incomplete
     * @throws UnsupportedOperationException when this agent cannot read the log
     */
    default List<String> logLines() {
        throw new UnsupportedOperationException("this agent cannot read the server log");
    }

    /**
     * The server log lines logged before the test started, oldest first, in the form of {@link #logLines()}: the complement
     * of it, so the two together are every line the agent captured. It starts at the agent's own enable (the agent loads at
     * server STARTUP, before every ordinary plugin and before the worlds), so it holds the enable lines of the plugins under
     * test, for example a plugin's start-up line, and the earlier tests' lines. It does not hold what the server logged
     * before the agent enabled (the server banner, library loading, the plugin loading list). Read-only.
     *
     * @throws IllegalStateException when the agent's log buffer (the last 5000 records) dropped a record since the agent
     *                               enabled, which a suite of many tests can cause; the result would then be incomplete
     * @throws UnsupportedOperationException when this agent cannot read the log
     */
    default List<String> bootLogLines() {
        throw new UnsupportedOperationException("this agent cannot read the server log");
    }

    /**
     * Waits until a log line logged since the test started contains a match of {@code pattern} ({@link
     * java.util.regex.Matcher#find()}), then returns every such line, oldest first. Lines are as in {@link #logLines()}.
     *
     * @throws RuntimeException when no line matches within {@code timeout}; the message names the pattern
     * @throws UnsupportedOperationException when this agent cannot read the log
     */
    default List<String> logLines(Pattern pattern, Duration timeout) {
        throw new UnsupportedOperationException("this agent cannot read the server log");
    }

    /**
     * The server's block type at the position, as a namespaced id such as {@code minecraft:stone} (the oracle for
     * {@link Client#placeBlock} and {@link Client#breakBlock}). Read-only: a {@code GET /v1/world/block}, no state.
     *
     * <p>Additive (T-M11-04). The default throws, so a custom {@code Agent} fails loudly instead of passing.
     *
     * @param world the world name (the default world of a fixture is {@code world})
     * @throws RuntimeException when the world is unknown or the agent does not answer, with the reason
     */
    default String blockType(String world, BlockPos pos) {
        throw new UnsupportedOperationException("this agent cannot read blocks");
    }
}
