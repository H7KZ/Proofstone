package io.github.h7kz.proofstone.core.driver;

import io.github.h7kz.proofstone.api.BlockPos;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * One joined player (client-driver spec §1). Everything it exposes is what the server sent; it never predicts.
 *
 * <p>Clicks and the experience level come from the server's syncs (the level is read by the client oracle).
 */
public interface ClientSession extends AutoCloseable {

    /** Sends a chat line. */
    void chat(String message);

    /** Sends a command without the leading slash. */
    void command(String commandWithoutSlash);

    /**
     * Waits for the next received message whose plain text matches. Messages that arrived earlier and were not
     * consumed count, so a reply is not lost when it beats the call.
     *
     * @throws ClientDriverException on timeout or when the server disconnected, with the reason
     */
    ChatMessage awaitMessage(Pattern pattern, Duration timeout);

    /**
     * Looks at the block and right-clicks it with the main hand (a look packet, then use-item-on with a sequence
     * number). The block must be within reach of the server-synced position.
     *
     * @throws ClientDriverException when the session is closed or no position has been synced yet
     */
    void useBlock(BlockPos pos, Face face);

    /**
     * Waits for the next window the server opened that this call has not returned, with its contents synced.
     *
     * @throws ClientDriverException on timeout or when the server disconnected, with the reason
     */
    WindowSession awaitWindow(Duration timeout);

    /** The player inventory (window id 0) as last synced by the server; no slots until the first sync. */
    WindowView inventory();

    /**
     * The experience level from the server's last experience sync.
     *
     * @throws ClientDriverException when the server has not synced an experience level yet
     */
    int experienceLevel();

    /** The last server-synced position {x, y, z}, or {@code null} before the first sync. */
    double[] position();

    /**
     * Waits for the next server switch the proxy completes on this live connection (ADR-0016 section 5): the
     * configuration phase, then the new server's join. Each completed switch is returned once, so a switch that
     * finished before the call is not lost and a second call waits for the next one. On return the views are the
     * new server's: no slot, window, position or experience of the previous server is kept, and each is empty until
     * the new server syncs it. Additive (T-M7-09); a driver without proxy support fails loudly instead of passing.
     *
     * @throws JoinTimeoutException when the timeout passes first, naming the stalled stage (the switch never began, or
     *     which step of the new join stopped); a backend's reply to the {@code /server} command is quoted
     * @throws ClientDriverException when the server disconnected during or before the switch, with its reason
     */
    default void awaitServerSwitch(Duration timeout) {
        throw new ClientDriverException("this driver cannot await a proxy server switch; use a driver that supports it");
    }

    /** Timestamped packet names and summaries, for repro bundles. */
    List<String> transcript();

    @Override
    void close();
}
