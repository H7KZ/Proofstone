package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.core.verdict.CellResult;
import io.github.h7kz.proofstone.core.verdict.SkipReason;
import io.github.h7kz.proofstone.core.verdict.Verdict;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/** T-M9-08: a test that aborts itself with a reason is reported SKIPPED with that reason, as JUnit reports it, never FAIL. */
class AbortedSkipTest {

    @Test
    void anAbortIsASkippedVerdictCarryingItsOwnReason() {
        CellResult result = CellInvocation.abortedResult("ConformanceGeyserTest.skippedForms", "geyser@2.12.1+paper@26.3",
                new TestAbortedException("forms are not in Bedrock driver v1"));

        assertThat(result.verdict()).isEqualTo(Verdict.SKIPPED);
        assertThat(result.skipReason()).isEqualTo(SkipReason.TEST_ABORTED);
        assertThat(result.reason()).contains("forms are not in Bedrock driver v1");
    }

    @Test
    void anAbortWithNoMessageStillNamesItself() {
        CellResult result = CellInvocation.abortedResult("T.t", "paper@26.3", new TestAbortedException());

        assertThat(result.reason()).isNotBlank();
    }
}
