package dev.proofstone.core.junit;

import dev.proofstone.api.Agent;
import dev.proofstone.api.Cell;
import dev.proofstone.api.CellExpectation;
import dev.proofstone.api.Expect;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks the {@link Expect} declarations of a test on one cell (test-api spec section 9, robustness R7). A class-level
 * declaration runs before a method-level one. Every expectation runs, even after one fails, so the report names all of
 * them.
 */
final class CellExpectations {

    private CellExpectations() {
    }

    /**
     * Checks every expectation of the test on this cell.
     *
     * @return the first failure, a {@code FAIL}-prefixed {@link AssertionError} with the others suppressed, or
     *         {@code null} when every expectation holds
     */
    static Throwable check(Class<?> testClass, Method test, Cell cell, Agent agent) {
        AssertionError failure = null;
        for (Class<? extends CellExpectation> type : declared(testClass, test)) {
            try {
                instantiate(type).check(cell, agent);
            } catch (RuntimeException | AssertionError e) {
                // A cell that cannot be checked is a failure too: "no expectation failed" must not come from a missing one.
                AssertionError verdict = new AssertionError("FAIL expect " + type.getName() + " on "
                        + cell.platform() + "@" + cell.version() + ": " + detail(e), e);
                if (failure == null) {
                    failure = verdict;
                } else {
                    failure.addSuppressed(verdict);
                }
            }
        }
        return failure;
    }

    /** The expectation classes of the class and of the method, class first. */
    private static List<Class<? extends CellExpectation>> declared(Class<?> testClass, Method test) {
        List<Class<? extends CellExpectation>> types = new ArrayList<>();
        Expect onClass = testClass.getAnnotation(Expect.class);
        if (onClass != null) {
            types.add(onClass.value());
        }
        Expect onMethod = test.getAnnotation(Expect.class);
        if (onMethod != null) {
            types.add(onMethod.value());
        }
        return types;
    }

    private static CellExpectation instantiate(Class<? extends CellExpectation> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot create the expectation " + type.getName()
                    + ": it needs a public no-argument constructor", e);
        }
    }

    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getName() : message;
    }
}
