package dev.proofstone.api;

import java.util.regex.Pattern;

/** A joined player: the client oracle and its actions (test-api spec §4). A fresh one per test. */
public interface Client {

    /** The player name, unique per test. */
    String name();

    /** Sends {@code /<command>}. */
    void command(String commandWithoutSlash);

    /**
     * Waits for a received message whose plain text matches, for the default timeout.
     *
     * @return the matching message text
     * @throws RuntimeException on timeout or disconnect, with the reason
     */
    String awaitMessage(Pattern pattern);
}
