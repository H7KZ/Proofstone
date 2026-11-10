package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.driver.ClientDriver;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import io.github.h7kz.proofstone.core.cell.TopologyCell;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * One proxy and two {@code paper@26.3} backends (T-M7-07): every container is ready with agent health, the proxy sees
 * both backends online through the forwarding config it was given, and a client joins through the proxy to the default
 * backend. One subclass per proxy, so one proxy runs per invocation (M7 flake rule).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class TopologyContract {

    /** As the extension joins (CellInvocation). A join timeout is reported with its hop, never retried. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration LOGIN_SEEN = Duration.ofSeconds(10);

    private TopologyHost topology;

    /** The planned topology cell of this proxy (versions.yml schema 3) with the given backends. */
    abstract TopologyCell cell(List<String> backends);

    abstract Path proxyAgentJar();

    @BeforeAll
    void start() {
        Topologies.requireEula();
        topology = TopologyHost.start(cell(Topologies.BACKENDS), name -> Topologies.backendOptions(),
                proxyAgentJar());
    }

    @AfterAll
    void stop() {
        if (topology == null) {
            return;
        }
        topology.close();
        List<String> left = Topologies.ourRunningContainers();
        System.out.println("docker ps after close: " + Topologies.dockerPs());
        assertThat(left).as("containers of ours still running after close").isEmpty();
    }

    @Test
    void everyContainerAnswersHealth() {
        ProxyHost proxy = topology.proxy();
        assertThat(proxy.health().platform()).isEqualTo(cell(Topologies.BACKENDS).proxy().id());
        System.out.println("proxy health: " + proxy.health());
        assertThat(topology.backendNames()).containsExactlyElementsOf(Topologies.BACKENDS);
        for (String name : topology.backendNames()) {
            PaperHost backend = topology.backend(name);
            assertThat(backend.health().platform()).as(name).isEqualTo("paper");
            assertThat(backend.health().mcVersion()).as(name).isEqualTo("26.3");
            System.out.println("backend " + name + " health: " + backend.health());
        }
    }

    @Test
    void theProxySeesEveryBackendOnlineAtItsAlias() {
        JsonNode servers = topology.proxy().request("GET", "/v1/proxy/servers", null).get("servers");
        List<String> seen = new ArrayList<>();
        for (JsonNode server : servers) {
            String name = server.get("name").asText();
            seen.add(name);
            assertThat(server.get("address").asText()).as(name).isEqualTo(TopologyConfig.alias(name) + ":25565");
            assertThat(server.get("online").asBoolean()).as(name + " online").isTrue();
        }
        assertThat(seen).containsExactlyInAnyOrderElementsOf(Topologies.BACKENDS);
    }

    @Test
    void aClientJoinsThroughTheProxyAndLandsOnTheDefaultBackend() {
        String player = "T7e1";
        ClientDriver driver = ServiceLoader.load(ClientDriver.class).stream().map(ServiceLoader.Provider::get)
                .filter(d -> d.id().equals("mcpl")).findFirst()
                .orElseThrow(() -> new IllegalStateException("no mcpl driver on the topologyTest classpath"));
        TopologyHost.JoinMarks marks = topology.mark();
        ClientSession session;
        try {
            session = driver.connect(new ConnectSpec(topology.proxy().gameHost(), topology.proxy().gamePort(), player,
                    CONNECT_TIMEOUT));
        } catch (JoinTimeoutException e) {
            throw topology.describe(e, marks, "lobby");
        }
        try {
            // Forwarding works when the default backend logs the player in through the proxy (the hop rule's evidence).
            Await.until("lobby logs " + player + " logged in", () -> topology.hop(player, marks, "lobby").name(),
                    hop -> hop.startsWith("after both hops"), LOGIN_SEEN);
            assertThat(topology.backend("survival").logs().since(marks.backends().get("survival")))
                    .noneMatch(line -> line.contains(player + "["));
        } finally {
            session.close();
        }
    }
}
