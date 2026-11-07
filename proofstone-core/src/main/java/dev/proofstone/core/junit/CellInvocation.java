package dev.proofstone.core.junit;

import dev.proofstone.api.Agent;
import dev.proofstone.api.Client;
import dev.proofstone.api.Isolation;
import dev.proofstone.api.Proofstone;
import dev.proofstone.core.cell.Cell;
import dev.proofstone.core.driver.ClientDriver;
import dev.proofstone.core.driver.ClientSession;
import dev.proofstone.core.driver.ConnectSpec;
import dev.proofstone.core.host.PaperHost;
import dev.proofstone.core.host.PaperHostOptions;
import dev.proofstone.core.host.ViaProxyHost;
import dev.proofstone.core.verdict.Attempt;
import dev.proofstone.core.versions.VersionsCatalog;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import org.testcontainers.containers.Network;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
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
 * <p>The server is per class and cell by default ({@link Isolation.Mode#PER_CLASS}): it lives in the class-level
 * store, is booted by the first invocation that needs it and is closed when the class finishes. With
 * {@link Isolation.Mode#PER_TEST} it lives in the invocation's own store instead. Each test also gets a lifecycle
 * (T-M2-08): before it, a fresh client joins and the player is reset and the log cursor recorded; after it, the
 * log is checked, patched config restored and the client disconnected.
 */
final class CellInvocation implements TestTemplateInvocationContext, ParameterResolver, BeforeEachCallback,
        AfterEachCallback {

    private static final Namespace NAMESPACE = Namespace.create(CellInvocation.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final String AGENT_JAR = "proofstone.agentJar";

    private static final String STATE = "state";

    private final Cell cell;
    private final Proofstone config;
    private final Isolation.Mode isolation;

    private final VersionsCatalog catalog;
    private final boolean bridged;
    /** Whether the run installs no plugin jars (self-tests section 2); read once, so a bad value fails at planning. */
    private final boolean controlRun;
    /** This run's number, from 1 to {@link #repeats}. */
    private final int repetition;
    /** N of the flake detector (self-tests section 4), or 1 when the detector is off. */
    private final int repeats;

    CellInvocation(Cell cell, Proofstone config, Isolation.Mode isolation, VersionsCatalog catalog, int repetition,
            int repeats) {
        this.cell = cell;
        this.config = config;
        this.isolation = isolation;
        this.catalog = catalog;
        this.controlRun = VerdictGate.controlRun();
        this.repetition = repetition;
        this.repeats = repeats;
        // The driver speaks one protocol; any other server needs ViaProxy in between (ADR-0004). With no driver on
        // the classpath the cell is not bridged, and the join fails loudly later.
        this.bridged = findDriver().map(driver -> !driver.protocolVersion().equals(cell.mc())).orElse(false);
    }

    @Override
    public String getDisplayName(int invocationIndex) {
        return cellId() + (bridged ? " (bridged)" : "") + (repeats > 1 ? " repeat " + repetition + "/" + repeats : "");
    }

    /** The cell's display id without the bridged marker, for example {@code paper@1.8.8}. */
    private String cellId() {
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
            return new CellView(cell, bridged);
        }
        TestState state = context.getStore(NAMESPACE).get(STATE, TestState.class);
        if (state == null) {
            throw new ParameterResolutionException("no test lifecycle state: the server did not start for this test");
        }
        if (type == Agent.class) {
            return new HostAgent(state.host, state.patchedPlugins);
        }
        if (state.client == null) {
            throw new ParameterResolutionException("the client joins before the test only when the test method "
                    + "declares a Client parameter; add one to " + context.getRequiredTestMethod().getName());
        }
        return state.client;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        HostResource resource = resource(context);
        PaperHost host = resource.host();
        // Before the join, so anything the join or the reset logs counts for this test.
        TestState state = new TestState(host, ErrorLogGuard.cursor(host));
        state.proxy = resource.proxy();
        context.getStore(NAMESPACE).put(STATE, state);
        if (declaresClient(context)) {
            state.client = joinClient(resource, context);
            // After the join: reset needs the player online. It also teleports to the fixture spot.
            new HostPlayer(host, state.client.name()).reset();
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TestState state = context.getStore(NAMESPACE).get(STATE, TestState.class);
        if (state == null) {
            // beforeEach failed before it had anything to undo; its own failure is already reported. A flake run
            // still counts this run, as a failure: a run that did not start is never a pass.
            if (repeats > 1) {
                AssertionError verdict = judgeRepeat(context, Attempt.fail(
                        "the server or the client did not start; see the failure reported for this run"));
                if (verdict != null) {
                    throw verdict;
                }
            }
            return;
        }
        // Every step runs, whatever an earlier one did, and the first failure is the one reported.
        Throwable failure = null;
        try {
            ErrorLogGuard.check(state.host, state.logCursor);
        } catch (RuntimeException | AssertionError e) {
            failure = e;
        }
        // Before the restore, so the bundle sees the config the test left behind; the server is still up. A flake run
        // keeps every run's bundle, so a FLAKY report has the transcripts of the runs that passed as well.
        if (repeats > 1 || failure != null || context.getExecutionException().isPresent()) {
            try {
                System.err.println("PROOFSTONE BUNDLE: " + BundleWriter.capture(context, cell, state,
                        repeats > 1 ? repetition : 0));
            } catch (RuntimeException e) {
                failure = combine(failure, new IllegalStateException("cannot write the repro bundle", e));
            }
        }
        // Judged only for a pass of a single run: a failure or skip already has its verdict from JUnit. A flake run
        // judges all its runs together, after the last one (see below).
        if (repeats == 1 && failure == null && context.getExecutionException().isEmpty()) {
            failure = VerdictGate.judgePass(context.getRequiredTestMethod(), cellId(), controlRun);
        }
        for (String plugin : state.patchedPlugins) {
            try {
                HostPlugin.restore(state.host, plugin);
            } catch (RuntimeException e) {
                failure = combine(failure, new IllegalStateException("cannot restore the config of " + plugin
                        + "; server log tail:\n" + state.host.logTail(), e));
            }
        }
        try {
            state.close();
        } catch (RuntimeException e) {
            failure = combine(failure, new IllegalStateException("cannot disconnect the client", e));
        }
        if (repeats > 1) {
            // This run's outcome is what it finally was, after its own restore and disconnect.
            Attempt attempt = failure != null ? Attempt.fail(detail(failure))
                    : context.getExecutionException().isPresent()
                            ? Attempt.fail(detail(context.getExecutionException().get())) : Attempt.pass();
            failure = combine(failure, judgeRepeat(context, attempt));
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
    }

    private static Throwable combine(Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (next != null) {
            first.addSuppressed(next);
        }
        return first;
    }

    /** A failure's message, or its class name when it has none: a FAIL run always needs a detail. */
    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getName() : message;
    }

    /**
     * Records this run in the test's ledger. After the last run it judges all of them together (self-tests section
     * 4). The ledger lives in the root store, keyed by test and cell, so it survives from one repeat to the next.
     *
     * @return the verdict failure, set only after the last run and only when the runs disagree
     */
    private AssertionError judgeRepeat(ExtensionContext context, Attempt attempt) {
        Method test = context.getRequiredTestMethod();
        String key = test.getDeclaringClass().getName() + "#" + test.getName() + "@" + cellId();
        RepeatLedger ledger = context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(key, ignored -> new RepeatLedger(repeats), RepeatLedger.class);
        List<Attempt> runs = ledger.record(repetition, attempt);
        if (runs == null) {
            return null;
        }
        context.getRoot().getStore(NAMESPACE).remove(key);
        return VerdictGate.judgeRepeats(test, cellId(), controlRun, runs);
    }

    private static boolean declaresClient(ExtensionContext context) {
        for (Class<?> type : context.getRequiredTestMethod().getParameterTypes()) {
            if (type == Client.class) {
                return true;
            }
        }
        return false;
    }

    private HostResource resource(ExtensionContext context) {
        ExtensionContext owner = context;
        if (isolation == Isolation.Mode.PER_CLASS) {
            while (owner.getTestMethod().isPresent()) {
                owner = owner.getParent().orElseThrow();
            }
        }
        return owner.getStore(NAMESPACE).getOrComputeIfAbsent(
                "host:" + cell.mc(), key -> boot(), HostResource.class);
    }

    /** Starts the cell's topology: the server, plus ViaProxy on the same network when the cell is bridged. */
    private HostResource boot() {
        String jar = System.getProperty(AGENT_JAR);
        if (jar == null) {
            throw new ParameterResolutionException("system property " + AGENT_JAR + " is not set: it points at "
                    + "the shaded agent jar");
        }
        // A control run installs no plugin jars. The agent and the fixture world are not plugins under test, so they stay.
        List<String> plugins = List.of(config.plugins());
        if (controlRun) {
            System.err.println("PROOFSTONE CONTROL RUN: plugin jars are not installed; a passing test is VACUOUS");
            plugins = List.of();
        }
        PaperHostOptions options = PaperHostOptions.of(Path.of(jar), true)
                .withArtefacts(Path.of("").toAbsolutePath(), plugins);
        if (!bridged) {
            return new HostResource(PaperHost.start(cell, options), null, null);
        }
        Network network = Network.newNetwork();
        PaperHost host = null;
        try {
            host = PaperHost.start(cell, options.withNetwork(network));
            ViaProxyHost proxy = ViaProxyHost.start(cell, catalog.driverImage("viaproxy"), network);
            return new HostResource(host, proxy, network);
        } catch (RuntimeException e) {
            if (host != null) {
                host.close();
            }
            network.close();
            throw e;
        }
    }

    private SessionClient joinClient(HostResource resource, ExtensionContext context) {
        ClientDriver driver = findDriver().orElseThrow(() -> new ParameterResolutionException(
                "no mcpl client driver on the classpath: add drivers:mcpl to the test runtime"));
        // Bridged cells are reached through ViaProxy, which speaks the driver's protocol (ADR-0004).
        String address = resource.proxy() == null ? resource.host().gameHost() : resource.proxy().gameHost();
        int port = resource.proxy() == null ? resource.host().gamePort() : resource.proxy().gamePort();
        // A hash of the unique test id keeps the name stable for a test and different between tests.
        String name = String.format("T%08x", context.getUniqueId().hashCode());
        ClientSession session = driver.connect(new ConnectSpec(address, port, name, CONNECT_TIMEOUT));
        return new SessionClient(name, session);
    }

    private static Optional<ClientDriver> findDriver() {
        for (ClientDriver driver : ServiceLoader.load(ClientDriver.class)) {
            if (driver.id().equals("mcpl")) {
                return Optional.of(driver);
            }
        }
        return Optional.empty();
    }

    /** Closes ViaProxy, the server and the network when the owning store closes. */
    private record HostResource(PaperHost host, ViaProxyHost proxy, Network network) implements AutoCloseable {
        @Override
        public void close() {
            try {
                if (proxy != null) {
                    proxy.close();
                }
            } finally {
                try {
                    host.close();
                } finally {
                    if (network != null) {
                        network.close();
                    }
                }
            }
        }
    }
}
