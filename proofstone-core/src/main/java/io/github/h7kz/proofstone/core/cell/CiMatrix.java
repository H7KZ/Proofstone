package io.github.h7kz.proofstone.core.cell;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.h7kz.proofstone.core.versions.Platform;
import io.github.h7kz.proofstone.core.versions.ProxyPlatform;
import io.github.h7kz.proofstone.core.versions.Status;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import io.github.h7kz.proofstone.core.versions.VersionsException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Prints the GitHub Actions matrix for a scope (T-M3-07, ADR-0008). The cells come from {@link CellPlanner} with
 * {@code all}, so CI and a local run select the same rows. Its output is {@code {"include": [...]}}, one entry per
 * cell; the workflows read it with {@code fromJSON}. Spec: docs/versioning.md section 4.
 *
 * <p>A platform the planner cannot run yet (a {@code TODO} pin, or no {@code required} row in PR scope) is left out
 * of the matrix and printed to stderr with its reason. Nothing else is skipped: an unknown value still fails.
 *
 * <p>Topology cells (ADR-0016) follow the single-server cells, with the same scope rules applied to the weakest status of
 * proxy row, pairing and backend row. A file without {@code proxies} plans none, so its output is what it was.
 */
public final class CiMatrix {

    /** The planner's message for a {@code TODO} pin; a platform with one is not runnable yet. */
    static final String TODO_PINS = "has TODO pins";
    /** The planner's message when a proxy has no topology cell in the scope; that only skips the proxy. */
    private static final String NO_TOPOLOGY = "no topology cell in scope";

    private static final ObjectMapper JSON = new ObjectMapper();

    private CiMatrix() {
    }

    /**
     * @param cells the single-server cells in scope, in matrix order
     * @param topologies the topology cells in scope, in proxy file order
     * @param skipped platform or proxy id to the reason it has no cells, in file order
     */
    public record Plan(List<Cell> cells, List<TopologyCell> topologies, Map<String, String> skipped) {
    }

    /**
     * @throws VersionsException on an unknown pin or Java rule problem; a {@code TODO} pin only skips its platform
     */
    public static Plan plan(VersionsCatalog catalog, Scope scope) {
        CellPlanner planner = new CellPlanner(catalog);
        List<Cell> cells = new ArrayList<>();
        Map<String, String> skipped = new LinkedHashMap<>();
        for (String id : catalog.platformIds()) {
            Platform platform = catalog.platform(id);
            // PR runs the required rows only (versioning spec section 4), so a platform without one has no PR cell.
            if (scope == Scope.PR && platform.rows().stream().noneMatch(row -> row.status() == Status.REQUIRED)) {
                skipped.put(id, "no required row; PR runs required rows only");
                continue;
            }
            try {
                cells.addAll(planner.plan(id, List.of("all"), scope, VersionFamilies.none()));
            } catch (VersionsException e) {
                if (e.getMessage() == null || !e.getMessage().contains(TODO_PINS)) {
                    throw e;
                }
                skipped.put(id, e.getMessage());
            }
        }
        List<TopologyCell> topologies = new ArrayList<>();
        for (String id : catalog.proxyIds()) {
            planProxy(planner, catalog.proxy(id), scope, topologies, skipped);
        }
        return new Plan(List.copyOf(cells), List.copyOf(topologies), Map.copyOf(skipped));
    }

    private static void planProxy(CellPlanner planner, ProxyPlatform proxy, Scope scope, List<TopologyCell> out,
            Map<String, String> skipped) {
        // One call per backend platform the proxy pairs with, in the order the file lists them.
        Set<String> backends = new LinkedHashSet<>();
        proxy.rows().forEach(row -> row.backends().forEach(pairing -> backends.add(pairing.platform())));
        List<TopologyCell> planned = new ArrayList<>();
        try {
            for (String backend : backends) {
                try {
                    planned.addAll(planner.planTopologies(proxy.id(), List.of("all"), backend, List.of("all"), scope,
                            TopologyCell.DEFAULT_BACKENDS));
                } catch (VersionsException e) {
                    if (e.getMessage() == null || !e.getMessage().contains(NO_TOPOLOGY)) {
                        throw e;
                    }
                }
            }
        } catch (VersionsException e) {
            if (e.getMessage() == null || !e.getMessage().contains(TODO_PINS)) {
                throw e;
            }
            skipped.put(proxy.id(), e.getMessage());
            return;
        }
        if (planned.isEmpty()) {
            skipped.put(proxy.id(), scope == Scope.PR ? "no required topology cell; PR runs required rows only"
                    : "no topology cell in scope nightly");
            return;
        }
        out.addAll(planned);
    }

