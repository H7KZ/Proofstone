package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Clients;
import io.github.h7kz.proofstone.api.Isolation;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.ServerResourcePack;
import io.github.h7kz.proofstone.api.Topology;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.GeyserCell;
import io.github.h7kz.proofstone.core.cell.LoaderCell;
import io.github.h7kz.proofstone.core.driver.ClientDriver;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.Edition;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import io.github.h7kz.proofstone.core.driver.ResourcePackPolicy;
import io.github.h7kz.proofstone.core.host.GeyserHost;
import io.github.h7kz.proofstone.core.host.GeyserHostOptions;
import io.github.h7kz.proofstone.core.host.LoaderHost;
import io.github.h7kz.proofstone.core.host.LoaderHostOptions;
import io.github.h7kz.proofstone.core.host.PaperHost;
import io.github.h7kz.proofstone.core.host.ServerHost;
import io.github.h7kz.proofstone.core.host.LeakDetector;
import io.github.h7kz.proofstone.core.host.PaperHostOptions;
import io.github.h7kz.proofstone.core.host.ResourcePackSource;
import io.github.h7kz.proofstone.core.host.ViaProxyHost;
import io.github.h7kz.proofstone.core.verdict.Attempt;
import io.github.h7kz.proofstone.core.verdict.CellResult;
import io.github.h7kz.proofstone.core.verdict.ConformanceCache;
import io.github.h7kz.proofstone.core.verdict.ConformanceResult;
import io.github.h7kz.proofstone.core.verdict.SkipReason;
import io.github.h7kz.proofstone.core.verdict.Verdict;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import io.github.h7kz.proofstone.protocol.Health;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.opentest4j.TestAbortedException;
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

    static final Namespace NAMESPACE = Namespace.create(CellInvocation.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final String AGENT_JAR = "proofstone.agentJar";
    /** Where the loader pins' downloads are kept between runs (each reused only when its sha256 matches). */
    private static final String LOADER_CACHE = "proofstone.loaderCache";
    /** The shaded Bedrock driver host jar a Geyser cell copies into its driver host container (ADR-0018 section 2). */
    private static final String BEDROCK_HOST_JAR = "proofstone.bedrockHostJar";
    /** Where a Geyser cell keeps its pinned jar downloads between runs (each reused only when its sha256 matches). */
    private static final String GEYSER_DOWNLOADS = "proofstone.geyserDownloads";
    /** How long the agent may take to list a Bedrock client after the spawn, or to forget it (the server notices ~10 s late). */
    private static final Duration BEDROCK_AGENT_WAIT = Duration.ofSeconds(30);
    /** The driver the cells run is chosen by id; mcpl is the primary (ADR-0004). */
    private static final String DRIVER_PROPERTY = "proofstone.driver";
    private static final String PRIMARY_DRIVER = DifferentialRun.PRIMARY;

    private static final String STATE = "state";
    /** The root-store key of the conformance cache: one per run, so each cell's conformance runs once (section 1). */
    static final String CONFORMANCE = "conformance";
    /** The root-store key of the failed conformances' driver transcripts, by cell id (T-M7-21). */
    static final String CONFORMANCE_TRANSCRIPTS = "conformance-transcripts";
    /** The root-store key of the run's report collector (RunReport). */
    private static final String REPORT = "report";

    private final Cell cell;
    /** The loader cell this invocation boots through {@link LoaderHost}; {@code null} on a Bukkit-family cell (ADR-0017). */
    private final LoaderCell loader;
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
    /** The server's view of this run's join timeout, for the bundle; {@code null} when no join stalled (T-M6-14). */
    private volatile String joinStall;
    /** The Geyser cell this invocation boots through {@link GeyserHost}; {@code null} on any other cell (ADR-0018). */
    private final GeyserCell geyser;

    CellInvocation(Cell cell, Proofstone config, Isolation.Mode isolation, VersionsCatalog catalog, String driverId,
            int repetition, int repeats) {
        this(cell, null, config, isolation, catalog, driverId, repetition, repeats);
    }

    CellInvocation(Cell cell, LoaderCell loader, Proofstone config, Isolation.Mode isolation, VersionsCatalog catalog,
            String driverId, int repetition, int repeats) {
        this(cell, loader, null, config, isolation, catalog, driverId, repetition, repeats);
    }

    /**
     * A Geyser cell (ADR-0018): the Paper backend is the {@link Cell} the lifecycle runs, and the Java client joins it with
     * the native driver. The planner refused a bridged or differential run before this is built.
     */
    CellInvocation(GeyserCell geyser, Proofstone config, Isolation.Mode isolation, VersionsCatalog catalog) {
        this(geyser.backend(), null, geyser, config, isolation, catalog, configuredDriver(), 1, 1);
    }

    private CellInvocation(Cell cell, LoaderCell loader, GeyserCell geyser, Proofstone config, Isolation.Mode isolation,
            VersionsCatalog catalog, String driverId, int repetition, int repeats) {
        this.cell = cell;
        this.loader = loader;
        this.geyser = geyser;
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

    /** The id of the native Java driver, the primary one (ADR-0004). */
    static String primaryDriver() {
        return PRIMARY_DRIVER;
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

    /**
     * The cell's display id without the bridged marker, for example {@code paper@1.8.8}; a Geyser cell is named by its whole
     * identity, {@code geyser@2.12.1+paper@26.3} (ADR-0018 section 8), so its report rows, conformance and skips never read
     * as the plain Paper cell.
     */
    private String cellId() {
        return geyser != null ? geyser.identity() : cell.platform() + "@" + cell.mc();
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of(this);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        return type == io.github.h7kz.proofstone.api.Cell.class || type == Agent.class || type == Client.class
                || type == Clients.class || type == Topology.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        TestState state = context.getStore(NAMESPACE).get(STATE, TestState.class);
        if (state == null) {
            throw new ParameterResolutionException("no test lifecycle state: the server did not start for this test");
        }
        if (type == io.github.h7kz.proofstone.api.Cell.class) {
            return new CellView(cell, bridged, state.capabilities);
        }
        if (type == Agent.class) {
            return new HostAgent(state.host, state.patchedPlugins);
        }
        if (type == Clients.class) {
            return state.clients;
        }
        if (type == Topology.class) {
            // Misdeclared: a test that needs a topology says so, and then a single-server cell skips it before this point.
            throw new ParameterResolutionException("INVALID test declaration: " + cellId() + " is a single-server cell "
                    + "and has no Topology; mark " + context.getRequiredTestClass().getName() + " with @Topology.Backends "
                    + "or @Requires(TOPOLOGY), so a single-server cell skips it with its reason (ADR-0016 section 10)");
        }
        if (state.client == null) {
            throw new ParameterResolutionException("the client joins before the test only when the test method "
                    + "declares a Client parameter; add one to " + context.getRequiredTestMethod().getName());
        }
        return state.client;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Set<io.github.h7kz.proofstone.api.Capability> required = CapabilityModel.required(
                context.getRequiredTestClass(), context.getRequiredTestMethod());
        // Only a proxy pack declares these, so the skip needs no server: none is booted for it (ADR-0016 section 10).
        Optional<String> topologyOnly = CapabilityModel.topologyOnlyMissing(required, cellId());
        if (topologyOnly.isPresent()) {
            TestState skipped = new TestState(null, 0);
            skipped.skipped = topologyOnly.get();
            context.getStore(NAMESPACE).put(STATE, skipped);
            throw new TestAbortedException(topologyOnly.get());
        }
        HostResource resource = resource(context);
        ServerHost host = resource.host();
        // Before the join, so anything the join or the reset logs counts for this test.
        TestState state = new TestState(host, ErrorLogGuard.cursor(host));
        state.proxy = resource.proxy();
        state.geyser = resource.geyser();
        state.capabilities = geyser != null ? CapabilityModel.geyserCapabilities(host.health().capabilities())
                : CapabilityModel.cellCapabilities(cell.platform(), host.health().capabilities(), bridged);
        // Extra clients join through the same driver and address as the test's own client (section 11), and answer a
        // resource-pack push as the test declared, or end the join loudly when it declared nothing (ADR-0017 section 7).
        ResourcePackPolicy packs = ResourcePackDeclaration.forTest(ResourcePackDeclaration.answer(
                context.getRequiredTestClass(), context.getRequiredTestMethod()), resource.pack());
        state.clients = new TestClients(name -> connect(resource, name, packs), cellId(), state.capabilities,
                resource.geyser() == null ? null : new TestClients.Bedrock() {
                    @Override
                    public SessionClient join(String name) {
                        return joinBedrock(resource, name);
                    }

                    @Override
                    public void awaitGone(String serverName) {
                        awaitListing(resource.host(), serverName, false);
                    }
                });
        context.getStore(NAMESPACE).put(STATE, state);
        // Capabilities come before conformance: a cell that lacks one is SKIPPED with its reason, and nothing else runs (T-M5-01).
        Optional<String> missing = CapabilityModel.missingReason(required, state.capabilities, cellId());
        if (missing.isPresent()) {
            state.skipped = missing.get();
            throw new TestAbortedException(missing.get());
        }
        // Loaded or loud for mods (ADR-0017 section 4): a declared mod the agent does not list is INVALID, naming it. A mod
        // that crashed the loader never got here (a failed boot, INVALID naming the mod, T-M8-11). Also after the skip.
        List<String> declaredMods = host.modIds();
        if (!declaredMods.isEmpty()) {
            String notLoaded = ModsLoaded.invalid(declaredMods, host.request("GET", "/v1/mods", null), cellId(),
                    host.logTail());
            if (notLoaded != null) {
                state.invalid = new AssertionError(notLoaded);
                throw state.invalid;
            }
        }
        // Loaded or loud (ADR-0017 section 4): a declared data pack the server did not enable is INVALID, naming it. After the
        // capability skip, so a cell without the capability skips with it named and is never judged on a pack it cannot load.
        List<String> declaredPacks = host.dataPackIds();
        if (!declaredPacks.isEmpty()) {
            String notLoaded = DataPackLoad.invalid(declaredPacks, host.request("GET", "/v1/datapacks", null), cellId(),
                    host.logTail());
            // Enabled is not loaded well: the server skips a function that does not parse and logs it at load, before the
            // agent's log window starts, so the host's log of the whole boot is read (T-M8-11).
            if (notLoaded == null) {
                notLoaded = DataPackLoad.errorsLogged(host.dataPackNamespaces(), host.logs().all(), cellId());
            }
            if (notLoaded != null) {
                state.invalid = new AssertionError(notLoaded);
                throw state.invalid;
            }
        }
        // Conformance comes first (self-tests section 1): on a failed cell the test body never runs, and the test is INVALID.
        state.conformance = conformance(context, resource, state.capabilities, state);
        AssertionError invalid = VerdictGate.judgeInvalid(context.getRequiredTestMethod(), cellId(),
                state.conformance);
        if (invalid != null) {
            state.invalid = invalid;
            throw invalid;
        }
        // After conformance: its scenarios use the same client oracle, and their observations are not the test's.
        if (differential) {
            DifferentialRun.begin();
        }
        // Before the join, so the snapshot is the server's state before this test's client arrives.
        state.baseline = LeakDetector.snapshot(host);
        if (declaresClient(context)) {
            // A hash of the unique test id keeps the name stable for a test and different between tests.
            String name = String.format("T%08x", context.getUniqueId().hashCode());
            state.client = connect(resource, name, packs);
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
                // A mod that crashed at load is INVALID, not a plain failed start (T-M8-11): the report says what JUnit says.
                report(context, new CellResult(reportName(context), cellId(),
                        VerdictGate.invalidDeclaration(reason) ? Verdict.INVALID : Verdict.FAIL, reason, null, 1));
            }
            return;
        }
        if (state.skipped != null) {
            // A skip is a verdict of its own: nothing ran, so there is no log to check and no bundle to write (T-M5-01).
            report(context, new CellResult(reportName(context), cellId(), Verdict.SKIPPED, state.skipped,
                    SkipReason.CAPABILITY_MISSING, 1));
            return;
        }
        // Every step runs, whatever an earlier one did, and the first failure is the one reported. An INVALID test
        // already failed in beforeEach, so that failure is the first one.
        Throwable failure = state.invalid;
        try {
            ErrorLogGuard.check(state.host, state.logCursor,
                    ErrorLogGuard.Patterns.of(geyser != null ? GeyserCell.ID : cell.platform()));
        } catch (RuntimeException | AssertionError e) {
            failure = combine(failure, e);
        }
        // The per-cell expectations (test-api spec section 9, robustness R7) judge a test that is otherwise green. They
        // read the server after the test's actions, so they run before the leak detector, and never change anything.
        if (failure == null && context.getExecutionException().isEmpty()) {
            failure = CellExpectations.check(context.getRequiredTestClass(), context.getRequiredTestMethod(),
                    new CellView(cell, bridged, state.capabilities), new HostAgent(state.host, state.patchedPlugins));
        }
        DifferentialRun.Disagreement disagreement = null;
        if (differential) {
            List<String> transcript = state.client == null ? List.of() : state.client.transcript();
            String test = context.getRequiredTestClass().getSimpleName() + "."
                    + context.getRequiredTestMethod().getName();
            DifferentialRun.Evidence evidence = new DifferentialRun.Evidence(driverId, observations, transcript);
            // Failed on its own, and still paired: the other driver's run then compares against this one's evidence.
            failure = combine(failure, DifferentialRun.unobserved(test, cellId(), evidence));
            disagreement = DifferentialRun.arrive(differentialKey(context), evidence);
            if (disagreement != null) {
                AssertionError verdict = DifferentialRun.failure(test, cellId(), disagreement, state.conformance);
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
                Path bundle = captureBundle(context, state);
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
        // The leak detector (robustness R6) judges the state after that cleanup, so the test's own work is not a leak: only
        // what the cleanup did not undo is (T-M4-12b). It judges a test that is otherwise green and never hides another
        // failure. A leak gets its own bundle, written after the cleanup (the config it restored is no longer in it).
        if (failure == null && context.getExecutionException().isEmpty() && state.baseline != null) {
            Throwable leak;
            try {
                leak = VerdictGate.judgeLeak(context.getRequiredTestMethod(), cellId(), state.baseline,
                        LeakDetector.snapshot(state.host));
            } catch (RuntimeException e) {
                leak = new IllegalStateException("cannot read the server state after the test", e);
            }
            if (leak != null) {
                failure = leak;
                outcome = Attempt.fail(detail(leak));
                try {
                    Path bundle = captureBundle(context, state);
                    System.err.println("PROOFSTONE BUNDLE: " + bundle);
                } catch (RuntimeException e) {
                    failure = combine(failure, new IllegalStateException("cannot write the repro bundle", e));
                }
            }
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
     * The repro bundle of this run. A Geyser cell writes its own: a directory per container, the Bedrock client's
     * transcript and a {@code cell.json} that says {@code offline-stub}, with the Floodgate key redacted (ADR-0018 section 5).
     */
    private Path captureBundle(ExtensionContext context, TestState state) {
        if (geyser == null) {
            return BundleWriter.capture(context, cell, state, repeats > 1 ? repetition : 0, driverId, differential, catalog,
                    joinStall);
        }
        String test = context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName();
        List<String> transcript = new ArrayList<>(state.client == null ? List.of() : state.client.transcript());
        transcript.addAll(state.conformanceTranscript);
        return GeyserBundle.write(BundleWriter.directory(geyser).resolve(test), geyser, state.geyser, transcript);
    }

    /**
     * The cell's conformance result, run once per cell per run and cached in the root store (self-tests section 1). A
     * failure of the run itself, rather than a failed scenario, is an error and is not cached.
     */
    @SuppressWarnings("unchecked")
    private ConformanceResult conformance(ExtensionContext context, HostResource resource,
            Set<io.github.h7kz.proofstone.api.Capability> capabilities, TestState state) {
        ConformanceCache cache = context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(CONFORMANCE, key -> new ConformanceCache(), ConformanceCache.class);
        // The per-cell check joins its own client through this invocation's driver (T-M4-10b).
        Map<String, List<String>> transcripts = context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(CONFORMANCE_TRANSCRIPTS, key -> new ConcurrentHashMap<String, List<String>>(),
                        Map.class);
        ConformanceResult cached = cache.get(cellId(), () -> {
            SessionClient[] joined = {null};
            ConformanceResult result = CellConformance.run(cellId(), resource.host(),
                    new CellView(cell, bridged, capabilities),
                    name -> {
                        joined[0] = connect(resource, name, ResourcePackDeclaration.forHarness(resource.pack()));
                        return joined[0];
                    });
            // A Geyser cell's own first check, after the Java client's round trip passed: it needs the Bedrock join (T-M9-08).
            if (geyser != null && result.passed()) {
                result = GeyserConformance.run(cellId(), resource.geyser(), name -> joinBedrock(resource, name),
                        serverName -> awaitListing(resource.host(), serverName, false));
            }
            // A failed conformance keeps its client's packets for the bundles of every test it makes INVALID (T-M7-21).
            if (!result.passed() && joined[0] != null) {
                transcripts.put(cellId(), joined[0].transcript());
            }
            // Only the invocation that ran it: the conformance's records and its client's leave are not this test's, and
            // the conformance checked its own window (T-M8-22). A cached result leaves the cursor where beforeEach put it.
            state.logCursor = ErrorLogGuard.cursor(resource.host());
            return result;
        });
        state.conformanceTranscript = transcripts.getOrDefault(cellId(), List.of());
        return cached;
    }

    /** The two runs of one test on one cell share this key, so the second one finds the first one's evidence. */
    private String differentialKey(ExtensionContext context) {
        return differentialKey(context.getRequiredTestClass().getName(), context.getRequiredTestMethod().getName(),
                cell);
    }

    /** Keyed by the whole cell, as the host is: two platforms of one version are two cells with two runs (T-M7-17). */
    static String differentialKey(String testClass, String testMethod, Cell cell) {
        return testClass + "." + testMethod + "@" + cell.platform() + "@" + cell.mc();
    }

    static Throwable combine(Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (next != null) {
            first.addSuppressed(next);
        }
        return first;
    }

    /** A failure's message, or its class name when it has none: a FAIL run always needs a detail. */
    static String detail(Throwable failure) {
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
    static void report(ExtensionContext context, CellResult result) {
        RunReport report = context.getRoot().getStore(NAMESPACE).getOrComputeIfAbsent(REPORT,
                key -> new RunReport(RunReport.defaultDirectory()), RunReport.class);
        report.record(result);
    }

    /**
     * A PASS whose own teardown then failed is reported as a FAIL: the test is red in JUnit, so the report never shows
     * it green.
     */
    static CellResult settle(CellResult verdict, Throwable failure) {
        if (verdict.verdict() == Verdict.PASS && failure != null) {
            return new CellResult(verdict.test(), verdict.cell(), Verdict.FAIL, detail(failure), null, 1);
        }
        return verdict;
    }

    static boolean declaresClient(ExtensionContext context) {
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
        HostResource resource = owner.getStore(NAMESPACE).getOrComputeIfAbsent(
                geyser != null ? hostKey(geyser) : hostKey(cell), key -> boot(context.getRequiredTestClass()),
                HostResource.class);
        // A shared host that is another platform's server is a harness defect, never a result (T-M7-17).
        requirePlatform(cell, resource.host().health());
        return resource;
    }

    /**
     * The store key of a class-shared host. It is the cell's whole identity, not its Minecraft version: cells of one
     * version on different platforms are different servers (T-M7-17, ADR-0003).
     */
    static String hostKey(Cell cell) {
        return "host:" + cell.platform() + "@" + cell.mc() + "#" + cell.build() + "/" + cell.java() + "/" + cell.sha256();
    }

    /** The store key of a Geyser cell's host: its whole identity, so it never shares the plain Paper cell's server. */
    static String hostKey(GeyserCell geyser) {
        return "host:" + geyser.identity() + "#" + geyser.build() + "/" + geyser.floodgate().build() + "/" + geyser.sha256();
    }

    /** The server the cell resolved to must be the server that answers: the agent's own platform id is the oracle. */
    static void requirePlatform(Cell cell, Health health) {
        if (!cell.platform().equals(health.platform())) {
            throw new IllegalStateException("INVALID: cell " + cell.platform() + "@" + cell.mc() + " is running on a "
                    + health.platform() + " server (a host shared across cells of one Minecraft version)");
        }
    }

    /**
     * Starts the cell's topology: the server, plus ViaProxy and the network when a driver needs them. A differential run
     * boots it for both drivers, so the shared server is bound to every driver the run uses.
     */
    private HostResource boot(Class<?> testClass) {
        if (geyser != null) {
            return bootGeyser();
        }
        if (loader != null) {
            // A loader cell does not hold RESOURCE_PACKS, so a declared pack skips there with the capability named.
            return bootLoader();
        }
        ServerResourcePack declaredPack = controlRun ? null : testClass.getAnnotation(ServerResourcePack.class);
        if (declaredPack != null && differential) {
            throw new ParameterResolutionException("INVALID: a differential run of the resource-pack test "
                    + testClass.getName() + " is refused: only the mcpl driver answers a push (ADR-0017 section 7)");
        }
        // Served from this JVM on loopback for as long as the server lives; the client is the only one that fetches it.
        ResourcePackSource pack = declaredPack == null ? null
                : ResourcePackSource.serve(Path.of("").toAbsolutePath(), declaredPack.value());
        try {
            return bootPaper(pack == null ? Map.of() : ResourcePackDeclaration.env(declaredPack, pack), pack);
        } catch (RuntimeException e) {
            if (pack != null) {
                pack.close();
            }
            throw e;
        }
    }

    private HostResource bootPaper(Map<String, String> packEnv, ResourcePackSource pack) {
        // Read here, not in boot: only a Bukkit-family cell loads the Bukkit agent (T-M8-14).
        String jar = agentJar(null);
        // A control run installs no plugin jars. The agent and the fixture world are not plugins under test, so they stay.
        List<String> plugins = List.of(config.plugins());
        if (controlRun) {
            System.err.println("PROOFSTONE CONTROL RUN: plugin jars are not installed; a passing test is VACUOUS");
            plugins = List.of();
        }
        // A data pack is an artefact under test too: a control run installs none (self-tests section 2).
        PaperHostOptions options = PaperHostOptions.of(Path.of(jar), true)
                .withArtefacts(Path.of("").toAbsolutePath(), plugins)
                .withDataPacks(controlRun ? List.of() : List.of(config.datapacks()))
                .withResourcePack(packEnv);
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
            return new HostResource(PaperHost.start(cell, options), null, null, Map.of(), pack);
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
            return new HostResource(host, viaProxy, cellNetwork, bound, pack);
        } catch (RuntimeException e) {
            new HostResource(host, viaProxy, cellNetwork, bound, null).closeQuietly(e);
            throw e;
        }
    }

    /**
     * Starts a Geyser cell through {@link GeyserHost} (ADR-0018 section 8): Paper with the agent and the plugins under test,
     * Geyser and Floodgate, the discovery stand-in and the driver host, on one network. A control run installs no plugin.
     * The Java client joins the server directly with the native driver; a Bedrock client joins through the driver host.
     */
    private HostResource bootGeyser() {
        String bedrockHostJar = System.getProperty(BEDROCK_HOST_JAR);
        if (bedrockHostJar == null) {
            throw new ParameterResolutionException("system property " + BEDROCK_HOST_JAR + " is not set: it points at the "
                    + "shaded Bedrock driver host jar (:drivers:bedrock:shadowJar)");
        }
        List<String> plugins = List.of(config.plugins());
        if (controlRun) {
            System.err.println("PROOFSTONE CONTROL RUN: plugin jars are not installed; a passing test is VACUOUS");
            plugins = List.of();
        }
        PaperHostOptions server = PaperHostOptions.of(Path.of(agentJar(null)), true)
                .withArtefacts(Path.of("").toAbsolutePath(), plugins);
        GeyserHostOptions options = GeyserHostOptions.of(Path.of(bedrockHostJar)).withDownloads(
                Path.of(System.getProperty(GEYSER_DOWNLOADS, "build/geyser-downloads")).toAbsolutePath());
        long started = System.nanoTime();
        GeyserHost host = GeyserHost.start(geyser, server, options);
        System.err.println("PROOFSTONE GEYSER BOOT " + geyser.identity() + " (offline-stub): "
                + Duration.ofNanos(System.nanoTime() - started).toMillis() + " ms (host finding, not a join timeout)");
        return new HostResource(host.server(), null, null, Map.of(), null, host);
    }

    /**
     * Starts a loader cell through {@link LoaderHost} (ADR-0017 section 2): the loader's own agent jar, no plugin, and the
     * native mcpl driver only (a sidecar driver or ViaProxy has no loader row, the planner refuses a bridged one). The boot
     * time is printed as a host finding, never part of a join timeout (M8 flake rule 4).
     */
    private HostResource bootLoader() {
        if (config.plugins().length > 0) {
            throw new ParameterResolutionException("INVALID test declaration: " + config.plugins().length + " plugin(s) "
                    + "declared on the loader cell " + cellId() + ". A " + cell.platform() + " server loads no plugin "
                    + "(ADR-0017 section 4); remove plugins from @Proofstone for this cell");
        }
        for (String id : driversFor(differential)) {
            if (!PRIMARY_DRIVER.equals(id)) {
                throw new ParameterResolutionException("INVALID: driver " + id + " cannot run on the loader cell "
                        + cellId() + ": only the native " + PRIMARY_DRIVER + " driver joins a loader row (ADR-0017)");
            }
        }
        if (bridged) {
            throw new ParameterResolutionException("INVALID: the loader cell " + cellId() + " would need ViaProxy for the "
                    + "driver; a bridged loader row is refused (ADR-0017 section 1)");
        }
        String jar = agentJar(loader.loader());
        String cache = System.getProperty(LOADER_CACHE, "build/loader-cache");
        LoaderHostOptions options = LoaderHostOptions.of(Path.of(jar), true, Path.of(cache).toAbsolutePath())
                // A mod is an artefact under test: a control run installs none (self-tests section 2, ADR-0017 section 4).
                .withMods(Path.of("").toAbsolutePath(), ModsLoaded.globsToInstall(config.mods(), controlRun))
                .withDataPacks(controlRun ? List.of() : List.of(config.datapacks()));
        LoaderHost host = LoaderHost.start(loader, options);
        System.err.println("PROOFSTONE LOADER BOOT " + loader.identity() + ": " + host.bootTime().toMillis()
                + " ms from container start to \"Done (\" (host finding, not a join timeout)");
        return new HostResource(host, null, null, Map.of(), null);
    }

    /**
     * The shaded agent jar this cell boots: {@code proofstone.agentJar} for a Bukkit-family cell ({@code loaderId} is
     * {@code null}), {@code proofstone.<loader>.agentJar} for a loader cell. Only the property the cell uses is required.
     */
    static String agentJar(String loaderId) {
        String property = loaderId == null ? AGENT_JAR : "proofstone." + loaderId + ".agentJar";
        String jar = System.getProperty(property);
        if (jar == null) {
            throw new ParameterResolutionException("system property " + property + " is not set: it points at the shaded "
                    + (loaderId == null ? "agent" : loaderId + " agent") + " jar");
        }
        return jar;
    }

    /**
     * Joins a Bedrock client through the driver host and returns it under the name the server sees, {@code .name}, once the
     * agent lists it (ADR-0018 sections 7 and 9). The server notices a client's disconnect only after about 10 s, so a join
     * under a name the agent still lists waits for the agent first: an await, never a sleep. A timeout names its stage and
     * is printed apart from Paper, topology and loader join timeouts.
     */
    private SessionClient joinBedrock(HostResource resource, String name) {
        GeyserHost host = resource.geyser();
        String serverName = "." + name;
        awaitListing(resource.host(), serverName, false);
        int serverMark = resource.host().logs().size();
        ClientSession session;
        try {
            session = host.driver().connect(new ConnectSpec(host.bedrockHost(), host.bedrockPort(), name,
                    CONNECT_TIMEOUT), Edition.BEDROCK);
        } catch (JoinTimeoutException e) {
            List<String> during = new ArrayList<>(resource.host().logs().since(serverMark));
            during.addAll(host.standInLogs().all().lines().toList());
            JoinStall stall = JoinStall.of(e, "server and stand-in logs", during, resource.host().logTail());
            System.err.println("PROOFSTONE BEDROCK JOIN TIMEOUT (counted apart from Paper, topology and loader joins) stage="
                    + e.stage() + " on " + cellId() + ":\n" + stall.report() + "--- stand-in log ---\n"
                    + host.standInLogs().all() + "\n--- driver host log tail ---\n" + host.driverHostLogs().tail(60));
            joinStall = stall.report();
            throw e.withSuffix(stall.suffix());
        }
        try {
            awaitListing(resource.host(), serverName, true);
        } catch (RuntimeException e) {
            session.close();
            throw e;
        }
        // transferTo is a proxy feature; on a Bedrock session it names the edition, as the other unsupported calls do.
        return new SessionClient(serverName, session, (player, ignored, target) -> {
            throw new UnsupportedOperationException("transferTo(\"" + target + "\") is not supported by the Bedrock edition "
                    + "driver in v1: a Geyser cell has no proxy and no second server (ADR-0018 section 9)");
        });
    }

    /** Waits until the agent lists {@code player} (or no longer lists it); a reply without the list is an error. */
    private static void awaitListing(ServerHost host, String player, boolean listed) {
        Await.until("the agent " + (listed ? "lists " : "no longer lists ") + player, () -> lists(host, player),
                value -> value == listed, BEDROCK_AGENT_WAIT);
    }

    private static boolean lists(ServerHost host, String player) {
        JsonNode players = host.request("GET", "/v1/players", null).get("players");
        if (players == null || !players.isArray()) {
            throw new IllegalStateException("the agent's /v1/players reply has no players list: " + players);
        }
        for (JsonNode listed : players) {
            if (player.equals(listed.asText())) {
                return true;
            }
        }
        return false;
    }

    /** Logs one client in under {@code name} through the cell's driver, at the address that driver reaches. */
    private SessionClient connect(HostResource resource, String name, ResourcePackPolicy packs) {
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
        // Marks, so a timeout reports only what the logs said since this join began (T-M6-14).
        int serverMark = resource.host().logs().size();
        int proxyMark = resource.proxy() == null ? 0 : resource.proxy().logs().size();
        ClientSession session;
        try {
            session = driver.connect(new ConnectSpec(address, port, name, CONNECT_TIMEOUT, packs, bridged), Edition.JAVA);
        } catch (JoinTimeoutException e) {
            throw reportStall(resource, e, serverMark, proxyMark);
        }
        return new SessionClient(name, session);
    }

    /**
     * Logs the server's view of a join that timed out, keeps it for the bundle, and returns the timeout with the
     * server's side in its message. It explains nothing and fixes nothing: the timeout and the failure stay as they
     * were (T-M6-14, ADR-0003).
     */
    private JoinTimeoutException reportStall(HostResource resource, JoinTimeoutException e, int serverMark,
            int proxyMark) {
        List<String> during = new ArrayList<>(resource.host().logs().since(serverMark));
        String source = "server log";
        if (resource.proxy() != null) {
            during.addAll(resource.proxy().logs().since(proxyMark));
            source = "server and ViaProxy logs";
        }
        JoinStall stall = JoinStall.of(e, source, during, resource.host().logTail());
        System.err.println("PROOFSTONE JOIN STALL on " + cellId() + ":\n" + stall.report());
        joinStall = stall.report();
        return e.withSuffix(stall.suffix());
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
    private record HostResource(ServerHost host, ViaProxyHost proxy, Network network, Map<String, ClientDriver> drivers,
            ResourcePackSource pack, GeyserHost geyser) implements AutoCloseable {

        HostResource(ServerHost host, ViaProxyHost proxy, Network network, Map<String, ClientDriver> drivers,
                ResourcePackSource pack) {
            this(host, proxy, network, drivers, pack, null);
        }

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
            // A Geyser cell's host owns its server, the stand-in, the driver host and the network.
            parts.add(geyser != null ? geyser : host);
            parts.add(network);
            parts.add(pack);
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
