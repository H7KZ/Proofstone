package dev.proofstone.core.junit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.proofstone.core.cell.Cell;
import dev.proofstone.core.driver.ClientDriver;
import dev.proofstone.core.host.PaperHost;
import dev.proofstone.core.versions.ImagePin;
import dev.proofstone.core.versions.VersionsCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * The repro bundle of a test that did not pass (glossary: Repro bundle). It is written to
 * {@code build/proofstone/<platform>@<mc>/<TestClass>.<method>/} while the server still runs, so the server log, the
 * agent state and the client transcript are all still there. The bundle is local and never committed (build/).
 *
 * <p>The agent token is redacted from every file, and every file is scanned again after writing: a bundle that
 * still holds the token is an error, never a silent leak (AGENTS.md section 3, the secret-scan test).
 */
final class BundleWriter {

    /** Relative to the test working directory, which is the project directory under Gradle. */
    static final Path ROOT = Path.of("build", "proofstone");
    static final String REDACTED = "<redacted: agent token>";
    /** The switch that makes the forced-failure example fail on purpose (ForcedFailureE2E). */
    static final String FORCE_FAIL = "proofstone.forceFail";
    /** The reference driver's fault switch, named as MineflayerRefDriver.FAULT_PROPERTY (core does not depend on it). */
    static final String DRIVER_FAULT = "proofstone.injectDriverFault";
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Everything a bundle holds. {@code viaproxyLog} is {@code null} for an unbridged cell. {@code host} is the rendered
     * host.json: OS, Java, Docker server version and the pins of the run.
     */
    record Contents(String serverLog, String viaproxyLog, List<String> agentTranscript,
            List<String> driverTranscript, Cell cell, String snapshot, String host, String rerun, String secret) {
    }

    private BundleWriter() {
    }

    /**
     * Captures the bundle of one run. Called from afterEach, before the server is torn down. In a flake detector run
     * ({@code -Dproofstone.repeat}) every run gets its own directory, {@code <TestClass>.<method>.r<N>}, so a FLAKY
     * report keeps the transcripts of all runs.
     *
     * @param repetition the run's number from 1 to N, or 0 when the test runs once
     * @param driverId the driver of this run; a differential run keeps each driver's bundle under {@code <test>/<driver>/}
     * @param differential whether this run is one half of a differential run
     */
    static Path capture(ExtensionContext context, Cell cell, TestState state, int repetition, String driverId,
            boolean differential, VersionsCatalog catalog) {
        String test = context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName();
        if (repetition > 0) {
            test += ".r" + repetition;
        }
        Path dir = ROOT.resolve(cell.platform() + "@" + cell.mc()).resolve(test);
        if (differential) {
            dir = dir.resolve(driverId);
        }
        List<String> driver = state.client == null ? List.of() : state.client.transcript();
        return write(dir, new Contents(
                state.host.logs().all(),
                state.proxy == null ? null : state.proxy.logs().all(),
                state.host.agentTranscript(),
                driver,
                cell,
                snapshot(state.host),
                hostInfo(catalog, driverId, state.proxy != null),
                rerun(context, cell),
                state.host.agentToken()));
    }

