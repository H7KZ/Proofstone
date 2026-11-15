package io.github.h7kz.proofstone.core.junit;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Loaded or loud for mods (ADR-0017 section 4): every declared mod's id must be in the agent's {@code GET /v1/mods} list,
 * never read from the console (ADR-0002). A mod that crashes the loader at load never gets here: that is a failed boot,
 * INVALID naming the mod ({@code ModLoad}).
 */
final class ModsLoaded {

    private ModsLoaded() {
    }

    /** The mod globs a run installs: none on a control run, which never installs an artefact under test (self-tests section 2). */
    static List<String> globsToInstall(String[] declared, boolean controlRun) {
        return controlRun ? List.of() : List.of(declared);
    }

    /**
     * The INVALID message naming the declared mods that the agent does not list, or {@code null} when all are listed.
     *
     * @param declared the ids the host installed, read from the mods' own descriptors
     * @param reply the {@code data} of {@code GET /v1/mods}
     */
    static String invalid(List<String> declared, JsonNode reply, String cell, String logTail) {
        if (declared.isEmpty()) {
            return null;
        }
        JsonNode mods = reply == null ? null : reply.get("mods");
        List<String> listed = new ArrayList<>();
        if (mods != null && mods.isArray()) {
            mods.forEach(mod -> listed.add(mod.path("id").asText()));
        }
        List<String> missing = declared.stream().filter(id -> !listed.contains(id)).toList();
        if (missing.isEmpty()) {
            return null;
        }
        String why = mods == null || !mods.isArray() ? "the agent's reply has no mods list" : "the agent lists " + listed;
        return "INVALID test declaration: declared mod " + String.join(", ", missing) + " did not load on " + cell + " ("
                + why + ")\n--- log tail ---\n" + logTail;
    }
}
