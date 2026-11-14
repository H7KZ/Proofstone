package io.github.h7kz.proofstone.core.host;

import java.util.Collection;

/**
 * The start check for copied files: a server log line that holds {@code Permission denied} fails the cell, naming the
 * copied file it is about when it mentions one (T-M8-02). Never an allowlist; an unknown line fails too (AGENTS.md,
 * fail loudly on unknowns).
 */
final class PermissionCheck {

    private static final String DENIED = "Permission denied";

    private PermissionCheck() {
    }

    /** Throws when {@code log} holds a permission error; {@code copied} are the container paths the host copied in. */
    static void require(String who, String log, Collection<String> copied) {
        StringBuilder found = new StringBuilder();
        for (String line : log.split("\\R")) {
            if (!line.contains(DENIED)) {
                continue;
            }
            String file = copied.stream().filter(path -> line.contains(baseName(path))).findFirst()
                    .map(path -> "copied file " + path).orElse("a file the host did not copy by name");
            found.append("\n  ").append(file).append(": ").append(line.strip());
        }
        if (found.length() > 0) {
            throw new PaperHostException(who + " logged \"" + DENIED + "\" at start, so a file it needs is not owned by "
                    + "its user (uid " + ServerFile.SERVER_ID + "):" + found);
        }
    }

    private static String baseName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
