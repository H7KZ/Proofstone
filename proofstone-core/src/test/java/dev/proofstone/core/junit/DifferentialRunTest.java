package dev.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.proofstone.api.ItemView;
import dev.proofstone.api.spi.OracleWaiter;
import dev.proofstone.core.junit.DifferentialRun.Evidence;
import dev.proofstone.core.junit.DifferentialRun.Observation;
import dev.proofstone.core.verdict.ConformanceResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The differential run (self-tests section 3) without Docker: flag, pinned comparison list, comparator, pairing. */
class DifferentialRunTest {

    private static final String CELL = "paper@26.1.2";

    @TempDir
    Path dir;

    @AfterEach
    void clearState() {
        DifferentialRun.end();
    }

    private static ItemView sword(int damage, Map<String, Object> customData, Map<String, Object> pdc) {
        return new ItemView("minecraft:diamond_sword", 1, Map.of("minecraft:sharpness", 5), Map.of(), "Blade",
                List.of("line"), damage, 0, customData, pdc, "raw");
    }

    private static Evidence evidence(String driver, String... values) {
        List<Observation> observations = new java.util.ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            observations.add(new Observation("point " + (i + 1), values[i]));
        }
        return new Evidence(driver, observations, List.of("-> chat", "<- chat " + driver));
    }

    @Test
    void theFlagIsStrict() {
        assertThat(DifferentialRun.parse(null)).isFalse();
        assertThat(DifferentialRun.parse("false")).isFalse();
        assertThat(DifferentialRun.parse("true")).isTrue();
        assertThatThrownBy(() -> DifferentialRun.parse("yes")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("proofstone.differential must be true or false");
    }

    @Test
    void theReferenceDriverIsNativeOnlyWhenItsProtocolIsTheCellRow() {
        assertThat(DifferentialRun.referenceNative("26.1.2", "26.1.2")).isTrue();
        assertThat(DifferentialRun.referenceNative("26.3", "26.1.2")).isFalse();
        assertThat(DifferentialRun.referenceNative("1.21.11", "26.1.2")).isFalse();
    }

    @Test
    void theComparedFieldsOfAnItemAreThePinnedOnesOnly() {
        ItemView plain = sword(0, null, null);
        // customData, pdc and raw are one-sided fields: a difference there is not a client observation.
        ItemView otherSides = sword(0, Map.of("x", 1), Map.of("y", 2));

        assertThat(DifferentialRun.canonicalItem(otherSides)).isEqualTo(DifferentialRun.canonicalItem(plain));
        assertThat(DifferentialRun.canonicalItem(plain)).isEqualTo("minecraft:diamond_sword x1 enchants={minecraft:sharpness=5}"
                + " storedEnchants={} name=Blade lore=[line] damage=0 repairCost=0");
    }

    @Test
    void aDamageDifferenceIsAnObservationDifference() {
        assertThat(DifferentialRun.canonicalItem(sword(1, null, null)))
                .isNotEqualTo(DifferentialRun.canonicalItem(sword(0, null, null)));
    }

    @Test
    void anInventoryRendersEachSlotInOrderAndEmptySlotsAsADash() {
        String rendered = DifferentialRun.render(
                new OracleWaiter.Side<>(true, List.of(ItemView.EMPTY, sword(0, null, null)), null));

        assertThat(rendered).startsWith("[0=-, 1=minecraft:diamond_sword x1");
    }

    @Test
    void aSideWithoutAnAnswerIsRenderedAsNoAnswer() {
        assertThat(DifferentialRun.render(new OracleWaiter.Side<>(false, null, "timeout")))
                .isEqualTo("no answer (timeout)");
    }

    @Test
    void equalObservationsDoNotDisagree() {
        assertThat(DifferentialRun.compare(evidence("mcpl", "a", "b"), evidence("mineflayer-ref", "a", "b"))).isNull();
    }

    @Test
    void aRunThatObservedNothingFailsSoItCannotPassByComparingNothing() {
        // Both runs empty would compare equal; the run itself must fail (T-M4-11, robustness X3).
        AssertionError failure = DifferentialRun.unobserved("Test.method", CELL, evidence("mcpl"));

        assertThat(failure).isNotNull();
        assertThat(failure.getMessage()).startsWith("FAIL Test.method on paper@26.1.2")
                .contains("mcpl run recorded no client observation").contains("DualAssert");
        assertThat(DifferentialRun.unobserved("Test.method", CELL, evidence("mcpl", "a"))).isNull();
    }

    @Test
    void aMismatchNamesTheFirstDifferingPointAndBothDrivers() {
        DifferentialRun.Disagreement disagreement = DifferentialRun.compare(
                evidence("mcpl", "a", "b", "c"), evidence("mineflayer-ref", "a", "X", "c"));

        assertThat(disagreement).isNotNull();
        assertThat(disagreement.detail()).contains("at assertion 2").contains("mcpl: point 2 => b")
                .contains("mineflayer-ref: point 2 => X");
    }

    @Test
    void aMissingObservationIsADisagreementToo() {
        DifferentialRun.Disagreement disagreement = DifferentialRun.compare(
                evidence("mcpl", "a", "b"), evidence("mineflayer-ref", "a"));

        assertThat(disagreement).isNotNull();
        assertThat(disagreement.detail()).contains("at assertion 2").contains("no assertion point");
    }

    @Test
    void theSecondArrivalOfADifferentDriverComparesAndClearsThePair() {
        String key = "Test.method@26.1.2";

        assertThat(DifferentialRun.arrive(key, evidence("mcpl", "a"))).isNull();
        assertThat(DifferentialRun.arrive(key, evidence("mineflayer-ref", "Z"))).isNotNull();
        // The pair is gone, so the next run starts over and cannot compare with stale evidence.
        assertThat(DifferentialRun.arrive(key, evidence("mcpl", "a"))).isNull();
    }

    @Test
    void aRepeatOfTheSameDriverReplacesTheWaitingEvidence() {
        String key = "Test.method@26.1.2";

        assertThat(DifferentialRun.arrive(key, evidence("mcpl", "stale"))).isNull();
        assertThat(DifferentialRun.arrive(key, evidence("mcpl", "a"))).isNull();
        assertThat(DifferentialRun.arrive(key, evidence("mineflayer-ref", "a"))).isNull();
    }

    @Test
    void recordingOnlyHappensBetweenBeginAndEnd() {
        DifferentialRun.record("ignored before begin", new OracleWaiter.Side<>(true, 0, null));
        assertThat(DifferentialRun.end()).isEmpty();

        DifferentialRun.begin();
        DifferentialRun.record("experience level 0", new OracleWaiter.Side<>(true, 0, null));
        DifferentialRun.record("missing", new OracleWaiter.Side<>(false, null, "gone"));

        assertThat(DifferentialRun.end()).containsExactly(
                new Observation("experience level 0", "0"),
                new Observation("missing", "no answer (gone)"));
        assertThat(DifferentialRun.end()).isEmpty();
    }

    @Test
    void theDisagreementVerdictIsDisagreeWithTheDetail() {
        DifferentialRun.Disagreement disagreement = DifferentialRun.compare(
                evidence("mcpl", "a"), evidence("mineflayer-ref", "b"));

        AssertionError failure = DifferentialRun.failure("Test.method", CELL, disagreement, ConformanceResult.pass(CELL));

        assertThat(failure.getMessage()).startsWith("DISAGREE Test.method on paper@26.1.2")
                .contains("mineflayer-ref: point 1 => b");
    }

    @Test
    void theEvidenceFilesHoldBothTranscriptsAndTheTokenIsRedacted() throws IOException {
        Evidence first = new Evidence("mcpl", List.of(new Observation("p", "a")), List.of("<- token secret-token"));
        Evidence second = new Evidence("mineflayer-ref", List.of(new Observation("p", "b")),
                List.of("<- chat secret-token"));
        DifferentialRun.Disagreement disagreement = DifferentialRun.compare(first, second);

        DifferentialRun.writeEvidence(dir, disagreement, "secret-token");

        Path out = dir.resolve("differential");
        assertThat(Files.readString(out.resolve("mcpl-transcript.jsonl"), StandardCharsets.UTF_8))
                .contains("<redacted: agent token>").doesNotContain("secret-token");
        assertThat(Files.readString(out.resolve("mineflayer-ref-transcript.jsonl"), StandardCharsets.UTF_8))
                .contains("<- chat");
        assertThat(Files.readString(out.resolve("disagreement.txt"), StandardCharsets.UTF_8))
                .contains("mineflayer-ref: p => b");
    }
}
