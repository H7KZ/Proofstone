package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Isolation;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Topology;
import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.core.cell.CellSelector;
import io.github.h7kz.proofstone.core.cell.Scope;
import io.github.h7kz.proofstone.core.cell.TopologyCell;
import io.github.h7kz.proofstone.core.cell.VersionFamilies;
import io.github.h7kz.proofstone.core.driver.ClientDriver;
import io.github.h7kz.proofstone.core.host.Eula;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import java.lang.reflect.AnnotatedElement;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.AnnotationSupport;
import org.testcontainers.DockerClientFactory;

/**
 * Makes {@link Proofstone}, {@link Matrix} and {@link CellTest} work (ADR-0001): one invocation per cell, and
 * a skip with a visible reason when the EULA is not accepted or Docker is missing. A skip is never a pass; the
 * reason is printed to stderr and reported by JUnit.
 *
 * <p>Registered by JUnit auto-detection, so it sees every class and must leave classes without
 * {@link Proofstone} alone.
 */
public final class ProofstoneExtension implements TestTemplateInvocationContextProvider, ExecutionCondition {

    static final String REPO_ROOT = "proofstone.repoRoot";
    /** Run-time cell selector and scope (test-api spec §1); the property wins over the environment variable. */
    static final String CELLS = "proofstone.cells";
    static final String CELLS_ENV = "PROOFSTONE_CELLS";
    static final String CELLS_SOURCE = "-Dproofstone.cells or PROOFSTONE_CELLS";
    static final String SCOPE = "proofstone.scope";
    static final String SCOPE_ENV = "PROOFSTONE_SCOPE";
    static final String SCOPE_SOURCE = "-Dproofstone.scope or PROOFSTONE_SCOPE";
    /** Read from the test working directory, which is the project directory under Gradle. */
    static final String FAMILIES_FILE = "proofstone.yml";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        // Judge the template method, so each skipped test shows its own reason.
        boolean cellTest = context.getTestMethod()
                .map(method -> AnnotationSupport.isAnnotated(method, CellTest.class)).orElse(false);
        Optional<Proofstone> config = context.getTestClass()
                .flatMap(type -> AnnotationSupport.findAnnotation(type, Proofstone.class));
        if (!cellTest || config.isEmpty()) {
            return ConditionEvaluationResult.enabled("not a Proofstone cell test");
        }
        if (!config.get().acceptEula() && !Eula.accepted()) {
            return skip("Minecraft EULA not accepted: set " + Eula.VARIABLE
                    + "=true (read https://aka.ms/MinecraftEULA)");
        }
        if (!dockerAvailable()) {
            return skip("Docker is not available");
        }
        return ConditionEvaluationResult.enabled("EULA accepted and Docker available");
    }

    private static ConditionEvaluationResult skip(String reason) {
        System.err.println("PROOFSTONE SKIPPED (not a pass): " + reason);
        return ConditionEvaluationResult.disabled(reason);
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod().map(method -> AnnotationSupport.isAnnotated(method, CellTest.class))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        Class<?> type = context.getRequiredTestClass();
        Proofstone config = AnnotationSupport.findAnnotation(type, Proofstone.class).orElseThrow(
                () -> new IllegalStateException("@CellTest needs @Proofstone on " + type.getName()));
        List<Matrix> matrices = AnnotationSupport.findRepeatableAnnotations((AnnotatedElement) type, Matrix.class);
        if (matrices.isEmpty()) {
            throw new IllegalStateException("@CellTest needs at least one @Matrix on " + type.getName());
        }
        VersionsCatalog catalog = catalog();
        CellPlanner planner = new CellPlanner(catalog);
        CellSelector selector = CellSelector.parse(setting(CELLS, CELLS_ENV), CELLS_SOURCE);
        Scope scope = Scope.parse(setting(SCOPE, SCOPE_ENV), SCOPE_SOURCE);
        VersionFamilies families = VersionFamilies.load(Path.of(FAMILIES_FILE), catalog);
        Plan plan = plan(type, matrices, selector, planner, scope, families);
        List<Cell> cells = plan.cells();
        Isolation.Mode isolation = AnnotationSupport.findAnnotation(type, Isolation.class).map(Isolation::value)
                .orElse(Isolation.Mode.PER_CLASS);
        boolean differential = DifferentialRun.enabled();
        // The flake detector (self-tests section 4) runs every cell N times in a row; a plain run runs each cell once.
        int repeats = FlakeDetector.repeats();
        if (differential && repeats > 1) {
            // Both would count every run in one ledger, so a repeat of a differential run has no defined verdict.
            throw new IllegalStateException("-D" + DifferentialRun.PROPERTY + " and -D" + FlakeDetector.REPEAT
                    + " cannot be combined: run the differential once, or the flake detector on its own");
        }
        List<TestTemplateInvocationContext> invocations = new ArrayList<>();
        boolean runnable = false;
        for (Cell cell : cells) {
            if (differential && !referenceNative(cell)) {
                // Listed as a JUnit skip with its reason, so it is never mistaken for a pass (T-M4-11).
                String reason = "NOT DIFFERENTIAL: " + cell.platform() + "@" + cell.mc()
                        + " is not the reference driver's native row";
                System.err.println("PROOFSTONE " + reason + " (not run, not a pass)");
                invocations.add(new NotRunInvocation(cell.platform() + "@" + cell.mc(), reason));
                continue;
            }
            runnable = true;
            for (String driverId : CellInvocation.driversFor(differential)) {
                for (int repetition = 1; repetition <= repeats; repetition++) {
                    invocations.add(new CellInvocation(cell, config, isolation, catalog, driverId, repetition,
                            repeats));
                }
            }
        }
        if (!plan.topologies().isEmpty() && (differential || repeats > 1)) {
            // One ledger and one driver pair per server; a topology has several, so these have no defined verdict yet.
            throw new IllegalStateException(type.getName() + " plans topology cells " + plan.topologies().stream()
                    .map(TopologyCell::identity).toList() + ": -D" + DifferentialRun.PROPERTY + " and -D"
                    + FlakeDetector.REPEAT + " do not run on topology cells (ADR-0016); run them without it");
        }
        for (TopologyCell topology : plan.topologies()) {
            runnable = true;
            invocations.add(new TopologyInvocation(topology, config, isolation, catalog));
        }
        if (!runnable) {
            throw new IllegalStateException(type.getName() + " has no cell for a differential run: the reference driver "
                    + DifferentialRun.REFERENCE + " must be native on a selected cell. Select its row, for example "
                    + "-Dproofstone.cells=paper@26.1.2.");
        }
        return invocations.stream();
    }

    /** Whether the reference driver speaks this cell's row natively: the differential run is only defined there. */
    private static boolean referenceNative(Cell cell) {
        ClientDriver reference = CellInvocation.findDriver(DifferentialRun.REFERENCE).orElseThrow(
                () -> new IllegalStateException("-D" + DifferentialRun.PROPERTY + " needs the reference driver "
                        + DifferentialRun.REFERENCE + " on the classpath"));
        return DifferentialRun.referenceNative(cell.mc(), reference.protocolVersion());
    }

    /**
     * The cell decision for one test class, kept free of JUnit state so it can be unit tested without Docker.
     * A selector replaces the {@link Matrix} versions of the platforms it names; other platforms are not run.
     * Zero cells is an error, not a pass: JUnit would otherwise fail with an opaque precondition error.
     */
    /** The cells of one test class: single-server cells and topology cells (ADR-0016). */
    record Plan(List<Cell> cells, List<TopologyCell> topologies) {
    }

    /** The backend platform of a topology a {@link Matrix} plans; the only backend row family in M7 (ADR-0016 section 1). */
    static final String TOPOLOGY_BACKEND = "paper";

    static Plan plan(Class<?> type, List<Matrix> matrices, CellSelector selector, CellPlanner planner,
            Scope scope, VersionFamilies families) {
        List<Cell> cells = new ArrayList<>();
        List<TopologyCell> topologies = new ArrayList<>();
        List<String> backends = AnnotationSupport.findAnnotation(type, Topology.Backends.class)
                .map(annotation -> List.of(annotation.value())).orElse(TopologyCell.DEFAULT_BACKENDS);
        List<String> proxies = new ArrayList<>();
        for (Matrix matrix : matrices) {
            String platform = matrix.platform().name().toLowerCase(Locale.ROOT);
            if (planner.isProxy(platform)) {
                proxies.add(platform);
                topologies.addAll(planTopologies(type, platform, matrix, selector, planner, scope, backends));
                continue;
            }
            List<String> requested = selector.isSet() ? selector.versions(platform) : List.of(matrix.versions());
            if (requested.isEmpty()) {
                System.err.println("PROOFSTONE NOT SELECTED (not run, not a pass): " + type.getName() + " declares "
                        + platform + ", which " + CELLS_SOURCE + " does not name");
                continue;
            }
            cells.addAll(planner.plan(platform, requested, scope, families));
        }
        // A topology entry whose proxy no @Matrix names is said out loud, never dropped silently.
        for (CellSelector.Topology topology : selector.topologies()) {
            if (!proxies.contains(topology.proxy())) {
                System.err.println("PROOFSTONE NOT SELECTED (not run, not a pass): " + type.getName() + " has no @Matrix "
                        + "that plans the topology cell " + topology.entry());
            }
        }
        if (cells.isEmpty() && topologies.isEmpty()) {
            throw new IllegalStateException(type.getName() + " has no cell to run: " + CELLS_SOURCE
                    + " names none of its @Matrix platforms. Name one, or run only the classes that match.");
        }
        return new Plan(List.copyOf(cells), List.copyOf(topologies));
    }

    /**
     * The topology cells a proxy {@link Matrix} plans: without a selector, its versions paired with
     * {@code paper@latest}; with one, the selector's entries for this proxy replace them. A malformed backend name in
     * {@link Topology.Backends} fails the class before anything starts.
     */
    private static List<TopologyCell> planTopologies(Class<?> type, String proxy, Matrix matrix, CellSelector selector,
            CellPlanner planner, Scope scope, List<String> backends) {
        if (!selector.isSet()) {
            return planner.planTopologies(proxy, List.of(matrix.versions()), TOPOLOGY_BACKEND, List.of("latest"), scope,
                    backends);
        }
        List<TopologyCell> planned = new ArrayList<>();
        for (CellSelector.Topology entry : selector.topologies()) {
            if (entry.proxy().equals(proxy)) {
                planned.addAll(planner.planTopologies(proxy, List.of(entry.proxyVersion()), entry.backend(),
                        List.of(entry.backendVersion()), scope, backends));
            }
        }
        if (planned.isEmpty()) {
            System.err.println("PROOFSTONE NOT SELECTED (not run, not a pass): " + type.getName() + " declares "
                    + proxy + ", which " + CELLS_SOURCE + " does not name");
        }
        return planned;
    }

    /** The system property wins over the environment variable of the same meaning (test-api spec §1). */
    private static String setting(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        return value;
    }

    private static VersionsCatalog catalog() {
        String root = System.getProperty(REPO_ROOT);
        if (root == null) {
            throw new IllegalStateException("system property " + REPO_ROOT + " is not set: it points at the "
                    + "directory holding versions.yml");
        }
        Path base = Path.of(root);
        return VersionsCatalog.load(base.resolve("versions.yml"),
                base.resolve("proofstone-protocol/schemas/versions.schema.json"));
    }
}
