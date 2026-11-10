package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Start order, cleanup and the per-hop join-timeout text of {@link TopologyHost}, without Docker. */
class TopologyHostTest {

    private static final String SECRET = "a7".repeat(32);

    /** Records what starts and what closes, in order. A name in {@code failing} throws instead of starting. */
    private static final class Recorder implements TopologyHost.Launcher, AutoCloseable {
        final List<String> events = new ArrayList<>();
        final List<String> failing;

        Recorder(String... failing) {
            this.failing = List.of(failing);
        }

        @Override
        public TopologyHost.Node backend(String name) {
            return start(TopologyConfig.alias(name));
        }

        @Override
        public TopologyHost.Node proxy() {
            return start(TopologyHost.PROXY_ALIAS);
        }

        private TopologyHost.Node start(String id) {
            events.add("start " + id);
            if (failing.contains(id)) {
                // A host that fails closes itself, as PaperHost and ProxyHost do, and its message carries its own tail.
                events.add("close " + id);
                throw new PaperHostException(id + " timed out waiting for Done (; secret " + SECRET
                        + "\n--- log tail ---\n" + id + " booting");
            }
            return new TopologyHost.Node(id, () -> events.add("close " + id), () -> id + " last line");
        }

        @Override
        public void close() {
            events.add("close network");
        }
    }

    @Test
    void everyBackendStartsBeforeTheProxy() {
        Recorder recorder = new Recorder();

        List<TopologyHost.Node> nodes = TopologyHost.startInOrder(List.of("lobby", "survival"), recorder, recorder,
                SECRET);

        assertThat(recorder.events).containsExactly("start server-lobby", "start server-survival", "start proxy");
        assertThat(nodes).extracting(TopologyHost.Node::id).containsExactly("server-lobby", "server-survival", "proxy");
    }

    @Test
    void aFailedSecondBackendStopsTheFirstAndTheNetworkAndNamesTheContainer() {
        Recorder recorder = new Recorder("server-survival");

        assertThatThrownBy(() -> TopologyHost.startInOrder(List.of("lobby", "survival"), recorder, recorder, SECRET))
                .isInstanceOf(PaperHostException.class)
                .hasMessageContaining("container server-survival did not become ready")
                .hasMessageContaining("server-survival booting")
                .hasMessageContaining("--- server-lobby log tail ---\nserver-lobby last line")
                .hasMessageNotContaining(SECRET)
                .hasMessageContaining(TopologyHost.REDACTED);
        // The proxy never starts, the first backend and the network are closed, and nothing is left behind.
        assertThat(recorder.events).containsExactly("start server-lobby", "start server-survival",
                "close server-survival", "close server-lobby", "close network");
    }

    @Test
    void aFailedProxyStopsEveryBackendInReverseOrder() {
        Recorder recorder = new Recorder(TopologyHost.PROXY_ALIAS);

        assertThatThrownBy(() -> TopologyHost.startInOrder(List.of("lobby", "survival"), recorder, recorder, SECRET))
                .hasMessageContaining("container proxy did not become ready")
                .hasMessageContaining("--- server-lobby log tail ---")
                .hasMessageContaining("--- server-survival log tail ---");
        assertThat(recorder.events).containsExactly("start server-lobby", "start server-survival", "start proxy",
                "close proxy", "close server-survival", "close server-lobby", "close network");
    }

    @Test
    void aCloseThatFailsStillClosesTheRestAndKeepsTheStartFailure() {
        Recorder recorder = new Recorder("server-survival");
        TopologyHost.Launcher launcher = new TopologyHost.Launcher() {
            @Override
            public TopologyHost.Node backend(String name) {
                if (name.equals("lobby")) {
                    recorder.events.add("start server-lobby");
                    return new TopologyHost.Node("server-lobby", () -> {
                        throw new IllegalStateException("docker refused stop");
                    }, () -> "tail");
                }
                return recorder.backend(name);
            }

            @Override
            public TopologyHost.Node proxy() {
                return recorder.proxy();
            }
        };

        assertThatThrownBy(() -> TopologyHost.startInOrder(List.of("lobby", "survival"), launcher, recorder, SECRET))
                .hasMessageContaining("server-survival did not become ready")
                .satisfies(e -> assertThat(e.getSuppressed()).extracting(Throwable::getMessage)
                        .anySatisfy(m -> assertThat(m).contains("server-lobby")));
        assertThat(recorder.events).endsWith("close network");
    }

