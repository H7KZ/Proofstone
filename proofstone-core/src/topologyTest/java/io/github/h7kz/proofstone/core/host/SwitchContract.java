package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.cell.TopologyCell;
import io.github.h7kz.proofstone.core.driver.ClientDriver;
import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.Face;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import io.github.h7kz.proofstone.core.driver.WindowSession;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-M7-09: one client on a proxy with two paper@26.3 backends types {@code /server survival} and back, twice in one run.
 * Each backend holds different things (a chest at the same spot, a different carried item), so a view that survived the
 * switch would show the other server's. A join timeout during a switch is reported with its stage and hop
 * ({@link TopologyHost#describeSwitch}); it is never retried. One subclass per proxy (M7 flake rule).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class SwitchContract {

    private static final String PLAYER = "T9s1";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SWITCH_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final BlockPos CHEST = new BlockPos(2, 64, 0);

    /** What one backend holds: the first chest slot and what the player carries. */
    private record Identity(String backend, String chestItem, int chestCount, String carried, int carriedCount) {
        String held() {
            return carried + "x" + carriedCount;
        }
    }

    private static final Identity LOBBY = new Identity("lobby", "minecraft:diamond", 5, "minecraft:apple", 7);
    private static final Identity SURVIVAL = new Identity("survival", "minecraft:stick", 3, "minecraft:gold_ingot", 2);

    private TopologyHost topology;
    private ClientSession client;

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
        if (client != null) {
            client.close();
        }
        if (topology == null) {
            return;
        }
        topology.close();
        System.out.println("docker ps after close: " + Topologies.dockerPs());
        assertThat(Topologies.ourRunningContainers()).as("containers of ours still running after close").isEmpty();
    }

    @Test
    void aClientSwitchesToSurvivalAndBackTwiceAndEveryViewIsTheNewServers() {
        TopologyHost.JoinMarks marks = topology.mark();
        client = join(marks);
        WindowSession held = arrive(LOBBY, true, marks, null);

        for (int round = 1; round <= 2; round++) {
            held = switchTo(SURVIVAL, round == 1, held, "round " + round + " out");
            held = switchTo(LOBBY, false, held, "round " + round + " back");
        }

        List<String> transcript = client.transcript();
        long configurations = transcript.stream().filter(l -> l.contains("<- ClientboundStartConfigurationPacket")).count();
        long logins = transcript.stream().filter(l -> l.contains("<- ClientboundLoginPacket")).count();
        long respawns = transcript.stream().filter(l -> l.contains("<- ClientboundRespawnPacket")).count();
        System.out.println("switch evidence: " + configurations + " start-configurations, " + logins + " logins, "
                + respawns + " respawns on " + topology.proxy().kind().id());
        // One join plus four switches, and each switch went through the configuration phase again.
        assertThat(configurations).isEqualTo(4);
        assertThat(logins).isEqualTo(5);
        assertThat(transcript).as("never disconnected").noneMatch(l -> l.contains("disconnected:"));
    }

    private ClientSession join(TopologyHost.JoinMarks marks) {
        ClientDriver driver = ServiceLoader.load(ClientDriver.class).stream().map(ServiceLoader.Provider::get)
                .filter(d -> d.id().equals("mcpl")).findFirst()
                .orElseThrow(() -> new IllegalStateException("no mcpl driver on the topologyTest classpath"));
        try {
            return driver.connect(new ConnectSpec(topology.proxy().gameHost(), topology.proxy().gamePort(), PLAYER,
                    CONNECT_TIMEOUT));
        } catch (JoinTimeoutException e) {
            throw topology.describe(e, marks, LOBBY.backend());
        }
    }

    /** Types {@code /server <name>} and waits for the switch; the first visit also builds the backend's world. */
    private WindowSession switchTo(Identity target, boolean firstVisit, WindowSession previousWindow, String step) {
        TopologyHost.JoinMarks marks = topology.mark();
        client.command("server " + target.backend());
        try {
            client.awaitServerSwitch(SWITCH_TIMEOUT);
        } catch (JoinTimeoutException e) {
            throw topology.describeSwitch(e, marks, target.backend());
        }
        System.out.println("switch " + step + " -> " + target.backend() + " seen by the client");
        return arrive(target, firstVisit, marks, previousWindow);
    }

    /**
     * The client is on {@code on} now: checks the server oracles agree, builds the world on a first visit, then reads the
     * client's inventory and a window and requires them to be this backend's and nothing of the previous one.
     */
    private WindowSession arrive(Identity on, boolean firstVisit, TopologyHost.JoinMarks marks,
            WindowSession previousWindow) {
        PaperHost backend = topology.backend(on.backend());
        Await.until(on.backend() + " logs " + PLAYER + " logged in", () -> backend.logs()
                        .since(marks.backends().get(on.backend())).stream()
                        .anyMatch(l -> l.contains(PLAYER) && l.contains("logged in")),
                seen -> seen, WAIT);
        Await.until("proxy agent says " + PLAYER + " is on " + on.backend(),
                () -> topology.proxy().request("GET", "/v1/players/" + PLAYER, null).path("proxy").path("server").asText(),
                on.backend()::equals, WAIT);
        if (previousWindow != null) {
            // The window of the server the client left is gone; a held handle fails loudly instead of showing stale slots.
            assertThatThrownBy(() -> previousWindow.click(0, Click.LEFT)).isInstanceOf(ClientDriverException.class)
                    .hasMessageContaining("closed");
            assertThat(client.inventory().itemViews()).as("nothing carried over from the previous server")
                    .noneMatch(v -> v.amount() > 0 && !v.type().equals(on.carried()));
        }
        if (firstVisit) {
            build(backend, on);
        }
        Await.until("synced position on " + on.backend(), client::position,
                p -> p != null && Math.abs(p[1] - 64.0) < 0.01, WAIT);
        Await.until("inventory of " + on.backend() + " holds exactly " + on.held(), () -> held(client),
                held -> held.equals(List.of(on.held())), WAIT);
        client.useBlock(CHEST, Face.UP);
        WindowSession chest = client.awaitWindow(WAIT);
        assertThat(chest.type()).isEqualTo("minecraft:generic_9x3");
        var first = chest.itemViews().get(0);
        assertThat(first.type()).as("first chest slot on " + on.backend()).isEqualTo(on.chestItem());
        assertThat(first.amount()).isEqualTo(on.chestCount());
        return chest;
    }

    /** Only chunks around a player are loaded, so the world is built once the player is there (docs/platforms/paper.md). */
    private static void build(PaperHost backend, Identity on) {
        backend.command("setblock 0 63 0 minecraft:stone");
        backend.command("setblock 2 64 0 minecraft:chest");
        backend.command("item replace block 2 64 0 container.0 with " + on.chestItem() + " " + on.chestCount());
        backend.command("tp " + PLAYER + " 0.5 64 0.5");
        backend.command("give " + PLAYER + " " + on.carried() + " " + on.carriedCount());
    }

    /** What the client's own inventory holds, as {@code type x amount}, empty slots left out. */
    private static List<String> held(ClientSession session) {
        return session.inventory().itemViews().stream().filter(v -> v.amount() > 0)
                .map(v -> v.type() + "x" + v.amount()).toList();
    }
}
