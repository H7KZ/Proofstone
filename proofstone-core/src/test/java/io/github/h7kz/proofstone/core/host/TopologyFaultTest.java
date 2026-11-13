package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The injectable topology faults of T-M7-13: how they parse and what each one changes in the rendered files. */
class TopologyFaultTest {

    private static final String SECRET = "5e".repeat(32);
    private static final List<String> BACKENDS = List.of("lobby", "survival");

    @Test
    void noValueAndFalseMeanNoFault() {
        assertThat(TopologyFault.parse(null)).isEqualTo(TopologyFault.NONE);
        assertThat(TopologyFault.parse("")).isEqualTo(TopologyFault.NONE);
        assertThat(TopologyFault.parse("false")).isEqualTo(TopologyFault.NONE);
    }

    @Test
    void anUnknownFaultIsAnErrorNamingTheChoices() {
        assertThatThrownBy(() -> TopologyFault.parse("forwarding-of"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proofstone.injectTopologyFault")
                .hasMessageContaining("\"forwarding-of\"")
                .hasMessageContaining("wrong-secret")
                .hasMessageContaining("unreachable-backend");
    }

    @Test
    void aWrongSecretOnlyMeansSomethingWhereThereIsASecret() {
        TopologyFault.WRONG_SECRET.requireApplicable(ProxyKind.VELOCITY);
        assertThatThrownBy(() -> TopologyFault.WRONG_SECRET.requireApplicable(ProxyKind.BUNGEECORD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wrong-secret")
                .hasMessageContaining("bungeecord")
                .hasMessageContaining("no forwarding secret");
        TopologyFault.FORWARDING_OFF.requireApplicable(ProxyKind.BUNGEECORD);
    }

    @Test
    void aWrongSecretChangesOnlyTheFirstBackendsSecret() {
        Map<String, String> lobby = TopologyConfig.backendFiles(ProxyKind.VELOCITY, SECRET, TopologyFault.WRONG_SECRET,
                true);
        Map<String, String> survival = TopologyConfig.backendFiles(ProxyKind.VELOCITY, SECRET,
                TopologyFault.WRONG_SECRET, false);

        assertThat(lobby.get("/config/paper-global.yml")).contains("enabled: true").contains("secret: ")
                .doesNotContain(SECRET);
        assertThat(lobby.get("/config/paper-global.yml")).isNotEqualTo(
                TopologyConfig.backendFiles(ProxyKind.VELOCITY, SECRET).get("/config/paper-global.yml"));
        assertThat(survival).isEqualTo(TopologyConfig.backendFiles(ProxyKind.VELOCITY, SECRET));
        // The proxy keeps the real secret, so the two sides disagree.
        assertThat(TopologyConfig.proxyFiles(ProxyKind.VELOCITY, BACKENDS, SECRET, TopologyFault.WRONG_SECRET))
                .isEqualTo(TopologyConfig.proxyFiles(ProxyKind.VELOCITY, BACKENDS, SECRET));
    }

    @Test
    void forwardingOffSwitchesTheProxyOffAndLeavesTheBackendsOn() {
        String velocity = TopologyConfig.proxyFiles(ProxyKind.VELOCITY, BACKENDS, SECRET, TopologyFault.FORWARDING_OFF)
                .get("/server/velocity.toml");
        assertThat(velocity).contains("player-info-forwarding-mode = \"none\"")
                .doesNotContain("player-info-forwarding-mode = \"modern\"");
        String bungee = TopologyConfig.proxyFiles(ProxyKind.BUNGEECORD, BACKENDS, SECRET, TopologyFault.FORWARDING_OFF)
                .get("/server/config.yml");
        assertThat(bungee).contains("ip_forward: false").doesNotContain("ip_forward: true");
        assertThat(TopologyConfig.backendFiles(ProxyKind.BUNGEECORD, SECRET, TopologyFault.FORWARDING_OFF, true))
                .isEqualTo(TopologyConfig.backendFiles(ProxyKind.BUNGEECORD, SECRET));
    }

    @Test
    void backendForwardingOffSwitchesOnlyTheFirstBackendOffAndLeavesTheProxyOn() {
        assertThat(TopologyConfig.backendFiles(ProxyKind.BUNGEECORD, SECRET, TopologyFault.BACKEND_FORWARDING_OFF, true)
                .get("/data/spigot.yml")).contains("bungeecord: false").doesNotContain("bungeecord: true");
        assertThat(TopologyConfig.backendFiles(ProxyKind.VELOCITY, SECRET, TopologyFault.BACKEND_FORWARDING_OFF, true)
                .get("/config/paper-global.yml")).contains("enabled: false").doesNotContain("enabled: true");
        assertThat(TopologyConfig.backendFiles(ProxyKind.BUNGEECORD, SECRET, TopologyFault.BACKEND_FORWARDING_OFF,
                false)).isEqualTo(TopologyConfig.backendFiles(ProxyKind.BUNGEECORD, SECRET));
        assertThat(TopologyConfig.proxyFiles(ProxyKind.BUNGEECORD, BACKENDS, SECRET,
                TopologyFault.BACKEND_FORWARDING_OFF))
                .isEqualTo(TopologyConfig.proxyFiles(ProxyKind.BUNGEECORD, BACKENDS, SECRET));
    }

    @Test
    void anUnreachableBackendPointsTheProxyAtAnAddressNothingAnswersOn() {
        String velocity = TopologyConfig.proxyFiles(ProxyKind.VELOCITY, BACKENDS, SECRET,
                TopologyFault.UNREACHABLE_BACKEND).get("/server/velocity.toml");
        assertThat(velocity).contains("lobby = \"server-lobby:25565\"")
                .contains("survival = \"server-survival-unreachable:25565\"")
                .doesNotContain("survival = \"server-survival:25565\"");
        String bungee = TopologyConfig.proxyFiles(ProxyKind.BUNGEECORD, BACKENDS, SECRET,
                TopologyFault.UNREACHABLE_BACKEND).get("/server/config.yml");
        assertThat(bungee).contains("address: server-lobby:25565")
                .contains("address: server-survival-unreachable:25565");
        assertThat(TopologyConfig.backendFiles(ProxyKind.VELOCITY, SECRET, TopologyFault.UNREACHABLE_BACKEND, true))
                .isEqualTo(TopologyConfig.backendFiles(ProxyKind.VELOCITY, SECRET));
    }

    @Test
    void noFaultRendersExactlyTheCleanFiles() {
        for (ProxyKind kind : ProxyKind.values()) {
            assertThat(TopologyConfig.proxyFiles(kind, BACKENDS, SECRET, TopologyFault.NONE))
                    .isEqualTo(TopologyConfig.proxyFiles(kind, BACKENDS, SECRET));
            assertThat(TopologyConfig.backendFiles(kind, SECRET, TopologyFault.NONE, true))
                    .isEqualTo(TopologyConfig.backendFiles(kind, SECRET));
        }
    }

    @Test
    void aKickedJoinNamesTheHopTheBackendAndBothLogTails() {
        ClientDriverException kicked = new ClientDriverException("could not join proxy:25577 as T1: Unable to verify");
        JoinHop hop = new JoinHop("proxy to backend lobby", "proxy logged \"T1 has connected\"");

        ClientDriverException described = TopologyHost.describeKick(kicked, hop, "proxy tail",
                Map.of("lobby", "lobby tail"), SECRET);

        assertThat(described).hasMessageStartingWith("could not join proxy:25577 as T1: Unable to verify")
                .hasMessageContaining("; hop: proxy to backend lobby (proxy logged")
                .hasMessageContaining("--- proxy log tail ---\nproxy tail")
                .hasMessageContaining("--- server-lobby log tail ---\nlobby tail");
        assertThat(described.getCause()).isSameAs(kicked);
    }
}
