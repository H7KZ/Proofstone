package dev.proofstone.driver.mineflayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.proofstone.core.await.Await;
import dev.proofstone.core.cell.Cell;
import dev.proofstone.core.cell.CellPlanner;
import dev.proofstone.core.driver.ChatMessage;
import dev.proofstone.core.driver.ClientDriverException;
import dev.proofstone.core.driver.ClientSession;
import dev.proofstone.core.driver.ConnectSpec;
import dev.proofstone.core.host.Eula;
import dev.proofstone.core.host.PaperHost;
import dev.proofstone.core.host.PaperHostOptions;
import dev.proofstone.core.host.ViaProxyHost;
import dev.proofstone.core.versions.VersionsCatalog;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Runs the reference sidecar against a real server, native and bridged (T-M4-05). The sidecar joins the cell network,
 * so it reaches the server by its alias, or ViaProxy on a bridged row. Needs Docker and an accepted EULA
 * ({@value Eula#VARIABLE}=true); without the EULA each test is skipped with that reason (ADR-0005).
 */
class MineflayerRefDriverIT {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final int SIDECAR_PORT = 8765;

    /** The native row: Mineflayer 4.39.0 speaks its protocol, so no ViaProxy sits in between. */
    @Test
    void joinsTheNativeRowThroughTheSidecarAndReadsItsReplies() {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
        try (Topology topology = Topology.start("26.1.2", false)) {
            assertThat(topology.driver.protocolVersion()).isEqualTo(topology.cell.mc());
            try (ClientSession session = topology.join()) {
                ChatMessage joined = session.awaitMessage(Pattern.compile("joined"), WAIT);
                System.out.println("join message: " + joined);

                session.command("version");
                ChatMessage version = session.awaitMessage(Pattern.compile("Paper"), WAIT);
                System.out.println("version reply: " + version);

                Await.until("position of the bot", session::position, position -> position != null, WAIT);
                int level = Await.until("experience sync", session::experienceLevel, value -> value >= 0, WAIT);
                System.out.println("experience level: " + level);

                // A dropped connection must fail the wait with a reason, never hang.
                topology.host.close();
                assertThatThrownBy(() -> session.awaitMessage(Pattern.compile("never sent"), WAIT))
                        .isInstanceOf(ClientDriverException.class)
                        .hasMessageContaining("disconnected while waiting");
                System.out.println("transcript: " + session.transcript());
            }
        }
    }

    /** A row whose protocol is older than the driver's: the sidecar talks to ViaProxy, which bridges to the server. */
    @Test
    void joinsTheBridgedRowThroughViaProxy() {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
        try (Topology topology = Topology.start("1.21.11", true)) {
            assertThat(topology.driver.protocolVersion()).isNotEqualTo(topology.cell.mc());
            try (ClientSession session = topology.join()) {
                ChatMessage joined = session.awaitMessage(Pattern.compile("joined"), WAIT);
                System.out.println("join message on the bridged row: " + joined);

                session.command("version");
                ChatMessage version = session.awaitMessage(Pattern.compile("Paper"), WAIT);
                System.out.println("version reply on the bridged row: " + version);
            }
        }
    }

    /**
     * One cell, the sidecar image and the driver. Closing it stops the sidecar, ViaProxy, the server and the network,
     * in that order, so a test that stops the server first is not left with a dangling container.
     */
    private static final class Topology implements AutoCloseable {

        final Cell cell;
        final MineflayerRefDriver driver;
        final PaperHost host;
        final ViaProxyHost proxy;
        final GenericContainer<?> sidecar;
        final Network network;
        private final String joinHost;
        private final int joinPort;

        private Topology(Cell cell, PaperHost host, ViaProxyHost proxy, GenericContainer<?> sidecar, Network network,
                MineflayerRefDriver driver) {
            this.cell = cell;
            this.host = host;
            this.proxy = proxy;
            this.sidecar = sidecar;
            this.network = network;
            this.driver = driver;
            // The sidecar's view of the server: the alias on the cell network, or ViaProxy's bind port.
            this.joinHost = proxy == null ? PaperHost.NETWORK_ALIAS : "viaproxy";
            this.joinPort = proxy == null ? PaperHost.GAME_PORT : ViaProxyHost.PROXY_PORT;
        }

        static Topology start(String mc, boolean bridged) {
            Path root = Path.of(System.getProperty("proofstone.repoRoot"));
            VersionsCatalog catalog = VersionsCatalog.load(root.resolve("versions.yml"),
                    root.resolve("proofstone-protocol/schemas/versions.schema.json"));
            Cell cell = new CellPlanner(catalog).plan("paper", List.of(mc)).get(0);
            Network network = Network.newNetwork();
            PaperHost host = null;
            ViaProxyHost proxy = null;
            GenericContainer<?> sidecar = null;
            try {
                PaperHostOptions options = PaperHostOptions.of(Path.of(System.getProperty("proofstone.agentJar")), true)
                        .withApplyGameRules(false)
                        .withNetwork(network);
                host = PaperHost.start(cell, options);
                if (bridged) {
                    proxy = ViaProxyHost.start(cell, catalog.driverImage("viaproxy"), network);
                }
                // The sidecar image is built from images/mineflayer-ref, copied in, never mounted (ADR-0005).
                ImageFromDockerfile image = new ImageFromDockerfile()
                        .withFileFromPath(".", root.resolve("images/mineflayer-ref"));
                sidecar = new GenericContainer<>(image)
                        .withNetwork(network)
                        .withExposedPorts(SIDECAR_PORT)
                        .waitingFor(Wait.forListeningPort());
                MineflayerRefDriver.publishOnLoopbackOnly(sidecar);
                sidecar.start();
                URI uri = URI.create("ws://" + sidecar.getHost() + ":" + sidecar.getMappedPort(SIDECAR_PORT));
                return new Topology(cell, host, proxy, sidecar, network, new MineflayerRefDriver(uri));
            } catch (RuntimeException e) {
                closeQuietly(sidecar, proxy, host, network, e);
                throw e;
            }
        }

        ClientSession join() {
            return driver.connect(new ConnectSpec(joinHost, joinPort, "ProofBot", Duration.ofSeconds(30)));
        }

        @Override
        public void close() {
            RuntimeException failure = null;
            try {
                closeQuietly(sidecar, proxy, host, network, null);
            } catch (RuntimeException e) {
                failure = e;
            }
            if (failure != null) {
                throw failure;
            }
        }

        private static void closeQuietly(GenericContainer<?> sidecar, ViaProxyHost proxy, PaperHost host,
                Network network, RuntimeException cause) {
            RuntimeException failure = cause;
            for (AutoCloseable part : new AutoCloseable[] {sidecar, proxy, host, network}) {
                if (part == null) {
                    continue;
                }
                try {
                    part.close();
                } catch (Exception e) {
                    if (failure == null) {
                        failure = new IllegalStateException("cannot close " + part, e);
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null && failure != cause) {
                throw failure;
            }
        }
    }
}