    // The hop of a join that timed out (ADR-0016 section 9).

    @Test
    void noProxyLineNamingThePlayerIsTheClientToProxyHop() {
        JoinHop hop = JoinHop.of("T1", List.of("[connected player] T10 (/172.18.0.1:5) has connected"),
                Map.of("lobby", List.of()), "lobby");

        assertThat(hop.name()).isEqualTo("client to proxy");
    }

    @Test
    void aProxyLineWithNoBackendLoginIsTheProxyToBackendHop() {
        JoinHop hop = JoinHop.of("T1", List.of("[connected player] T1 (/172.18.0.1:5) has connected"),
                Map.of("lobby", List.of("T10[/172.18.0.1:6] logged in with entity id 7"),
                        "survival", List.of("T1[/172.18.0.1:5] logged in with entity id 8")), "lobby");

        assertThat(hop.name()).isEqualTo("proxy to backend lobby");
        assertThat(hop.evidence()).contains("[connected player] T1");
    }

    @Test
    void aBackendLoginMeansNeitherHopStalled() {
        JoinHop hop = JoinHop.of("T1", List.of("[T1|/172.18.0.1:5] <-> InitialHandler has connected"),
                Map.of("lobby", List.of("T1[/172.18.0.1:5] logged in with entity id 7")), "lobby");

        assertThat(hop.name()).isEqualTo("after both hops (backend lobby logged the player in)");
    }

    // A server switch (T-M7-09): the client to proxy hop was already up, so only the target's login decides.

    @Test
    void aSwitchWithNoLoginOnTheTargetStoppedAtTheProxyToBackendHop() {
        JoinHop hop = JoinHop.ofSwitch("T1", List.of(), List.of("T10[/172.18.0.1:6] logged in with entity id 7"),
                "survival");

        assertThat(hop.name()).isEqualTo("proxy to backend survival");
        assertThat(hop.evidence()).contains("client to proxy was up before the switch")
                .contains("no proxy log line since the command names the player");
    }

    @Test
    void aSwitchWithTheProxyLoggingTheAttemptQuotesIt() {
        JoinHop hop = JoinHop.ofSwitch("T1", List.of("[connected player] T1 -> survival has connected"), List.of(),
                "survival");

        assertThat(hop.name()).isEqualTo("proxy to backend survival");
        assertThat(hop.evidence()).contains("[connected player] T1 -> survival has connected");
    }

    @Test
    void aSwitchWithALoginOnTheTargetStoppedAtNeitherHop() {
        JoinHop hop = JoinHop.ofSwitch("T1", List.of(), List.of("T1[/172.18.0.1:5] logged in with entity id 7"),
                "survival");

        assertThat(hop.name()).isEqualTo("after both hops (backend survival logged the player in)");
    }

    @Test
    void anUnknownTargetIsAnError() {
        assertThatThrownBy(() -> JoinHop.of("T1", List.of(), Map.of("lobby", List.of()), "ghost"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ghost");
    }

    @Test
    void theTimeoutMessageNamesHopStageAndBothTails() {
        JoinTimeoutException timeout = new JoinTimeoutException("timed out joining T1 -> 127.0.0.1:1: stalled",
                null, "T1", "CONFIGURATION_STALLED", "777", "ClientboundLoginFinishedPacket");
        JoinHop hop = JoinHop.of("T1", List.of("[connected player] T1 (/x) has connected"),
                Map.of("lobby", List.of()), "lobby");
        Map<String, String> backendTails = new LinkedHashMap<>();
        backendTails.put("lobby", "lobby tail " + SECRET);
        backendTails.put("survival", "survival tail");

        JoinTimeoutException described = TopologyHost.describe(timeout, hop, "proxy tail", backendTails, SECRET);

        assertThat(described.getMessage())
                .startsWith("timed out joining T1")
                .contains("; hop: proxy to backend lobby")
                .contains("stalled stage: CONFIGURATION_STALLED")
                .contains("--- proxy log tail ---\nproxy tail")
                .contains("--- server-lobby log tail ---\nlobby tail")
                .contains("--- server-survival log tail ---\nsurvival tail")
                .doesNotContain(SECRET);
        assertThat(described.stage()).isEqualTo("CONFIGURATION_STALLED");
        assertThat(described.player()).isEqualTo("T1");
    }
}
