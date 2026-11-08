package dev.proofstone.core.junit;

import dev.proofstone.core.verdict.CellResult;
import dev.proofstone.core.verdict.JunitXml;
import dev.proofstone.core.verdict.ReportWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;

/**
 * Collects the verdict of every cell run in this JVM and writes {@code proofstone-report.json} and the JUnit XML when
 * the run ends (test-api spec section 7, T-M4-09). It lives in the root store, so JUnit closes it after the last test.
 *
 * <p>A run that started no cell writes nothing. The directory is {@code build/proofstone} unless
 * {@code -Dproofstone.reportDir} says otherwise: the bad-test meta run gives each task its own directory and merges them
 * (bad-tests build script), because every Gradle test task is a separate JVM.
 */
final class RunReport implements CloseableResource {

    /** The system property that moves the report directory. The bad-test build script sets it per task. */
    static final String DIRECTORY_PROPERTY = "proofstone.reportDir";

    private final Path directory;

    /** @return the directory the run writes to: {@code -Dproofstone.reportDir}, or the default repro-bundle root */
    static Path defaultDirectory() {
        return Path.of(System.getProperty(DIRECTORY_PROPERTY, BundleWriter.ROOT.toString()));
    }

    private final List<CellResult> results = new ArrayList<>();

    RunReport(Path directory) {
        this.directory = directory;
    }

    synchronized void record(CellResult result) {
        results.add(result);
    }

    @Override
    public synchronized void close() throws IOException {
        if (results.isEmpty()) {
            return;
        }
        ReportWriter.write(directory, results);
        JunitXml.write(directory, results);
        System.err.println("PROOFSTONE REPORT: " + directory.resolve(ReportWriter.FILE_NAME));
    }
}
