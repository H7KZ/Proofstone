package dev.proofstone.core.junit;

import dev.proofstone.api.CellTest;
import dev.proofstone.api.Isolation;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.Proofstone;
import dev.proofstone.core.cell.Cell;
import dev.proofstone.core.cell.CellPlanner;
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
        CellPlanner planner = new CellPlanner(catalog());
        List<Cell> cells = new ArrayList<>();
        for (Matrix matrix : matrices) {
            cells.addAll(planner.plan(matrix.platform().name().toLowerCase(Locale.ROOT), List.of(matrix.versions())));
        }
        Isolation.Mode isolation = AnnotationSupport.findAnnotation(type, Isolation.class).map(Isolation::value)
                .orElse(Isolation.Mode.PER_CLASS);
        return cells.stream().map(cell -> new CellInvocation(cell, config, isolation));
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
