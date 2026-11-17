package io.github.h7kz.proofstone.core.host;

import com.fasterxml.jackson.databind.JavaType;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.driver.bedrock.BedrockDriver;
import io.github.h7kz.proofstone.core.driver.bedrock.ExecCurlTransport;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import java.nio.file.Path;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The Bedrock driver host's container (ADR-0018 section 2): a container of the pinned image, started with
 * {@code --entrypoint java}, running the shaded driver jar copied in. Its DNS is the stand-in and its JVM trusts the
 * cell's trust store, because the Cloudburst client fetches the two Microsoft discovery documents in every JVM. The
 * harness reaches it with curl inside the container ({@link ExecCurlTransport}), so no port is published.
 */
final class BedrockDriverHost implements AutoCloseable {

    static final String ALIAS = "driver";
    private static final String JAR = "/driver/bedrock-host-all.jar";
    private static final String READY = "bedrock driver host ready";
    private static final int TAIL_LINES = 60;

    private final GenericContainer<?> container;
    private final LogBuffer logs = new LogBuffer();
    private final String token = AgentClient.randomToken();

    private BedrockDriverHost(String image, Network network, String dns, Path jar, byte[] trustStore) {
        this.container = new GenericContainer<>(DockerImageName.parse(image))
                .waitingFor(new NoPortWait())
                .withNetwork(network)
                .withNetworkAliases(ALIAS)
                .withEnv("JAVA_TOOL_OPTIONS", CellTrust.javaToolOptions(GeyserHost.TRUST_PATH))
                .withCreateContainerCmdModifier(cmd -> {
                    cmd.withEntrypoint("java");
                    cmd.getHostConfig().withDns(dns);
                })
                .withCommand("-jar", JAR, token)
                .withCopyFileToContainer(MountableFile.forHostPath(jar), JAR)
                .withCopyToContainer(Transferable.of(trustStore), GeyserHost.TRUST_PATH)
                .withLogConsumer(frame -> logs.append(frame.getUtf8String()));
    }

    /** Starts it and waits for its ready line and a health answer; on a failure it stops and throws. */
    static BedrockDriverHost start(String image, Network network, String dns, Path jar, byte[] trustStore,
            Duration timeout) {
        BedrockDriverHost host = new BedrockDriverHost(image, network, dns, jar, trustStore);
        try {
            host.container.start();
            Await.until("the driver host's ready line", () -> {
                if (!host.container.isRunning()) {
                    throw new IllegalStateException("driver host container exited");
                }
                return host.logs.contains(READY);
            }, Boolean::booleanValue, timeout);
            String reply = host.transport().call("GET", BedrockRpc.HEALTH, null, Duration.ofSeconds(10));
            JavaType type = BedrockRpc.JSON.getTypeFactory().constructParametricType(BedrockRpc.Envelope.class,
                    BedrockRpc.Health.class);
            BedrockRpc.Envelope<BedrockRpc.Health> envelope = BedrockRpc.JSON.readValue(reply, type);
            if (!envelope.ok() || envelope.data().rpc() != BedrockRpc.VERSION) {
                throw new PaperHostException("the driver host's health is not RPC v" + BedrockRpc.VERSION + ": " + reply);
            }
        } catch (RuntimeException | java.io.IOException e) {
            String tail = host.logTail();
            host.close();
            throw new PaperHostException("the Bedrock driver host did not start: " + e.getMessage()
                    + "\n--- driver host log tail ---\n" + tail, e);
        }
        return host;
    }

    ExecCurlTransport transport() {
        return new ExecCurlTransport(container, token);
    }

    BedrockDriver driver() {
        return new BedrockDriver(transport());
    }

    /** The RPC bearer token; harness code only, redacted from every bundle. */
    String token() {
        return token;
    }

    LogBuffer logs() {
        return logs;
    }

    String logTail() {
        return logs.tail(TAIL_LINES);
    }

    @Override
    public void close() {
        container.stop();
    }
}
