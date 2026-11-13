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

/** P01 to P05 (ADR-0016 sections 6 and 7) on scripted agents: what passes, and which disagreement is DISAGREE. */
class TopologyScenariosTest {

    private static final String PLAYER = "T7e5c0f01";
    private static final List<String> BACKENDS = List.of("lobby", "survival");
    private static final String OFFLINE = UUID.nameUUIDFromBytes(("OfflinePlayer:" + PLAYER)
            .getBytes(StandardCharsets.UTF_8)).toString();

    /** A topology where everything agrees; each test breaks one thing. */
    private static class World implements TopologyScenarios.World {
        final List<String> calls = new ArrayList<>();
        /** When set, the proxy agent says this whatever the player's real location. */
        String proxyServer;
        List<String> proxyPlayers = List.of(PLAYER);
        List<String> lobbyPlayers = List.of(PLAYER);
        Identity proxyIdentity = new Identity(PLAYER, OFFLINE);
        Identity backendIdentity = new Identity(PLAYER, OFFLINE);
        List<String> events = List.of(PLAYER + "@lobby");
        String proxyAddress = "172.25.0.1";
        String backendAddress = "172.25.0.1";
        DirectJoin direct = new DirectJoin(false, "This server requires you to connect with Velocity.");
        RuntimeException joinFailure;
        // P03: where the player really is, and what the proxy agent remembers of the move before.
        String location = "lobby";
        String previous;
        boolean switchIgnored;
        String previousReported;
        // P04
        boolean fixtureInstalled = true;
        List<String> replies = List.of();
        boolean sendReply = true;
        String replyServer = "lobby";
        List<String> channels = List.of();
        boolean channelSeen = true;
        // P05
        String consoleOutput = "This server is running Velocity 3.4.0 (proofstone)";
        boolean commandSeen = true;
        String typed;

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
            return proxyServer != null ? proxyServer : location;
        }

        @Override
        public List<String> proxyPlayers() {
            return proxyPlayers;
        }

        @Override
        public List<String> backendPlayers(String backend) {
            if (backend.equals("lobby")) {
                return location.equals("lobby") ? lobbyPlayers : List.of();
            }
            return location.equals(backend) ? List.of(PLAYER) : List.of();
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

        @Override
        public void typeCommand(String command) {
            calls.add("type " + command);
            typed = command;
            if (command.startsWith("server ") && !switchIgnored) {
                previous = location;
                location = command.substring("server ".length());
            }
        }

        @Override
        public void awaitSwitch() {
            calls.add("awaitSwitch");
        }

        @Override
        public String previousServer(String player) {
            return previousReported != null ? previousReported : previous;
        }

        @Override
        public boolean fixtureInstalled(String backend) {
            return fixtureInstalled;
        }

        @Override
        public String subscribeMessageProbe() {
            calls.add("subscribe messages");
            return "probe-2";
        }

        @Override
        public void requestServerName(String backend, String player) {
            calls.add("getserver " + backend + " " + player);
            if (sendReply) {
                replies = List.of(replyServer);
            }
            if (channelSeen) {
                channels = List.of("bungeecord:main");
            }
        }

        @Override
        public List<String> fixtureReplies(String backend) {
            return replies;
        }

        @Override
        public List<String> messageChannels(String probe) {
            return channels;
        }

        @Override
        public String subscribeCommandProbe() {
            calls.add("subscribe commands");
            return "probe-3";
        }

        @Override
        public List<String> commandEvents(String probe) {
            return commandSeen && typed != null ? List.of(PLAYER + ":/" + typed) : List.of();
        }

        @Override
        public String consoleCommand(String command) {
            calls.add("console " + command);
            return consoleOutput;
        }
    }

    private static ConformanceResult runBungee(World world) {
        world.consoleOutput = "This server is running BungeeCord version git:BungeeCord-Bootstrap";
        world.direct = new DirectJoin(false, TopologyScenarios.BUNGEE_DIRECT_REFUSAL);
        return TopologyScenarios.run("bungeecord@2111+paper@26.3", "bungeecord", BACKENDS, world,
                Duration.ofMillis(300));
    }

    private static ConformanceResult run(World world) {
        return TopologyScenarios.run("velocity@4.3.1+paper@26.3", "velocity", BACKENDS, world, Duration.ofMillis(300));
    }

