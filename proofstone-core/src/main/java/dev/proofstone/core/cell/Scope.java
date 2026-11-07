package dev.proofstone.core.cell;

import dev.proofstone.core.versions.VersionsException;
import java.util.Locale;

/**
 * Which rows {@code all} expands to (test-api spec §1, versioning spec §4). {@code PR} runs the {@code required}
 * rows; experimental rows run there only when named explicitly or listed as a version family. {@code NIGHTLY}
 * runs every non-retired row. Retired rows only run when named explicitly.
 */
public enum Scope {
    PR,
    NIGHTLY;

    /**
     * @param value the setting's value, or {@code null} or blank for the default ({@link #PR})
     * @param source where the value came from, for the error message
     * @throws VersionsException on any other value
     */
    public static Scope parse(String value, String source) {
        if (value == null || value.isBlank()) {
            return PR;
        }
        String name = value.trim().toUpperCase(Locale.ROOT);
        for (Scope scope : values()) {
            if (scope.name().equals(name)) {
                return scope;
            }
        }
        throw new VersionsException(source + " \"" + value + "\" is not a scope; use pr or nightly");
    }
}
