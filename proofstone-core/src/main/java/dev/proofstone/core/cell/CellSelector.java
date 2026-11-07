package dev.proofstone.core.cell;

import dev.proofstone.core.versions.VersionsException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The run-time cell selector, {@code -Dproofstone.cells} or {@code PROOFSTONE_CELLS}: a comma-separated list of
 * {@code platform@version} entries, where the version is an exact version, {@code latest} or {@code all}
 * (test-api spec §1, versioning spec §4). When it is set it replaces the {@code @Matrix} versions of every
 * platform it names; a platform it does not name is not run. Unknown forms are errors, never ignored.
 */
public final class CellSelector {

    // Only the forms the spec allows. An exact version that is not a row is the planner's error, not this one.
    private static final Pattern ENTRY = Pattern.compile("([a-z][a-z0-9-]*)@(latest|all|\\d+(?:\\.\\d+)*)");
    private static final CellSelector NONE = new CellSelector(Map.of());

    private final Map<String, List<String>> versionsByPlatform;

    private CellSelector(Map<String, List<String>> versionsByPlatform) {
        this.versionsByPlatform = versionsByPlatform;
    }

    /** @return a selector that is not set: every {@code @Matrix} runs as declared */
    public static CellSelector none() {
        return NONE;
    }

    /**
     * @param spec the setting's value; {@code null} or blank means not set
     * @param source where the value came from, for the error message
     * @throws VersionsException on an entry that is not {@code platform@version}, with the entry quoted
     */
    public static CellSelector parse(String spec, String source) {
        if (spec == null || spec.isBlank()) {
            return NONE;
        }
        Map<String, List<String>> byPlatform = new LinkedHashMap<>();
        for (String raw : spec.split(",", -1)) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                throw new VersionsException(source + " \"" + spec + "\" has an empty entry; use a list such as "
                        + "paper@latest,folia@all");
            }
            var matcher = ENTRY.matcher(entry);
            if (!matcher.matches()) {
                throw new VersionsException(source + " entry \"" + entry + "\" must be platform@version, for "
                        + "example paper@latest, paper@1.21.11 or paper@all (platform is lower case)");
            }
            List<String> versions = byPlatform.computeIfAbsent(matcher.group(1), key -> new ArrayList<>());
            if (!versions.contains(matcher.group(2))) {
                versions.add(matcher.group(2));
            }
        }
        Map<String, List<String>> frozen = new LinkedHashMap<>();
        byPlatform.forEach((platform, versions) -> frozen.put(platform, List.copyOf(versions)));
        return new CellSelector(Map.copyOf(frozen));
    }

    /** @return {@code true} when the setting names at least one entry */
    public boolean isSet() {
        return !versionsByPlatform.isEmpty();
    }

    /** @return {@code true} when the setting names this platform */
    public boolean selects(String platform) {
        return versionsByPlatform.containsKey(platform);
    }

    /**
     * @return the versions the setting asks for on this platform, in the order written, or an empty list when the
     *     setting does not name the platform
     */
    public List<String> versions(String platform) {
        return versionsByPlatform.getOrDefault(platform, List.of());
    }
}
