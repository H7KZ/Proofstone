package io.github.h7kz.proofstone.core.host;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.cell.GeyserCell;
import io.github.h7kz.proofstone.core.driver.bedrock.BedrockDriver;
import io.github.h7kz.proofstone.core.host.standin.StandIn;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.MountableFile;

/**
 * A Geyser cell (ADR-0018 sections 4 to 8), not a {@link TopologyHost}: one Paper server with the pinned Geyser and
 * Floodgate and the Bukkit agent, the discovery stand-in and the Bedrock driver host, on one network. It owns a
 * {@link PaperHost}, the stand-in and the driver host. Nothing is bind-mounted, no UDP port is published and the server
 * keeps its default route; the Bedrock client joins from the driver host over the cell network.
 *
 * <p>Start order: stand-in, then the server (DNS and JVM trust pointed at the stand-in), then the driver host. Ready means
 * the stand-in's ready line, Geyser's ready line for the cell's {@link GeyserTransport} ({@code Started Geyser on UDP port 19132} for {@code both}, {@code Built-in signaling started on http://0.0.0.0:19132} for {@code nethernet}) and Paper's {@code Done (}, agent health
 * with the {@code floodgate} token, and the driver host's health. A failed start stops every container and reports every
 * container's log tail, the Floodgate key and tokens redacted. This cell has Xbox authentication disabled
 * ({@code validate-bedrock-login: false}): {@code cell.json} says {@code offline-stub}.
 */
public final class GeyserHost implements AutoCloseable {

    /** The server's name on the cell network; a driver joins {@code server:19132}. */
    public static final String SERVER_ALIAS = PaperHost.NETWORK_ALIAS;
    /** Geyser's RakNet port inside the server's container; never published. */
    public static final int BEDROCK_PORT = 19132;
    /** Where the trust store sits in the server and in the driver host. */
    static final String TRUST_PATH = "/stub/cacerts";
    /** The agent token that says the Floodgate plugin is enabled (ADR-0018 section 5). */
    public static final String FLOODGATE_TOKEN = "floodgate";
    /** The key file Floodgate reads in the key-mismatch fault; the real key stays in {@link FloodgateKey#PATH}, where Geyser takes it. */
    static final String DECOY_KEY_FILE = "decoy-key.pem";
    static final String DECOY_PATH = "/plugins/floodgate/" + DECOY_KEY_FILE;
    static final String FLOODGATE_CONFIG_PATH = "/plugins/floodgate/config.yml";

    private final Network network;
    private final StandInHost standIn;
    private final PaperHost server;
    private final BedrockDriverHost driverHost;
    private final FloodgateKey key;
    /** The key of the key-mismatch fault, or {@code null}; a secret of the cell like {@link #key}. */
    private final FloodgateKey decoy;
    /** The name guard and the stand-in and driver host log guards over the cell's whole life (T-M10-06). */
    private final CellGuards guards;
    private final GeyserTransport transport;

    private GeyserHost(Network network, StandInHost standIn, PaperHost server, BedrockDriverHost driverHost,
            FloodgateKey key, FloodgateKey decoy, GeyserFault fault, GeyserTransport transport) {
        this.transport = transport;
        this.guards = new CellGuards(standIn.logs(), driverHost.logs(), fault == GeyserFault.STAND_IN_ABSENT);
        this.decoy = decoy;
        this.network = network;
        this.standIn = standIn;
        this.server = server;
        this.driverHost = driverHost;
        this.key = key;
    }

    /**
     * Starts the cell ready, or stops everything and throws with every container's log tail.
     *
     * @param serverOptions the Paper server's options (agent jar, plugins under test, EULA); network and files are set here
     */
    public static GeyserHost start(GeyserCell cell, PaperHostOptions serverOptions, GeyserHostOptions options) {
        return start(cell, serverOptions, options, GeyserFault.fromSystemProperty());
    }

