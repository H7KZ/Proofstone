package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.DisagreeException;
import io.github.h7kz.proofstone.core.junit.TopologyScenarios.DirectJoin;
import io.github.h7kz.proofstone.core.junit.TopologyScenarios.Identity;
import io.github.h7kz.proofstone.core.verdict.ConformanceResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** P01 and P02 (ADR-0016 sections 6 and 7) on scripted agents: what passes, and which disagreement is DISAGREE. */
class TopologyScenariosTest {

    private static final String PLAYER = "PSConf";
    private static final List<String> BACKENDS = List.of("lobby", "survival");
    private static final String OFFLINE = UUID.nameUUIDFromBytes(("OfflinePlayer:" + PLAYER)
            .getBytes(StandardCharsets.UTF_8)).toString();

    /** A topology where everything agrees; each test breaks one thing. */
    private static class World implements TopologyScenarios.World {
        final List<String> calls = new ArrayList<>();
        String proxyServer = "lobby";
        List<String> proxyPlayers = List.of(PLAYER);
        List<String> lobbyPlayers = List.of(PLAYER);
        Identity proxyIdentity = new Identity(PLAYER, OFFLINE);
        Identity backendIdentity = new Identity(PLAYER, OFFLINE);
        List<String> events = List.of(PLAYER + "@lobby");
        String proxyAddress = "172.25.0.1";
        String backendAddress = "172.25.0.1";
        DirectJoin direct = new DirectJoin(false, "This server requires you to connect with Velocity.");
        RuntimeException joinFailure;

        @Override
        public String subscribeJoinProbe() {
            calls.add("subscribe");
            return "probe-1";
        }

        @Override
        public void join(String player) {
            calls.add("join " + player);
            if (joinFailure != null) {
                throw joinFailure;
            }
        }

        @Override
        public void leave(String player) {
            calls.add("leave " + player);
        }

        @Override
        public String proxyServer(String player) {
            return proxyServer;
        }

        @Override
        public List<String> proxyPlayers() {
            return proxyPlayers;
        }

        @Override
        public List<String> backendPlayers(String backend) {
            return backend.equals("lobby") ? lobbyPlayers : List.of();
        }

        @Override
        public Identity proxyIdentity(String player) {
            return proxyIdentity;
        }

        @Override
        public Identity backendIdentity(String backend, String player) {
            return backendIdentity;
        }

        @Override
        public List<String> joinEvents(String probe) {
            return events;
        }

        @Override
        public String clientAddressSeenByProxy(String player) {
            return proxyAddress;
        }

        @Override
        public String clientAddressSeenByBackend(String backend, String player) {
            return backendAddress;
        }

        @Override
        public DirectJoin directJoin(String backend, String player) {
            calls.add("direct " + backend + " " + player);
            return direct;
        }
    }

    private static ConformanceResult run(World world) {
        return TopologyScenarios.run("velocity@4.3.1+paper@26.3", "velocity", BACKENDS, world, Duration.ofMillis(300));
    }

    @Test
    void everyOracleAgreeingPassesBothScenariosAndTheClientLeaves() {
        World world = new World();

        ConformanceResult result = run(world);

        assertThat(result.passed()).as(String.valueOf(result.detail())).isTrue();
        assertThat(world.calls).containsExactly("subscribe", "join PSConf", "direct lobby PSDirect", "leave PSConf");
    }

    @Test
    void theOfflineUuidIsTheNameBasedOneOfThatName() {
        assertThat(TopologyScenarios.offlineUuid(PLAYER)).isEqualTo(OFFLINE);
        assertThat(TopologyScenarios.offlineUuid("Spike2")).isEqualTo("d8520406-990d-3901-ba26-f0302c0f873a");
    }

    @Test
    void aBackendUuidThatDiffersFromTheProxysIsDisagreeNotAPass() {
        World world = new World();
        world.backendIdentity = new Identity(PLAYER, "11111111-2222-3333-4444-555555555555");

        assertThatThrownBy(() -> TopologyScenarios.judgeForwarding(PLAYER, world.proxyIdentity,
                world.backendIdentity, "1.2.3.4", "1.2.3.4")).isInstanceOf(DisagreeException.class)
                .hasMessageContaining("DISAGREE").hasMessageContaining("11111111-2222-3333-4444-555555555555")
                .hasMessageContaining(OFFLINE);
        ConformanceResult result = run(world);
        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P02").contains("DISAGREE");
    }

    @Test
    void aBackendNameThatDiffersFromTheProxysIsDisagree() {
        World world = new World();
        world.backendIdentity = new Identity("Other", OFFLINE);

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P02").contains("DISAGREE").contains("Other");
    }

    @Test
    void aForwardedAddressThatIsNotTheClientsIsDisagree() {
        World world = new World();
        world.backendAddress = "172.25.0.9";

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P02").contains("DISAGREE").contains("172.25.0.9")
                .contains("172.25.0.1");
    }

