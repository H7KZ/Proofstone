package dev.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.proofstone.core.verdict.Attempt;
import dev.proofstone.core.verdict.ConformanceResult;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The flake detector's switch, its run ledger and the aggregate verdict (self-tests section 4), without Docker. */
class FlakeDetectorTest {

    private static final String CELL = "paper@1.8.8";

    static class Samples {
        void plain() {
        }
    }

    @AfterEach
    void clearProperty() {
        System.clearProperty(FlakeDetector.REPEAT);
    }

    private static Method plain() throws NoSuchMethodException {
        return Samples.class.getDeclaredMethod("plain");
    }

    @Test
    void theDetectorIsOffWhenThePropertyIsUnset() {
        assertThat(FlakeDetector.repeats()).isEqualTo(1);
    }

    @Test
    void anIntegerOfAtLeastThreeIsTheRepeatCount() {
        System.setProperty(FlakeDetector.REPEAT, "5");
        assertThat(FlakeDetector.repeats()).isEqualTo(5);
        System.setProperty(FlakeDetector.REPEAT, "3");
        assertThat(FlakeDetector.repeats()).isEqualTo(3);
    }

    @Test
    void aCountBelowThreeIsAnErrorNotADefault() {
        for (String value : List.of("2", "1", "0", "-4")) {
            System.setProperty(FlakeDetector.REPEAT, value);
            assertThatThrownBy(FlakeDetector::repeats).as(value).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must be at least 3");
        }
    }

    @Test
    void aValueThatIsNotAnIntegerIsAnError() {
        for (String value : List.of("", "five", "3.0", "yes")) {
            System.setProperty(FlakeDetector.REPEAT, value);
            assertThatThrownBy(FlakeDetector::repeats).as(value).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must be an integer");
        }
    }

    @Test
    void theLedgerGivesNothingUntilTheLastRunAndThenAllRunsInOrder() {
        RepeatLedger ledger = new RepeatLedger(3);

        assertThat(ledger.record(1, Attempt.pass())).isNull();
        assertThat(ledger.record(2, Attempt.fail("boom"))).isNull();
        List<Attempt> runs = ledger.record(3, Attempt.pass());

        assertThat(runs).containsExactly(Attempt.pass(), Attempt.fail("boom"), Attempt.pass());
    }

    @Test
    void aLostRunIsAnErrorNotAVerdict() {
        RepeatLedger ledger = new RepeatLedger(3);
        ledger.record(1, Attempt.pass());

        assertThatThrownBy(() -> ledger.record(3, Attempt.pass())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("a run was lost");
    }

    @Test
    void aChangeOfVerdictAcrossTheRunsIsFlaky() throws NoSuchMethodException {
        List<Attempt> runs = List.of(Attempt.pass(), Attempt.fail("draw 2 failed"), Attempt.pass());

        AssertionError failure = VerdictGate.judgeRepeats(plain(), CELL, false, runs, ConformanceResult.pass(CELL));

        assertThat(failure).isNotNull();
        assertThat(failure.getMessage()).startsWith("FLAKY Samples.plain on paper@1.8.8 over 3 runs")
                .contains("run 2: draw 2 failed");
    }

    @Test
    void theRunsAgreeingIsPass() throws NoSuchMethodException {
        List<Attempt> runs = List.of(Attempt.pass(), Attempt.pass(), Attempt.pass());

        assertThat(VerdictGate.judgeRepeats(plain(), CELL, false, runs, ConformanceResult.pass(CELL))).isNull();
    }

    @Test
    void everyRunFailingIsNotReportedAgainBecauseJUnitAlreadyReportsEachRun() throws NoSuchMethodException {
        List<Attempt> runs = List.of(Attempt.fail("a"), Attempt.fail("b"), Attempt.fail("c"));

        assertThat(VerdictGate.judgeRepeats(plain(), CELL, false, runs, ConformanceResult.pass(CELL))).isNull();
    }

    @Test
    void allRunsPassingInAControlRunIsVacuous() throws NoSuchMethodException {
        List<Attempt> runs = List.of(Attempt.pass(), Attempt.pass(), Attempt.pass());

        AssertionError failure = VerdictGate.judgeRepeats(plain(), CELL, true, runs, ConformanceResult.pass(CELL));

        assertThat(failure).isNotNull();
        assertThat(failure.getMessage()).startsWith("VACUOUS Samples.plain on paper@1.8.8 over 3 runs");
    }

    @Test
    void aMixedControlRunIsFlakyBecauseAFlakeOutranksVacuity() throws NoSuchMethodException {
        // VerdictEngine precedence: FLAKY before VACUOUS, so a control run whose runs differ reports the flake.
        List<Attempt> runs = List.of(Attempt.pass(), Attempt.fail("draw 2 failed"), Attempt.pass());

        AssertionError failure = VerdictGate.judgeRepeats(plain(), CELL, true, runs, ConformanceResult.pass(CELL));

        assertThat(failure).isNotNull();
        assertThat(failure.getMessage()).startsWith("FLAKY Samples.plain");
    }
}
