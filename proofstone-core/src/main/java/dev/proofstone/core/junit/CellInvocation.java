package dev.proofstone.core.junit;

import dev.proofstone.api.Agent;
import dev.proofstone.api.Client;
import dev.proofstone.api.Proofstone;
import dev.proofstone.core.cell.Cell;
import dev.proofstone.core.driver.ClientDriver;
import dev.proofstone.core.driver.ClientSession;
import dev.proofstone.core.driver.ConnectSpec;
import dev.proofstone.core.host.PaperHost;
import dev.proofstone.core.host.PaperHostOptions;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * One cell of one test method. It also resolves the parameters, because it knows its cell.
 *
 * <p>The server is per class and cell (PER_CLASS): it lives in the class-level store, is booted by the first
 * invocation that needs it and is closed when the class finishes. The client is per test: a fresh session in the
 * method-level store, closed when the test ends.
 */
final class CellInvocation implements TestTemplateInvocationContext, ParameterResolver {

    private static final Namespace NAMESPACE = Namespace.create(CellInvocation.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final String AGENT_JAR = "proofstone.agentJar";

    private final Cell cell;
    private final Proofstone config;

    CellInvocation(Cell cell, Proofstone config) {
        this.cell = cell;
        this.config = config;
    }

    @Override
    public String getDisplayName(int invocationIndex) {
        return cell.platform() + "@" + cell.mc();
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of(this);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        return type == dev.proofstone.api.Cell.class || type == Agent.class || type == Client.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        if (type == dev.proofstone.api.Cell.class) {
            return new CellView(cell);
        }
        PaperHost host = host(context);
        if (type == Agent.class) {
            return new HostAgent(host);
        }
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(
                "client:" + cell.mc(), key -> joinClient(host, context), SessionClient.class);
    }

    private PaperHost host(ExtensionContext context) {
        ExtensionContext classContext = context;
        while (classContext.getTestMethod().isPresent()) {
            classContext = classContext.getParent().orElseThrow();
        }
        return classContext.getStore(NAMESPACE).getOrComputeIfAbsent(
                "host:" + cell.mc(), key -> new HostResource(boot()), HostResource.class).host();
    }

    private PaperHost boot() {
        String jar = System.getProperty(AGENT_JAR);
        if (jar == null) {
            throw new ParameterResolutionException("system property " + AGENT_JAR + " is not set: it points at "
                    + "the shaded agent jar");
        }
        PaperHostOptions options = PaperHostOptions.of(Path.of(jar), true)
                .withArtefacts(Path.of("").toAbsolutePath(), List.of(config.plugins()));
        return PaperHost.start(cell, options);
    }

    private SessionClient joinClient(PaperHost host, ExtensionContext context) {
        ClientDriver driver = driver();
        if (!driver.protocolVersion().equals(cell.mc())) {
            throw new ParameterResolutionException("driver " + driver.id() + " speaks " + driver.protocolVersion()
                    + " natively but the cell is " + cell.mc() + "; bridging is not available yet");
        }
        // A hash of the unique test id keeps the name stable for a test and different between tests.
        String name = String.format("T%08x", context.getUniqueId().hashCode());
        ClientSession session = driver.connect(new ConnectSpec(host.gameHost(), host.gamePort(), name,
                CONNECT_TIMEOUT));
        return new SessionClient(name, session);
    }

    private static ClientDriver driver() {
        for (ClientDriver driver : ServiceLoader.load(ClientDriver.class)) {
            if (driver.id().equals("mcpl")) {
                return driver;
            }
        }
        throw new ParameterResolutionException("no mcpl client driver on the classpath: add drivers:mcpl "
                + "to the test runtime");
    }

    /** Closes the server when the class-level store closes. */
    private record HostResource(PaperHost host) implements AutoCloseable {
        @Override
        public void close() {
            host.close();
        }
    }
}
