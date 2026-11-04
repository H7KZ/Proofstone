package dev.proofstone.api;

import java.util.List;

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
}
