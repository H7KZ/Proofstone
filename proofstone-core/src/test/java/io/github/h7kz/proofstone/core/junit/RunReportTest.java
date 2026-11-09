package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.core.verdict.CellResult;
import io.github.h7kz.proofstone.core.verdict.Verdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunReportTest {

    @TempDir
    Path dir;

    @Test
    void closingTheRunWritesTheReportAndTheJunitXml() throws IOException {
        RunReport report = new RunReport(dir);
        report.record(new CellResult("A.ok", "paper@26.3", Verdict.PASS, null, null, 1));
        report.record(new CellResult("A.bad", "paper@26.3", Verdict.FAIL, "expected 1", null, 1));

        report.close();

        assertThat(Files.readString(dir.resolve("proofstone-report.json"))).contains("\"green\" : false",
                "\"verdict\" : \"FAIL\"");
        assertThat(Files.readString(dir.resolve("proofstone-junit.xml"))).contains("<testsuite", "<failure");
    }

    @Test
    void aRunWithNoCellResultWritesNothing() throws IOException {
        new RunReport(dir).close();

        assertThat(List.of(dir.toFile().list())).isEmpty();
    }
}
