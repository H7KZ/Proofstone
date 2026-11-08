package dev.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.api.Agent;
import dev.proofstone.api.Capability;
import dev.proofstone.api.CapturedEvent;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Client;
import dev.proofstone.api.EventProbe;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Proofstone;
import dev.proofstone.api.Requires;

/**
 * C-F02 (specs/self-tests.md §1, Folia pack): an event probe reports the {@code thread} that ran the event. A console
 * {@code gamemode} change fires {@code PlayerGameModeChangeEvent} on the thread the server runs the command on, so the
 * probe must name that thread, and the agent must say which one.
 *
 * <p>Trigger choice, from observed runs (docs/platforms/folia.md, Gotchas): the console {@code tp} command and the
 * agent's reset do not fire {@code PlayerTeleportEvent} on Folia, so a teleport cannot be the trigger. The probe is
 * read-only at MONITOR priority (ADR-0002). The test checks only that the thread is named and is not the main thread;
 * it does not hard-code the region thread's name.
 */
@Proofstone
// Paper is declared so a paper@latest selection plans a cell (zero cells is a loud error); @Requires then makes the
// class a reasoned skip there, since only Folia has regions.
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Requires(Capability.FOLIA_REGIONS)
class ConformanceFoliaEventTest {

    private static final String GAMEMODE_EVENT = "org.bukkit.event.player.PlayerGameModeChangeEvent";
    /** The name a global-thread event would carry on a Folia server (the vanilla main thread name). */
    private static final String MAIN_THREAD = "Server thread";

    @CellTest
    void gamemodeEventReportsTheThreadThatRanIt(Agent agent, Client client) {
        ConformanceFixture.stand(agent, client);
        try (EventProbe probe = agent.events().subscribe(GAMEMODE_EVENT)) {
            agent.command("gamemode creative " + client.name());
            CapturedEvent event = probe.awaitOne();
            assertThat(event.className()).isEqualTo(GAMEMODE_EVENT);
            // Evidence that the event ran on a region thread: the observed name goes to the run output.
            System.err.println("PROOFSTONE C-F02 observed event thread: " + event.thread());
            assertThat(event.thread()).as("thread that ran PlayerGameModeChangeEvent").isNotBlank()
                    .isNotEqualTo(MAIN_THREAD);
        }
    }
}
