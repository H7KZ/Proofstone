package io.github.h7kz.proofstone.core.host;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import io.github.h7kz.proofstone.protocol.Envelope;
import io.github.h7kz.proofstone.protocol.Health;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The proxy container of a topology (ADR-0016): {@code itzg/mc-proxy} by digest, which downloads the pinned proxy
 * build; the jar's sha256 and the Java major are checked in the container. The config files and the agent are copied
 * in, never mounted (ADR-0005). Ready means the proxy's ready line and agent health OK. Only the proxy's game port is
 * published; the agent is reached with {@code curl} inside the container.
 */
public final class ProxyHost implements AgentEndpoint, AutoCloseable {

    private static final int TAIL_LINES = 60;

    private final ProxyHostOptions options;
    private final String token = AgentClient.randomToken();
    private final LogBuffer logs = new LogBuffer();
    private final GenericContainer<?> container;
    private final AgentClient agent;
    private Health health;

    private ProxyHost(ProxyHostOptions options) {
        this.options = options;
        this.container = new GenericContainer<>(DockerImageName.parse(options.image()));
        this.agent = new AgentClient(container, token);
    }

    /**
     * Starts the proxy on {@code network} as {@link TopologyHost#PROXY_ALIAS} with {@code files} (container path to
     * content, {@link TopologyConfig#proxyFiles}) copied in. Returns it ready, or closes it and throws with its log
     * tail.
     */
    public static ProxyHost start(ProxyHostOptions options, Network network, Map<String, String> files) {
        if (!Files.isRegularFile(options.agentJar())) {
            throw new PaperHostException("proxy agent jar not found: " + options.agentJar());
        }
        ProxyHost host = new ProxyHost(options);
        host.configure(network, files);
        try {
            host.container.start();
            host.await("the proxy log shows \"" + options.kind().readyLine() + "\"",
                    () -> host.logs.contains(options.kind().readyLine()), options.bootTimeout());
            host.await("proxy agent /v1/health OK", () -> {
                host.health = host.agent.health();
                return true;
            }, options.agentTimeout());
            host.verify();
        } catch (RuntimeException e) {
            String tail = host.logTail();
            host.close();
            throw new PaperHostException(options.kind().id() + " proxy failed to start: " + e.getMessage()
                    + "\n--- proxy log tail ---\n" + tail, e);
        }
        return host;
    }

    private void configure(Network network, Map<String, String> files) {
        ProxyKind kind = options.kind();
        container.withEnv("TYPE", kind.imageType())
                // The image would otherwise fetch an RCON plugin and default configs from GitHub (ADR-0016 section 2).
                .withEnv("ENABLE_RCON", "false")
                .withEnv("SKIP_DOWNLOAD_DEFAULTS", "true")
                .withEnv("PROOFSTONE_AGENT_TOKEN", token)
                .withEnv("PROOFSTONE_AGENT_PORT", Integer.toString(AgentClient.AGENT_PORT))
                .withNetwork(network)
                .withNetworkAliases(TopologyHost.PROXY_ALIAS)
                .withExposedPorts(TopologyConfig.PROXY_PORT)
                .withLogConsumer(frame -> logs.append(frame.getUtf8String()));
        switch (kind) {
            case VELOCITY -> container.withEnv("VELOCITY_VERSION", options.version())
                    .withEnv("VELOCITY_BUILD_ID", options.build());
            case BUNGEECORD -> container.withEnv("BUNGEE_JOB_ID", options.build());
        }
        files.forEach((path, content) -> container.withCopyToContainer(Transferable.of(content), path));
        container.withCopyFileToContainer(MountableFile.forHostPath(options.agentJar()),
                "/server/plugins/proofstone-agent.jar");
    }

    private void await(String what, Supplier<Boolean> probe, Duration timeout) {
        try {
            Await.until(what, () -> {
                if (!container.isRunning()) {
                    throw new IllegalStateException("proxy container exited");
                }
                return probe.get();
            }, Boolean::booleanValue, timeout);
        } catch (AwaitTimeoutException e) {
            throw new PaperHostException("timed out waiting for " + what + ": " + e.getMessage(), e);
        }
    }

    /** The pins must be what runs (ADR-0016 section 2): jar sha256, Java major, platform and version. */
    private void verify() {
        String sums = agent.exec("sh", "-c", "sha256sum " + options.kind().jarGlob());
        if (!sums.contains(options.jarSha256())) {
            throw new PaperHostException("proxy jar sha256 differs from the pinned " + options.jarSha256() + ":\n"
                    + sums);
        }
        String java = agent.exec("java", "-version");
        if (!java.matches("(?s).*version \"" + options.javaMajor() + "[.\"].*")) {
            throw new PaperHostException("proxy wants Java " + options.javaMajor() + " but the image runs: " + java);
        }
        if (!options.kind().id().equals(health.platform())) {
            throw new PaperHostException("proxy agent reports platform " + health.platform() + ", expected "
                    + options.kind().id());
        }
        boolean version = switch (options.kind()) {
            case VELOCITY -> health.mcVersion().startsWith(options.version());
            case BUNGEECORD -> health.mcVersion().endsWith(":" + options.build());
        };
        if (!version) {
            throw new PaperHostException("proxy agent reports version " + health.mcVersion() + ", pinned "
                    + options.version() + " build " + options.build());
        }
    }

    /** Calls a proxy agent endpoint and returns the reply's {@code data}; a reply that is not ok is an error. */
    public JsonNode request(String method, String path, Object body) {
        String call = method + " " + path;
        try {
            String reply = agent.call(method, path, body == null ? null : AgentClient.JSON.writeValueAsString(body));
            Envelope<JsonNode> envelope = AgentClient.parse(reply, new TypeReference<>() { });
            if (!envelope.ok()) {
                throw new PaperHostException("proxy agent " + call + " failed: " + envelope.error());
            }
            return envelope.data() == null ? AgentClient.JSON.createObjectNode() : envelope.data();
        } catch (JsonProcessingException e) {
            throw new PaperHostException("cannot encode the body of " + call, e);
        }
    }

    /** Runs a proxy console command through the agent. Setup only; the reply is best effort (ADR-0002). */
    @Override
    public String command(String command) {
        return request("POST", "/v1/command", Map.of("command", command)).path("raw").asText();
    }

    public ProxyKind kind() {
        return options.kind();
    }

    /** The proxy agent's {@code /v1/health} reply from readiness. */
    public Health health() {
        return health;
    }

    public LogBuffer logs() {
        return logs;
    }

    public String logTail() {
        return logs.tail(TAIL_LINES);
    }

    /** The agent calls made so far, as JSON lines (repro bundle). */
    public List<String> agentTranscript() {
        return agent.transcript();
    }

    /** The bearer token the proxy agent accepts. Harness code only, never logged. */
    public String agentToken() {
        return token;
    }

    /** The host name a client joins. */
    public String gameHost() {
        return container.getHost();
    }

    /** The host port mapped to the proxy's bind port, the one port a topology publishes. */
    public int gamePort() {
        return container.getMappedPort(TopologyConfig.PROXY_PORT);
    }

    @Override
    public void close() {
        container.stop();
    }
}