    @Test
    void everyOracleAgreeingPassesBothScenariosAndTheClientLeaves() {
        World world = new World();

        ConformanceResult result = run(world);

        assertThat(result.passed()).as(String.valueOf(result.detail())).isTrue();
        assertThat(world.calls).containsExactly("subscribe", "join T7e5c0f01", "direct lobby PSDirect",
                "type server survival", "awaitSwitch", "type server lobby", "awaitSwitch", "subscribe messages",
                "getserver lobby T7e5c0f01", "console velocity info", "subscribe commands", "type server",
                "leave T7e5c0f01");
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
        assertThat(result.detail()).contains("P01").contains("event probe").contains("T7e5c0f01@lobby");
    }

    @Test
    void aJoinThatFailsIsAFailedScenarioAndTheClientStillLeaves() {
        World world = new World();
        world.joinFailure = new IllegalStateException("timed out joining T7e5c0f01");

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P01").contains("timed out joining T7e5c0f01");
        assertThat(world.calls).contains("leave T7e5c0f01").doesNotContain("direct lobby PSDirect");
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
        assertThat(TopologyScenarios.proxyAddress("bungeecord", "T5a1", List.of(
                "20:18:33 [INFO] [T5a1,/172.26.0.1:47956] <-> InitialHandler has connected")))
                .isEqualTo("172.26.0.1");
        assertThatThrownBy(() -> TopologyScenarios.proxyAddress("bungeecord", "T5a1", List.of(
                "20:18:33 [INFO] [T5a2,/172.26.0.9:47956] <-> InitialHandler has connected")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("T5a1");
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
        assertThat(Map.of("leave", world.calls.contains("leave T7e5c0f01"))).containsEntry("leave", true);
    }

    // P03

    @Test
    void aSwitchToSurvivalAndBackWithEveryAgentAgreeingPasses() {
        World world = new World();

        ConformanceResult result = run(world);

        assertThat(result.passed()).as(String.valueOf(result.detail())).isTrue();
        assertThat(world.location).isEqualTo("lobby");
        assertThat(world.previous).isEqualTo("survival");
    }

    @Test
    void aSwitchTheAgentsNeverSawNamesTheTargetAndIsNotAPass() {
        World world = new World();
        world.switchIgnored = true;

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P03").contains("survival").contains("DISAGREE");
    }

    @Test
    void aProxyAgentThatSaysAnotherServerThanTheBackendsListIsDisagreeInP03() {
        // The proxy agent never leaves the lobby while the stub moves the player: the agents contradict each other.
        World moving = new World() {
            @Override
            public String proxyServer(String player) {
                return "lobby";
            }
        };

        ConformanceResult result = run(moving);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P03").contains("DISAGREE").contains("survival");
    }

    @Test
    void aPreviousServerThatIsNotTheBackendThePlayerLeftIsDisagree() {
        World world = new World();
        world.previousReported = "lobby";
        // The first move left the lobby, so "lobby" is right once and wrong on the way back.

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P03").contains("DISAGREE").contains("previousServer")
                .contains("survival");
    }

    @Test
    void aMissingPreviousServerAfterARealSwitchIsAnErrorNotASkippedCheck() {
        assertThatThrownBy(() -> TopologyScenarios.judgePreviousServer(PLAYER, "lobby", "survival", null))
                .isInstanceOf(AssertionError.class).hasMessageContaining("previousServer");
        assertThatThrownBy(() -> TopologyScenarios.judgePreviousServer(PLAYER, "lobby", "survival", "lobby"))
                .isInstanceOf(DisagreeException.class).hasMessageContaining("DISAGREE");
        TopologyScenarios.judgePreviousServer(PLAYER, "lobby", "survival", "survival");
    }

    // P04

    @Test
    void theFixtureRecordingTheProxysAnswerAndTheProbeSeeingTheMessagePass() {
        World world = new World();

        ConformanceResult result = run(world);

        assertThat(result.passed()).as(String.valueOf(result.detail())).isTrue();
        assertThat(world.calls).contains("getserver lobby T7e5c0f01");
    }

    @Test
    void aReplyForAnotherServerThanTheProxyAgentSaysIsDisagree() {
        World world = new World();
        world.replyServer = "survival";

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P04").contains("DISAGREE").contains("survival").contains("lobby");
    }

    @Test
    void noRecordedReplyIsAFailureThatSaysWhatTheProbeDidSee() {
        World world = new World();
        world.sendReply = false;

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P04").contains("FAIL").contains("recorded no GetServer reply")
                .contains("bungeecord:main");
    }

    @Test
    void aProbeThatSawNoPluginMessageIsAFailureEvenWithAReply() {
        World world = new World();
        world.channelSeen = false;

        ConformanceResult result = runBungee(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P04").contains("FAIL").contains("event probe").contains("plugin message");
    }

    @Test
    void velocityFiresNoPluginMessageEventForItsOwnAnswerSoOnlyTheReplyIsRequiredThere() {
        World world = new World();
        world.channelSeen = false;

        ConformanceResult result = run(world);

        assertThat(result.passed()).as(String.valueOf(result.detail())).isTrue();
        assertThat(TopologyScenarios.proxyFiresMessageEvent("velocity")).isFalse();
        assertThat(TopologyScenarios.proxyFiresMessageEvent("bungeecord")).isTrue();
    }

    @Test
    void velocityStillNeedsTheFixtureReply() {
        World world = new World();
        world.channelSeen = false;
        world.sendReply = false;

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P04").contains("recorded no GetServer reply");
    }

    @Test
    void aMissingFixtureSkipsP04WithTheReasonAndNeverRunsIt() {
        World world = new World();
        world.fixtureInstalled = false;

        ConformanceResult result = run(world);

        assertThat(result.passed()).as(String.valueOf(result.detail())).isTrue();
        assertThat(world.calls).doesNotContain("getserver lobby T7e5c0f01", "subscribe messages");
        assertThat(TopologyScenarios.skipReason("lobby")).contains("proofstone-fixture").contains("lobby");
    }

    @Test
    void thePluginMessageProbeClassIsKnownForEveryProxyAndNeverGuessed() {
        assertThat(TopologyScenarios.messageEventClass("velocity"))
                .isEqualTo("com.velocitypowered.api.event.connection.PluginMessageEvent");
        assertThat(TopologyScenarios.messageEventClass("bungeecord"))
                .isEqualTo("net.md_5.bungee.api.event.PluginMessageEvent");
        assertThatThrownBy(() -> TopologyScenarios.messageEventClass("waterfall"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("waterfall");
    }

    @Test
    void theFixtureReplyIsParsedFromItsLogLineAndAnotherSubchannelIsNotAReply() {
        assertThat(TopologyScenarios.fixtureReplies(List.of(
                "[18:00:22 INFO]: [proofstone-fixture] fixture BungeeCord reply: GetServer survival",
                "[18:00:22 INFO]: [proofstone-fixture] fixture BungeeCord reply: other Connect",
                "[18:00:23 INFO]: unrelated"))).containsExactly("survival");
    }

    // P05

    @Test
    void aConsoleCommandThatRanOnTheProxyAndATypedCommandTheEventsSawPass() {
        World world = new World();

        ConformanceResult result = run(world);

        assertThat(result.passed()).as(String.valueOf(result.detail())).isTrue();
        assertThat(world.calls).contains("console velocity info", "type server");
    }

    @Test
    void aConsoleCommandWhoseOutputDoesNotNameTheProxyIsAFailure() {
        World world = new World();
        world.consoleOutput = "";

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P05").contains("FAIL").contains("Velocity");
    }

    @Test
    void aTypedCommandTheProxyEventsNeverSawIsAFailure() {
        World world = new World();
        world.commandSeen = false;

        ConformanceResult result = run(world);

        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("P05").contains("FAIL").contains("/server").contains("T7e5c0f01");
    }

    @Test
    void theConsoleCommandAndItsExpectedOutputAreKnownPerProxy() {
        assertThat(TopologyScenarios.consoleCommand("velocity")).isEqualTo("velocity info");
        assertThat(TopologyScenarios.consoleCommand("bungeecord")).isEqualTo("bungee");
        assertThat(TopologyScenarios.consoleOutputNames("velocity")).isEqualTo("Velocity");
        assertThat(TopologyScenarios.consoleOutputNames("bungeecord")).isEqualTo("BungeeCord");
        assertThatThrownBy(() -> TopologyScenarios.consoleCommand("waterfall"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("waterfall");
    }

    @Test
    void theCommandEventClassIsKnownForEveryProxyAndNeverGuessed() {
        assertThat(TopologyScenarios.commandEventClass("velocity"))
                .isEqualTo("com.velocitypowered.api.event.command.CommandExecuteEvent");
        assertThat(TopologyScenarios.commandEventClass("bungeecord"))
                .isEqualTo("net.md_5.bungee.api.event.ChatEvent");
        assertThatThrownBy(() -> TopologyScenarios.commandEventClass("waterfall"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("waterfall");
    }
}