    @Test
    void anIdentityBothSidesAgreeOnButIsNotTheOfflineUuidIsAFailureNotAPass() {
        World world = new World();
        String other = "11111111-2222-3333-4444-555555555555";
        world.proxyIdentity = new Identity(PLAYER, other);
        world.backendIdentity = new Identity(PLAYER, other);

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P02").contains("FAIL").contains("offline").contains(other);
    }

    @Test
    void aClientInPlayWhoseProxyDoesNotListItIsDisagree() {
        World world = new World();
        world.proxyPlayers = List.of();

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P01").contains("DISAGREE").contains("proxy agent");
    }

    @Test
    void aProxyThatPutsThePlayerOnAnotherServerThanTheBackendThatListsItIsDisagree() {
        World world = new World();
        world.proxyServer = "survival";

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P01").contains("DISAGREE").contains("survival").contains("lobby");
    }

    @Test
    void aBackendThatDoesNotListThePlayerIsDisagree() {
        World world = new World();
        world.lobbyPlayers = List.of();

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P01").contains("DISAGREE").contains("backend lobby");
    }

    @Test
    void aProxyEventProbeThatNeverSawTheJoinIsAFailure() {
        World world = new World();
        world.events = List.of();

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P01").contains("event probe").contains("PSConf@lobby");
    }

    @Test
    void aJoinThatFailsIsAFailedScenarioAndTheClientStillLeaves() {
        World world = new World();
        world.joinFailure = new IllegalStateException("timed out joining PSConf");

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P01").contains("timed out joining PSConf");
        assertThat(world.calls).contains("leave PSConf").doesNotContain("direct lobby PSDirect");
    }

    @Test
    void velocityModernForwardingMustRefuseADirectJoin() {
        World world = new World();
        world.direct = new DirectJoin(true, "joined");

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P02").contains("direct join").contains("lobby");
    }

    @Test
    void aVelocityRefusalThatIsNotTheForwardingRefusalIsNotAPass() {
        // Any kick would read as "refused"; the message must be the one Paper gives for a missing Velocity forward.
        World world = new World();
        world.direct = new DirectJoin(false, "Connection throttled! Please wait before reconnecting.");

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P02").contains("direct join").contains("Connection throttled");
    }

    @Test
    void anEmptyDirectJoinMessageIsAnErrorNotARefusal() {
        assertThatThrownBy(() -> TopologyScenarios.judgeDirectJoin("velocity", "lobby", new DirectJoin(false, " ")))
                .isInstanceOf(AssertionError.class).hasMessageContaining("no reason");
    }

    @Test
    void anUnknownProxyHasNoDirectJoinExpectation() {
        assertThatThrownBy(() -> TopologyScenarios.judgeDirectJoin("waterfall", "lobby", new DirectJoin(false, "x")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("waterfall");
    }

    @Test
    void theJoinEventProbeClassIsKnownForEveryProxyAndNeverGuessed() {
        assertThat(TopologyScenarios.joinEventClass("velocity"))
                .isEqualTo("com.velocitypowered.api.event.player.ServerConnectedEvent");
        assertThat(TopologyScenarios.joinEventClass("bungeecord"))
                .isEqualTo("net.md_5.bungee.api.event.ServerConnectedEvent");
        assertThatThrownBy(() -> TopologyScenarios.joinEventClass("waterfall"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("waterfall");
    }

    @Test
    void theProxyLogLineThatNamesTheClientAddressIsParsedPerProxyAndAnUnknownLineIsAnError() {
        assertThat(TopologyScenarios.proxyAddress("velocity", "T5a1", List.of(
                "[00:04:47 INFO]: [connected player] T5a1 (/172.25.0.1:59446) has connected")))
                .isEqualTo("172.25.0.1");
        assertThat(TopologyScenarios.proxyAddress("bungeecord", "T5a1", List.of(
                "18:00:21 [INFO] [/172.26.0.1:48150] <-> InitialHandler has connected")))
                .isEqualTo("172.26.0.1");
        assertThatThrownBy(() -> TopologyScenarios.proxyAddress("velocity", "T5a1", List.of("nothing here")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("T5a1");
    }

    @Test
    void theBackendLogLineThatNamesTheClientAddressIsParsedAndANeighbouringNameIsNotTaken() {
        List<String> lines = List.of(
                "[18:00:22 INFO]: T5a10[/10.0.0.7:1111] logged in with entity id 1 at ([world]0.5, 65.0, 0.5)",
                "[18:00:22 INFO]: T5a1[/172.26.0.1:60103] logged in with entity id 1 at ([world]0.5, 65.0, 0.5)");

        assertThat(TopologyScenarios.backendAddress("lobby", "T5a1", lines)).isEqualTo("172.26.0.1");
        assertThatThrownBy(() -> TopologyScenarios.backendAddress("lobby", "T5a2", lines))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("T5a2");
    }

    @Test
    void aWorldFailingToAnswerIsAnErrorInTheResultNeverAPass() {
        World world = new World() {
            @Override
            public Identity backendIdentity(String backend, String player) {
                throw new IllegalStateException("backend agent answered 500");
            }
        };

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P02").contains("backend agent answered 500");
        assertThat(Map.of("leave", world.calls.contains("leave PSConf"))).containsEntry("leave", true);
    }
}
