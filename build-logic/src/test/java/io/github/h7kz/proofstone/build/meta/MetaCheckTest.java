package io.github.h7kz.proofstone.build.meta;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.build.meta.MetaCheck.Expected;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// T-M4-13: the checker behind metaTest judges every bad test on its own, from fixtures shaped like a real run's JUnit XML,
// proofstone-report.json and repro bundles. Each rule has a failing and a passing case (AGENTS.md section 3).
class MetaCheckTest {

    private static final String PKG = "io.github.h7kz.proofstone.examples.badtests.invalid.window.";
    private static final Instant START = Instant.parse("2026-11-19T10:00:00Z");

    @TempDir
    Path dir;

    private Path xml;
    private Path report;
    private Path bundles;

    @BeforeEach
    void layout() throws IOException {
        xml = Files.createDirectories(dir.resolve("xml"));
        report = Files.createDirectories(dir.resolve("report"));
        bundles = Files.createDirectories(dir.resolve("bundles"));
    }

    private static final String ONE = "WindowConformanceInvalidE2E.aWindowBrokenCellGivesNoPluginVerdictForThisTest";
    private static final String TWO = "WindowConformanceInvalidE2E.aSecondTestOnTheSameWindowBrokenCellIsInvalidToo";

    private static Expected invalid(String test) {
        return new Expected(test, "INVALID", List.of("cell conformance failed"), List.of("timed out"), List.of(test), 1,
                List.of(List.of("INVALID " + test)), List.of(), null);
    }

    private static String failure(String test) {
        return "<failure message=\"java.lang.AssertionError: INVALID " + test + " on paper@26.1.2: cell conformance failed\" "
                + "type=\"java.lang.AssertionError\">stack</failure>";
    }

    private static String row(String test, String verdict, String reason) {
        return "{\"cell\":\"paper@26.1.2\",\"test\":\"" + PKG + test + "\",\"verdict\":\"" + verdict + "\",\"reason\":"
                + (reason == null ? "null" : "\"" + reason + "\"") + "}";
    }

    private void writeXml(int skipped, String... cases) throws IOException {
        StringBuilder body = new StringBuilder();
        int failures = 0;
        for (String testcase : cases) {
            body.append(testcase).append('\n');
            if (testcase.contains("<failure")) {
                failures++;
            }
        }
        Files.writeString(xml.resolve("TEST-" + PKG + "WindowConformanceInvalidE2E.xml"),
                "<?xml version=\"1.0\"?>\n<testsuite name=\"x\" tests=\"" + cases.length + "\" skipped=\"" + skipped
                        + "\" failures=\"" + failures + "\" errors=\"0\" timestamp=\"" + START + "\">\n" + body
                        + "</testsuite>\n", StandardCharsets.UTF_8);
    }

    private static String testcase(String failure) {
        return "<testcase name=\"paper@26.1.2 (bridged)\" classname=\"x\">" + failure + "</testcase>";
    }

    private void writeReport(String... rows) throws IOException {
        Files.writeString(report.resolve("proofstone-report.json"),
                "{\"schema\":\"proofstone-report/1\",\"results\":[" + String.join(",", rows) + "]}");
    }

    private void writeBundle(String name, Instant written) throws IOException {
        Path bundle = Files.createDirectories(bundles.resolve("paper@26.1.2").resolve(name));
        for (String file : List.of("cell.json", "rerun.txt", "server.log")) {
            Path path = bundle.resolve(file);
            Files.writeString(path, "x");
            Files.setLastModifiedTime(path, FileTime.from(written));
        }
    }

    private List<String> check(Expected... expected) {
        return MetaCheck.check("metaInvalidWindow", xml, report, bundles, List.of(expected));
    }

    private void greenWindowRun() throws IOException {
        writeXml(0, testcase(failure(ONE)), testcase(failure(TWO)));
        writeReport(row(ONE, "INVALID", "cell conformance failed: C04"), row(TWO, "INVALID", "cell conformance failed: C04"));
        writeBundle(ONE, START.plusSeconds(40));
        writeBundle(TWO, START.plusSeconds(41));
    }

    @Test
    void everyTestWithItsExpectedVerdictAndBundleIsGreen() throws IOException {
        greenWindowRun();

        assertThat(check(invalid(ONE), invalid(TWO))).isEmpty();
    }

