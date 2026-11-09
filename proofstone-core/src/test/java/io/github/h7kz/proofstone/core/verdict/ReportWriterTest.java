package io.github.h7kz.proofstone.core.verdict;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The report schema against a golden snapshot (T-M4-01 acceptance). The fixture has one result per verdict, so a
 * change to any verdict's serialisation shows up in the diff.
 */
class ReportWriterTest {

    @TempDir
    Path dir;

    /** One result per verdict, on two cells, in a deliberately unsorted list. */
    static List<CellResult> fixture() {
        return List.of(
                new CellResult("vacuous-me", "paper@1.8.8", Verdict.VACUOUS,
                        "passes without the plugin (control run): the test does not prove the plugin works", null, 1),
                new CellResult("pass-me", "paper@1.21.11", Verdict.PASS, null, null, 1),
                new CellResult("fail-me", "paper@1.8.8", Verdict.FAIL,
                        "expected \"5\" <got> & 4", null, 1),
                new CellResult("invalid-me", "paper@1.8.8", Verdict.INVALID,
                        "cell conformance failed: C03 enchant mismatch", null, 1),
                new CellResult("flaky-me", "paper@1.8.8", Verdict.FLAKY,
                        "verdicts differ across 5 runs: PASS, FAIL", null, 5),
                new CellResult("disagree-me", "paper@1.21.11", Verdict.DISAGREE,
                        "client and reference driver observations differ", null, 1),
                new CellResult("skip-me", "paper@1.12.2", Verdict.SKIPPED,
                        "grindstone arrived in 1.14", SkipReason.CAPABILITY_MISSING, 1));
    }

    static String golden(String name) throws IOException {
        try (InputStream in = ReportWriterTest.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("missing golden file " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Test
    void reportMatchesTheGoldenSnapshot() throws IOException {
        String actual = ReportWriter.render(fixture());

        assertThat(actual).isEqualTo(golden("report-golden.json"));
    }

    @Test
    void writesTheReportFileAndCreatesTheDirectory() throws IOException {
        Path out = dir.resolve("build").resolve("proofstone");

        Path file = ReportWriter.write(out, fixture());

        assertThat(file.getFileName().toString()).isEqualTo(ReportWriter.FILE_NAME);
        assertThat(Files.readString(file)).isEqualTo(golden("report-golden.json"));
    }

    @Test
    void greenNeedsAtLeastOneResultAndAllGreen() throws IOException {
        String empty = ReportWriter.render(List.of());
        String allGreen = ReportWriter.render(List.of(
                new CellResult("a", "paper@1.8.8", Verdict.PASS, null, null, 1)));

        assertThat(empty).contains("\"green\" : false");
        assertThat(allGreen).contains("\"green\" : true");
    }

    @Test
    void twoResultsForTheSameTestAndCellAreAnError() {
        CellResult first = new CellResult("a", "paper@1.8.8", Verdict.PASS, null, null, 1);
        CellResult second = new CellResult("a", "paper@1.8.8", Verdict.FAIL, "x", null, 1);

        assertThatThrownBy(() -> ReportWriter.render(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
