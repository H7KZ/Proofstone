package io.github.h7kz.proofstone.core.host;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import io.github.h7kz.proofstone.core.cell.LoaderCell;
import io.github.h7kz.proofstone.core.host.LoaderEnvironment.PinnedFile;
import io.github.h7kz.proofstone.protocol.Command;
import io.github.h7kz.proofstone.protocol.CommandReply;
import io.github.h7kz.proofstone.protocol.Envelope;
import io.github.h7kz.proofstone.protocol.Health;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One Fabric or NeoForge server in a container (ADR-0017 section 2), the loader cell's counterpart of {@link PaperHost}.
 * The image is pinned by digest, the loader jar, the Fabric API and the agent are copied in before start (ADR-0005, no bind
 * mount), and the mods under test go to {@code /mods}. Ready means {@code Done (} in the log, the vanilla jar and Java
 * matching their pins, and agent health OK.
 *
 * <p>A failed start fails at once: the loader's own give-up line, or a container that exited, ends the wait, so a failed
 * start never waits out the boot timeout (a Fabric mod's non-daemon thread kept a failed JVM alive for 600 s in the
 * spike; NeoForge's failed start was waited out for 5 minutes in T-M8-06). Every container is stopped on any failure.
 *
 * <p>The boot time, from the container start to {@code Done (}, is measured and kept ({@link #bootTime()}); it is a host
 * finding and is never part of a join timeout.
 */
public final class LoaderHost implements ServerHost, AutoCloseable {

    /** The server's port inside its container. */
    public static final int GAME_PORT = PaperHost.GAME_PORT;
    private static final int TAIL_LINES = 60;
    private static final ObjectMapper JSON = AgentClient.JSON;
    private static final Pattern JAVA_MAJOR = Pattern.compile("version \"(\\d+)(?:\\.(\\d+))?");

    /** Where one wait stands: keep waiting, reached, or the loader gave up (the reason is kept). */
    private enum State { WAITING, REACHED, FAILED }

    private final LoaderCell cell;
    private final LoaderHostOptions options;
    private final String token = AgentClient.randomToken();
    private final LogBuffer logs = new LogBuffer();
    private final GenericContainer<?> container;
    private final AgentClient agent;
    private final List<String> copiedMods;
    /** The mod jars under test, kept to name the one a failed start blames (T-M8-11). */
    private final List<Path> modJars;
    /** Data packs under test, installed by the image before the server starts (ADR-0017 section 4). */
    private final List<DataPack> dataPacks;
    private Health health;
    private Duration bootTime;
    private String failure;

    private LoaderHost(LoaderCell cell, LoaderHostOptions options, List<Path> mods,
            List<DataPack> dataPacks) {
        this.dataPacks = dataPacks;
        this.cell = cell;
        this.options = options;
        this.modJars = mods;
        this.copiedMods = mods.stream().map(mod -> mod.getFileName().toString()).toList();
        this.container = new GenericContainer<>(DockerImageName.parse(cell.hostImage() + "@" + cell.hostDigest()));
        this.agent = new AgentClient(container, token);
    }

    /** Starts the cell's server and returns it ready, or throws {@link PaperHostException} with the log tail. */
    public static LoaderHost start(LoaderCell cell, LoaderHostOptions options) {
        Objects.requireNonNull(cell, "cell");
        if (!options.eulaAccepted()) {
            throw new PaperHostException("The Minecraft EULA is not accepted. Set " + Eula.VARIABLE
                    + "=true after reading https://aka.ms/MinecraftEULA; the harness never accepts it for you.");
        }
        if (!Files.isRegularFile(options.agentJar())) {
            throw new PaperHostException("agent jar not found: " + options.agentJar()
                    + " (build it with the loader agent's shadowJar task)");
        }
        // Everything that can fail without Docker fails first, so a bad glob or pin never leaves a container behind.
        List<Path> mods = Mods.resolve(options.artefactBase(), options.modGlobs());
        List<DataPack> dataPacks = DataPack.resolve(options.artefactBase(), options.datapackGlobs());
        List<PinnedFile> pins = LoaderPins.resolve(LoaderEnvironment.downloads(cell), options.cacheDir(), LoaderPins.http());
        Map<String, Path> jars = LoaderEnvironment.jarCopies(pins, options.agentJar(), mods);
        Map<String, ServerFile> config = LoaderEnvironment.configCopies(options.files());
        if (cell.vanillaSha256() == null) {
            throw new PaperHostException(cell.identity() + ": vanilla-sha256 is TODO in versions.yml, so the vanilla server "
                    + "jar the install fetches is not pinned (ADR-0017 section 6). Pin it from a real run and the "
                    + "published value; the cell does not start without it.");
        }
        LoaderHost host = new LoaderHost(cell, options, mods,
                dataPacks);
        host.boot(jars, config);
        return host;
    }

    private void boot(Map<String, Path> jars, Map<String, ServerFile> config) {
        configure(jars, config);
        try {
            long started = System.nanoTime();
            container.start();
            awaitDone();
            bootTime = Duration.ofNanos(System.nanoTime() - started);
            verifyServer();
            awaitAgent();
            PermissionCheck.require(cell.identity(), logs.all(), config.keySet());
            verifyMods();
            // The same deterministic-world rules PaperHost sets (T-M8-27), so the leak detector's baseline and the world a
            // test runs in do not depend on the platform. The spawn stays vanilla: PaperHost moves it only for 1.8.8's flat world.
            GameRules.apply(cell.mc(), this::command);
        } catch (RuntimeException e) {
            String tail = logTail();
            close();
            String message = e instanceof PaperHostException ? e.getMessage()
                    : cell.identity() + " failed to start: " + e.getMessage();
            // The loader gave up (or the container exited) with mods under test installed, and its log names one of them: the
            // mod crashed at load. INVALID naming the mod, never a boot or join timeout (ADR-0017 section 4, T-M8-11). A
            // failed start that names no declared mod stays a plain failed boot, a host finding.
            if (failure != null) {
                String modCrash = ModLoad.invalid(cell.identity(), modJars, failure, logs.all());
                if (modCrash != null) {
                    message = modCrash;
                }
            }
            throw new PaperHostException(message + "\n--- log tail ---\n" + tail, e);
        }
    }

    private void configure(Map<String, Path> jars, Map<String, ServerFile> config) {
        LoaderEnvironment.env(cell, token, AgentClient.AGENT_PORT).forEach(container::withEnv);
        container.withLogConsumer(frame -> logs.append(frame.getUtf8String()));
        jars.forEach((path, jar) -> container.withCopyFileToContainer(MountableFile.forHostPath(jar), path));
        // Owned by the server user, or the loader or a mod cannot rewrite it (T-M8-02).
        config.forEach((path, file) -> container.withCopyToContainer(file, path));
        // Installed by the image into world/datapacks before the server starts: no reload ever (T-M8-09).
        dataPacks.forEach(pack -> container.withCopyToContainer(pack.file(), pack.containerPath()));
        if (!dataPacks.isEmpty()) {
            container.withEnv("DATAPACKS", DataPack.env(dataPacks));
        }
        container.addExposedPort(GAME_PORT);
        // Readiness is ours: the default wait is a 60 s port wait, a second timeout that would hide a slow boot.
        container.waitingFor(new NoPortWait());
    }

    /** {@code Done (} in the log, or the loader's give-up line or an exit, which fail now and not at the timeout. */
    private void awaitDone() {
        String what = "server log shows \"Done (\"";
        State state;
        try {
            state = Await.until(what, () -> {
                String found = LoaderEnvironment.bootFailure(cell, logs.all());
                if (found != null) {
                    failure = "the loader reported a failed start: " + found;
                    return State.FAILED;
                }
                if (!container.isRunning()) {
                    failure = "the container exited before \"Done (\"";
                    return State.FAILED;
                }
                return logs.contains("Done (") ? State.REACHED : State.WAITING;
            }, s -> s != State.WAITING, options.bootTimeout());
        } catch (AwaitTimeoutException e) {
            throw new PaperHostException("BOOT TIMEOUT (a host finding, not a join timeout): timed out waiting for " + what
                    + " on " + cell.identity() + " after " + options.bootTimeout() + ": " + e.getMessage(), e);
        }
        if (state == State.FAILED) {
            throw new PaperHostException(cell.identity() + " failed to boot: " + failure);
        }
    }

    /** Readiness is the log line and agent health OK together (ADR-0005). */
    private void awaitAgent() {
        try {
            Await.until("agent /v1/health OK", () -> {
                if (!container.isRunning()) {
                    throw new IllegalStateException("container exited");
                }
                health = agent.health();
                return true;
            }, Boolean::booleanValue, options.agentTimeout());
        } catch (AwaitTimeoutException e) {
            throw new PaperHostException("timed out waiting for agent /v1/health OK on " + cell.identity()
                    + " (the server finished loading without a working agent): " + e.getMessage(), e);
        }
        if (!cell.loader().equals(health.platform())) {
            throw new PaperHostException("agent reports platform " + health.platform() + ", cell is " + cell.identity());
        }
        if (!cell.mc().equals(health.mcVersion())) {
            throw new PaperHostException("agent reports Minecraft " + health.mcVersion() + ", cell is " + cell.mc());
        }
    }

    /**
     * The pins must be what actually runs: the Java major matches the cell, the loader jar and the Fabric API match their
     * sha256, and the vanilla server jar the install fetched matches {@code vanilla-sha256} (ADR-0017 section 6), checked
     * after the install and before the cell is ready.
     */
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
        for (LoaderEnvironment.Download pin : LoaderEnvironment.downloads(cell)) {
            expectSha256(pin.containerPath(), pin.sha256(), pin.name());
        }
        expectSha256(LoaderEnvironment.vanillaJarPath(cell), cell.vanillaSha256(),
                "the vanilla server jar (vanilla-sha256 in versions.yml)");
    }

    private void expectSha256(String path, String expected, String what) {
        String sums = exec("sha256sum", path);
        if (!sums.startsWith(expected)) {
            throw new PaperHostException(what + " differs from the pin: expected sha256 " + expected + ", the container's "
                    + path + " is:\n" + sums);
        }
    }

    /** Every mod copied in must be in {@code /data/mods}, the folder the loader reads. */
    private void verifyMods() {
        String listing = exec("ls", "/data/mods");
        for (String name : copiedMods) {
            if (!listing.contains(name)) {
                throw new PaperHostException("mod " + name + " was not copied into /data/mods:\n" + listing);
            }
        }
    }

    /** Runs a console command through the agent. Setup only; the reply is best effort (ADR-0002). */
    @Override
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

    /** Calls an agent endpoint and returns the reply's {@code data}; a reply that is not ok is an error. */
    @Override
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

    @Override
    public List<String> agentTranscript() {
        return agent.transcript();
    }

    /** Runs a command in the container; a non-zero exit is an error with its stderr. */
    String exec(String... args) {
        return agent.exec(args);
    }

    @Override
    public java.util.Map<String, List<String>> dataPackNamespaces() {
        java.util.Map<String, List<String>> namespaces = new java.util.LinkedHashMap<>();
        dataPacks.forEach(pack -> namespaces.put(pack.id(), pack.namespaces()));
        return namespaces;
    }

    /** The ids the mod jars under test declare (from their own descriptors), which {@code GET /v1/mods} must list. */
    @Override
    public List<String> modIds() {
        return modJars.stream().flatMap(jar -> ModLoad.ids(jar).stream()).toList();
    }

    @Override
    public List<String> dataPackIds() {
        return dataPacks.stream().map(DataPack::id).toList();
    }

    public LoaderCell cell() {
        return cell;
    }

    @Override
    public String mc() {
        return cell.mc();
    }

    @Override
    public Health health() {
        return health;
    }

    @Override
    public LogBuffer logs() {
        return logs;
    }

    @Override
    public String logTail() {
        return logs.tail(TAIL_LINES);
    }

    /** From the container start to {@code Done (} in its log, a host finding (install included on a cold start). */
    public Duration bootTime() {
        return bootTime;
    }

    /** The host name a driver connects to. */
    @Override
    public String gameHost() {
        return container.getHost();
    }

    /** The host port mapped to the server's game port. */
    @Override
    public int gamePort() {
        return container.getMappedPort(GAME_PORT);
    }

    @Override
    public String agentToken() {
        return token;
    }

    @Override
    public void close() {
        container.stop();
    }
}