    @Test
    void oneBadTestWithTheWrongVerdictIsRedByNameWhileTheOtherPasses() throws IOException {
        // The old check asked for at least one INVALID failure in the task, so one right test hid the wrong one.
        writeXml(0, testcase(failure(ONE)), testcase(""));
        writeReport(row(ONE, "INVALID", "cell conformance failed: C04"), row(TWO, "PASS", null));
        writeBundle(ONE, START.plusSeconds(40));
        writeBundle(TWO, START.plusSeconds(41));

        List<String> problems = check(invalid(ONE), invalid(TWO));

        assertThat(problems).anyMatch(p -> p.contains(TWO) && p.contains("PASS") && p.contains("expected INVALID"));
        assertThat(problems).noneMatch(p -> p.contains(ONE + " has verdict"));
    }

    @Test
    void aFailureNoExpectedTestClaimsIsRedWithNoExemption() throws IOException {
        // What the old "random draw" exemption let through: a failure that no expectation names.
        greenWindowRun();
        writeXml(0, testcase(failure(ONE)), testcase(failure(TWO)),
                testcase("<failure message=\"x: [this run's random draw]\" type=\"x\">stack</failure>"));

        assertThat(check(invalid(ONE), invalid(TWO))).anyMatch(p -> p.contains("random draw") && p.contains("no expected test"));
    }

    @Test
    void aTestMissingFromTheReportIsRed() throws IOException {
        greenWindowRun();
        writeReport(row(ONE, "INVALID", "cell conformance failed: C04"));

        assertThat(check(invalid(ONE), invalid(TWO))).anyMatch(p -> p.contains(TWO) && p.contains("no row"));
    }

    @Test
    void aReportRowNoExpectationNamesIsRed() throws IOException {
        greenWindowRun();
        writeReport(row(ONE, "INVALID", "cell conformance failed: C04"), row(TWO, "INVALID", "cell conformance failed: C04"),
                row("Other.test", "PASS", null));

        assertThat(check(invalid(ONE), invalid(TWO))).anyMatch(p -> p.contains("Other.test") && p.contains("not expected"));
    }

    @Test
    void aReasonThatLacksTheFaultsOwnTextIsRed() throws IOException {
        greenWindowRun();
        writeReport(row(ONE, "INVALID", "cell conformance failed: C04"), row(TWO, "INVALID", "something else timed out"));

        List<String> problems = check(invalid(ONE), invalid(TWO));

        assertThat(problems).anyMatch(p -> p.contains(TWO) && p.contains("cell conformance failed"));
        assertThat(problems).anyMatch(p -> p.contains(TWO) && p.contains("timed out"));
    }

    @Test
    void aMissingBundleIsRed() throws IOException {
        greenWindowRun();
        Files.delete(bundles.resolve("paper@26.1.2").resolve(TWO).resolve("rerun.txt"));

        assertThat(check(invalid(ONE), invalid(TWO))).anyMatch(p -> p.contains(TWO) && p.contains("rerun.txt"));
    }

    @Test
    void aBundleLeftByAnEarlierRunIsRed() throws IOException {
        greenWindowRun();
        writeBundle(TWO, START.minusSeconds(3600));

        assertThat(check(invalid(ONE), invalid(TWO))).anyMatch(p -> p.contains(TWO) && p.contains("earlier run"));
    }

    @Test
    void aSkipThatNoExpectationNamesIsRed() throws IOException {
        greenWindowRun();
        writeXml(1, testcase(failure(ONE)), testcase(failure(TWO)), "<testcase name=\"y\" classname=\"x\"><skipped/></testcase>");

        assertThat(check(invalid(ONE), invalid(TWO))).anyMatch(p -> p.contains("skipped=1"));
    }

    @Test
    void aMissingTestcaseIsRed() throws IOException {
        greenWindowRun();
        writeXml(0, testcase(failure(ONE)));

        assertThat(check(invalid(ONE), invalid(TWO))).anyMatch(p -> p.contains("testcases"));
    }

    @Test
    void aFailureHoldingTheTextThisFaultMustNotGiveIsRed() throws IOException {
        greenWindowRun();
        Expected strict = new Expected(ONE, "INVALID", List.of(), List.of(), List.of(ONE), 1,
                List.of(List.of("INVALID " + ONE)), List.of("cell conformance failed"), null);

        assertThat(check(strict, invalid(TWO))).anyMatch(p -> p.contains(ONE) && p.contains("a different red"));
    }

