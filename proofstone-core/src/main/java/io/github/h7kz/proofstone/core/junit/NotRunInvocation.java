package io.github.h7kz.proofstone.core.junit;

import java.util.List;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * A cell that the run does not run, for example a bridged row in a differential run. It is a disabled invocation, so
 * JUnit reports it as a skipped testcase with its reason (T-M4-11, robustness X1). It never runs a test and never counts
 * as a pass.
 */
final class NotRunInvocation implements TestTemplateInvocationContext, ExecutionCondition {

    private final String cell;
    private final String reason;

    NotRunInvocation(String cell, String reason) {
        this.cell = cell;
        this.reason = reason;
    }

    /** The reason is in the name, because JUnit's XML skip element carries no message for a skipped invocation. */
    @Override
    public String getDisplayName(int invocationIndex) {
        return cell + " (not run: " + reason + ")";
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of(this);
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return ConditionEvaluationResult.disabled(reason);
    }
}
