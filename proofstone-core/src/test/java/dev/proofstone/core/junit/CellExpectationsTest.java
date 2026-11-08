package dev.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.api.Agent;
import dev.proofstone.api.Cell;
import dev.proofstone.api.CellExpectation;
import dev.proofstone.api.Expect;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The {@link Expect} rules of one cell (test-api spec section 9), without a server or JUnit. */
class CellExpectationsTest {

    private static final Cell CELL = new CellView(
            new dev.proofstone.core.cell.Cell("paper", "26.3", 1, "", "java25", null, "", "", List.of()), false, Set.of());

    public static final class Holds implements CellExpectation {
        @Override
        public void check(Cell cell, Agent agent) {
        }
    }

    public static final class WrongMapping implements CellExpectation {
        @Override
        public void check(Cell cell, Agent agent) {
            throw new AssertionError("expected NMS_v1_21_R5, the server reports NMS_v1_21_R4");
        }
    }

    public static final class Throws implements CellExpectation {
        @Override
        public void check(Cell cell, Agent agent) {
            throw new IllegalStateException("agent did not answer");
        }
    }

    public static final class Hidden implements CellExpectation {
        private Hidden() {
        }

        @Override
        public void check(Cell cell, Agent agent) {
        }
    }

    @Expect(Holds.class)
    void holdsOnThisCell() {
    }

    @Expect(WrongMapping.class)
    void wrongMapping() {
    }

    @Expect(Throws.class)
    void throwing() {
    }

    @Expect(Hidden.class)
    void cannotBeCreated() {
    }

    @Expect(Holds.class)
    static class ClassLevelHolds {
        void method() {
        }
    }

    @Expect(WrongMapping.class)
    static class ClassLevelWrong {
        @Expect(Holds.class)
        void method() {
        }
    }

    private static Method method(String name) {
        for (Method method : CellExpectationsTest.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new IllegalArgumentException(name);
    }

    @Test
    void anExpectationThatHoldsGivesNoFailure() {
        assertThat(CellExpectations.check(CellExpectationsTest.class, method("holdsOnThisCell"), CELL, null)).isNull();
    }

    @Test
    void aFailingExpectationIsAFailWithItsMessageAndTheCell() {
        Throwable failure = CellExpectations.check(CellExpectationsTest.class, method("wrongMapping"), CELL, null);

        assertThat(failure).isInstanceOf(AssertionError.class);
        assertThat(failure.getMessage()).startsWith("FAIL expect ")
                .contains("paper@26.3").contains("expected NMS_v1_21_R5, the server reports NMS_v1_21_R4");
    }

    @Test
    void anExpectationThatThrowsIsAFailWithItsCause() {
        Throwable failure = CellExpectations.check(CellExpectationsTest.class, method("throwing"), CELL, null);

        assertThat(failure.getMessage()).startsWith("FAIL expect ").contains("agent did not answer");
        assertThat(failure.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anExpectationThatCannotBeCreatedIsAFailNotAPass() {
        Throwable failure = CellExpectations.check(CellExpectationsTest.class, method("cannotBeCreated"), CELL, null);

        assertThat(failure.getMessage()).startsWith("FAIL expect ").contains("Hidden");
    }

    @Test
    void theClassLevelExpectationIsCheckedToo() {
        assertThat(CellExpectations.check(ClassLevelHolds.class, method("holdsOnThisCell"), CELL, null)).isNull();
        assertThat(CellExpectations.check(ClassLevelWrong.class, ClassLevelWrong.class.getDeclaredMethods()[0], CELL,
                null)).isNotNull();
    }
}
