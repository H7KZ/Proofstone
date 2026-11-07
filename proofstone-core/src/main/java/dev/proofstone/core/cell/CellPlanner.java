package dev.proofstone.core.cell;

import dev.proofstone.core.versions.Platform;
import dev.proofstone.core.versions.Row;
import dev.proofstone.core.versions.Status;
import dev.proofstone.core.versions.VersionNumber;
import dev.proofstone.core.versions.VersionsCatalog;
import dev.proofstone.core.versions.VersionsException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
        if (row.build() == null) {
            todo.add("build");
        }
        if (row.sha256() == null) {
            todo.add("sha256");
        }
        if (platform.host().digest() == null) {
            todo.add("host digest");
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
                catalog.javaTag(row),
                row.status(),
                platform.host().image(),
                platform.host().digest(),
                List.copyOf(notes));
    }
}
