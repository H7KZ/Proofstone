package dev.proofstone.examples.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import dev.proofstone.examples.smoke.nested.ProvokesServerError;
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
 * summary must hold one success and one failure.
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
        assertThat(failures).as("failures of the nested scenario").hasSize(1);
        assertThat(failures.get(0).getTestIdentifier().getUniqueId()).contains("logsAnErrorAndChecksNothingItself");
        assertThat(failures.get(0).getException()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("unexpected WARN or worse")
                .hasMessageContaining("fixture provoked error: " + ProvokesServerError.MARKER)
                .hasMessageContaining("IllegalStateException: fixture error");
        assertThat(result.getTestsSucceededCount()).as("the clean second test passes").isEqualTo(1);
    }
}