    @Test
    void aSkipWithAnotherReasonCodeIsRed() throws IOException {
        writeXml(1, "<testcase name=\"y\" classname=\"x\"><skipped/></testcase>");
        writeReport("{\"cell\":\"paper@26.1.2\",\"test\":\"p.SkipE2E.skips\",\"verdict\":\"SKIPPED\","
                + "\"reason\":\"missing capability X\",\"skipReason\":\"EULA\"}");
        Expected skip = new Expected("SkipE2E.skips", "SKIPPED", List.of("missing capability X"), List.of(), List.of(), 1,
                List.of(), List.of(), "CAPABILITY_MISSING");

        assertThat(check(skip)).anyMatch(p -> p.contains("skipReason EULA") && p.contains("CAPABILITY_MISSING"));
    }

    @Test
    void theExpectationsReadBackFromTheJsonTheBuildScriptWrites() {
        List<Expected> read = MetaCheck.parse("[{\"test\":\"A.b\",\"verdict\":\"INVALID\",\"testcases\":2,"
                + "\"xmlFailures\":[[\"INVALID A.b\",\"x\"]],\"bundles\":[\"A.b\"]}]");

        assertThat(read).singleElement().satisfies(one -> {
            assertThat(one.test()).isEqualTo("A.b");
            assertThat(one.testcases()).isEqualTo(2);
            assertThat(one.xmlFailures()).containsExactly(List.of("INVALID A.b", "x"));
            assertThat(one.reasonHas()).isEmpty();
        });
    }

    @Test
    void aTaskWithNoXmlDidNotRun() throws IOException {
        assertThat(check(invalid(ONE))).anyMatch(p -> p.contains("no JUnit XML"));
    }

    @Test
    void theFlakeRunMatchesTheFlakyMessageAndTheOneRawDrawFailure() throws IOException {
        // Seed 3: repeats 1, 2 and 4 pass, 3 fails on its draw, 5 is FLAKY. Both red testcases are claimed by name.
        String test = "FlakyE2E.theOutcomeOfThisRunIsRandom";
        String draw = "<failure message=\"AssertionFailedError: [this run's random draw]\" type=\"x\">stack</failure>";
        String flaky = "<failure message=\"AssertionFailedError: [this run's random draw]\" type=\"x\">Suppressed: "
                + "java.lang.AssertionError: FLAKY " + test + " on paper@26.3 over 5 runs: verdicts differ across 5 runs"
                + "</failure>";
        writeXml(0, testcase(""), testcase(""), testcase(draw), testcase(""), testcase(flaky));
        writeReport(row(test, "FLAKY", "verdicts differ across 5 runs: PASS, FAIL"));
        List<String> runs = new ArrayList<>();
        for (int run = 1; run <= 5; run++) {
            runs.add(test + ".r" + run);
            writeBundle(test + ".r" + run, START.plusSeconds(run));
        }
        Expected expected = new Expected(test, "FLAKY", List.of("verdicts differ across 5 runs"), List.of(), runs, 5,
                List.of(List.of("FLAKY " + test + " on paper@26.3 over 5 runs"), List.of("this run's random draw")), List.of(),
                null);

        assertThat(check(expected)).isEmpty();
    }

    @Test
    void theFlakeRunIsRedWhenTheFlakyMessageIsMissing() throws IOException {
        String test = "FlakyE2E.theOutcomeOfThisRunIsRandom";
        String draw = "<failure message=\"AssertionFailedError: [this run's random draw]\" type=\"x\">stack</failure>";
        writeXml(0, testcase(""), testcase(""), testcase(draw), testcase(""), testcase(draw));
        writeReport(row(test, "FLAKY", "verdicts differ across 5 runs: PASS, FAIL"));
        Expected expected = new Expected(test, "FLAKY", List.of(), List.of(), List.of(), 5,
                List.of(List.of("FLAKY " + test + " on paper@26.3 over 5 runs"), List.of("this run's random draw")), List.of(),
                null);

        assertThat(check(expected)).anyMatch(p -> p.contains("FLAKY " + test) && p.contains("no JUnit failure holds"));
    }
}
