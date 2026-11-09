package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;

/** A cell the run does not run is a disabled invocation with its reason, never an enabled one (T-M4-11). */
class NotRunInvocationTest {

    @Test
    void itIsDisabledWithItsReasonAndNamesTheCell() {
        NotRunInvocation invocation = new NotRunInvocation("paper@26.3", "NOT DIFFERENTIAL: paper@26.3 is not native");

        ConditionEvaluationResult result = invocation.evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isTrue();
        assertThat(result.getReason().orElseThrow()).contains("NOT DIFFERENTIAL").contains("paper@26.3");
        assertThat(invocation.getDisplayName(0)).isEqualTo("paper@26.3 (not run: NOT DIFFERENTIAL: paper@26.3 is not native)");
    }
}
