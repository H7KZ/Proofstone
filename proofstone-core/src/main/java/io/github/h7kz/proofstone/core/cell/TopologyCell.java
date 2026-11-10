package io.github.h7kz.proofstone.core.cell;

import io.github.h7kz.proofstone.core.versions.Status;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One topology (glossary: Cell, "proxy with backends"), resolved from {@code versions.yml} schema 3: a proxy row plus a
 * backend row set, N named backends all on the same backend row (ADR-0016 section 1). Every pin is set; the planner
 * refuses rows that still say {@code TODO}. Single-server code never sees this type: {@link Cell} is unchanged.
 *
 * @param identity {@code <proxy>@<proxy-version>+<backend>@<mc>}, used unchanged for the selector, the matrix, the
 *     report, {@code cell.json} and the bundle directory; a single-server identity never contains {@code +}
 * @param proxy the proxy row's pins and host
 * @param backend the backend row every backend runs, with its own pins
 * @param backendNames the backends in order; the first is the proxy's default server
 * @param pairingStatus the status of the pairing in {@code versions.yml}
 * @param status the weakest of the proxy row, the pairing and the backend row (ADR-0016 section 2)
 * @param notes warnings, for example that {@code latest} fell back to an experimental row
 */
public record TopologyCell(
        String identity,
        Proxy proxy,
        Cell backend,
        List<String> backendNames,
        Status pairingStatus,
        Status status,
        List<String> notes) {

    /** The backends of a topology that names none (ADR-0016 section 1): {@code lobby} first, then {@code survival}. */
    public static final List<String> DEFAULT_BACKENDS = List.of("lobby", "survival");
    /** The reserved name and network alias of the proxy. */
    public static final String PROXY = "proxy";

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9]{0,15}");

    public TopologyCell {
        backendNames = List.copyOf(backendNames);
        notes = List.copyOf(notes);
    }

    /**
     * The pins of the proxy half.
     *
     * @param id proxy id, for example {@code velocity}
     * @param version the proxy's version; BungeeCord's is its build number as a string
     * @param build the build the image downloads
     * @param sha256 the jar checksum, checked in the container before start
     * @param java Java image tag
     * @param forwarding {@code modern} or {@code legacy}
     * @param hostImage host image repository
     * @param hostDigest host image digest for {@code java}
     * @param status the proxy row's own status
     */
    public record Proxy(String id, String version, int build, String sha256, String java, String forwarding,
            String hostImage, String hostDigest, Status status) {
    }

    /** {@code <proxy>@<version>+<platform>@<mc>} (ADR-0016 section 1). */
    public static String identity(String proxy, String proxyVersion, String platform, String mc) {
        return proxy + "@" + proxyVersion + "+" + platform + "@" + mc;
    }

    /**
     * Fails on an empty list, a name outside {@code [a-z][a-z0-9]{0,15}}, {@code proxy} or a duplicate.
     *
     * @throws IllegalArgumentException with the offending name
     */
    public static void requireBackendNames(List<String> names) {
        if (names.isEmpty()) {
            throw new IllegalArgumentException("a topology needs at least one backend");
        }
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("backend name \"" + name + "\" must match " + NAME.pattern());
            }
            if (name.equals(PROXY)) {
                throw new IllegalArgumentException("backend name \"proxy\" is reserved for the proxy (ADR-0016)");
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("duplicate backend name \"" + name + "\"");
            }
        }
    }
}
