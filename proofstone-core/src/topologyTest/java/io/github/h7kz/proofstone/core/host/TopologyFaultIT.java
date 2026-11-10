package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.dockerjava.api.DockerClient;
import io.github.h7kz.proofstone.core.driver.ClientDriver;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ServiceLoader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;

/**
 * Forced faults on a Velocity topology (T-M7-07 acceptance): a backend that never becomes ready, and a join that stalls
 * between the proxy and its backend. Each must be loud, name what failed, and leave no container of ours running.
 */
class TopologyFaultIT {

    /** Shorter than Velocity's read-timeout (30 s), so the client gives up while the proxy still waits on the backend. */
    private static final Duration STALL_TIMEOUT = Duration.ofSeconds(20);

    @AfterEach
    void nothingOfOursIsLeft() {
        System.out.println("docker ps after the test: " + Topologies.dockerPs());
        assertThat(Topologies.ourRunningContainers()).as("containers of ours still running").isEmpty();
    }

    @Test
    void aBackendThatNeverBecomesReadyStopsEveryContainerAndIsNamed(@TempDir Path dir) throws IOException {
        Topologies.requireEula();
        // A plugin jar that is not the agent: survival loads, but its agent never answers health, so it is never ready.
        Path notAnAgent = dir.resolve("not-an-agent.jar");
        try (OutputStream out = Files.newOutputStream(notAnAgent); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("not a plugin".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        Throwable failure = catchThrowable(() -> TopologyHost.start(Topologies.velocity(Topologies.BACKENDS),
                name -> name.equals("survival")
                        ? PaperHostOptions.of(notAnAgent, true).withAgentTimeout(Duration.ofSeconds(10))
                        : Topologies.backendOptions(),
                Topologies.velocityAgentJar()));

        assertThat(failure).isInstanceOf(PaperHostException.class)
                .hasMessageContaining("container server-survival did not become ready")
                .hasMessageContaining("agent /v1/health OK")
                .hasMessageContaining("--- server-lobby log tail ---");
        System.out.println("INVALID message of the forced failure:\n" + failure.getMessage());
    }

    @Test
    void aJoinStalledBehindTheProxyNamesTheProxyToBackendHop() {
        Topologies.requireEula();
        ClientDriver driver = ServiceLoader.load(ClientDriver.class).stream().map(ServiceLoader.Provider::get)
                .filter(d -> d.id().equals("mcpl")).findFirst().orElseThrow();
        try (TopologyHost topology = TopologyHost.start(Topologies.velocity(List.of("lobby")),
                name -> Topologies.backendOptions(), Topologies.velocityAgentJar())) {
            // A paused backend still accepts the proxy's TCP connection but never answers: a stall, not a refusal.
            DockerClient docker = DockerClientFactory.instance().client();
            String lobby = topology.backend("lobby").containerId();
            docker.pauseContainerCmd(lobby).exec();
            try {
                TopologyHost.JoinMarks marks = topology.mark();
                assertThatThrownBy(() -> {
                    try {
                        driver.connect(new ConnectSpec(topology.proxy().gameHost(), topology.proxy().gamePort(),
                                "T5a1", STALL_TIMEOUT)).close();
                    } catch (JoinTimeoutException e) {
                        JoinTimeoutException described = topology.describe(e, marks, "lobby");
                        System.out.println("join stall message:\n" + described.getMessage());
                        throw described;
                    }
                }).isInstanceOf(JoinTimeoutException.class)
                        .hasMessageStartingWith("timed out joining")
                        .hasMessageContaining("; hop: proxy to backend lobby")
                        .hasMessageContaining("stalled stage: ")
                        .hasMessageContaining("--- proxy log tail ---")
                        .hasMessageContaining("--- server-lobby log tail ---")
                        .hasMessageNotContaining(topology.forwardingSecret());
            } finally {
                // Docker refuses to stop a paused container, so it is resumed before the topology closes.
                docker.unpauseContainerCmd(lobby).exec();
            }
        }
    }
}
