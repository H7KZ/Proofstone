package io.github.h7kz.proofstone.core.cell;

import io.github.h7kz.proofstone.core.versions.VersionsException;
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
 *
 * <p>A topology entry is {@code proxy@version+platform@version}, for example {@code velocity@4.3.1+paper@26.3}
 * (ADR-0016 section 1). It selects a topology cell, never a single-server platform, so it has its own list.
 *
 * <p>A loader entry is {@code fabric@26.3} or {@code neoforge@latest} (ADR-0017 section 2): a single-server loader cell,
 * kept in {@link #loaders()}, never in the platform lists. A loader id on either side of a {@code +} is an error.
 *
 * <p>A Geyser entry is {@code geyser@2.12.1+paper@26.3} (ADR-0018 section 8), kept in {@link #geysers()}: never a topology
 * entry, though it shares the {@code +} form. {@code geyser} is reserved: it appears only left of the {@code +}, and
 * a bare {@code geyser@version}, a Geyser id on the right or a malformed Geyser entry is an error.
 */
public final class CellSelector {

    // Only the forms the spec allows. An exact version that is not a row is the planner's error, not this one.
    private static final Pattern ENTRY = Pattern.compile("([a-z][a-z0-9-]*)@(latest|all|\\d+(?:\\.\\d+)*)");
    private static final String VERSION = "(latest|all|\\d+(?:\\.\\d+)*)";
    private static final Pattern TOPOLOGY = Pattern.compile(
            "([a-z][a-z0-9-]*)@" + VERSION + "\\+([a-z][a-z0-9-]*)@" + VERSION);
    private static final CellSelector NONE = new CellSelector(Map.of(), List.of(), List.of(), List.of());
    /** The loader ids (ADR-0017): {@code fabric@26.3} is a loader cell, never a platform entry or half a topology. */
    private static final List<String> LOADERS = List.of("fabric", "neoforge");
    /** The reserved Geyser id (ADR-0018 section 8): {@code geyser@2.12.1+paper@26.3}, left of the {@code +} only. */
    private static final String GEYSER = "geyser";

    private final Map<String, List<String>> versionsByPlatform;
    private final List<Topology> topologies;
    private final List<LoaderEntry> loaders;
    private final List<GeyserEntry> geysers;

    private CellSelector(Map<String, List<String>> versionsByPlatform, List<Topology> topologies,
            List<LoaderEntry> loaders, List<GeyserEntry> geysers) {
        this.versionsByPlatform = versionsByPlatform;
        this.topologies = topologies;
        this.loaders = loaders;
        this.geysers = geysers;
    }

    /**
     * One Geyser entry: {@code geyser@2.12.1+paper@26.3}, a Geyser version and a backend platform with a version, each
     * exact, {@code latest} or {@code all}. Whether the row or the pairing exists, and that the backend is paper, is
     * the planner's error, not this one.
     */
    public record GeyserEntry(String version, String backend, String backendVersion) {

        /** The entry as written, for messages. */
        public String entry() {
            return GEYSER + "@" + version + "+" + backend + "@" + backendVersion;
        }
    }

    /**
     * One loader entry: {@code fabric@26.3}, a loader id with an exact version, {@code latest} or {@code all}. Whether the
     * row exists is the planner's error, not this one.
     */
    public record LoaderEntry(String loader, String version) {

        /** The entry as written, for messages. */
        public String entry() {
            return loader + "@" + version;
        }
    }

    /**
     * One topology entry: a proxy and a backend platform, each with an exact version, {@code latest} or {@code all}.
     * Whether the proxy or the pairing exists is the planner's error, not this one.
     */
    public record Topology(String proxy, String proxyVersion, String backend, String backendVersion) {

        /** The entry as written, for messages. */
        public String entry() {
            return proxy + "@" + proxyVersion + "+" + backend + "@" + backendVersion;
        }
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
        List<Topology> topologies = new ArrayList<>();
        List<LoaderEntry> loaders = new ArrayList<>();
        List<GeyserEntry> geysers = new ArrayList<>();
        for (String raw : spec.split(",", -1)) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                throw new VersionsException(source + " \"" + spec + "\" has an empty entry; use a list such as "
                        + "paper@latest,folia@all");
            }
            if (entry.contains("+")) {
                var topology = TOPOLOGY.matcher(entry);
                if (topology.matches() && GEYSER.equals(topology.group(3))) {
                    throw new VersionsException(source + " entry \"" + entry + "\" names geyser on the right of the +: "
                            + "geyser is reserved to the left of the + (geyser@version+paper@version), never a "
                            + "backend, and nothing pairs with it (ADR-0018 section 8)");
                }
                if (!topology.matches() && entry.startsWith(GEYSER + "@")) {
                    throw new VersionsException(source + " entry \"" + entry + "\" must be "
                            + "geyser@version+paper@version, for example geyser@2.12.1+paper@26.3 or "
                            + "geyser@latest+paper@latest (lower case, version is latest, all or digits and dots; "
                            + "ADR-0018 section 8)");
                }
                if (!topology.matches()) {
                    throw new VersionsException(source + " entry \"" + entry + "\" must be "
                            + "proxy@version+platform@version, for example velocity@4.3.1+paper@26.3 or "
                            + "velocity@latest+paper@latest (lower case, one proxy and one backend platform)");
                }
                if (LOADERS.contains(topology.group(1)) || LOADERS.contains(topology.group(3))) {
                    throw new VersionsException(source + " entry \"" + entry + "\" names a loader in a topology: a "
                            + "loader cell is a single-server cell written fabric@26.3 or neoforge@26.3, never "
                            + "joined with + (ADR-0017 section 2)");
                }
                if (GEYSER.equals(topology.group(1))) {
                    GeyserEntry geyser = new GeyserEntry(topology.group(2), topology.group(3), topology.group(4));
                    if (!geysers.contains(geyser)) {
                        geysers.add(geyser);
                    }
                    continue;
                }
                Topology parsed = new Topology(topology.group(1), topology.group(2), topology.group(3),
                        topology.group(4));
                if (!topologies.contains(parsed)) {
                    topologies.add(parsed);
                }
                continue;
            }
            var matcher = ENTRY.matcher(entry);
            if (!matcher.matches()) {
                throw new VersionsException(source + " entry \"" + entry + "\" must be platform@version, for "
                        + "example paper@latest, paper@1.21.11 or paper@all (platform is lower case)");
            }
            if (GEYSER.equals(matcher.group(1))) {
                throw new VersionsException(source + " entry \"" + entry + "\" must be "
                        + "geyser@version+paper@version, for example geyser@2.12.1+paper@26.3: a Geyser cell is always "
                        + "written with its paper pairing (ADR-0018 section 8)");
            }
            if (LOADERS.contains(matcher.group(1))) {
                LoaderEntry parsed = new LoaderEntry(matcher.group(1), matcher.group(2));
                if (!loaders.contains(parsed)) {
                    loaders.add(parsed);
                }
                continue;
            }
            List<String> versions = byPlatform.computeIfAbsent(matcher.group(1), key -> new ArrayList<>());
            if (!versions.contains(matcher.group(2))) {
                versions.add(matcher.group(2));
            }
        }
        Map<String, List<String>> frozen = new LinkedHashMap<>();
        byPlatform.forEach((platform, versions) -> frozen.put(platform, List.copyOf(versions)));
        return new CellSelector(Map.copyOf(frozen), List.copyOf(topologies), List.copyOf(loaders),
                List.copyOf(geysers));
    }

    /** @return {@code true} when the setting names at least one entry */
    public boolean isSet() {
        return !versionsByPlatform.isEmpty() || !topologies.isEmpty() || !loaders.isEmpty() || !geysers.isEmpty();
    }

    /** @return the Geyser entries in the order written, without duplicates; empty when none is named */
    public List<GeyserEntry> geysers() {
        return geysers;
    }

    /** @return the loader entries in the order written, without duplicates; empty when none is named */
    public List<LoaderEntry> loaders() {
        return loaders;
    }

    /** @return the topology entries in the order written, without duplicates; empty when none is named */
    public List<Topology> topologies() {
        return topologies;
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
