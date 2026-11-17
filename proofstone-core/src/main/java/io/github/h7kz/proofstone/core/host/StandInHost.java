package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.host.standin.StandIn;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/**
 * The discovery stand-in's container (ADR-0018 section 4): a container of the pinned image, started with
 * {@code --entrypoint java}, running the one {@link StandIn} class copied in from the harness classpath. Nothing is
 * mounted. Its log is the record of every name the cell asked.
 */
final class StandInHost implements AutoCloseable {

    static final String ALIAS = "stand-in";
    private static final String DIR = "/standin";
    private static final String CLASS_RESOURCE = StandIn.class.getName().replace('.', '/') + ".class";
    private static final int TAIL_LINES = 60;

    private final GenericContainer<?> container;
    private final LogBuffer logs = new LogBuffer();

    private StandInHost(String image, Network network, CellTrust trust) {
        this.container = new GenericContainer<>(DockerImageName.parse(image))
                .waitingFor(new NoPortWait())
                .withNetwork(network)
                .withNetworkAliases(ALIAS)
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java"))
                .withCommand("-cp", DIR, StandIn.class.getName(), DIR + "/stand-in.p12")
                .withCopyToContainer(Transferable.of(classBytes()), DIR + "/" + CLASS_RESOURCE)
                .withCopyToContainer(Transferable.of(trust.stubKeystore()), DIR + "/stand-in.p12")
                .withLogConsumer(frame -> logs.append(frame.getUtf8String()));
    }

    /** Starts it and waits for the line that says DNS and TLS are both up; on a failure it stops and throws. */
    static StandInHost start(String image, Network network, CellTrust trust, Duration timeout) {
        StandInHost host = new StandInHost(image, network, trust);
        try {
            host.container.start();
            Await.until("the stand-in's ready line", () -> {
                if (!host.container.isRunning()) {
                    throw new IllegalStateException("stand-in container exited");
                }
                return host.logs.contains(StandIn.READY_PREFIX);
            }, Boolean::booleanValue, timeout);
        } catch (RuntimeException e) {
            String tail = host.logTail();
            host.close();
            throw new PaperHostException("the stand-in did not start: " + e.getMessage()
                    + "\n--- stand-in log tail ---\n" + tail, e);
        }
        return host;
    }

    /** The compiled single class, from the harness classpath (Java 17 bytecode, JDK classes only). */
    static byte[] classBytes() {
        try (InputStream in = StandIn.class.getClassLoader().getResourceAsStream(CLASS_RESOURCE)) {
            if (in == null) {
                throw new PaperHostException("the stand-in class " + CLASS_RESOURCE + " is not on the classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new PaperHostException("cannot read the stand-in class: " + e.getMessage(), e);
        }
    }

    /** Its IPv4 address on the cell network: the server's and the driver host's {@code --dns}. */
    String address() {
        Map<String, com.github.dockerjava.api.model.ContainerNetwork> networks =
                container.getContainerInfo().getNetworkSettings().getNetworks();
        if (networks.size() != 1) {
            throw new PaperHostException("the stand-in is on " + networks.keySet() + ", expected the one cell network");
        }
        String address = networks.values().iterator().next().getIpAddress();
        if (address == null || address.isBlank()) {
            throw new PaperHostException("the stand-in has no address on the cell network");
        }
        return address;
    }

    /** The image's {@code cacerts}: the trust store the cell's two JVMs start from. */
    byte[] cacerts() {
        try {
            String home = container.execInContainer("sh", "-c", "printf %s \"$JAVA_HOME\"").getStdout().strip();
            if (home.isEmpty()) {
                throw new PaperHostException("the pinned image sets no JAVA_HOME; cannot find its cacerts");
            }
            return container.copyFileFromContainer(home + "/lib/security/cacerts", InputStream::readAllBytes);
        } catch (IOException e) {
            throw new PaperHostException("cannot read cacerts from the stand-in container: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaperHostException("reading cacerts was interrupted", e);
        }
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
