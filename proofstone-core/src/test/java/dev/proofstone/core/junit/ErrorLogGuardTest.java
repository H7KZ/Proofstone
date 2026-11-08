package dev.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.proofstone.core.junit.ErrorLogGuard.Patterns;
import dev.proofstone.protocol.LogRecord;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ErrorLogGuardTest {

    private static final Patterns PAPER = Patterns.PAPER;

    private static LogRecord record(String level, String message, String thrown) {
        return new LogRecord("2026-01-01T00:00:00Z", level, "Minecraft", message, thrown);
    }

    @Test
    void noRecordsMeansNoProblem() {
        assertThat(ErrorLogGuard.describe(List.of(), PAPER)).isNull();
    }

    @Test
    void knownVanillaNoiseIsAllowed() {
        assertThat(ErrorLogGuard.describe(List.of(record("ERROR", "No key layers in MapLike[{}]", null)), PAPER))
                .isNull();
    }

    @Test
    void theHarnessClientsMoveWarningIsAllowedButAnotherPlayersIsNot() {
        assertThat(ErrorLogGuard.describe(List.of(
                record("WARN", "T9513c1c1 moved too quickly! 0.5,-60.0,-3.5", null)), PAPER)).isNull();
        assertThat(ErrorLogGuard.describe(List.of(
                record("WARN", "Steve moved too quickly! 0.5,-60.0,-3.5", null)), PAPER))
                .contains("Steve moved too quickly");
    }

    @Test
    void anUnknownRecordFailsAndTheMessageCarriesItWithItsStackTrace() {
        String text = ErrorLogGuard.describe(List.of(
                record("ERROR", "No key layers in MapLike[{}]", null),
                record("ERROR", "boom in plugin", "java.lang.IllegalStateException: boom\n\tat A.b(A.java:1)")), PAPER);

        assertThat(text).contains("1 unexpected").contains("ERROR Minecraft: boom in plugin")
                .contains("IllegalStateException: boom").contains("at A.b(A.java:1)")
                .doesNotContain("No key layers");
    }

    @Test
    void theAllowlistIsPerPackSoAnotherPackDoesNotInheritPaperNoise() {
        Patterns other = new Patterns("other", Map.of(), List.of());

        assertThat(ErrorLogGuard.describe(List.of(record("ERROR", "No key layers in MapLike[{}]", null)), other))
                .contains("No key layers");
    }

    @Test
    void anErrorPatternFailsARecordEvenWhenTheAllowlistMatchesIt() {
        Patterns folia = new Patterns("folia", Map.of(Pattern.compile("Thread failed main thread check"), "noisy"),
                List.of(Pattern.compile("Thread failed main thread check")));

        assertThat(ErrorLogGuard.describe(List.of(
                record("WARN", "Thread failed main thread check: Accessing entity off thread", null)), folia))
                .contains("1 unexpected").contains("Accessing entity off thread");
    }

    @Test
    void aPackWithoutItsOwnListFailsLoudly() {
        assertThat(Patterns.of("paper")).isSameAs(PAPER);
        assertThat(Patterns.of("folia")).isSameAs(Patterns.FOLIA);
        // Spigot has no list until T-M5-05. Folia got its own in T-M5-03, so it no longer stands in for "unknown".
        assertThatThrownBy(() -> Patterns.of("spigot")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spigot").hasMessageContaining("its own list");
    }
}
