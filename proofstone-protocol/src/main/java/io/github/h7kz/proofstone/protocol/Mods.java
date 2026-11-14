package io.github.h7kz.proofstone.protocol;

import java.util.List;

/** Reply of {@code GET /v1/mods} (mods.json), sorted by id. Mod agents only. Protocol 1.3. */
public record Mods(List<Mod> mods) {

    /** One loaded mod. */
    public record Mod(String id, String version) {
    }
}