    /** {@link #start(GeyserCell, PaperHostOptions, GeyserHostOptions)} with a fault injected on purpose (T-M9-11). */
    public static GeyserHost start(GeyserCell cell, PaperHostOptions serverOptions, GeyserHostOptions options,
            GeyserFault fault) {
        if (!java.nio.file.Files.isRegularFile(options.driverHostJar())) {
            throw new PaperHostException("driver host jar not found: " + options.driverHostJar()
                    + " (build it with :drivers:bedrock:shadowJar)");
        }
        // Everything that can fail without a container fails first: the jars are downloaded and checked, the config rendered.
        Path geyserJar = GeyserJars.geyser(cell, options.downloads(), options.fetcher());
        Path floodgateJar = GeyserJars.floodgate(cell, options.downloads(), options.fetcher());
        FloodgateKey key = FloodgateKey.generate();
        String config = GeyserConfig.render(cell.version(), cell.build(), options.transport());
        // Key mismatch: Geyser on the same server takes the key Floodgate holds in key.pem, whatever its own setting says (geyser.md),
        // so Floodgate is told to read another file. Geyser then encrypts with the key, and Floodgate decrypts with the decoy.
        FloodgateKey decoy = fault == GeyserFault.KEY_MISMATCH ? FloodgateKey.generate() : null;
        String floodgateConfig = decoy == null ? null : FloodgateConfig.withKeyFile(floodgateJar, DECOY_KEY_FILE);
        CellTrust trust = CellTrust.generate();
        String image = cell.hostImage() + "@" + cell.hostDigest();

        Network network = Network.newNetwork();
        List<NamedTail> started = new ArrayList<>();
        StandInHost standIn = null;
        PaperHost server = null;
        BedrockDriverHost driverHost = null;
        try {
            standIn = StandInHost.start(image, network, trust, options.auxTimeout(), fault == GeyserFault.STAND_IN_ABSENT);
            started.add(new NamedTail("stand-in", standIn::logTail));
            String dns = standIn.address();
            byte[] trustStore = trust.trustStore(standIn.cacerts());

            ServerInputs inputs = new ServerInputs(geyserJar, floodgateJar, config, key, decoy, floodgateConfig, trustStore, dns);
            server = PaperHost.start(cell.backend(), serverOptions.withNetwork(network, SERVER_ALIAS),
                    container -> prepareServer(container, inputs));
            PaperHost running = server;
            started.add(new NamedTail("server", running::logTail));
            String readyLine = options.transport().readyLine();
            Await.until("the server log shows \"" + readyLine + "\"", () -> running.logs().contains(readyLine),
                    Boolean::booleanValue, options.auxTimeout());
            Await.until("agent health lists the " + FLOODGATE_TOKEN + " token", () -> hasToken(running),
                    Boolean::booleanValue, options.auxTimeout());
            // Readiness read the health before Floodgate reported its token; the capabilities of the cell are read from this one.
            running.refreshHealth();

            driverHost = BedrockDriverHost.start(image, network, dns, options.driverHostJar(), trustStore,
                    options.auxTimeout(), fault);
            BedrockDriverHost runningDriver = driverHost;
            started.add(new NamedTail("driver host", runningDriver::logTail));
        } catch (RuntimeException e) {
            StringBuilder message = new StringBuilder("Geyser cell ").append(cell.identity()).append(" failed to start: ")
                    .append(e.getMessage());
            for (NamedTail tail : started) {
                message.append("\n--- ").append(tail.name()).append(" log tail ---\n").append(tail.tail().get());
            }
            PaperHostException failure = new PaperHostException(redact(message.toString(), List.of(key, decoy), server, driverHost), e);
            closeAll(failure, driverHost, server, standIn, network);
            throw failure;
        }
        return new GeyserHost(network, standIn, server, driverHost, key, decoy, fault, options.transport());
    }

    /** Everything copied into or set on the server beyond a plain Paper cell. */
    record ServerInputs(Path geyserJar, Path floodgateJar, String config, FloodgateKey key, FloodgateKey decoy,
            String floodgateConfig, byte[] trustStore, String dns) {
    }

