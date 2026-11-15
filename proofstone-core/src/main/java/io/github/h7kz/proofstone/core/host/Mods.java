package io.github.h7kz.proofstone.core.host;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The mods a test declares through {@code @Proofstone(mods)} (ADR-0017 section 4, T-M8-13): each glob is one jar, and the
 * Fabric API is never one of them, because the Fabric cell provides the pinned one. Everything here fails before Docker, so a
 * bad declaration never leaves a container behind, and the message starts with {@code INVALID test declaration} so the
 * verdict is INVALID and not a failed start.
 */
final class Mods {

    /** The id of the Fabric API's own descriptor: the cell copies the pinned jar in, a second copy would clash with it. */
    static final String FABRIC_API_ID = "fabric-api";

    private Mods() {
    }

    /**
     * Resolves each glob, relative to {@code base}, to exactly one mod jar.
     *
     * @throws PaperHostException naming the glob when it matches none or more than one, or the jar when it is not a mod or
     *     declares the id {@code fabric-api}
     */
    static List<Path> resolve(Path base, List<String> globs) {
        List<Path> mods = new ArrayList<>();
        for (String glob : globs) {
            List<Path> found;
            try {
                found = Artefacts.resolve(base, List.of(glob));
            } catch (PaperHostException e) {
                throw new PaperHostException("INVALID test declaration: mod " + e.getMessage(), e);
            }
            if (found.size() != 1) {
                throw new PaperHostException("INVALID test declaration: mod glob \"" + glob + "\" must match exactly one jar, "
                        + "it matched " + found.size() + ": " + found);
            }
            Path jar = found.get(0);
            List<String> ids;
            try {
                ids = ModLoad.ids(jar);
            } catch (PaperHostException e) {
                throw new PaperHostException("INVALID test declaration: " + e.getMessage(), e);
            }
            if (ids.contains(FABRIC_API_ID)) {
                throw new PaperHostException("INVALID test declaration: mod " + jar.getFileName() + " (glob \"" + glob
                        + "\") has the id " + FABRIC_API_ID + ". A Fabric cell provides the pinned Fabric API itself "
                        + "(ADR-0017 section 4); remove it from @Proofstone(mods)");
            }
            if (mods.contains(jar)) {
                throw new PaperHostException("INVALID test declaration: mod glob \"" + glob + "\" matches " + jar.getFileName()
                        + " again; each mod is declared once");
            }
            mods.add(jar);
        }
        return List.copyOf(mods);
    }
}
