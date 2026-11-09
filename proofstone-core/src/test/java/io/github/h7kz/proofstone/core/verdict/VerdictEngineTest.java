package io.github.h7kz.proofstone.core.verdict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * One unit test per verdict (T-M4-01 acceptance), plus the precedence between rules and the guards that stop a
 * malformed run from becoming a pass.
 */
class VerdictEngineTest {

    private static final String CELL = "paper@1.8.8";
    private static final ConformanceResult CONFORMANT = ConformanceResult.pass(CELL);
    private static final ConformanceResult BROKEN = new ConformanceResult(CELL, false, "C03 enchant mismatch");

    private static CellRun run(List<Attempt> attempts, boolean controlRun, boolean disagreement) {
        return new CellRun("sample test", CELL, attempts, controlRun, disagreement);
    }

    private static CellResult resolve(CellRun run) {
        return VerdictEngine.resolve(run, CONFORMANT);
    }

    @Test
    void passIsTheOnlyGreenVerdictOfAPlainRun() {
        CellResult result = resolve(run(List.of(Attempt.pass()), false, false));

        assertThat(result.verdict()).isEqualTo(Verdict.PASS);
        assertThat(result.reason()).isNull();
        assertThat(result.runs()).isEqualTo(1);
        assertThat(result.green()).isTrue();
    }

    @Test
    void failCarriesTheFailureMessage() {
        CellResult result = resolve(run(List.of(Attempt.fail("expected 5, got 4")), false, false));

        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(result.reason()).isEqualTo("expected 5, got 4");
        assertThat(result.green()).isFalse();
    }

    @Test
    void invalidWhenTheCellsConformanceFailed() {
        CellResult passing = VerdictEngine.resolve(run(List.of(Attempt.pass()), false, false), BROKEN);
        CellResult failing = VerdictEngine.resolve(run(List.of(Attempt.fail("boom")), false, false), BROKEN);

        assertThat(passing.verdict()).isEqualTo(Verdict.INVALID);
        assertThat(passing.reason()).isEqualTo("cell conformance failed: C03 enchant mismatch");
        assertThat(failing.verdict()).isEqualTo(Verdict.INVALID);
        assertThat(passing.green()).isFalse();
    }

    @Test
    void vacuousWhenAControlRunPasses() {
        CellResult result = resolve(run(List.of(Attempt.pass()), true, false));

        assertThat(result.verdict()).isEqualTo(Verdict.VACUOUS);
        assertThat(result.reason()).startsWith("passes without the plugin (control run)");
        assertThat(result.green()).isFalse();
    }

    @Test
    void aFailingControlRunIsTheExpectedOutcome() {
        CellResult result = resolve(run(List.of(Attempt.fail("plugin effect missing")), true, false));

        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    }

    @Test
    void flakyWhenRepeatedRunsDiffer() {
        List<Attempt> runs = List.of(Attempt.pass(), Attempt.fail("x"), Attempt.pass(), Attempt.pass(),
                Attempt.pass());
        CellResult result = resolve(run(runs, false, false));

        assertThat(result.verdict()).isEqualTo(Verdict.FLAKY);
        assertThat(result.reason()).isEqualTo("verdicts differ across 5 runs: PASS, FAIL");
        assertThat(result.runs()).isEqualTo(5);
        assertThat(result.green()).isFalse();
    }

    @Test
    void aSkipAndAPassAcrossRepeatsAreAlsoFlaky() {
        List<Attempt> runs = List.of(Attempt.pass(),
                Attempt.skipped(SkipReason.DOCKER_UNAVAILABLE, "docker went away"));
        CellResult result = resolve(run(runs, false, false));

        assertThat(result.verdict()).isEqualTo(Verdict.FLAKY);
    }

    @Test
    void disagreeWhenTheDriversDiffer() {
        CellResult result = resolve(run(List.of(Attempt.pass()), false, true));

        assertThat(result.verdict()).isEqualTo(Verdict.DISAGREE);
        assertThat(result.reason()).isEqualTo("client and reference driver observations differ");
        assertThat(result.green()).isFalse();
    }

    @Test
    void skippedKeepsItsAllowedReasonAndIsGreen() {
        CellResult result = resolve(run(List.of(Attempt.skipped(SkipReason.CAPABILITY_MISSING,
                "grindstone arrived in 1.14")), false, false));

        assertThat(result.verdict()).isEqualTo(Verdict.SKIPPED);
        assertThat(result.skipReason()).isEqualTo(SkipReason.CAPABILITY_MISSING);
        assertThat(result.reason()).isEqualTo("grindstone arrived in 1.14");
        assertThat(result.green()).isTrue();
    }

    @Test
    void invalidOutranksEverythingElse() {
        CellRun flakyAndDisagreeing = run(List.of(Attempt.pass(), Attempt.fail("x")), true, true);

        assertThat(VerdictEngine.resolve(flakyAndDisagreeing, BROKEN).verdict()).isEqualTo(Verdict.INVALID);
    }

    @Test
    void disagreeOutranksFlakyAndVacuous() {
        assertThat(resolve(run(List.of(Attempt.pass(), Attempt.fail("x")), false, true)).verdict())
                .isEqualTo(Verdict.DISAGREE);
        assertThat(resolve(run(List.of(Attempt.pass()), true, true)).verdict()).isEqualTo(Verdict.DISAGREE);
    }

    @Test
    void flakyOutranksVacuous() {
        assertThat(resolve(run(List.of(Attempt.pass(), Attempt.fail("x")), true, false)).verdict())
                .isEqualTo(Verdict.FLAKY);
    }

    @Test
    void onlyPassAndSkippedAreGreen() {
        for (Verdict verdict : Verdict.values()) {
            boolean expected = verdict == Verdict.PASS || verdict == Verdict.SKIPPED;
            assertThat(verdict.isGreen()).as(verdict.name()).isEqualTo(expected);
        }
    }

    @Test
    void aRunThatIsNotPassFailOrSkipIsRejected() {
        assertThatThrownBy(() -> new Attempt(Verdict.VACUOUS, null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Attempt(Verdict.INVALID, null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSkipWithoutAReasonIsRejectedNotPassed() {
        assertThatThrownBy(() -> new Attempt(Verdict.SKIPPED, null, "no reason"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CellResult("t", CELL, Verdict.SKIPPED, "why", null, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTestWithNoRunsIsRejectedNotPassed() {
        assertThatThrownBy(() -> run(List.of(), false, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingOrForeignConformanceResultIsRejected() {
        CellRun run = run(List.of(Attempt.pass()), false, false);

        assertThatThrownBy(() -> VerdictEngine.resolve(run, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> VerdictEngine.resolve(run, ConformanceResult.pass("paper@1.21.11")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
