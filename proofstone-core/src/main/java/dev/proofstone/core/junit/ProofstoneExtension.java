package dev.proofstone.core.junit;

import dev.proofstone.api.CellTest;
import dev.proofstone.api.Isolation;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.Proofstone;
import dev.proofstone.core.cell.Cell;
import dev.proofstone.core.cell.CellPlanner;
import dev.proofstone.core.cell.CellSelector;
import dev.proofstone.core.cell.Scope;
import dev.proofstone.core.cell.VersionFamilies;
import dev.proofstone.core.driver.ClientDriver;
import dev.proofstone.core.host.Eula;
import dev.proofstone.core.versions.VersionsCatalog;
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
        List<Cell> cells = planCells(type, matrices, selector, planner, scope, families);
        Isolation.Mode isolation = AnnotationSupport.findAnnotation(type, Isolation.class).map(Isolation::value)
                .orElse(Isolation.Mode.PER_CLASS);
        boolean differential = DifferentialRun.enabled();
        List<TestTemplateInvocationContext> invocations = new ArrayList<>();
        for (Cell cell : cells) {
            if (differential && !referenceNative(cell)) {
                System.err.println("PROOFSTONE NOT DIFFERENTIAL (not run, not a pass): " + cell.platform() + "@"
                        + cell.mc() + " is not the reference driver's native row");
                continue;
            }
            for (String driverId : CellInvocation.driversFor(differential)) {
                invocations.add(new CellInvocation(cell, config, isolation, catalog, driverId));
            }
        }
        if (invocations.isEmpty()) {
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
    static List<Cell> planCells(Class<?> type, List<Matrix> matrices, CellSelector selector, CellPlanner planner,
            Scope scope, VersionFamilies families) {
        List<Cell> cells = new ArrayList<>();
        for (Matrix matrix : matrices) {
            String platform = matrix.platform().name().toLowerCase(Locale.ROOT);
            List<String> requested = selector.isSet() ? selector.versions(platform) : List.of(matrix.versions());
            if (requested.isEmpty()) {
                System.err.println("PROOFSTONE NOT SELECTED (not run, not a pass): " + type.getName() + " declares "
                        + platform + ", which " + CELLS_SOURCE + " does not name");
                continue;
            }
            cells.addAll(planner.plan(platform, requested, scope, families));
        }
        if (cells.isEmpty()) {
            throw new IllegalStateException(type.getName() + " has no cell to run: " + CELLS_SOURCE
                    + " names none of its @Matrix platforms. Name one, or run only the classes that match.");
        }
        return cells;
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
