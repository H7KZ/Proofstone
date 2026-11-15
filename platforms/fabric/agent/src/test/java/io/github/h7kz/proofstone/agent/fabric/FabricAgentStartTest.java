package io.github.h7kz.proofstone.agent.fabric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.agent.log.LogBuffer;
import io.github.h7kz.proofstone.agent.server.AgentConfig;
import io.github.h7kz.proofstone.agent.vanilla.AgentClient;
import io.github.h7kz.proofstone.agent.vanilla.MockView;
import io.github.h7kz.proofstone.agent.vanilla.TickCounter;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * ADR-0017 section 5: every agent thread is a daemon thread, so a server that fails after the agent started still ends
 * its JVM (the T-M8-01 spike saw a non-daemon HTTP thread keep a failed server alive for 600 s). And a port that cannot
 * be bound is a loud start failure.
 */
class FabricAgentStartTest {

    @Test
    void everyThreadTheAgentStartsIsADaemon() throws IOException {
        Set<Thread> before = new HashSet<>(Thread.getAllStackTraces().keySet());
        FabricMockView mock = new FabricMockView();
        LogBuffer logs = LogBuffer.attach();
        TickCounter ticks = new TickCounter();
        FabricAgent agent = FabricAgent.start(new AgentConfig(AgentClient.TOKEN, 0), mock::execute, mock,
                new FabricEventProbes(ticks::current), logs, ticks, "0.1.0", "26.3");
        try {
            // A request makes the HTTP server use its handler thread too.
            assertThat(new AgentClient(agent.port()).get("/v1/health").status).isEqualTo(200);
            List<Thread> started = Thread.getAllStackTraces().keySet().stream()
                    .filter(thread -> !before.contains(thread) && thread != Thread.currentThread())
                    .filter(thread -> !MockView.THREAD.equals(thread.getName()))
                    .collect(Collectors.toList());
            assertThat(started).as("threads the agent started").isNotEmpty();
            assertThat(started).allSatisfy(thread -> assertThat(thread.isDaemon()).as(thread.getName()).isTrue());
        } finally {
            agent.close();
            logs.detach();
            mock.serverThread.shutdownNow();
        }
    }

    @Test
    void aPortInUseIsALoudStartFailure() throws IOException {
        try (ServerSocket taken = new ServerSocket(0)) {
            FabricMockView mock = new FabricMockView();
            TickCounter ticks = new TickCounter();
            assertThatThrownBy(() -> FabricAgent.start(new AgentConfig(AgentClient.TOKEN, taken.getLocalPort()),
                    mock::execute, mock, new FabricEventProbes(ticks::current), LogBuffer.attach(), ticks, "0.1.0",
                    "26.3")).isInstanceOf(IOException.class);
            mock.serverThread.shutdownNow();
        }
    }
}
