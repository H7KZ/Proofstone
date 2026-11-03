package dev.proofstone.core.host;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.proofstone.core.await.Await;
import dev.proofstone.core.await.AwaitTimeoutException;
import dev.proofstone.core.cell.Cell;
import dev.proofstone.protocol.Command;
import dev.proofstone.protocol.CommandReply;
import dev.proofstone.protocol.Envelope;
import dev.proofstone.protocol.Health;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
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

    static final int AGENT_PORT = 25580;
    private static final int GAME_PORT = 25565;
    private static final String AGENT_URL = "http://127.0.0.1:" + AGENT_PORT;
    private static final int TAIL_LINES = 60;
    private static final String SEED = "1234567";
    private static final List<String> GAME_RULES = List.of(
            "gamerule randomTickSpeed 0",
            "gamerule doDaylightCycle false",
            "gamerule doWeatherCycle false",
            "gamerule doMobSpawning false");
    // Absent optional fields are omitted: the agent rejects an explicit "timeoutMs": null (found in T-M1-08).
    private static final ObjectMapper JSON = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private static final Pattern JAVA_MAJOR = Pattern.compile("version \"(\\d+)(?:\\.(\\d+))?");

    private final Cell cell;
    private final PaperHostOptions options;
    private final String token;
    private final LogBuffer logs = new LogBuffer();
    private final GenericContainer<?> container;
    private final List<String> copiedPlugins = new ArrayList<>();
    private Health health;

    private PaperHost(Cell cell, PaperHostOptions options) {
        this.cell = cell;
        this.options = options;
        this.token = randomToken();
        this.container = new GenericContainer<>(DockerImageName.parse(cell.hostImage() + "@" + cell.hostDigest()));
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
        PaperHost host = new PaperHost(cell, options);
        host.boot(artefacts);
        return host;
    }

    private void boot(List<Path> artefacts) {
        configure(artefacts);
        try {
            container.start();
            awaitDone();
            awaitAgent();
            verifyServer();
            verifyPlugins();
            if (options.applyGameRules()) {
                GAME_RULES.forEach(this::command);
            }
        } catch (RuntimeException e) {
            String tail = logTail();
            close();
            String message = e instanceof PaperHostException ? e.getMessage()
                    : cell.platform() + "@" + cell.mc() + " failed to start: " + e.getMessage();
            throw new PaperHostException(message + "\n--- log tail ---\n" + tail, e);
        }
    }

    private void configure(List<Path> artefacts) {
        container
                .withEnv("EULA", "TRUE")
                .withEnv("TYPE", "PAPER")
                .withEnv("VERSION", cell.mc())
                .withEnv("PAPER_BUILD", Integer.toString(cell.build()))
                .withEnv("ONLINE_MODE", "FALSE")
                .withEnv("ENFORCE_SECURE_PROFILE", "FALSE")
                .withEnv("LEVEL_TYPE", "minecraft:flat")
                .withEnv("SEED", SEED)
                .withEnv("SPAWN_PROTECTION", "0")
                .withEnv("DIFFICULTY", "peaceful")
                .withEnv("VIEW_DISTANCE", "4")
                .withEnv("ENABLE_RCON", "false")
                .withEnv("PROOFSTONE_AGENT_TOKEN", token)
                .withEnv("PROOFSTONE_AGENT_PORT", Integer.toString(AGENT_PORT))
                .withLogConsumer(frame -> logs.append(frame.getUtf8String()));
        // The image syncs /plugins into /data/plugins at startup. Copy in, never bind-mount (ADR-0005).
        copyPlugin(options.agentJar());
        artefacts.forEach(this::copyPlugin);
        // Players join over the game port, so it is the one port always published (T-M1-08).
        container.addExposedPort(GAME_PORT);
        if (options.debugPorts()) {
            container.addExposedPort(AGENT_PORT);
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
            health = readHealth();
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

    private Health readHealth() {
        Envelope<Health> envelope = parse(agent("GET", "/v1/health", null), new TypeReference<>() { });
        if (!envelope.ok() || envelope.data() == null) {
            throw new IllegalStateException("agent health not ok: " + envelope.error());
        }
        if (!envelope.data().protocol().startsWith("1.")) {
            throw new PaperHostException("agent protocol " + envelope.data().protocol() + " is not 1.x");
        }
        return envelope.data();
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
                    + ". The host digest in versions.yml pins one Java tag only.");
        }
        String sums = exec("sh", "-c", "sha256sum /data/paper-*.jar");
        if (!sums.contains(cell.sha256())) {
            throw new PaperHostException("server jar sha256 differs from the pin " + cell.sha256() + ":\n" + sums);
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
            Envelope<CommandReply> envelope = parse(agent("POST", "/v1/command", body), new TypeReference<>() { });
            if (!envelope.ok()) {
                throw new PaperHostException("command \"" + command + "\" failed: " + envelope.error());
            }
            return envelope.data().raw();
        } catch (JsonProcessingException e) {
            throw new PaperHostException("cannot encode command \"" + command + "\"", e);
        }
    }

    private static <T> T parse(String json, TypeReference<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unparsable agent reply: " + json, e);
        }
    }

    private String agent(String method, String path, String body) {
        List<String> args = new ArrayList<>(List.of("curl", "-sS", "-m", "5", "-X", method,
                "-H", "Authorization: Bearer " + token, "-H", "Content-Type: application/json"));
        if (body != null) {
            args.addAll(List.of("-d", body));
        }
        args.add(AGENT_URL + path);
        return exec(args.toArray(String[]::new));
    }

    private String exec(String... args) {
        try {
            ExecResult result = container.execInContainer(args);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException(args[0] + " exited " + result.getExitCode() + ": "
                        + result.getStderr());
            }
            return result.getStdout() + result.getStderr();
        } catch (IOException e) {
            throw new IllegalStateException("exec " + args[0] + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("exec " + args[0] + " interrupted", e);
        }
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

    /** The published agent port, or fails when debug mode is off. */
    public int debugAgentPort() {
        if (!options.debugPorts()) {
            throw new PaperHostException("agent port is not published; enable debug mode");
        }
        return container.getMappedPort(AGENT_PORT);
    }

    @Override
    public void close() {
        container.stop();
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
