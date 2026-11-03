package dev.proofstone.core.driver;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * One joined player (client-driver spec §1). Everything it exposes is what the server sent; it never predicts.
 *
 * <p>SEAM: block use, windows, inventory, experience and position are added by the M2 cards.
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

    /** Timestamped packet names and summaries, for repro bundles. */
    List<String> transcript();

    @Override
    void close();
}
