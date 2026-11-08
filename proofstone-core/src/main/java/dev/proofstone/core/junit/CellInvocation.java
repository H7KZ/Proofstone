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
import dev.proofstone.core.host.LeakDetector;
import dev.proofstone.core.host.PaperHostOptions;
import dev.proofstone.core.host.ViaProxyHost;
import dev.proofstone.core.verdict.Attempt;
import dev.proofstone.core.verdict.CellResult;
import dev.proofstone.core.verdict.ConformanceCache;
import dev.proofstone.core.verdict.ConformanceResult;
import dev.proofstone.core.verdict.Verdict;
import dev.proofstone.core.versions.VersionsCatalog;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * One cell of one test method, run with one client driver, once of N when the flake detector is on. It also resolves
 * the parameters, because it knows its cell.
 *
 * <p>The server is per class and cell by default ({@link Isolation.Mode#PER_CLASS}): it lives in the class-level
 * store, is booted by the first invocation that needs it and is closed when the class finishes. With
 * {@link Isolation.Mode#PER_TEST} it lives in the invocation's own store instead. Each test also gets a lifecycle
 * (T-M2-08): before it, a fresh client joins and the player is reset and the log cursor recorded; after it, the
 * log is checked, patched config restored and the client disconnected.
 *
 * <p>A differential run (self-tests section 3) has one invocation per driver of the cell, and both share the server.
 * The reference driver is bound to the cell's network when the server boots, so both drivers can reach it.
 */
final class CellInvocation implements TestTemplateInvocationContext, ParameterResolver, BeforeEachCallback,
        AfterEachCallback {

    private static final Namespace NAMESPACE = Namespace.create(CellInvocation.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final String AGENT_JAR = "proofstone.agentJar";
    /** The driver the cells run is chosen by id; mcpl is the primary (ADR-0004). */
    private static final String DRIVER_PROPERTY = "proofstone.driver";
    private static final String PRIMARY_DRIVER = DifferentialRun.PRIMARY;

    private static final String STATE = "state";
    /** The root-store key of the conformance cache: one per run, so each cell's conformance runs once (section 1). */
    private static final String CONFORMANCE = "conformance";
    /** The root-store key of the run's report collector (RunReport). */
    private static final String REPORT = "report";

    private final Cell cell;
    private final Proofstone config;
    private final Isolation.Mode isolation;

    private final VersionsCatalog catalog;
    /** The driver this invocation runs with. */
    private final String driverId;
    /** Whether this driver's protocol differs from the cell's row, so the join goes through ViaProxy. */
    private final boolean bridged;
    /** Whether the run installs no plugin jars (self-tests section 2); read once, so a bad value fails at planning. */
    private final boolean controlRun;
    /** Whether this invocation is one half of a differential run (self-tests section 3). */
    private final boolean differential;
    /** This run's number, from 1 to {@link #repeats}. */
    private final int repetition;
    /** N of the flake detector (self-tests section 4), or 1 when the detector is off. */
    private final int repeats;

    CellInvocation(Cell cell, Proofstone config, Isolation.Mode isolation, VersionsCatalog catalog, String driverId,
            int repetition, int repeats) {
        this.cell = cell;
        this.config = config;
        this.isolation = isolation;
        this.catalog = catalog;
        this.driverId = driverId;
        this.controlRun = VerdictGate.controlRun();
        this.differential = DifferentialRun.enabled();
        this.repetition = repetition;
        this.repeats = repeats;
        // The driver speaks one protocol; any other server needs ViaProxy in between (ADR-0004). With no driver on
        // the classpath the cell is not bridged, and the join fails loudly later.
        this.bridged = findDriver(driverId).map(driver -> !driver.protocolVersion().equals(cell.mc())).orElse(false);
    }

    /** The driver the run uses when it is not differential: {@code -Dproofstone.driver}, default mcpl. */
    static String configuredDriver() {
        return System.getProperty(DRIVER_PROPERTY, PRIMARY_DRIVER);
    }

    /**
     * The drivers one cell runs with: the configured one, or both drivers of a differential run. Every driver of a
     * differential run is listed in run order, the primary first, so the reference driver compares last.
     */
    static List<String> driversFor(boolean differential) {
        return differential ? DifferentialRun.DRIVERS : List.of(configuredDriver());
    }

    @Override
    public String getDisplayName(int invocationIndex) {
        return cellId() + (bridged ? " (bridged)" : "") + (differential ? " [" + driverId + "]" : "")
                + (repeats > 1 ? " repeat " + repetition + "/" + repeats : "");
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
        if (differential) {
            DifferentialRun.begin();
        }
        HostResource resource = resource(context);
        PaperHost host = resource.host();
        // Before the join, so anything the join or the reset logs counts for this test.
        TestState state = new TestState(host, ErrorLogGuard.cursor(host));
        state.proxy = resource.proxy();
        context.getStore(NAMESPACE).put(STATE, state);
        // Conformance comes first (self-tests section 1): on a failed cell the test body never runs, and the test is INVALID.
        state.conformance = conformance(context, host);
        AssertionError invalid = VerdictGate.judgeInvalid(context.getRequiredTestMethod(), cellId(),
                state.conformance);
        if (invalid != null) {
            state.invalid = invalid;
            throw invalid;
        }
        // Before the join, so the snapshot is the server's state before this test's client arrives.
        state.gameRules = LeakDetector.snapshot(host);
        if (declaresClient(context)) {
            state.client = joinClient(resource, context);
            // After the join: reset needs the player online. It also teleports to the fixture spot.
            new HostPlayer(host, state.client.name()).reset();
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        // Stop recording first, so a failed beforeEach cannot leave the thread recording into the next test.
        List<DifferentialRun.Observation> observations = DifferentialRun.end();
        TestState state = context.getStore(NAMESPACE).get(STATE, TestState.class);
        if (state == null) {
            // beforeEach failed before it had anything to undo; its own failure is already reported. A flake run still
            // counts this run, as a failure: a run that did not start is never a pass.
            if (repeats > 1) {
                // The cell never booted, so its conformance never ran: no plugin verdict exists for it (section 1). This
                // result is not cached, so a later test that boots the cell runs the real conformance.
                ConformanceResult notRun = new ConformanceResult(cellId(), false,
                        "the cell did not boot, so its conformance did not run");
                AssertionError verdict = judgeRepeat(context, Attempt.fail(
                        "the server or the client did not start; see the failure reported for this run"), notRun);
                if (verdict != null) {
                    throw verdict;
                }
            } else {
                // No server, so no bundle: the report still counts the run as the failure it was.
                String reason = context.getExecutionException().map(CellInvocation::detail)
                        .orElse("the server or the client did not start; see the failure reported for this run");
                report(context, new CellResult(reportName(context), cellId(), Verdict.FAIL, reason, null, 1));
            }
            return;
        }
        // Every step runs, whatever an earlier one did, and the first failure is the one reported. An INVALID test
        // already failed in beforeEach, so that failure is the first one.
        Throwable failure = state.invalid;
        try {
            ErrorLogGuard.check(state.host, state.logCursor);
        } catch (RuntimeException | AssertionError e) {
            failure = combine(failure, e);
        }
        // The leak detector (robustness R6) judges a test that is otherwise green, so it never hides another failure.
        if (failure == null && state.gameRules != null) {
            try {
                failure = VerdictGate.judgeLeak(context.getRequiredTestMethod(), cellId(), state.gameRules,
                        LeakDetector.snapshot(state.host));
            } catch (RuntimeException e) {
                failure = new IllegalStateException("cannot read the server state after the test", e);
            }
        }
        DifferentialRun.Disagreement disagreement = null;
        if (differential) {
            List<String> transcript = state.client == null ? List.of() : state.client.transcript();
            disagreement = DifferentialRun.arrive(differentialKey(context),
                    new DifferentialRun.Evidence(driverId, observations, transcript));
            if (disagreement != null) {
                AssertionError verdict = DifferentialRun.failure(
                        context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName(),
                        cellId(), disagreement, state.conformance);
                failure = failure == null ? verdict : combine(verdict, failure);
            }
        }
        // The run's own outcome, before the verdict rules: a VACUOUS or DISAGREE verdict is resolved from it.
        Attempt outcome = failure == null && context.getExecutionException().isEmpty() ? Attempt.pass()
                : Attempt.fail(detail(failure != null ? failure : context.getExecutionException().get()));
        // Judged before the bundle, so a single-run VACUOUS has a bundle with its rerun command (T-M4-09).
        if (repeats == 1 && failure == null && context.getExecutionException().isEmpty()) {
            failure = VerdictGate.judgePass(context.getRequiredTestMethod(), cellId(), controlRun,
                    state.conformance);
        }
        // Before the restore, so the bundle sees the config the test left behind; the server is still up. A flake run
        // keeps every run's bundle, so a FLAKY report has the transcripts of the runs that passed as well.
        if (repeats > 1 || failure != null || context.getExecutionException().isPresent()) {
            try {
                Path bundle = BundleWriter.capture(context, cell, state, repeats > 1 ? repetition : 0, driverId,
                        differential, catalog);
                if (disagreement != null) {
                    DifferentialRun.writeEvidence(bundle, disagreement, state.host.agentToken());
                }
                System.err.println("PROOFSTONE BUNDLE: " + bundle);
            } catch (RuntimeException e) {
                failure = combine(failure, new IllegalStateException("cannot write the repro bundle", e));
            }
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
            // The verdict over all runs replaces this run's own failure as the reported one (the run's failure stays
            // attached), so a FLAKY or VACUOUS verdict is never hidden behind a raw assertion.
            AssertionError verdict = judgeRepeat(context, attempt, state.conformance);
            if (verdict != null) {
                if (failure != null && failure != verdict) {
                    verdict.addSuppressed(failure);
                }
                failure = verdict;
            }
        } else {
            report(context, settle(VerdictGate.single(reportName(context), context.getRequiredTestMethod(), cellId(),
                    controlRun, outcome, disagreement != null, state.conformance), failure));
        }
        // The INVALID failure was already thrown by beforeEach, so it is not thrown twice.
        if (failure != null && failure == state.invalid) {
            return;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
    }

    /**
     * The cell's conformance result, run once per cell per run and cached in the root store (self-tests section 1). A
     * failure of the run itself, rather than a failed scenario, is an error and is not cached.
     */
    private ConformanceResult conformance(ExtensionContext context, PaperHost host) {
        ConformanceCache cache = context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(CONFORMANCE, key -> new ConformanceCache(), ConformanceCache.class);
        return cache.get(cellId(), () -> CellConformance.run(cellId(), host));
    }

    /** The two runs of one test on one cell share this key, so the second one finds the first one's evidence. */
    private String differentialKey(ExtensionContext context) {
        return context.getRequiredTestClass().getName() + "." + context.getRequiredTestMethod().getName() + "@"
                + cell.mc();
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
    private AssertionError judgeRepeat(ExtensionContext context, Attempt attempt, ConformanceResult conformance) {
        Method test = context.getRequiredTestMethod();
        String key = test.getDeclaringClass().getName() + "#" + test.getName() + "@" + cellId();
        RepeatLedger ledger = context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(key, ignored -> new RepeatLedger(repeats), RepeatLedger.class);
        List<Attempt> runs = ledger.record(repetition, attempt);
        if (runs == null) {
            return null;
        }
        context.getRoot().getStore(NAMESPACE).remove(key);
        report(context, VerdictGate.repeated(reportName(context), test, cellId(), controlRun, runs, conformance));
        return VerdictGate.judgeRepeats(test, cellId(), controlRun, runs, conformance);
    }

    /**
     * The test's name in the report: the fully qualified class and method, plus the driver of a differential run, so
     * each driver's run is its own row (the report holds one row per test, cell and driver).
     */
    private String reportName(ExtensionContext context) {
        String name = context.getRequiredTestClass().getName() + "." + context.getRequiredTestMethod().getName();
        return differential ? name + " [" + driverId + "]" : name;
    }

    /** Adds one verdict to the run's report, which is written when the run ends (T-M4-09, X2). */
    private void report(ExtensionContext context, CellResult result) {
        RunReport report = context.getRoot().getStore(NAMESPACE).getOrComputeIfAbsent(REPORT,
                key -> new RunReport(RunReport.defaultDirectory()), RunReport.class);
        report.record(result);
    }

    /**
     * A PASS whose own teardown then failed is reported as a FAIL: the test is red in JUnit, so the report never shows
     * it green.
     */
    private static CellResult settle(CellResult verdict, Throwable failure) {
        if (verdict.verdict() == Verdict.PASS && failure != null) {
            return new CellResult(verdict.test(), verdict.cell(), Verdict.FAIL, detail(failure), null, 1);
        }
        return verdict;
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

    /**
     * Starts the cell's topology: the server, plus ViaProxy and the network when a driver needs them. A differential run
     * boots it for both drivers, so the shared server is bound to every driver the run uses.
     */
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
        List<String> drivers = driversFor(differential);
        // Only a driver other than mcpl needs the network to reach the server, and only a bridged driver needs ViaProxy.
        boolean network = false;
        boolean proxy = false;
        for (String id : drivers) {
            ClientDriver driver = findDriver(id).orElseThrow(() -> new ParameterResolutionException(
                    "no client driver " + id + " on the classpath"));
            boolean bridgedFor = !driver.protocolVersion().equals(cell.mc());
            network |= !PRIMARY_DRIVER.equals(id) || bridgedFor;
            proxy |= bridgedFor;
        }
        if (!network) {
            return new HostResource(PaperHost.start(cell, options), null, null, Map.of());
        }
        Network cellNetwork = Network.newNetwork();
        PaperHost host = null;
        ViaProxyHost viaProxy = null;
        Map<String, ClientDriver> bound = new LinkedHashMap<>();
        try {
            host = PaperHost.start(cell, options.withNetwork(cellNetwork));
            if (proxy) {
                viaProxy = ViaProxyHost.start(cell, catalog.driverImage("viaproxy"), cellNetwork);
            }
            // Only the sidecar drivers are bound: mcpl joins through the host and ViaProxy directly.
            for (String id : drivers) {
                if (!PRIMARY_DRIVER.equals(id)) {
                    bound.put(id, findDriver(id).orElseThrow().onNetwork(cellNetwork,
                            Path.of(System.getProperty("proofstone.repoRoot"))));
                }
            }
            return new HostResource(host, viaProxy, cellNetwork, bound);
        } catch (RuntimeException e) {
            new HostResource(host, viaProxy, cellNetwork, bound).closeQuietly(e);
            throw e;
        }
    }

    private SessionClient joinClient(HostResource resource, ExtensionContext context) {
        ClientDriver driver = PRIMARY_DRIVER.equals(driverId) ? findDriver(driverId).orElseThrow(
                () -> new ParameterResolutionException("no client driver " + driverId + " on the classpath"))
                : resource.drivers().get(driverId);
        if (driver == null) {
            throw new IllegalStateException("driver " + driverId + " was not bound to the cell's network");
        }
        String address;
        int port;
        if (PRIMARY_DRIVER.equals(driverId)) {
            // Bridged cells are reached through ViaProxy, which speaks the driver's protocol (ADR-0004).
            ViaProxyHost via = bridged ? requireProxy(resource) : null;
            address = via == null ? resource.host().gameHost() : via.gameHost();
            port = via == null ? resource.host().gamePort() : via.gamePort();
        } else {
            // A sidecar driver sits on the cell network: the server by its alias when native, ViaProxy when bridged.
            address = bridged ? "viaproxy" : PaperHost.NETWORK_ALIAS;
            port = bridged ? ViaProxyHost.PROXY_PORT : PaperHost.GAME_PORT;
        }
        // A hash of the unique test id keeps the name stable for a test and different between tests.
        String name = String.format("T%08x", context.getUniqueId().hashCode());
        ClientSession session = driver.connect(new ConnectSpec(address, port, name, CONNECT_TIMEOUT));
        return new SessionClient(name, session);
    }

    private static ViaProxyHost requireProxy(HostResource resource) {
        if (resource.proxy() == null) {
            throw new IllegalStateException("a bridged cell has no ViaProxy: the cell was booted without one");
        }
        return resource.proxy();
    }

    /** The driver with this id on the classpath, found through ServiceLoader. */
    static Optional<ClientDriver> findDriver(String id) {
        for (ClientDriver driver : ServiceLoader.load(ClientDriver.class)) {
            if (driver.id().equals(id)) {
                return Optional.of(driver);
            }
        }
        return Optional.empty();
    }

    /**
     * Closes the bound drivers, ViaProxy, the server and the network when the owning store closes. {@code drivers} holds
     * only the sidecar drivers that {@link ClientDriver#onNetwork} started.
     */
    private record HostResource(PaperHost host, ViaProxyHost proxy, Network network, Map<String, ClientDriver> drivers)
            implements AutoCloseable {
        @Override
        public void close() {
            RuntimeException failure = null;
            try {
                closeQuietly(null);
            } catch (RuntimeException e) {
                failure = e;
            }
            if (failure != null) {
                throw failure;
            }
        }

        /** Closes every part, keeping the first failure; a cause already in flight gets the suppressed ones. */
        void closeQuietly(RuntimeException cause) {
            RuntimeException failure = cause;
            List<AutoCloseable> parts = new ArrayList<>(drivers.values());
            parts.add(proxy);
            parts.add(host);
            parts.add(network);
            for (AutoCloseable part : parts) {
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
