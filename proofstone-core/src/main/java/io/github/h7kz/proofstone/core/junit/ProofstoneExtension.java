package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Isolation;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Topology;
import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.core.cell.CellSelector;
import io.github.h7kz.proofstone.core.cell.GeyserCell;
import io.github.h7kz.proofstone.core.cell.LoaderCell;
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
        List<TestTemplateInvocationContext> invocations = new ArrayList<>(notSelectedInvocations(plan));
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
        if (!plan.loaders().isEmpty() && (differential || repeats > 1)) {
            // The reference driver has no loader row it speaks natively (ADR-0017), and a loader run is judged once per cell.
            throw new IllegalStateException(type.getName() + " plans loader cells " + plan.loaders().stream()
                    .map(LoaderCell::identity).toList() + ": -D" + DifferentialRun.PROPERTY + " and -D"
                    + FlakeDetector.REPEAT + " do not run on loader cells (ADR-0017); run them without it");
        }
        for (LoaderCell loader : plan.loaders()) {
            runnable = true;
            invocations.add(new CellInvocation(asCell(loader), loader, config, isolation, catalog,
                    CellInvocation.configuredDriver(), 1, 1));
        }
        for (GeyserCell geyser : plan.geysers()) {
            // Refused before anything starts, naming the cell (ADR-0018 sections 3 and 9); never a skip, never a pass.
            String driver = CellInvocation.configuredDriver();
            boolean bridged = CellInvocation.findDriver(driver).map(d -> !d.protocolVersion().equals(geyser.backend().mc()))
                    .orElse(false);
            GeyserRun.refusal(geyser.identity(), differential, repeats, driver, CellInvocation.primaryDriver(), bridged)
                    .ifPresent(reason -> {
                        throw new IllegalStateException(type.getName() + ": " + reason);
                    });
            runnable = true;
            invocations.add(new CellInvocation(geyser, config, isolation, catalog));
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
    /**
     * The cells of one test class: single-server cells, topology cells (ADR-0016), loader cells (ADR-0017) and Geyser cells
     * (ADR-0018).
     */
    record Plan(List<Cell> cells, List<TopologyCell> topologies, List<LoaderCell> loaders, List<GeyserCell> geysers,
            List<NotSelected> notSelected) {

        Plan(List<Cell> cells, List<TopologyCell> topologies, List<LoaderCell> loaders, List<GeyserCell> geysers) {
            this(cells, topologies, loaders, geysers, List.of());
        }

        Plan(List<Cell> cells, List<TopologyCell> topologies, List<LoaderCell> loaders) {
            this(cells, topologies, loaders, List.of(), List.of());
        }
    }

    /**
     * A declared row the selector left out (T-M4-13, robustness X1): reported as a JUnit skip with its reason, so it is never
     * mistaken for a pass, as well as printed to stderr.
     *
     * @param what the platform or cell entry that was not selected
     * @param reason why, starting with {@code NOT SELECTED}
     */
    record NotSelected(String what, String reason) {
    }

    /** One disabled invocation per {@link NotSelected} row: JUnit lists each as a skip with its reason. */
    static List<TestTemplateInvocationContext> notSelectedInvocations(Plan plan) {
        return plan.notSelected().stream()
                .<TestTemplateInvocationContext>map(row -> new NotRunInvocation(row.what(), row.reason())).toList();
    }

    /** Says a row is not selected on stderr and records it for the plan, so JUnit can report it as a skip. */
    private static void notSelected(List<NotSelected> rows, Class<?> type, String what, String text) {
        System.err.println("PROOFSTONE NOT SELECTED (not run, not a pass): " + type.getName() + " " + text);
        rows.add(new NotSelected(what, "NOT SELECTED: " + text));
    }

    /**
     * A loader cell as the {@link Cell} the lifecycle names and reports it by (platform, version, pins the bundle records).
     * The host still boots from the {@link LoaderCell}, which keeps the loader pins.
     */
    static Cell asCell(LoaderCell loader) {
        return new Cell(loader.loader(), loader.mc(), null, loader.sha256(), loader.java(), loader.status(),
                loader.hostImage(), loader.hostDigest(), loader.notes());
    }

    /** The backend platform of a topology a {@link Matrix} plans; the only backend row family in M7 (ADR-0016 section 1). */
    static final String TOPOLOGY_BACKEND = "paper";

    static Plan plan(Class<?> type, List<Matrix> matrices, CellSelector selector, CellPlanner planner,
            Scope scope, VersionFamilies families) {
        List<Cell> cells = new ArrayList<>();
        List<TopologyCell> topologies = new ArrayList<>();
        List<LoaderCell> loaders = new ArrayList<>();
        List<GeyserCell> geysers = new ArrayList<>();
        List<NotSelected> notSelected = new ArrayList<>();
        boolean declaresGeyser = false;
        List<String> declaredLoaders = new ArrayList<>();
        List<String> backends = AnnotationSupport.findAnnotation(type, Topology.Backends.class)
                .map(annotation -> List.of(annotation.value())).orElse(TopologyCell.DEFAULT_BACKENDS);
        List<String> proxies = new ArrayList<>();
        for (Matrix matrix : matrices) {
            String platform = matrix.platform().name().toLowerCase(Locale.ROOT);
            if (matrix.platform() == Platform.GEYSER) {
                declaresGeyser = true;
                GeyserRun.declarationRefusal(type.getName(), AnnotationSupport.findAnnotation(type, Proofstone.class)
                        .orElse(null)).ifPresent(reason -> {
                            throw new IllegalStateException(reason);
                        });
                geysers.addAll(planGeysers(type, selector, planner, scope, notSelected));
                continue;
            }
            if (planner.isProxy(platform)) {
                proxies.add(platform);
                topologies.addAll(planTopologies(type, platform, matrix, selector, planner, scope, backends, notSelected));
                continue;
            }
            if (planner.isLoader(platform)) {
                declaredLoaders.add(platform);
                loaders.addAll(planLoaders(type, platform, selector, planner, scope, notSelected));
                continue;
            }
            List<String> requested = selector.isSet() ? selector.versions(platform) : List.of(matrix.versions());
            if (requested.isEmpty()) {
                notSelected(notSelected, type, platform, "declares " + platform + ", which " + CELLS_SOURCE
                        + " does not name");
                continue;
            }
            cells.addAll(planner.plan(platform, requested, scope, families));
        }
        // A topology entry whose proxy no @Matrix names is said out loud, never dropped silently.
        for (CellSelector.Topology topology : selector.topologies()) {
            if (!proxies.contains(topology.proxy())) {
                notSelected(notSelected, type, topology.entry(), "has no @Matrix that plans the topology cell "
                        + topology.entry());
            }
        }
        for (CellSelector.LoaderEntry entry : selector.loaders()) {
            if (!declaredLoaders.contains(entry.loader())) {
                notSelected(notSelected, type, entry.entry(), "has no @Matrix that plans the loader cell "
                        + entry.entry());
            }
        }
        // A Geyser entry no @Matrix plans is said out loud, never dropped silently.
        for (CellSelector.GeyserEntry entry : selector.geysers()) {
            if (!declaresGeyser) {
                notSelected(notSelected, type, entry.entry(), "has no @Matrix that plans the Geyser cell "
                        + entry.entry());
            }
        }
        if (cells.isEmpty() && topologies.isEmpty() && loaders.isEmpty() && geysers.isEmpty()) {
            throw new IllegalStateException(type.getName() + " has no cell to run: " + CELLS_SOURCE
                    + " names none of its @Matrix platforms. Name one, or run only the classes that match.");
        }
        return new Plan(List.copyOf(cells), List.copyOf(topologies), List.copyOf(loaders), List.copyOf(geysers),
                List.copyOf(notSelected));
    }

    /**
     * The Geyser cells a {@code Platform.GEYSER} {@link Matrix} plans (ADR-0018 section 9). A Geyser row is experimental, so
     * it runs only when the selector names it, for example {@code geyser@2.12.1+paper@26.3}: without one the matrix plans
     * nothing and says so, so an experimental cell never boots in a default run and is never skipped silently.
     */
    private static List<GeyserCell> planGeysers(Class<?> type, CellSelector selector, CellPlanner planner, Scope scope,
            List<NotSelected> notSelected) {
        List<GeyserCell> planned = new ArrayList<>();
        for (CellSelector.GeyserEntry entry : selector.geysers()) {
            planned.addAll(planner.planGeysers(List.of(entry.version()), entry.backend(), List.of(entry.backendVersion()),
                    scope));
        }
        if (planned.isEmpty()) {
            notSelected(notSelected, type, "geyser", "declares geyser, an experimental row that runs only when "
                    + CELLS_SOURCE + " names it, for example geyser@<version>+paper@<mc>");
        }
        return planned;
    }

    /**
     * The loader cells a loader {@link Matrix} plans. A loader row is experimental (ADR-0017), so it runs only when the
     * selector names it: without one the matrix plans nothing, and says so, so an experimental loader never boots in a
     * default run and is never skipped silently.
     */
    private static List<LoaderCell> planLoaders(Class<?> type, String loader, CellSelector selector, CellPlanner planner,
            Scope scope, List<NotSelected> notSelected) {
        List<LoaderCell> planned = new ArrayList<>();
        for (CellSelector.LoaderEntry entry : selector.loaders()) {
            if (entry.loader().equals(loader)) {
                planned.addAll(planner.planLoaders(loader, List.of(entry.version()), scope));
            }
        }
        if (planned.isEmpty()) {
            notSelected(notSelected, type, loader, "declares " + loader + ", an experimental loader row that runs only when "
                    + CELLS_SOURCE + " names it, for example " + loader + "@<mc>");
        }
        return planned;
    }

    /**
     * The topology cells a proxy {@link Matrix} plans: without a selector, its versions paired with
     * {@code paper@latest}; with one, the selector's entries for this proxy replace them. A malformed backend name in
     * {@link Topology.Backends} fails the class before anything starts.
     */
    private static List<TopologyCell> planTopologies(Class<?> type, String proxy, Matrix matrix, CellSelector selector,
            CellPlanner planner, Scope scope, List<String> backends, List<NotSelected> notSelected) {
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
            notSelected(notSelected, type, proxy, "declares " + proxy + ", which " + CELLS_SOURCE + " does not name");
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
