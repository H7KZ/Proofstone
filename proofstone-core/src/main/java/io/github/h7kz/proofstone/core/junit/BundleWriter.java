package io.github.h7kz.proofstone.core.junit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.TopologyCell;
import io.github.h7kz.proofstone.core.driver.ClientDriver;
import io.github.h7kz.proofstone.core.host.AgentEndpoint;
import io.github.h7kz.proofstone.core.host.PaperHost;
import io.github.h7kz.proofstone.core.host.TopologyConfig;
import io.github.h7kz.proofstone.core.host.TopologyHost;
import io.github.h7kz.proofstone.core.versions.ImagePin;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    static final String REDACTED_FORWARDING = "<redacted: forwarding secret>";
    /** The switch that makes the forced-failure example fail on purpose (ForcedFailureE2E). */
    static final String FORCE_FAIL = "proofstone.forceFail";
    /** The reference driver's fault switch, named as MineflayerRefDriver.FAULT_PROPERTY (core does not depend on it). */
    static final String DRIVER_FAULT = "proofstone.injectDriverFault";
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Everything a bundle holds. {@code viaproxyLog} is {@code null} for an unbridged cell. {@code host} is the rendered
     * host.json: OS, Java, Docker server version and the pins of the run. {@code joinStall} is the server's view of a
     * join that timed out (T-M6-14), {@code null} when no join stalled. {@code cell} is the {@link Cell} of a
     * single-server run or the {@link TopologyCell} of a topology run: {@code cell.json} records it as it is, with every
     * pin (T-M7-08).
     */
    record Contents(String serverLog, String viaproxyLog, List<String> agentTranscript,
            List<String> driverTranscript, Object cell, String snapshot, String host, String rerun, String joinStall,
            String secret) {
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
            boolean differential, VersionsCatalog catalog, String joinStall) {
        String test = context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName();
        if (repetition > 0) {
            test += ".r" + repetition;
        }
        Path dir = directory(cell).resolve(test);
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
                joinStall,
                state.host.agentToken()));
    }

    /** {@code build/proofstone/<platform>@<mc>}, the directory of a single-server cell's bundles. */
    static Path directory(Cell cell) {
        return ROOT.resolve(cell.platform() + "@" + cell.mc());
    }

    /** {@code build/proofstone/<identity>}: the topology identity unchanged (ADR-0016 section 1). */
    static Path directory(TopologyCell cell) {
        return ROOT.resolve(cell.identity());
    }

    /** Writes the files, replacing an earlier bundle of the same test, and returns the directory. */
    static Path write(Path dir, Contents contents) {
        return write(dir, contents, List.of());
    }

    /**
     * As {@link #write(Path, Contents)}, and also redacts each of {@code forwardingSecrets}, a topology's forwarding
     * secret (ADR-0016 section 8), from every file, scanning again after writing.
     */
    static Path write(Path dir, Contents contents, List<String> forwardingSecrets) {
        if (contents.secret() == null || contents.secret().isBlank()) {
            throw new IllegalStateException("a repro bundle needs the agent token to redact it");
        }
        for (String secret : forwardingSecrets) {
            if (secret == null || secret.isBlank()) {
                throw new IllegalArgumentException("a forwarding secret to redact must not be blank");
            }
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
        if (contents.joinStall() != null) {
            files.put("join-stall.txt", contents.joinStall());
        }
        return writeFiles(dir, files, List.of(contents.secret()), forwardingSecrets);
    }

    /**
     * Writes {@code files} (path relative to {@code dir}, then content) after replacing every agent token and every
     * forwarding secret, then reads each file back: a secret still present is an error, never a silent leak.
     */
    static Path writeFiles(Path dir, Map<String, String> files, List<String> tokens, List<String> forwardingSecrets) {
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("a repro bundle needs the agent token to redact it");
            }
        }
        deleteTree(dir);
        try {
            for (Map.Entry<String, String> file : files.entrySet()) {
                String text = file.getValue();
                for (String token : tokens) {
                    text = redact(text, token);
                }
                for (String secret : forwardingSecrets) {
                    text = text.replace(secret, REDACTED_FORWARDING);
                }
                Path target = dir.resolve(file.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, text, StandardCharsets.UTF_8);
            }
            for (String name : files.keySet()) {
                String text = Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
                if (tokens.stream().anyMatch(text::contains)) {
                    throw new IllegalStateException("the agent token is in bundle file " + name + "; not left behind");
                }
                if (forwardingSecrets.stream().anyMatch(text::contains)) {
                    throw new IllegalStateException("the forwarding secret is in bundle file " + name
                            + "; not left behind");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the repro bundle to " + dir, e);
        }
        return dir;
    }

    /**
     * The bundle of a topology test (ADR-0016 sections 8 and 9): one directory per container ({@code proxy/},
     * {@code server-<name>/}) with its log and agent transcript, a snapshot per backend, and the client transcript, cell,
     * host and rerun at the top. Every agent token and the forwarding secret are redacted from every file.
     */
    static Path captureTopology(ExtensionContext context, TopologyCell cell, TopologyHost topology,
            List<String> driverTranscript, VersionsCatalog catalog, String driverId, String joinStall) {
        String test = context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName();
        Map<String, String> files = new LinkedHashMap<>();
        List<String> tokens = new ArrayList<>();
        files.put("proxy/proxy.log", topology.proxy().logs().all() + "\n");
        files.put("proxy/agent-transcript.jsonl", lines(topology.proxy().agentTranscript()));
        tokens.add(topology.proxy().agentToken());
        for (String name : topology.backendNames()) {
            PaperHost backend = topology.backend(name);
            String alias = TopologyConfig.alias(name);
            files.put(alias + "/server.log", backend.logs().all() + "\n");
            files.put(alias + "/agent-transcript.jsonl", lines(backend.agentTranscript()));
            files.put(alias + "/snapshot.json", snapshot(backend) + "\n");
            tokens.add(backend.agentToken());
        }
        files.put("driver-transcript.jsonl", lines(driverTranscript.stream()
                .map(line -> JSON.createObjectNode().put("line", line).toString()).toList()));
        files.put("cell.json", JSON.valueToTree(cell).toPrettyString() + "\n");
        files.put("host.json", hostInfo(catalog, driverId, false) + "\n");
        files.put("rerun.txt", rerunCommand(gradleTask(Path.of(repoRoot()), Path.of("")),
                context.getRequiredTestClass().getName() + "." + context.getRequiredTestMethod().getName(),
                cell.identity(), switches()));
        if (joinStall != null) {
            files.put("join-stall.txt", joinStall);
        }
        return writeFiles(directory(cell).resolve(test), files, tokens, List.of(topology.forwardingSecret()));
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
        String test = context.getRequiredTestClass().getName() + "." + context.getRequiredTestMethod().getName();
        return rerunCommand(gradleTask(Path.of(repoRoot()), Path.of("")), test, cell, switches());
    }

    private static String repoRoot() {
        String root = System.getProperty(ProofstoneExtension.REPO_ROOT);
        if (root == null) {
            throw new IllegalStateException("system property " + ProofstoneExtension.REPO_ROOT + " is not set");
        }
        return root;
    }

    /**
     * The command for one test on one cell. Every switch is passed, so the rerun reproduces the verdict: a repeat, a
     * control run, a conformance fault or a differential run only fails the same way with its switch on (T-M4-09, X4).
     *
     * @param gradleTask the Gradle test task, for example {@code :examples:bad-tests:test}
     * @param switches the system properties to pass, name to value; a null value is not allowed
     */
    static String rerunCommand(String gradleTask, String test, Cell cell, Map<String, String> switches) {
        return rerunCommand(gradleTask, test, cell.platform() + "@" + cell.mc(), switches);
    }

    /** As above, for a cell given by its identity, such as a topology's {@code velocity@4.3.1+paper@26.3}. */
    static String rerunCommand(String gradleTask, String test, String cellId, Map<String, String> switches) {
        StringBuilder command = new StringBuilder("bash scripts/dockerlock.sh ./gradlew ")
                .append(gradleTask)
                .append(" --tests '").append(test).append('\'')
                .append(" -Dproofstone.cells=").append(cellId);
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
    private static String snapshot(AgentEndpoint host) {
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
