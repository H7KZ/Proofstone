package dev.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.proofstone.api.ControlRun;
import dev.proofstone.core.verdict.Attempt;
import dev.proofstone.core.verdict.CellResult;
import dev.proofstone.core.verdict.ConformanceResult;
import dev.proofstone.core.verdict.Verdict;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The control-run verdict of a passing test (self-tests section 2), without JUnit or Docker. */
class VerdictGateTest {

    private static final String CELL = "paper@1.8.8";

    static class Samples {
        void plain() {
        }

        @ControlRun(value = ControlRun.Expectation.EXPECTED_PASS, reason = "vanilla unchanged when disabled")
        void expectedPass() {
        }

        @ControlRun(value = ControlRun.Expectation.EXPECTED_PASS, reason = " ")
        void blankReason() {
        }
    }

    @AfterEach
    void clearProperty() {
        System.clearProperty(VerdictGate.CONTROL_RUN);
    }

    private static Method method(String name) throws NoSuchMethodException {
        return Samples.class.getDeclaredMethod(name);
    }

    @Test
    void aPassInAControlRunIsVacuous() throws NoSuchMethodException {
        AssertionError failure = VerdictGate.judgePass(method("plain"), CELL, true, ConformanceResult.pass(CELL));

        assertThat(failure).isNotNull();
        assertThat(failure.getMessage()).startsWith("VACUOUS Samples.plain on paper@1.8.8");
    }

    @Test
    void aPassInANormalRunIsPass() throws NoSuchMethodException {
        assertThat(VerdictGate.judgePass(method("plain"), CELL, false, ConformanceResult.pass(CELL))).isNull();
    }

    @Test
    void anExpectedPassInAControlRunIsNotVacuous() throws NoSuchMethodException {
        assertThat(VerdictGate.judgePass(method("expectedPass"), CELL, true, ConformanceResult.pass(CELL))).isNull();
    }

    @Test
    void aBlankControlRunReasonIsAnErrorNotAPass() throws NoSuchMethodException {
        assertThatThrownBy(() -> VerdictGate.judgePass(method("blankReason"), CELL, true,
                ConformanceResult.pass(CELL)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("needs a reason");
    }

    @Test
    void aFailedCellConformanceMakesThePassingTestInvalid() throws NoSuchMethodException {
        ConformanceResult broken = new ConformanceResult(CELL, false, "list did not print");

        AssertionError failure = VerdictGate.judgeInvalid(method("plain"), CELL, broken);

        assertThat(failure).isNotNull();
        assertThat(failure.getMessage()).startsWith("INVALID Samples.plain on paper@1.8.8")
                .contains("list did not print");
        assertThat(VerdictGate.judgeInvalid(method("plain"), CELL, ConformanceResult.pass(CELL))).isNull();
    }

    @Test
    void aChangedServerRuleIsALeakNamingTheRule() throws NoSuchMethodException {
        List<String> before = List.of("random_tick_speed: 0");
        List<String> after = List.of("random_tick_speed: 3");

        AssertionError failure = VerdictGate.judgeLeak(method("plain"), CELL, before, after);

        assertThat(failure).isNotNull();
        assertThat(failure.getMessage()).startsWith("LEAK Samples.plain on paper@1.8.8")
                .contains("random_tick_speed: 0 -> random_tick_speed: 3");
        assertThat(VerdictGate.judgeLeak(method("plain"), CELL, before, before)).isNull();
    }

    @Test
    void theControlRunPropertyIsStrict() {
        assertThat(VerdictGate.controlRun()).isFalse();
        System.setProperty(VerdictGate.CONTROL_RUN, "true");
        assertThat(VerdictGate.controlRun()).isTrue();
        System.setProperty(VerdictGate.CONTROL_RUN, "yes");
        assertThatThrownBy(VerdictGate::controlRun).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be true or false");
    }

    @Test
    void theReportResultOfAPassInAControlRunIsVacuous() throws NoSuchMethodException {
        CellResult result = VerdictGate.single("Samples.plain", method("plain"), CELL, true, Attempt.pass(), false,
                ConformanceResult.pass(CELL));

        assertThat(result.verdict()).isEqualTo(Verdict.VACUOUS);
        assertThat(result.test()).isEqualTo("Samples.plain");
        assertThat(result.runs()).isEqualTo(1);
    }

    @Test
    void aDisagreeingRunIsReportedAsDisagree() throws NoSuchMethodException {
        CellResult result = VerdictGate.single("Samples.plain [mineflayer-ref]", method("plain"), CELL, false,
                Attempt.fail("the client saw a different slot"), true, ConformanceResult.pass(CELL));

        assertThat(result.verdict()).isEqualTo(Verdict.DISAGREE);
    }

    @Test
    void aRunOnAFailedCellIsReportedAsInvalid() throws NoSuchMethodException {
        CellResult result = VerdictGate.single("Samples.plain", method("plain"), CELL, false, Attempt.pass(), false,
                new ConformanceResult(CELL, false, "list did not print"));

        assertThat(result.verdict()).isEqualTo(Verdict.INVALID);
    }

    @Test
    void aFailureWithoutConformanceIsAFailNotAnError() throws NoSuchMethodException {
        CellResult result = VerdictGate.single("Samples.plain", method("plain"), CELL, false,
                Attempt.fail("the server did not start"), false, null);

        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(result.reason()).isEqualTo("the server did not start");
    }

    @Test
    void aPassWithoutConformanceIsAnError() throws NoSuchMethodException {
        assertThatThrownBy(() -> VerdictGate.single("Samples.plain", method("plain"), CELL, false, Attempt.pass(),
                false, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aFlakeReportNamesTheVerdictOfEveryRun() throws NoSuchMethodException {
        List<Attempt> runs = List.of(Attempt.pass(), Attempt.fail("draw"), Attempt.pass(), Attempt.fail("draw"),
                Attempt.fail("draw"));

        CellResult result = VerdictGate.repeated("Samples.plain", method("plain"), CELL, false, runs,
                ConformanceResult.pass(CELL));

        assertThat(result.verdict()).isEqualTo(Verdict.FLAKY);
        assertThat(result.runs()).isEqualTo(5);
        assertThat(result.reason()).contains("runs: PASS, FAIL, PASS, FAIL, FAIL");
    }
}
