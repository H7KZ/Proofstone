package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.TopologyCell;
import io.github.h7kz.proofstone.core.versions.Status;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundleWriterTest {

    /** A stand-in for the agent token: 48 hex digits, like the real one. It must never reach a bundle file. */
    private static final String TOKEN = "9f3c0a1e7b2d4f6a8c0e1b3d5f7a9c2e4b6d8f0a1c3e5b7d";

    private static final Cell CELL = new Cell("paper", "1.8.8", 445, "ab".repeat(32), "java8",
            Status.REQUIRED, "eclipse-temurin", "sha256:" + "cd".repeat(32), List.of());

    @TempDir
    Path tmp;

    private static BundleWriter.Contents contents(String leakyLog, String viaproxyLog) {
        return contents(leakyLog, viaproxyLog, null);
    }

    private static BundleWriter.Contents contents(String leakyLog, String viaproxyLog, String joinStall) {
        return contents(leakyLog, viaproxyLog, joinStall, CELL);
    }

    private static BundleWriter.Contents contents(String leakyLog, String viaproxyLog, String joinStall,
            Object cell) {
        return new BundleWriter.Contents(
                leakyLog,
                viaproxyLog,
                List.of("{\"call\":\"POST /v1/command\",\"reply\":\"token " + TOKEN + "\"}"),
                List.of("12:00:00 handshake", "12:00:01 " + TOKEN),
                cell,
                "{\"error\":\"not found\"}\n",
                "{\"os\":\"Windows 11\",\"docker\":\"29.9.2\",\"viaproxy\":{\"digest\":\"sha256:46ee\"}}\n",
                "bash scripts/dockerlock.sh ./gradlew :examples:smoke:test\n",
                joinStall,
                TOKEN);
    }

    @Test
    void aTopologyCellIsRecordedUnderItsIdentityWithTheProxyAndBackendPins() throws IOException {
        TopologyCell topology = new TopologyCell("velocity@4.3.1+paper@26.3",
                new TopologyCell.Proxy("velocity", "4.3.1", 45, "ef".repeat(32), "java25", "modern", "itzg/mc-proxy",
                        "sha256:" + "12".repeat(32), Status.EXPERIMENTAL),
                new Cell("paper", "26.3", 244, "ab".repeat(32), "java25", Status.REQUIRED, "itzg/minecraft-server",
                        "sha256:" + "cd".repeat(32), List.of()),
                List.of("lobby", "survival"), Status.EXPERIMENTAL, Status.EXPERIMENTAL, List.of());

        Path dir = BundleWriter.write(tmp.resolve("velocity@4.3.1+paper@26.3").resolve("T.test"),
                contents("log", null, null, topology));

        String json = Files.readString(dir.resolve("cell.json"));
        assertThat(json).contains("\"identity\" : \"velocity@4.3.1+paper@26.3\"", "\"build\" : 45",
                "\"forwarding\" : \"modern\"", "\"hostDigest\" : \"sha256:" + "12".repeat(32) + "\"",
                "\"mc\" : \"26.3\"", "\"build\" : 244", "\"lobby\"", "\"survival\"");
        assertThat(BundleWriter.directory(topology).getFileName().toString()).isEqualTo("velocity@4.3.1+paper@26.3");
        assertThat(BundleWriter.directory(CELL).getFileName().toString()).isEqualTo("paper@1.8.8");
    }

    @Test
    void aJoinStallIsWrittenAndRedactedAndAbsentWhenNoJoinStalled() throws IOException {
        Path with = BundleWriter.write(tmp.resolve("with"),
                contents("log", null, "join stall\nserver log tail at the timeout:\nagent " + TOKEN + "\n"));
        Path without = BundleWriter.write(tmp.resolve("without"), contents("log", null));

        assertThat(Files.readString(with.resolve("join-stall.txt"))).contains("join stall")
                .contains(BundleWriter.REDACTED).doesNotContain(TOKEN);
        assertThat(Files.exists(without.resolve("join-stall.txt"))).isFalse();
    }

    @Test
    void theTokenIsAbsentFromEveryBundleFile() throws IOException {
        Path dir = BundleWriter.write(tmp.resolve("bundle"),
                contents("Done (1.2s)\nagent started with " + TOKEN, "viaproxy listening " + TOKEN));

        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                assertThat(text).as(file.getFileName().toString()).doesNotContain(TOKEN);
            }
        }
        assertThat(Files.readString(dir.resolve("server.log"))).contains(BundleWriter.REDACTED);
        assertThat(Files.readString(dir.resolve("driver-transcript.jsonl"))).contains(BundleWriter.REDACTED);
    }

    @Test
    void theForwardingSecretIsAbsentFromEveryBundleFileEvenInACopiedConfig() throws IOException {
        // ADR-0016 section 8: a topology's forwarding secret is redacted like the token, from logs and copied configs.
        String secret = io.github.h7kz.proofstone.core.host.TopologyConfig.newSecret();
        String config = io.github.h7kz.proofstone.core.host.TopologyConfig
                .backendFiles(io.github.h7kz.proofstone.core.host.ProxyKind.VELOCITY, secret)
                .get("/config/paper-global.yml");
        Path dir = BundleWriter.write(tmp.resolve("bundle"),
                contents("paper-global.yml:\n" + config, "proxy log " + secret, "stall " + secret), List.of(secret));

        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                assertThat(text).as(file.getFileName().toString()).doesNotContain(secret).doesNotContain(TOKEN);
            }
        }
        assertThat(Files.readString(dir.resolve("server.log"))).contains("secret: " + BundleWriter.REDACTED_FORWARDING);
        assertThatThrownBy(() -> BundleWriter.write(tmp.resolve("blank"), contents("log", null), List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSecretInAnyJsonLineIsStillRedacted() throws IOException {
        // The agent transcript is JSON; redaction works on the text, so a quoted token is caught too.
        Path dir = BundleWriter.write(tmp.resolve("bundle"), contents("log", null));
        assertThat(Files.readString(dir.resolve("agent-transcript.jsonl"))).doesNotContain(TOKEN)
                .contains(BundleWriter.REDACTED);
    }

    @Test
    void aForcedFailureWritesEveryRequiredFile() throws IOException {
        Path dir = BundleWriter.write(tmp.resolve("bundle"), contents("log", "viaproxy log"));

        assertThat(Files.list(dir).map(p -> p.getFileName().toString()).toList()).containsExactlyInAnyOrder(
                "server.log", "viaproxy.log", "agent-transcript.jsonl", "driver-transcript.jsonl", "cell.json",
                "snapshot.json", "host.json", "rerun.txt");
        assertThat(Files.readString(dir.resolve("cell.json"))).contains("\"mc\" : \"1.8.8\"", "\"sha256\"");
        assertThat(Files.readString(dir.resolve("rerun.txt"))).contains("examples:smoke:test");
    }

    @Test
    void theHostFileRecordsTheHostAndThePins() throws IOException {
        Path dir = BundleWriter.write(tmp.resolve("bundle"), contents("log", "viaproxy log"));

        assertThat(Files.readString(dir.resolve("host.json"))).contains("\"docker\"", "\"digest\"");
    }

    @Test
    void aRerunNamesEveryConfiguredSwitchThatReproducesTheVerdict() {
        String command = BundleWriter.rerunCommand(":examples:bad-tests:test", "dev.x.FlakyE2E.theOutcome", CELL,
                java.util.Map.of(FlakeDetector.REPEAT, "5", VerdictGate.CONTROL_RUN, "true",
                        CellConformance.INJECT, "true"));

        assertThat(command).contains("--tests 'dev.x.FlakyE2E.theOutcome'", "-Dproofstone.cells=paper@1.8.8",
                "-Dproofstone.repeat=5", "-Dproofstone.controlRun=true",
                "-Dproofstone.injectConformanceFault=true", "--no-parallel");
    }

    @Test
    void aRerunOmitsASwitchThatWasNotSet() {
        String command = BundleWriter.rerunCommand(":test", "dev.x.T.m", CELL, java.util.Map.of());

        assertThat(command).doesNotContain("-Dproofstone.repeat", "-Dproofstone.controlRun");
    }

    @Test
    void anUnbridgedCellHasNoViaProxyLog() throws IOException {
        Path dir = BundleWriter.write(tmp.resolve("bundle"), contents("log", null));
        assertThat(Files.exists(dir.resolve("viaproxy.log"))).isFalse();
    }

    @Test
    void anEarlierBundleOfTheSameTestDoesNotLeakItsFiles() throws IOException {
        Path dir = tmp.resolve("bundle");
        BundleWriter.write(dir, contents("log", "old viaproxy log"));
        BundleWriter.write(dir, contents("log", null));
        assertThat(Files.exists(dir.resolve("viaproxy.log"))).as("stale file from a bridged run").isFalse();
    }

    @Test
    void theWriterRefusesToWriteWithoutTheTokenToRedact() {
        assertThatThrownBy(() -> BundleWriter.write(tmp.resolve("bundle"),
                new BundleWriter.Contents("log", null, List.of(), List.of(), CELL, "{}", "{}", "", null,
                        " ")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void gradleTaskNamesTheProjectOfTheTestDirectory() {
        Path root = Path.of("/repo");
        assertThat(BundleWriter.gradleTask(root, Path.of("/repo/examples/smoke"))).isEqualTo(":examples:smoke:test");
        assertThat(BundleWriter.gradleTask(root, Path.of("/repo"))).isEqualTo(":test");
    }
}
