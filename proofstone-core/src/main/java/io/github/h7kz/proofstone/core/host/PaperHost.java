package io.github.h7kz.proofstone.core.host;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.versions.VersionNumber;
import io.github.h7kz.proofstone.protocol.Command;
import io.github.h7kz.proofstone.protocol.CommandReply;
import io.github.h7kz.proofstone.protocol.Envelope;
import io.github.h7kz.proofstone.protocol.Health;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One Paper server in a container (architecture §3). The image is pinned by digest, and every input is copied
 * in before start; nothing is bind-mounted (ADR-0005). Ready means {@code Done (} in the log and agent health
 * OK, both awaited through {@link Await}.
 *
 * <p>The agent is reached by running {@code curl} inside the container, so no port is published to the host
 * unless debug mode asks for it.
 */
public final class PaperHost implements AutoCloseable {

    static final int AGENT_PORT = AgentClient.AGENT_PORT;
    /** The server's name on the cell network, where {@link PaperHostOptions#network()} is set. */
    public static final String NETWORK_ALIAS = "server";
    /** The port the server listens on inside its container. */
    public static final int GAME_PORT = 25565;
    private static final int TAIL_LINES = 60;
    private static final String BUKKIT_YML = "settings:\n  connection-throttle: -1\n";
    private static final String SEED = "1234567";
    private static final String WORLD_SPAWN = "setworldspawn 0 64 0";
    private static final ObjectMapper JSON = AgentClient.JSON;
    private static final Pattern JAVA_MAJOR = Pattern.compile("version \"(\\d+)(?:\\.(\\d+))?");

    private final Cell cell;
    /** The jar bytes the container must hold: the pin, or for a built jar the sha256 of the file copied in. */
    private String expectedJarSha256;
    private final PaperHostOptions options;
    private final String token;
    private final LogBuffer logs = new LogBuffer();
    private final GenericContainer<?> container;
    private final List<String> copiedPlugins = new ArrayList<>();
    /** Agent calls through curl in the container; it keeps the transcript for the repro bundle. */
    private final AgentClient agent;
    private Health health;

    private PaperHost(Cell cell, PaperHostOptions options) {
        this.cell = cell;
        this.options = options;
        this.token = AgentClient.randomToken();
        this.container = new GenericContainer<>(DockerImageName.parse(cell.hostImage() + "@" + cell.hostDigest()));
        this.agent = new AgentClient(container, token);
    }

    /** Starts the cell's server and returns it ready, or throws {@link PaperHostException} with the log tail. */
    public static PaperHost start(Cell cell, PaperHostOptions options) {
        Objects.requireNonNull(cell, "cell");
        if (!options.eulaAccepted()) {
            throw new PaperHostException("The Minecraft EULA is not accepted. Set " + Eula.VARIABLE
                    + "=true after reading https://aka.ms/MinecraftEULA; the harness never accepts it for you.");
        }
        if (!Files.isRegularFile(options.agentJar())) {
            throw new PaperHostException("agent jar not found: " + options.agentJar()
                    + " (build it with :platforms:bukkit:agent:shadowJar)");
        }
        // Resolve before any container exists, so a bad glob fails fast and names itself.
        List<Path> artefacts = Artefacts.resolve(options.artefactBase(), options.artefactGlobs());
        // Spigot is built, not downloaded: the jar is resolved before any container exists (T-M5-05).
        Path serverJar = SpigotServerJar.PLATFORM.equals(cell.platform()) ? SpigotServerJar.resolve(cell) : null;
        PaperHost host = new PaperHost(cell, options);
        host.boot(artefacts, serverJar);
        return host;
    }

    private void boot(List<Path> artefacts, Path serverJar) {
        // A built jar is pinned by content digest (JarDigest), so the copy is checked against the file it came from.
        expectedJarSha256 = serverJar == null ? cell.sha256() : SpigotServerJar.fileSha256(serverJar);
        configure(artefacts, serverJar);
        try {
            container.start();
            awaitDone();
            awaitAgent();
            verifyServer();
            verifyPlugins();
            if (options.applyGameRules()) {
                GameRules.commands(cell.mc()).forEach(this::command);
                // 1.8.8's flat world spawns about 1500 blocks from the origin, where the fixture is. A spawn at the
                // origin makes "teleport to spawn" (agent reset) load the fixture's chunk on every version.
                command(WORLD_SPAWN);
            }
        } catch (RuntimeException e) {
            String tail = logTail();
            close();
            String message = e instanceof PaperHostException ? e.getMessage()
                    : cell.platform() + "@" + cell.mc() + " failed to start: " + e.getMessage();
            throw new PaperHostException(message + "\n--- log tail ---\n" + tail, e);
        }
    }

    private void configure(List<Path> artefacts, Path serverJar) {
        container
                .withEnv("EULA", "TRUE")
                .withEnv("TYPE", serverJar == null ? cell.platform().toUpperCase(Locale.ROOT) : "CUSTOM")
                .withEnv("VERSION", cell.mc())
                .withEnv("ONLINE_MODE", "FALSE")
                .withEnv("ENFORCE_SECURE_PROFILE", "FALSE")
                .withEnv("LEVEL_TYPE", levelType(cell.mc()))
                .withEnv("SEED", SEED)
                .withEnv("SPAWN_PROTECTION", "0")
                .withEnv("DIFFICULTY", "peaceful")
                .withEnv("VIEW_DISTANCE", "4")
                .withEnv("ENABLE_RCON", "false")
                .withEnv("PROOFSTONE_AGENT_TOKEN", token)
                .withEnv("PROOFSTONE_AGENT_PORT", Integer.toString(AGENT_PORT))
                .withLogConsumer(frame -> logs.append(frame.getUtf8String()));
        if (serverJar == null) {
            container.withEnv(buildEnv(cell.platform()), Integer.toString(cell.build()));
        } else {
            // itzg runs a jar it is given and never builds one, so BuildTools' output is the only Spigot (T-M5-05).
            String path = "/data/" + SpigotServerJar.containerName(cell);
            container.withEnv("CUSTOM_SERVER", path)
                    .withCopyFileToContainer(MountableFile.forHostPath(serverJar), path);
        }
        // The image syncs /plugins into /data/plugins at startup. Copy in, never bind-mount (ADR-0005).
        copyPlugin(options.agentJar());
        artefacts.forEach(this::copyPlugin);
        // Tests join several clients from one address within seconds; Paper's default 4 s connection throttle
        // would refuse the second ("Connection throttled!"). Copied in, like every input (T-M1-10).
        container.withCopyToContainer(Transferable.of(BUKKIT_YML), "/data/bukkit.yml");
        // A topology backend's forwarding settings (TopologyConfig); empty for a single server.
        options.files().forEach((path, content) -> container.withCopyToContainer(Transferable.of(content), path));
        // Players join over the game port, so it is the one port always published (T-M1-08).
        container.addExposedPort(GAME_PORT);
        if (options.network() != null) {
            container.withNetwork(options.network()).withNetworkAliases(options.networkAlias());
        }
        if (options.debugPorts()) {
            container.addExposedPort(AGENT_PORT);
        }
    }

    /**
     * The itzg variable that selects the server build. Paper and Folia both go through {@code start-deployPaper}, which
     * reads {@code PAPER_BUILD}. Purpur's {@code start-deployPurpur} reads {@code PURPUR_BUILD} and ignores the other, so
     * a Purpur cell with {@code PAPER_BUILD} would run the latest build, not the pinned one (docs/platforms/purpur.md).
     */
    static String buildEnv(String platform) {
        return switch (platform) {
            case "paper", "folia" -> "PAPER_BUILD";
            case "purpur" -> "PURPUR_BUILD";
            default -> throw new PaperHostException("no build variable known for platform " + platform);
        };
    }

    /**
     * Servers before 1.18 do not know the namespaced name: 1.8.8 silently falls back to a default world (spawn near
     * -168, 216 at y 77) when it sees {@code minecraft:flat}, and the fixture then has no loaded chunk at the origin
     * (docs/platforms/paper.md).
     */
    static String levelType(String mc) {
        try {
            return VersionNumber.compare(mc, "1.18") >= 0 ? "minecraft:flat" : "flat";
        } catch (IllegalArgumentException e) {
            throw new PaperHostException("cannot read the Minecraft version from \"" + mc + "\"", e);
        }
    }

    private void copyPlugin(Path jar) {
        String name = jar.getFileName().toString();
        container.withCopyFileToContainer(MountableFile.forHostPath(jar), "/plugins/" + name);
        copiedPlugins.add(name);
    }

    private void awaitDone() {
        awaitProbe("server log shows \"Done (\"", () -> {
            requireRunning();
            return logs.contains("Done (");
        }, options.bootTimeout());
    }

    /** Readiness is the log line and agent health OK together (ADR-0005). */
    private void awaitAgent() {
        awaitProbe("agent /v1/health OK", () -> {
            requireRunning();
            health = agent.health();
            return true;
        }, options.agentTimeout());
    }

    private void awaitProbe(String what, Supplier<Boolean> probe, Duration timeout) {
        try {
            Await.until(what, probe, Boolean::booleanValue, timeout);
        } catch (AwaitTimeoutException e) {
            String hint = what.startsWith("agent") ? " (the server finished loading without a working agent)" : "";
            throw new PaperHostException("timed out waiting for " + what + " on " + cell.platform() + "@"
                    + cell.mc() + hint + ": " + e.getMessage(), e);
        }
    }

    private void requireRunning() {
        if (!container.isRunning()) {
            throw new IllegalStateException("container exited");
        }
    }

    /** The pins must be what actually runs: the Java major matches the cell, and the jar matches the sha256. */
    private void verifyServer() {
        String java = exec("java", "-version");
        Matcher matcher = JAVA_MAJOR.matcher(java);
        if (!matcher.find()) {
            throw new PaperHostException("cannot read the Java version from: " + java);
        }
        int major = Integer.parseInt(matcher.group(1).equals("1") ? matcher.group(2) : matcher.group(1));
        if (!cell.java().equals("java" + major)) {
            throw new PaperHostException("cell wants " + cell.java() + " but the host image runs Java " + major
                    + ". The digest for " + cell.java() + " in versions.yml points at a different Java (ADR-0013).");
        }
        String sums = exec("sh", "-c", "sha256sum /data/" + cell.platform() + "-*.jar");
        if (!sums.contains(expectedJarSha256)) {
            throw new PaperHostException("server jar sha256 differs from the expected " + expectedJarSha256 + ":\n" + sums);
        }
        if (!cell.mc().equals(health.mcVersion())) {
            throw new PaperHostException("agent reports Minecraft " + health.mcVersion() + ", cell is " + cell.mc());
        }
    }

    /**
     * Checks that every copied jar reached {@code /data/plugins}. SEAM: the card asks for the plugin list
     * through the agent, which has no such endpoint yet (T-M1-04). Until then the file listing is the proof,
     * and a healthy agent proves the agent jar itself loaded.
     */
    private void verifyPlugins() {
        String listing = exec("ls", "/data/plugins");
        for (String name : copiedPlugins) {
            if (!listing.contains(name)) {
                throw new PaperHostException("plugin " + name + " was not copied into /data/plugins:\n" + listing);
            }
        }
    }

    /** Runs a console command through the agent. Setup only; the reply is best effort (ADR-0002). */
    public String command(String command) {
        try {
            String body = JSON.writeValueAsString(new Command(command, null));
            Envelope<CommandReply> envelope = AgentClient.parse(agent.call("POST", "/v1/command", body),
                    new TypeReference<>() { });
            if (!envelope.ok()) {
                throw new PaperHostException("command \"" + command + "\" failed: " + envelope.error());
            }
            return envelope.data().raw();
        } catch (JsonProcessingException e) {
            throw new PaperHostException("cannot encode command \"" + command + "\"", e);
        }
    }

    /**
     * Calls an agent endpoint and returns the reply's {@code data}. A reply that is not {@code ok}, or whose data
     * is absent, is an error that names the call and the agent's message.
     *
     * @param body a JSON-serialisable object, or {@code null} for no body
     */
    public JsonNode request(String method, String path, Object body) {
        String call = method + " " + path;
        try {
            String reply = agent.call(method, path, body == null ? null : JSON.writeValueAsString(body));
            Envelope<JsonNode> envelope = AgentClient.parse(reply, new TypeReference<>() { });
            if (!envelope.ok()) {
                throw new PaperHostException("agent " + call + " failed: " + envelope.error());
            }
            return envelope.data() == null ? JSON.createObjectNode() : envelope.data();
        } catch (JsonProcessingException e) {
            throw new PaperHostException("cannot encode the body of " + call, e);
        }
    }

    /** The agent calls made so far, as JSON lines (repro bundle, agent-transcript.jsonl). */
    public List<String> agentTranscript() {
        return agent.transcript();
    }

    /** Runs a command in the container; a non-zero exit is an error with its stderr. */
    String exec(String... args) {
        return agent.exec(args);
    }

    public Cell cell() {
        return cell;
    }

    /** The agent's {@code /v1/health} reply from readiness: version, platform and capabilities. */
    public Health health() {
        return health;
    }

    public LogBuffer logs() {
        return logs;
    }

    public String logTail() {
        return logs.tail(TAIL_LINES);
    }

    /** The host name a driver connects to. */
    public String gameHost() {
        return container.getHost();
    }

    /** The host port mapped to the server's game port. */
    public int gamePort() {
        return container.getMappedPort(GAME_PORT);
    }

    /** The bearer token the agent accepts. Harness code sends it; it is random per server and never logged. */
    public String agentToken() {
        return token;
    }

    /** The published agent port, or fails when debug mode is off. */
    public int debugAgentPort() {
        if (!options.debugPorts()) {
            throw new PaperHostException("agent port is not published; enable debug mode");
        }
        return container.getMappedPort(AGENT_PORT);
    }

    /** The Docker container id, for fault tests that pause the server (T-M7-07). */
    String containerId() {
        return container.getContainerId();
    }

    @Override
    public void close() {
        container.stop();
    }
}
