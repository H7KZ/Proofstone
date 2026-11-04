package dev.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.protocol.LogRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorLogGuardTest {

    private static LogRecord record(String level, String message, String thrown) {
        return new LogRecord("2026-01-01T00:00:00Z", level, "Minecraft", message, thrown);
    }

    @Test
    void noRecordsMeansNoProblem() {
        assertThat(ErrorLogGuard.describe(List.of())).isNull();
    }

    @Test
    void knownVanillaNoiseIsAllowed() {
        assertThat(ErrorLogGuard.describe(List.of(record("ERROR", "No key layers in MapLike[{}]", null)))).isNull();
    }

    @Test
    void theHarnessClientsMoveWarningIsAllowedButAnotherPlayersIsNot() {
        assertThat(ErrorLogGuard.describe(List.of(
                record("WARN", "T9513c1c1 moved too quickly! 0.5,-60.0,-3.5", null)))).isNull();
        assertThat(ErrorLogGuard.describe(List.of(
                record("WARN", "Steve moved too quickly! 0.5,-60.0,-3.5", null)))).contains("Steve moved too quickly");
    }

    @Test
    void anUnknownRecordFailsAndTheMessageCarriesItWithItsStackTrace() {
        String text = ErrorLogGuard.describe(List.of(
                record("ERROR", "No key layers in MapLike[{}]", null),
                record("ERROR", "boom in plugin", "java.lang.IllegalStateException: boom\n\tat A.b(A.java:1)")));

        assertThat(text).contains("1 unexpected").contains("ERROR Minecraft: boom in plugin")
                .contains("IllegalStateException: boom").contains("at A.b(A.java:1)")
                .doesNotContain("No key layers");
    }
}
