package io.github.h7kz.proofstone.examples.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import io.github.h7kz.proofstone.examples.smoke.nested.ProvokesServerError;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.launcher.listeners.TestExecutionSummary.Failure;

/**
 * T-M2-08 on a real server: a test that provokes a server ERROR fails, with the log record in the message. A failing
 * test cannot assert on itself, so this test runs {@link ProvokesServerError} through the JUnit launcher and reads
 * the outcome. It is not a {@code @CellTest}, and a skipped nested test (no Docker, no EULA) fails it here: the
 * summary must hold one success and two failures.
 */
class ErrorLogFailsTheTestE2E {

    @Test
    void anErrorLoggedDuringATestFailsItWithTheRecordAndDoesNotLeakIntoTheNextTest() {
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener summary = new SummaryGeneratingListener();
        launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass(ProvokesServerError.class))
                .build(), new TestExecutionListener[] {summary});

        TestExecutionSummary result = summary.getSummary();
        List<Failure> failures = result.getFailures();
        assertThat(result.getTestsSkippedCount()).as("nested tests must not be skipped").isZero();
        assertThat(failures.stream().map(f -> f.getTestIdentifier().getDisplayName() + ": " + f.getException())
                .toList()).as("failures of the nested scenario").hasSize(2);
        Failure jul = failureOf(failures, "logsAnErrorAndChecksNothingItself");
        assertThat(jul.getException()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("unexpected WARN or worse")
                .hasMessageContaining("fixture provoked error: " + ProvokesServerError.MARKER)
                .hasMessageContaining("IllegalStateException: fixture error");
        // Once: the record reaches the agent through JUL and Log4j, and the buffer must not list it twice.
        assertThat(occurrences(jul.getException().getMessage(), "fixture provoked error: ")).isEqualTo(1);
        // Logged by Log4j directly, as the server's own code does: the JUL handler never sees it.
        Failure log4j = failureOf(failures, "logsAnErrorThroughLog4jAndChecksNothingItself");
        assertThat(log4j.getException()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("fixture log4j error: " + ProvokesServerError.MARKER)
                .hasMessageContaining("IllegalStateException: fixture error");
        assertThat(occurrences(log4j.getException().getMessage(), "fixture log4j error: ")).isEqualTo(1);
        assertThat(result.getTestsSucceededCount()).as("the clean third test passes").isEqualTo(1);
    }

    private static Failure failureOf(List<Failure> failures, String method) {
        return failures.stream().filter(f -> f.getTestIdentifier().getUniqueId().contains(method)).findFirst()
                .orElseThrow(() -> new AssertionError("no failure for " + method + " in " + failures));
    }

    private static int occurrences(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