    /** @return {@code {"include": [...]}}, one object per cell, in the order given */
    public static ObjectNode matrix(List<Cell> cells) {
        return matrix(cells, List.of());
    }

    /**
     * @return {@code {"include": [...]}}: the single-server cells as {@link #matrix(List)} writes them, then one object
     *     per topology cell. A topology entry's {@code cell} is its identity ({@code velocity@4.3.1+paper@26.3}),
     *     {@code platform} is the proxy, and its slug joins the halves with an underscore, which no platform id holds.
     */
    public static ObjectNode matrix(List<Cell> cells, List<TopologyCell> topologies) {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode include = root.putArray("include");
        for (Cell cell : cells) {
            ObjectNode entry = include.addObject();
            entry.put("cell", cell.platform() + "@" + cell.mc());
            entry.put("platform", cell.platform());
            entry.put("mc", cell.mc());
            entry.put("build", cell.build());
            entry.put("java", cell.java());
            entry.put("status", cell.status().name().toLowerCase(Locale.ROOT));
            // Artifact names cannot hold some characters; platform and version never contain them.
            entry.put("slug", cell.platform() + "-" + cell.mc());
        }
        for (TopologyCell cell : topologies) {
            ObjectNode entry = include.addObject();
            entry.put("cell", cell.identity());
            entry.put("platform", cell.proxy().id());
            entry.put("kind", "topology");
            entry.put("proxyVersion", cell.proxy().version());
            entry.put("backend", cell.backend().platform());
            entry.put("backendMc", cell.backend().mc());
            entry.put("build", cell.proxy().build());
            entry.put("java", cell.proxy().java());
            entry.put("status", cell.status().name().toLowerCase(Locale.ROOT));
            entry.put("slug", cell.proxy().id() + "-" + cell.proxy().version() + "_" + cell.backend().platform() + "-"
                    + cell.backend().mc());
        }
        return root;
    }

    /**
     * Arguments: {@code --scope pr|nightly --versions FILE --schema FILE --out FILE}. Prints the matrix to stdout,
     * writes it to {@code --out}, and prints each skipped platform to stderr. A nightly run with no cell fails.
     */
    public static void main(String[] args) throws IOException {
        Map<String, String> options = options(args);
        Scope scope = Scope.parse(require(options, "scope"), "--scope");
        VersionsCatalog catalog = VersionsCatalog.load(
                Path.of(require(options, "versions")), Path.of(require(options, "schema")));
        Plan plan = plan(catalog, scope);
        PrintStream err = System.err;
        plan.skipped().forEach((id, reason) -> err.println(
                "printMatrix: skipped " + id + " (" + scope.name().toLowerCase(Locale.ROOT) + "): " + reason));
        if (scope == Scope.NIGHTLY && plan.cells().isEmpty() && plan.topologies().isEmpty()) {
            throw new VersionsException("printMatrix: nightly plans no cell; check versions.yml statuses and pins");
        }
        String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(matrix(plan.cells(), plan.topologies()));
        Path out = Path.of(require(options, "out"));
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, json + System.lineSeparator());
        System.out.println(json);
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("expected --name value pairs, got \"" + args[i] + "\"");
            }
            options.put(args[i].substring(2), args[i + 1]);
        }
        return options;
    }

    private static String require(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null) {
            throw new IllegalArgumentException("missing --" + name);
        }
        return value;
    }
}
