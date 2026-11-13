package io.github.h7kz.proofstone.core.host;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.protocol.Health;
import java.util.List;

/**
 * One in-server agent, as the harness calls it: a Paper backend's or a proxy's (ADR-0016 section 6). The lifecycle's
 * log guard, events and console run on either through this, so a topology checks every container the same way.
 */
public interface AgentEndpoint {

    /** Calls an agent endpoint and returns the reply's {@code data}; a reply that is not ok is an error. */
    JsonNode request(String method, String path, Object body);

    /** Runs a console command. Setup only; the reply is best effort (ADR-0002). */
    String command(String command);

    /** The agent's {@code /v1/health} reply from readiness. */
    Health health();

    /** The bearer token the agent accepts. Harness code only: redacted from every bundle, never logged. */
    String agentToken();

    /** The agent calls made so far, as JSON lines (repro bundle). */
    List<String> agentTranscript();

    /** The container's log so far. */
    LogBuffer logs();
}
