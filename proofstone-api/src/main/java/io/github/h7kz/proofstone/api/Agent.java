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
     * Waits until a log line logged since the test started contains a match of {@code pattern} ({@link
     * java.util.regex.Matcher#find()}), then returns every such line, oldest first. Lines are as in {@link #logLines()}.
     *
     * @throws RuntimeException when no line matches within {@code timeout}; the message names the pattern
     * @throws UnsupportedOperationException when this agent cannot read the log
     */
    default List<String> logLines(Pattern pattern, Duration timeout) {
        throw new UnsupportedOperationException("this agent cannot read the server log");
    }
}
