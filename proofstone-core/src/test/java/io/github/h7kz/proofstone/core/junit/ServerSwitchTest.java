package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.DisagreeException;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/** The dual oracle of {@code Client.transferTo} (ADR-0016 sections 5 and 6), on scripted agents and a scripted client. */
class ServerSwitchTest {

    private static final List<String> BACKENDS = List.of("lobby", "survival");
    private static final Duration SHORT = Duration.ofMillis(300);

    /** Agents whose answers the test sets; the client's switch moves them unless told not to. */
    private static final class Script implements ServerSwitch.Agents, ServerSwitch.ClientSide {
        volatile String proxy = "lobby";
        final Set<String> lobby = ConcurrentHashMap.newKeySet();
        final Set<String> survival = ConcurrentHashMap.newKeySet();
        final List<String> typed = new ArrayList<>();
        Runnable onSwitch = () -> { };
        RuntimeException proxyFailure;
        RuntimeException clientFailure;

        Script() {
            lobby.add("T1");
        }

        @Override
        public String proxyServer(String player) {
            if (proxyFailure != null) {
                throw proxyFailure;
            }
            return proxy;
        }

        @Override
        public boolean lists(String backend, String player) {
            return (backend.equals("lobby") ? lobby : survival).contains(player);
        }

        @Override
        public void type(String command) {
            typed.add(command);
        }

        @Override
        public void awaitSwitch() {
            if (clientFailure != null) {
                throw clientFailure;
            }
            onSwitch.run();
        }

        /** The switch every agent agrees on. */
        void moves() {
            onSwitch = () -> {
                proxy = "survival";
                lobby.remove("T1");
                survival.add("T1");
            };
        }
    }

    private static void transfer(Script script, String target) {
        ServerSwitch.transfer("T1", target, BACKENDS, script, script, SHORT);
    }

    @Test
    void aSwitchEveryOracleAgreesOnPassesAndTypesTheServerCommandOnce() {
        Script script = new Script();
        script.moves();

        transfer(script, "survival");

        assertThat(script.typed).containsExactly("server survival");
    }

    @Test
    void anUnknownBackendIsAnIllegalArgumentBeforeAnythingIsSent() {
        Script script = new Script();

        assertThatThrownBy(() -> transfer(script, "creative")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("creative").hasMessageContaining("[lobby, survival]");
        assertThat(script.typed).isEmpty();
    }

    @Test
    void theBackendThePlayerIsOnIsAnIllegalArgumentBeforeAnythingIsSent() {
        Script script = new Script();

        assertThatThrownBy(() -> transfer(script, "lobby")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already on lobby");
        assertThat(script.typed).isEmpty();
    }

    @Test
    void aClientSwitchTimeoutPropagatesWithItsStageAndIsNotRetried() {
        Script script = new Script();
        script.clientFailure = new JoinTimeoutException("switch timed out; hop: proxy to backend survival", null, "T1",
                "configuration", "777", "none");

        assertThatThrownBy(() -> transfer(script, "survival")).isSameAs(script.clientFailure);
        assertThat(script.typed).containsExactly("server survival");
    }

    @Test
    void theClientSawASwitchButTheProxyAgentSaysTheOldServerIsDisagree() {
        Script script = new Script();

        assertThatThrownBy(() -> transfer(script, "survival")).isInstanceOf(DisagreeException.class)
                .hasMessageStartingWith("DISAGREE transferTo(survival) of T1 from lobby")
                .hasMessageContaining("proxy agent says server=lobby")
                .hasMessageContaining("backend lobby agent lists the player")
                .hasMessageContaining("backend survival agent does not list the player");
    }

    @Test
    void theProxyAgentSaysTheTargetButTheTargetBackendDoesNotListThePlayerIsDisagree() {
        Script script = new Script();
        script.onSwitch = () -> {
            script.proxy = "survival";
            script.lobby.remove("T1");
        };

        assertThatThrownBy(() -> transfer(script, "survival")).isInstanceOf(DisagreeException.class)
                .hasMessageContaining("proxy agent says server=survival")
                .hasMessageContaining("backend survival agent does not list the player");
    }

    @Test
    void theSourceBackendStillListingThePlayerIsDisagree() {
        Script script = new Script();
        script.onSwitch = () -> {
            script.proxy = "survival";
            script.survival.add("T1");
        };

        assertThatThrownBy(() -> transfer(script, "survival")).isInstanceOf(DisagreeException.class)
                .hasMessageContaining("backend lobby agent lists the player");
    }

    @Test
    void noAgentShowingThePlayerOnTheTargetIsFail() {
        Script script = new Script();
        script.onSwitch = () -> {
            script.proxy = "creative";
            script.lobby.remove("T1");
        };

        assertThatThrownBy(() -> transfer(script, "survival")).isExactlyInstanceOf(AssertionError.class)
                .hasMessageStartingWith("FAIL transferTo(survival)");
    }

    @Test
    void anAgentThatNeverAnswersIsOracleMissing() {
        Script script = new Script();
        script.onSwitch = () -> script.proxyFailure = new IllegalStateException("curl exited 7");

        assertThatThrownBy(() -> transfer(script, "survival")).isExactlyInstanceOf(AssertionError.class)
                .hasMessageStartingWith("ORACLE MISSING transferTo(survival)")
                .hasMessageContaining("curl exited 7");
    }

    @Test
    void aSlowButAgreeingAgentIsAwaitedNotFailed() {
        Script script = new Script();
        script.onSwitch = () -> new Thread(() -> {
            script.survival.add("T1");
            script.lobby.remove("T1");
            script.proxy = "survival";
        }).start();

        transfer(script, "survival");

        assertThat(script.typed).hasSize(1);
    }

    @Test
    void theVerdictNamesEveryAgentAndItsValue() {
        ServerSwitch.View view = new ServerSwitch.View("lobby", new java.util.LinkedHashMap<>(Map.of("lobby", true)),
                Map.of());

        assertThat(ServerSwitch.judge("T1", "lobby", "survival", view).getMessage())
                .contains("proxy agent says server=lobby").contains("backend lobby agent lists the player");
    }
}
