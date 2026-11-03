package dev.proofstone.api;

import java.util.List;

/** The server oracle and setup channel (test-api spec §3). Every call blocks until the in-server agent replies. */
public interface Agent {

    /** Runs a console command. Setup only: the reply is best effort, so never assert on it (ADR-0002). */
    String command(String command);

    /** The capabilities the agent reported at readiness. */
    List<String> capabilities();
}
