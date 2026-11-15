package io.github.h7kz.proofstone.core.junit;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Loaded or loud (ADR-0017 section 4): every declared data pack must be in the agent's {@code enabled} list. The list
 * comes from {@code GET /v1/datapacks}, never from the console (ADR-0002).
 */
final class DataPackLoad {

    private DataPackLoad() {
    }

    /**
     * The INVALID message naming the declared packs that are not enabled, or {@code null} when all are.
     *
     * @param declared the ids the host installed ({@code file/<name>})
     * @param reply the {@code data} of {@code GET /v1/datapacks}
     */
    static String invalid(List<String> declared, JsonNode reply, String cell, String logTail) {
        if (declared.isEmpty()) {
            return null;
        }
        JsonNode enabled = reply == null ? null : reply.get("enabled");
        List<String> missing = new ArrayList<>();
        List<String> listed = new ArrayList<>();
        if (enabled != null && enabled.isArray()) {
            enabled.forEach(id -> listed.add(id.asText()));
        }
        for (String id : declared) {
            if (!listed.contains(id)) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return null;
        }
        String why = enabled == null || !enabled.isArray() ? "the agent's reply has no enabled list"
                : "the server enabled " + listed;
        return "INVALID test declaration: declared data pack " + String.join(", ", missing) + " did not load on " + cell
                + " (" + why + ")\n--- log tail ---\n" + logTail;
    }
}
