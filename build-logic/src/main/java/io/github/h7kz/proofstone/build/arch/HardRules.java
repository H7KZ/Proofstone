package io.github.h7kz.proofstone.build.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The AGENTS.md §3 hard rules that bytecode can check. Applied to every host module by the
 * {@code archTest} task. Decision: ADR-0007 (no sleeps, no retries), ADR-0005 (no bind mounts).
 */
public final class HardRules {

    /** The only class allowed to wait on wall-clock time. */
    static final String AWAIT = "io.github.h7kz.proofstone.core.await.Await";

    /** The only package allowed to repeat a test: the flake detector. */
    static final String FLAKE_PACKAGE = "io.github.h7kz.proofstone.conformance.flake..";

    /** Testcontainers' bind mode. Only bind mounts need it, so using it at all is forbidden. */
    static final String BIND_MODE = "org.testcontainers.containers.BindMode";

    public static final ArchRule NO_SLEEP = noClasses()
            .that().doNotHaveFullyQualifiedName(AWAIT)
            .should().callMethod(Thread.class, "sleep", long.class)
            .orShould().callMethod(Thread.class, "sleep", long.class, int.class)
            .orShould().callMethod(Thread.class, "sleep", Duration.class)
            .orShould().callMethod(TimeUnit.class, "sleep", long.class)
            .because("waits go through Await.await(predicate, timeout), not fixed delays (ADR-0007)");

    public static final ArchRule NO_BIND_MOUNTS = noClasses()
            .should().dependOnClassesThat().haveFullyQualifiedName(BIND_MODE)
            .because("test inputs are copied into containers, never bind-mounted (ADR-0005)");

    public static final ArchRule NO_RETRIES = noClasses()
            .that().resideOutsideOfPackage(FLAKE_PACKAGE)
            .should().dependOnClassesThat().haveFullyQualifiedName("org.junit.jupiter.api.RepeatedTest")
            .orShould().dependOnClassesThat().haveSimpleNameContaining("Retry")
            .because("retry turns red into green; only the flake detector may repeat (ADR-0007)");

    private static final List<ArchRule> RULES = List.of(NO_SLEEP, NO_BIND_MOUNTS, NO_RETRIES);

    private HardRules() {
    }

    /**
     * Checks every rule against the classes under the given directories.
     *
     * @return one message per violated rule; empty when the directories pass
     */
    public static List<String> check(Collection<Path> classesDirs) {
        Path[] existing = classesDirs.stream().filter(Files::isDirectory).toArray(Path[]::new);
        JavaClasses classes = new ClassFileImporter().importPaths(existing);
        List<String> violations = new ArrayList<>();
        // A module with no classes yet (M0 skeleton) has nothing to check. Its rules are not vacuous,
        // because each one runs again as soon as the module has classes.
        if (classes.isEmpty()) {
            return violations;
        }
        for (ArchRule rule : RULES) {
            EvaluationResult result = rule.evaluate(classes);
            if (result.hasViolation()) {
                violations.add(result.getFailureReport().toString());
            }
        }
        return violations;
    }
}