    /** Writes the files, replacing an earlier bundle of the same test, and returns the directory. */
    static Path write(Path dir, Contents contents) {
        if (contents.secret() == null || contents.secret().isBlank()) {
            throw new IllegalStateException("a repro bundle needs the agent token to redact it");
        }
        Map<String, String> files = new LinkedHashMap<>();
        files.put("server.log", contents.serverLog() + "\n");
        if (contents.viaproxyLog() != null) {
            files.put("viaproxy.log", contents.viaproxyLog() + "\n");
        }
        files.put("agent-transcript.jsonl", lines(contents.agentTranscript()));
        files.put("driver-transcript.jsonl", lines(contents.driverTranscript().stream()
                .map(line -> JSON.createObjectNode().put("line", line).toString()).toList()));
        files.put("cell.json", JSON.valueToTree(contents.cell()).toPrettyString() + "\n");
        files.put("snapshot.json", contents.snapshot() + "\n");
        files.put("host.json", contents.host() + "\n");
        files.put("rerun.txt", contents.rerun());
        deleteTree(dir);
        try {
            Files.createDirectories(dir);
            for (Map.Entry<String, String> file : files.entrySet()) {
                Files.writeString(dir.resolve(file.getKey()),
                        redact(file.getValue(), contents.secret()), StandardCharsets.UTF_8);
            }
            for (String name : files.keySet()) {
                String text = Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
                if (text.contains(contents.secret())) {
                    throw new IllegalStateException("the agent token is in bundle file " + name + "; not left behind");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the repro bundle to " + dir, e);
        }
        return dir;
    }

    /** Replaces every occurrence of the secret. A blank secret would match everywhere, so it is refused. */
    static String redact(String text, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("a secret to redact must not be blank");
        }
        return text.replace(secret, REDACTED);
    }

    /**
     * The gradle task of the project the test runs in, for example {@code :examples:smoke:test}. The test working
     * directory is the project directory, and the root is {@code proofstone.repoRoot}.
     */
    static String gradleTask(Path root, Path projectDir) {
        StringBuilder task = new StringBuilder();
        for (Path part : root.toAbsolutePath().normalize().relativize(projectDir.toAbsolutePath().normalize())) {
            if (!part.toString().isEmpty()) {
                task.append(':').append(part);
            }
        }
        return task.append(":test").toString();
    }

    /** The command that reruns the one test on the one cell, with the Docker lock (AGENTS.md section 4). */
    static String rerun(ExtensionContext context, Cell cell) {
        String root = System.getProperty(ProofstoneExtension.REPO_ROOT);
        if (root == null) {
            throw new IllegalStateException("system property " + ProofstoneExtension.REPO_ROOT + " is not set");
        }
        String test = context.getRequiredTestClass().getName() + "." + context.getRequiredTestMethod().getName();
        return rerunCommand(gradleTask(Path.of(root), Path.of("")), test, cell, switches());
    }

    /**
     * The command for one test on one cell. Every switch is passed, so the rerun reproduces the verdict: a repeat, a
     * control run, a conformance fault or a differential run only fails the same way with its switch on (T-M4-09, X4).
     *
     * @param gradleTask the Gradle test task, for example {@code :examples:bad-tests:test}
     * @param switches the system properties to pass, name to value; a null value is not allowed
     */
    static String rerunCommand(String gradleTask, String test, Cell cell, Map<String, String> switches) {
        StringBuilder command = new StringBuilder("bash scripts/dockerlock.sh ./gradlew ")
                .append(gradleTask)
                .append(" --tests '").append(test).append('\'')
                .append(" -Dproofstone.cells=").append(cell.platform()).append('@').append(cell.mc());
        for (Map.Entry<String, String> entry : switches.entrySet()) {
            command.append(" -D").append(entry.getKey()).append('=').append(entry.getValue());
        }
        return "# Set PROOFSTONE_ACCEPT_EULA=true in the environment for this command; it is never written to a file.\n"
                + command + " --no-parallel\n";
    }

    /** The switches that were set on this run, in a fixed order, so a rerun carries every one of them. */
    private static Map<String, String> switches() {
        Map<String, String> switches = new LinkedHashMap<>();
        for (String name : List.of(FORCE_FAIL, DifferentialRun.PROPERTY, DRIVER_FAULT, FlakeDetector.REPEAT,
                VerdictGate.CONTROL_RUN, CellConformance.INJECT)) {
            String value = System.getProperty(name);
            if (value != null) {
                switches.put(name, value);
            }
        }
        return switches;
    }

    /**
     * The host and the pins of the run (T-M4-09): OS, Java, the Docker server version, the driver and, when the cell is
     * bridged, the ViaProxy image. A value that cannot be read is written as its error, never left out.
     */
    static String hostInfo(VersionsCatalog catalog, String driverId, boolean bridged) {
        ObjectNode node = JSON.createObjectNode();
        node.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " ("
                + System.getProperty("os.arch") + ")");
        node.put("java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        node.put("docker", dockerVersion());
        ObjectNode driver = node.putObject("driver");
        driver.put("id", driverId);
        ClientDriver found = CellInvocation.findDriver(driverId).orElse(null);
        if (found == null) {
            driver.put("protocol", "driver " + driverId + " is not on the classpath");
        } else {
            driver.put("protocol", found.protocolVersion());
        }
        if (bridged) {
            ImagePin pin = catalog.driverImage("viaproxy");
            node.putObject("viaproxy").put("image", pin.image()).put("digest", pin.digest());
        } else {
            node.putNull("viaproxy");
        }
        return node.toPrettyString();
    }

    private static String dockerVersion() {
        try {
            return DockerClientFactory.instance().client().versionCmd().exec().getVersion();
        } catch (RuntimeException e) {
            return "unavailable: " + e.getMessage();
        }
    }

    /** The agent's full-state dump (agent-protocol §2). A failure is recorded in the file, not left out. */
    private static String snapshot(PaperHost host) {
        ObjectNode node = JSON.createObjectNode();
        try {
            node.set("snapshot", host.request("GET", "/v1/snapshot", null));
        } catch (RuntimeException e) {
            node.put("endpoint", "GET /v1/snapshot").put("error", String.valueOf(e.getMessage()));
        }
        return node.toPrettyString();
    }

    private static String lines(List<String> lines) {
        return lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot clear the old repro bundle " + dir, e);
        }
    }
}
