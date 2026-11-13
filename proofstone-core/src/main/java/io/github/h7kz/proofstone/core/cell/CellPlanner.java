package io.github.h7kz.proofstone.core.cell;

import io.github.h7kz.proofstone.core.versions.Platform;
import io.github.h7kz.proofstone.core.versions.ProxyPlatform;
import io.github.h7kz.proofstone.core.versions.ProxyRow;
import io.github.h7kz.proofstone.core.versions.Row;
import io.github.h7kz.proofstone.core.versions.Status;
import io.github.h7kz.proofstone.core.versions.VersionNumber;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import io.github.h7kz.proofstone.core.versions.VersionsException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns a platform and a list of versions into {@link Cell}s. {@code latest} means the highest
 * {@code required} row, or the highest {@code experimental} row with a warning when none is required
 * (architecture.md §2.2). Spec: docs/specs/versions-yml.md.
 */
public final class CellPlanner {

    private static final String LATEST = "latest";
    private static final String ALL = "all";

    private final VersionsCatalog catalog;

    public CellPlanner(VersionsCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * Plans with the default scope ({@link Scope#PR}) and no version families.
     *
     * @see #plan(String, List, Scope, VersionFamilies)
     */
    public List<Cell> plan(String platformId, List<String> versions) {
        return plan(platformId, versions, Scope.PR, VersionFamilies.none());
    }

    /**
     * @param platformId platform id from the matrix
     * @param versions exact Minecraft versions, {@code latest}, or {@code all} (every row in {@code scope}, plus the
     *     family versions); duplicates collapse to one cell
     * @param scope which rows {@code all} takes
     * @param families the project's version families, used only by {@code all}
     * @return one cell per distinct version, in request order; {@code all} contributes rows in file order
     * @throws VersionsException on an unknown platform or version, an empty request, an empty {@code all} or a
     *     {@code TODO} pin
     */
    public List<Cell> plan(String platformId, List<String> versions, Scope scope, VersionFamilies families) {
        Platform platform = catalog.platform(platformId);
        if (versions.isEmpty()) {
            throw new VersionsException("no versions requested for " + platformId);
        }
        Map<String, Cell> cells = new LinkedHashMap<>();
        for (String requested : versions) {
            if (ALL.equals(requested)) {
                for (Row row : allRows(platform, scope, families)) {
                    cells.putIfAbsent(row.mc(), toCell(platform, row, List.of()));
                }
                continue;
            }
            Cell cell = resolve(platform, requested);
            cells.putIfAbsent(cell.mc(), cell);
        }
        return List.copyOf(cells.values());
    }

    /** Whether {@code id} names a proxy platform of {@code versions.yml} (schema 3 {@code proxies}). */
    public boolean isProxy(String id) {
        return catalog.proxyIds().contains(id);
    }

    /**
     * Plans one topology cell (ADR-0016): a proxy row and one backend row it is paired with. {@code latest} resolves as
     * it does for that platform, and the resolved pairing must be listed in {@code versions.yml}.
     *
     * @param backendNames the backends in order, validated as {@link TopologyCell#requireBackendNames}
     * @throws VersionsException on an unknown proxy or version, a pairing that is not listed, a {@code TODO} pin, or
     *     more than one cell ({@code all}: use {@link #planTopologies})
     * @throws IllegalArgumentException on a malformed backend list
     */
    public TopologyCell planTopology(String proxyId, String proxyVersion, String backendPlatform,
            String backendVersion, Scope scope, List<String> backendNames) {
        List<TopologyCell> cells = planTopologies(proxyId, List.of(proxyVersion), backendPlatform,
                List.of(backendVersion), scope, backendNames);
        if (cells.size() != 1) {
            throw new VersionsException(TopologyCell.identity(proxyId, proxyVersion, backendPlatform, backendVersion)
                    + " plans " + cells.size() + " cells; planTopology takes one, use planTopologies");
        }
        return cells.get(0);
    }

    /**
     * Plans topology cells from a proxy and a backend platform. Each version is exact, {@code latest} or {@code all}.
     * {@code all} on either half takes the pairings whose weakest status is in {@code scope} (PR runs
     * {@code required}, nightly also {@code experimental}); exact and {@code latest} cells run in any scope.
     *
     * @return the cells in request order, proxy rows in file order within {@code all}; duplicates collapse
     * @throws VersionsException on an unknown proxy or version, an exact or {@code latest} pairing that is not listed,
     *     a {@code TODO} pin, or no cell in scope
     * @throws IllegalArgumentException on a malformed backend list
     */
    public List<TopologyCell> planTopologies(String proxyId, List<String> proxyVersions, String backendPlatform,
            List<String> backendVersions, Scope scope, List<String> backendNames) {
        TopologyCell.requireBackendNames(backendNames);
        ProxyPlatform proxy = catalog.proxy(proxyId);
        if (proxyVersions.isEmpty() || backendVersions.isEmpty()) {
            throw new VersionsException("no versions requested for " + proxyId + "+" + backendPlatform);
        }
        Map<String, TopologyCell> cells = new LinkedHashMap<>();
        for (String requestedProxy : proxyVersions) {
            boolean allProxyRows = ALL.equals(requestedProxy);
            Map<ProxyRow, List<String>> rows = new LinkedHashMap<>();
            if (allProxyRows) {
                proxy.rows().forEach(row -> rows.put(row, List.of()));
            } else {
                rows.putAll(resolveProxyRow(proxy, requestedProxy));
            }
            for (Map.Entry<ProxyRow, List<String>> row : rows.entrySet()) {
                for (String requestedBackend : backendVersions) {
                    for (ProxyRow.Pairing pairing : pairings(proxy, row.getKey(), backendPlatform, requestedBackend,
                            allProxyRows)) {
                        TopologyCell cell = toTopologyCell(proxy, row.getKey(), pairing, backendNames, row.getValue());
                        boolean named = !allProxyRows && !ALL.equals(requestedBackend);
                        if (named || inScope(cell.status(), scope)) {
                            cells.putIfAbsent(cell.identity(), cell);
                        }
                    }
                }
            }
        }
        if (cells.isEmpty()) {
            throw new VersionsException(proxyId + "+" + backendPlatform + ": no topology cell in scope " + scope
                    + " (PR runs required pairings, nightly runs required and experimental ones; the weakest of proxy "
                    + "row, pairing and backend row counts)");
        }
        return List.copyOf(cells.values());
    }

    private static boolean inScope(Status status, Scope scope) {
        return status == Status.REQUIRED || (scope == Scope.NIGHTLY && status == Status.EXPERIMENTAL);
    }

    /** The proxy row for an exact version or {@code latest}, with the notes the choice carries. */
    private static Map<ProxyRow, List<String>> resolveProxyRow(ProxyPlatform proxy, String requested) {
        if (LATEST.equals(requested)) {
            Optional<ProxyRow> required = highestProxy(proxy.rows(), Status.REQUIRED);
            if (required.isPresent()) {
                return Map.of(required.get(), List.of());
            }
            Optional<ProxyRow> experimental = highestProxy(proxy.rows(), Status.EXPERIMENTAL);
            if (experimental.isEmpty()) {
                throw new VersionsException("latest: proxy " + proxy.id() + " has no required or experimental row");
            }
            return Map.of(experimental.get(), List.of("latest: no required " + proxy.id()
                    + " row; using experimental " + experimental.get().version()));
        }
        ProxyRow row = proxy.rows().stream()
                .filter(candidate -> candidate.version().equals(requested))
                .findFirst()
                .orElseThrow(() -> new VersionsException("unknown version \"" + requested + "\" for proxy "
                        + proxy.id() + "; valid: " + proxy.rows().stream().map(ProxyRow::version)
                        .collect(Collectors.joining(", "))));
        return Map.of(row, List.of());
    }

    private static Optional<ProxyRow> highestProxy(List<ProxyRow> rows, Status status) {
        return rows.stream()
                .filter(row -> row.status() == status)
                .max(Comparator.comparing(ProxyRow::version, VersionNumber::compare));
    }

    /**
     * The pairings of {@code row} that match a backend request. {@code lenient} (the proxy half was {@code all}) skips
     * a row that lacks the pairing; otherwise a missing pairing is an error that lists the row's pairings.
     */
    private List<ProxyRow.Pairing> pairings(ProxyPlatform proxy, ProxyRow row, String platform, String requested,
            boolean lenient) {
        if (ALL.equals(requested)) {
            return row.backends().stream().filter(p -> p.platform().equals(platform)).toList();
        }
        String mc = LATEST.equals(requested) ? latest(catalog.platform(platform)).mc() : requested;
        List<ProxyRow.Pairing> found = row.backends().stream()
                .filter(p -> p.platform().equals(platform) && p.mc().equals(mc)).toList();
        if (found.isEmpty() && !lenient) {
            throw new VersionsException(proxy.id() + "@" + row.version() + " is not paired with " + platform + "@" + mc
                    + (LATEST.equals(requested) ? " (latest of " + platform + ")" : "") + "; its pairings: "
                    + row.backends().stream().map(p -> p.platform() + "@" + p.mc())
                    .collect(Collectors.joining(", ")));
        }
        return found;
    }

    private TopologyCell toTopologyCell(ProxyPlatform proxy, ProxyRow row, ProxyRow.Pairing pairing,
            List<String> backendNames, List<String> extraNotes) {
        String identity = TopologyCell.identity(proxy.id(), row.version(), pairing.platform(), pairing.mc());
        List<String> todo = new ArrayList<>();
        if (row.build() == null) {
            todo.add("build");
        }
        if (row.sha256() == null) {
            todo.add("sha256");
        }
        String hostDigest = proxy.host().digest(row.java());
        if (hostDigest == null) {
            todo.add("host digest for " + row.java());
        }
        if (!todo.isEmpty()) {
            throw new VersionsException(identity + " has TODO pins: " + String.join(", ", todo)
                    + ". Pin them in versions.yml before use (AGENTS.md §3).");
        }
        Platform backendPlatform = catalog.platform(pairing.platform());
        Row backendRow = backendPlatform.rows().stream().filter(r -> r.mc().equals(pairing.mc())).findFirst()
                .orElseThrow(() -> new VersionsException(identity + ": backend row " + pairing.platform() + "@"
                        + pairing.mc() + " is not in versions.yml"));
        Cell backend = toCell(backendPlatform, backendRow, List.of());
        Status status = Stream.of(row.status(), pairing.status(), backend.status())
                .max(Comparator.naturalOrder()).orElseThrow();
        List<String> notes = new ArrayList<>(extraNotes);
        notes.addAll(backend.notes());
        if (status == Status.RETIRED) {
            notes.add("status: retired");
        }
        return new TopologyCell(identity,
                new TopologyCell.Proxy(proxy.id(), row.version(), row.build(), row.sha256(), row.java(),
                        proxy.forwarding(), proxy.host().image(), hostDigest, row.status()),
                backend, backendNames, pairing.status(), status, notes);
    }

    private List<Row> allRows(Platform platform, Scope scope, VersionFamilies families) {
        List<String> familyVersions = families.versions(platform.id(), scope);
        List<Row> rows = platform.rows().stream()
                .filter(row -> row.status() == Status.REQUIRED
                        || (scope == Scope.NIGHTLY && row.status() == Status.EXPERIMENTAL)
                        || familyVersions.contains(row.mc()))
                .toList();
        if (rows.isEmpty()) {
            throw new VersionsException("all: " + platform.id() + " has no row in scope " + scope
                    + " (PR runs required rows, nightly runs required and experimental ones)");
        }
        return rows;
    }

    private Cell resolve(Platform platform, String requested) {
        if (LATEST.equals(requested)) {
            return latest(platform);
        }
        Row row = platform.rows().stream()
                .filter(candidate -> candidate.mc().equals(requested))
                .findFirst()
                .orElseThrow(() -> new VersionsException("unknown version \"" + requested + "\" for "
                        + platform.id() + "; valid: " + platform.rows().stream().map(Row::mc)
                        .collect(Collectors.joining(", "))));
        return toCell(platform, row, List.of());
    }

    private Cell latest(Platform platform) {
        Optional<Row> required = highest(platform.rows(), Status.REQUIRED);
        if (required.isPresent()) {
            return toCell(platform, required.get(), List.of());
        }
        Optional<Row> experimental = highest(platform.rows(), Status.EXPERIMENTAL);
        if (experimental.isEmpty()) {
            throw new VersionsException("latest: " + platform.id() + " has no required or experimental row");
        }
        String warning = "latest: no required row; using experimental " + experimental.get().mc();
        return toCell(platform, experimental.get(), List.of(warning));
    }

    private static Optional<Row> highest(List<Row> rows, Status status) {
        return rows.stream()
                .filter(row -> row.status() == status)
                .max(Comparator.comparing(Row::mc, VersionNumber::compare));
    }

    private Cell toCell(Platform platform, Row row, List<String> extraNotes) {
        List<String> todo = new ArrayList<>();
        if (row.build() == null && platform.builder() == null) {
            todo.add("build");
        }
        if (row.sha256() == null) {
            todo.add("sha256");
        }
        String javaTag = catalog.javaTag(row);
        String hostDigest = platform.host().digest(javaTag);
        if (hostDigest == null) {
            todo.add("host digest for " + javaTag);
        }
        if (platform.builder() != null) {
            if (platform.builder().buildToolsSha256() == null) {
                todo.add("builder BuildTools sha256");
            }
            if (platform.builder().jdkImage(javaTag) == null) {
                todo.add("builder JDK for " + javaTag);
            }
        }
        if (!todo.isEmpty()) {
            throw new VersionsException(platform.id() + "@" + row.mc() + " has TODO pins: "
                    + String.join(", ", todo) + ". Pin them in versions.yml before use (AGENTS.md §3).");
        }
        List<String> notes = new ArrayList<>(extraNotes);
        if (row.status() == Status.RETIRED) {
            notes.add("status: retired");
        }
        return new Cell(
                platform.id(),
                row.mc(),
                row.build(),
                row.sha256(),
                javaTag,
                row.status(),
                platform.host().image(),
                hostDigest,
                List.copyOf(notes));
    }
}