    /**
     * The Geyser cell's additions to the server's container (ADR-0018 section 5): the two pinned plugin jars, Geyser's rendered
     * config, the Floodgate key and the trust store, all copied in (no mount), the JVM option that makes the server trust the
     * stand-in, and the stand-in as its DNS. The default route is left alone.
     */
    static void prepareServer(org.testcontainers.containers.GenericContainer<?> container, ServerInputs in) {
        if (in.decoy() != null) {
            container.withCopyToContainer(ServerFile.ofBytes(in.decoy().bytes()), DECOY_PATH)
                    .withCopyToContainer(ServerFile.of(in.floodgateConfig()), FLOODGATE_CONFIG_PATH);
        }
        container.withEnv("JAVA_TOOL_OPTIONS", CellTrust.javaToolOptions(TRUST_PATH))
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withDns(in.dns()))
                .withCopyFileToContainer(MountableFile.forHostPath(in.geyserJar()), "/plugins/" + in.geyserJar().getFileName())
                .withCopyFileToContainer(MountableFile.forHostPath(in.floodgateJar()), "/plugins/" + in.floodgateJar().getFileName())
                .withCopyToContainer(ServerFile.of(in.config()), GeyserConfig.PATH)
                .withCopyToContainer(ServerFile.ofBytes(in.key().bytes()), FloodgateKey.PATH)
                .withCopyToContainer(ServerFile.ofBytes(in.trustStore()), TRUST_PATH);
    }

    private record NamedTail(String name, java.util.function.Supplier<String> tail) {
    }

    private static boolean hasToken(PaperHost server) {
        JsonNode capabilities = server.request("GET", "/v1/health", null).path("capabilities");
        for (JsonNode token : capabilities) {
            if (FLOODGATE_TOKEN.equals(token.asText())) {
                return true;
            }
        }
        return false;
    }

    private static String redact(String text, List<FloodgateKey> keys, PaperHost server, BedrockDriverHost driverHost) {
        String redacted = text;
        for (FloodgateKey key : keys) {
            if (key != null) {
                redacted = key.redact(redacted);
            }
        }
        if (server != null) {
            redacted = redacted.replace(server.agentToken(), "<redacted: agent token>");
        }
        if (driverHost != null) {
            redacted = redacted.replace(driverHost.token(), "<redacted: agent token>");
        }
        return redacted;
    }

    private static void closeAll(RuntimeException failure, AutoCloseable... parts) {
        Map<String, AutoCloseable> named = new LinkedHashMap<>();
        String[] names = {"the driver host", "the server", "the stand-in", "the network"};
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null) {
                named.put(names[i], parts[i]);
            }
        }
        named.forEach((what, part) -> {
            try {
                part.close();
            } catch (Exception e) {
                failure.addSuppressed(new IllegalStateException("cannot stop " + what + ": " + e.getMessage(), e));
            }
        });
    }

    // ---- the cell ----

    /** The Paper server (the plain {@code Agent}, the injected Java client's target). */
    public PaperHost server() {
        return server;
    }

    /** The Bedrock driver that talks to this cell's driver host. */
    public BedrockDriver driver() {
        return driverHost.driver(transport.driverTransport());
    }

    /** The name a Bedrock client joins on the cell network. */
    public String bedrockHost() {
        return SERVER_ALIAS;
    }

    public int bedrockPort() {
        return BEDROCK_PORT;
    }

    /** The stand-in's log: every name asked and every document served. */
    public LogBuffer standInLogs() {
        return standIn.logs();
    }

    /** The driver host's log. */
    public LogBuffer driverHostLogs() {
        return driverHost.logs();
    }

    /** Every name the cell asked the stand-in, in first-asked order. */
    public Set<String> namesAsked() {
        return NameGuard.namesAsked(standIn.logs().all());
    }

    /** The names the stand-in answered itself; for a healthy cell exactly {@link StandIn#NAMES}. */
    public Set<String> namesAnswered() {
        return NameGuard.namesAnswered(standIn.logs().all());
    }

    /** Fails when a name the guard refuses was asked; the two stand-in names are expected. */
    public void requireNameGuardClean() {
        NameGuard.require(standIn.logs().all(), standIn.logTail());
    }

    /**
     * The guards of the stand-in and the driver host over what was logged since the previous call: a denied name asked, an
     * {@code ERROR}, an uncaught exception or a {@code STUB refused} outside the {@code stand-in-absent} fault. The test runner
     * calls it after every test and {@link #close()} once more, so the test that caused a line is the one that fails.
     *
     * @throws PaperHostException naming each problem with the log tails
     */
    public void requireGuardsClean() {
        guards.requireClean();
    }

    /**
     * Everything a bundle must not hold: the agent token, the driver host token and every form of the Floodgate key. For
     * redaction only (the bundle writer); never log or write it.
     */
    public List<String> secrets() {
        List<String> secrets = new ArrayList<>(key.forms());
        if (decoy != null) {
            secrets.addAll(decoy.forms());
        }
        secrets.add(server.agentToken());
        secrets.add(driverHost.token());
        return secrets;
    }

    /** The forms of the Floodgate key, for the bundle writer's redaction. */
    public List<String> floodgateKeyForms() {
        List<String> forms = new ArrayList<>(key.forms());
        if (decoy != null) {
            forms.addAll(decoy.forms());
        }
        return forms;
    }

    /** The driver host's RPC token, for redaction only. */
    public String driverToken() {
        return driverHost.token();
    }

    /** Stops the driver host, the server, the stand-in and the network; every part is tried. */
    @Override
    public void close() {
        RuntimeException failure = new IllegalStateException("cannot stop the Geyser cell");
        // Last, before the containers go: what was logged after the final test's check (T-M10-06).
        try {
            guards.requireClean();
        } catch (RuntimeException e) {
            failure.addSuppressed(e);
        }
        closeAll(failure, driverHost, server, standIn, network);
        if (failure.getSuppressed().length > 0) {
            throw failure;
        }
    }

    @Override
    public String toString() {
        return "GeyserHost[" + server.cell().platform() + "@" + server.cell().mc() + ", offline-stub]";
    }
}
