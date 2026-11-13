package io.github.h7kz.proofstone.core.junit;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Capability;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Clients;
import io.github.h7kz.proofstone.api.Isolation;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Topology;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.cell.TopologyCell;
import io.github.h7kz.proofstone.core.driver.ClientDriver;
import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import io.github.h7kz.proofstone.core.host.AgentEndpoint;
import io.github.h7kz.proofstone.core.host.LeakDetector;
import io.github.h7kz.proofstone.core.host.PaperHostOptions;
import io.github.h7kz.proofstone.core.host.TopologyFault;
import io.github.h7kz.proofstone.core.host.TopologyHost;
import io.github.h7kz.proofstone.core.verdict.Attempt;
import io.github.h7kz.proofstone.core.verdict.CellResult;
import io.github.h7kz.proofstone.core.verdict.ConformanceCache;
import io.github.h7kz.proofstone.core.verdict.ConformanceResult;
import io.github.h7kz.proofstone.core.verdict.SkipReason;
import io.github.h7kz.proofstone.core.verdict.Verdict;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.opentest4j.TestAbortedException;

/**
 * One topology cell of one test method (ADR-0016 sections 6 and 7), the topology counterpart of {@link CellInvocation}.
 * The topology (proxy and every backend) is per class by default. Before each test the client joins through the proxy,
 * the harness awaits it on the proxy agent and on the default backend, and the player is reset there; the leak
 * baseline is read on every backend. After it the log guard runs per container with that container's pack patterns,
 * configs are restored per agent, the client leaves (awaited on the proxy agent) and the leak detector compares every
 * backend. A failing test's bundle has a directory per container and names no secret.
 *
 * <p>Runs with the primary driver only: a differential or flake-detector run on a topology cell is refused at planning.
 */
final class TopologyInvocation implements TestTemplateInvocationContext, ParameterResolver, BeforeEachCallback,
        AfterEachCallback {

    private static final String STATE = "topologyState";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    /** A switch completes in a few seconds in the spike (T-M7-01); this is the same bound as SwitchContract. */
    private static final Duration SWITCH_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration AGENT_WAIT = Duration.ofSeconds(20);
    /** The proxy agent jars by proxy id; the build sets each property (proofstone-core topologyTest). */
    static final Map<String, String> PROXY_AGENT_JAR = Map.of(
            "velocity", "proofstone.velocity.agentJar",
            "bungeecord", "proofstone.bungee.agentJar");

    private final TopologyCell cell;
    private final Proofstone config;
    private final Isolation.Mode isolation;
    private final VersionsCatalog catalog;
    private final String driverId = CellInvocation.configuredDriver();
    private final boolean controlRun = VerdictGate.controlRun();
    private volatile String joinStall;

    TopologyInvocation(TopologyCell cell, Proofstone config, Isolation.Mode isolation, VersionsCatalog catalog) {
        this.cell = cell;
        this.config = config;
        this.isolation = isolation;
        this.catalog = catalog;
    }

    /** What the lifecycle keeps for one test on a topology. */
    private static final class State {
        final TopologyHost topology;
        /** The plugins each test patched, per container ({@code proxy} or a backend name). */
        final Map<String, Set<String>> patched = new LinkedHashMap<>();
        /** Log cursors per container, taken before the join. */
        final Map<String, Long> cursors = new LinkedHashMap<>();
        Set<Capability> capabilities = Set.of();
        String skipped;
        ConformanceResult conformance;
        AssertionError invalid;
        Map<String, List<String>> baselines;
        SessionClient client;
        TestClients clients;

        State(TopologyHost topology) {
            this.topology = topology;
            if (topology != null) {
                patched.put(TopologyHost.PROXY_ALIAS, ConcurrentHashMap.newKeySet());
                topology.backendNames().forEach(name -> patched.put(name, ConcurrentHashMap.newKeySet()));
            }
        }

        String defaultBackend() {
            return topology.backendNames().get(0);
        }
    }

    @Override
    public String getDisplayName(int invocationIndex) {
        return cell.identity();
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
        State state = context.getStore(CellInvocation.NAMESPACE).get(STATE, State.class);
        if (state == null || state.topology == null) {
            throw new ParameterResolutionException("no test lifecycle state: the topology did not start for this test");
        }
        if (type == io.github.h7kz.proofstone.api.Cell.class) {
            return cellView(state);
        }
        if (type == Agent.class) {
            // ADR-0016 section 6: the plain Agent is the default backend's, where the client lands.
            return new HostAgent(state.topology.backend(state.defaultBackend()), state.patched.get(state.defaultBackend()));
        }
        if (type == Topology.class) {
            return new TopologyView(state.topology, state.patched);
        }
        if (type == Clients.class) {
            return state.clients;
        }
        if (state.client == null) {
            throw new ParameterResolutionException("the client joins before the test only when the test method "
                    + "declares a Client parameter; add one to " + context.getRequiredTestMethod().getName());
        }
        return state.client;
    }

    /** The {@code Cell} of a topology test describes the backend row; the proxy is reached through {@link Topology}. */
    private CellView cellView(State state) {
        return new CellView(cell.backend(), false, state.capabilities);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        TopologyHost topology = resource(context).topology();
        State state = new State(topology);
        context.getStore(CellInvocation.NAMESPACE).put(STATE, state);
        // Before the join, so anything the join or the reset logs counts for this test, on every container.
        state.cursors.put(TopologyHost.PROXY_ALIAS, ErrorLogGuard.cursor(topology.proxy()));
        for (String name : topology.backendNames()) {
            state.cursors.put(name, ErrorLogGuard.cursor(topology.backend(name)));
        }
        state.capabilities = CapabilityModel.topologyCapabilities(cell.backend().platform(),
                topology.backend(state.defaultBackend()).health().capabilities(), cell.proxy().id(),
                topology.proxy().health().capabilities());
        state.clients = new TestClients(name -> connect(topology, name));
        Optional<String> missing = CapabilityModel.missingReason(CapabilityModel.required(
                context.getRequiredTestClass(), context.getRequiredTestMethod()), state.capabilities, cell.identity());
        if (missing.isPresent()) {
            state.skipped = missing.get();
            throw new TestAbortedException(missing.get());
        }
        ConformanceCache cache = context.getRoot().getStore(CellInvocation.NAMESPACE).getOrComputeIfAbsent(
                CellInvocation.CONFORMANCE, key -> new ConformanceCache(), ConformanceCache.class);
        state.conformance = cache.get(cell.identity(), () -> TopologyConformance.run(cell.identity(), cell.proxy().id(),
                topology, this::joinProxy, this::joinDirect));
        AssertionError invalid = VerdictGate.judgeInvalid(context.getRequiredTestMethod(), cell.identity(),
                state.conformance);
        if (invalid != null) {
            state.invalid = invalid;
            throw invalid;
        }
        state.baselines = snapshots(topology);
        if (CellInvocation.declaresClient(context)) {
            String name = String.format("T%08x", context.getUniqueId().hashCode());
            state.client = connect(topology, name);
            new HostPlayer(topology.backend(state.defaultBackend()), name).reset();
        }
    }

    /**
     * Joins through the proxy and returns once the client is in play and the proxy agent and the default backend's
     * agent both list it there (ADR-0016 section 7). A join timeout names the stalled stage and the hop.
     */
    private SessionClient connect(TopologyHost topology, String name) {
        String lobby = topology.backendNames().get(0);
        ClientSession session = joinProxy(topology, name);
        SessionClient client = new SessionClient(name, session, (player, live, target) -> transfer(topology, player,
                live, target));
        try {
            Await.until("proxy agent says " + name + " is on " + lobby, () -> proxyServer(topology, name),
                    lobby::equals, AGENT_WAIT);
            Await.until("backend " + lobby + " lists " + name, () -> lists(topology.backend(lobby), name),
                    Boolean::booleanValue, AGENT_WAIT);
        } catch (RuntimeException e) {
            client.close();
            throw e;
        }
        return client;
    }

    /** The client joins the proxy and is in play when this returns; the agents are not consulted (P01 does that). */
    private ClientSession joinProxy(TopologyHost topology, String name) {
        String lobby = topology.backendNames().get(0);
        TopologyHost.JoinMarks marks = topology.mark();
        try {
            return driver().connect(new ConnectSpec(topology.proxy().gameHost(), topology.proxy().gamePort(), name,
                    CONNECT_TIMEOUT));
        } catch (JoinTimeoutException e) {
            JoinTimeoutException described = topology.describe(e, marks, lobby);
            joinStall = described.getMessage();
            throw described;
        } catch (ClientDriverException e) {
            // A refusal (a kick) is not a timeout: it names the hop and the tails too, so a misconfigured hop reads as such.
            throw topology.describeKick(e, name, marks, lobby);
        }
    }

    /** A client that joins a backend's own game port, around the proxy (P02's direct join). */
    private ClientSession joinDirect(String host, int port, String name) {
        return driver().connect(new ConnectSpec(host, port, name, CONNECT_TIMEOUT));
    }

    private ClientDriver driver() {
        return CellInvocation.findDriver(driverId).orElseThrow(() -> new ParameterResolutionException(
                "no client driver " + driverId + " on the classpath"));
    }

    /** {@code Client.transferTo}: the client types {@code /server}, then the dual oracle of {@link ServerSwitch}. */
    private void transfer(TopologyHost topology, String player, ClientSession session, String target) {
        TopologyHost.JoinMarks[] marks = new TopologyHost.JoinMarks[1];
        ServerSwitch.transfer(player, target, topology.backendNames(), new ServerSwitch.Agents() {
            @Override
            public String proxyServer(String name) {
                return TopologyInvocation.proxyServer(topology, name);
            }

            @Override
            public boolean lists(String backend, String name) {
                return TopologyInvocation.lists(topology.backend(backend), name);
            }
        }, new ServerSwitch.ClientSide() {
            @Override
            public void type(String command) {
                marks[0] = topology.mark();
                session.command(command);
            }

            @Override
            public void awaitSwitch() {
                try {
                    session.awaitServerSwitch(SWITCH_TIMEOUT);
                } catch (JoinTimeoutException e) {
                    JoinTimeoutException described = topology.describeSwitch(e, marks[0], target);
                    joinStall = described.getMessage();
                    throw described;
                }
            }
        }, AGENT_WAIT);
    }

    /** The proxy agent's current server of the player, or {@code null} when the proxy does not list the player. */
    static String proxyServer(TopologyHost topology, String player) {
        if (!lists(topology.proxy(), player)) {
            return null;
        }
        JsonNode server = topology.proxy().request("GET", "/v1/players/" + player, null).path("proxy").path("server");
        if (!server.isTextual()) {
            throw new IllegalStateException("the proxy agent's reply for " + player + " has no proxy.server: " + server);
        }
        return server.asText();
    }

    /** Whether the agent's {@code GET /v1/players} lists the player; a reply without the list is an error. */
    static boolean lists(AgentEndpoint agent, String player) {
        JsonNode players = agent.request("GET", "/v1/players", null).get("players");
        if (players == null || !players.isArray()) {
            throw new IllegalStateException("the agent's /v1/players reply has no players list: " + players);
        }
        for (JsonNode name : players) {
            if (player.equals(name.asText())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, List<String>> snapshots(TopologyHost topology) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        topology.backendNames().forEach(name -> out.put(name, LeakDetector.snapshot(topology.backend(name))));
        return out;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        State state = context.getStore(CellInvocation.NAMESPACE).get(STATE, State.class);
        if (state == null) {
            String reason = context.getExecutionException().map(CellInvocation::detail)
                    .orElse("the topology or the client did not start; see the failure reported for this run");
            CellInvocation.report(context, new CellResult(reportName(context), cell.identity(), Verdict.FAIL, reason,
                    null, 1));
            return;
        }
        if (state.skipped != null) {
            CellInvocation.report(context, new CellResult(reportName(context), cell.identity(), Verdict.SKIPPED,
                    state.skipped, SkipReason.CAPABILITY_MISSING, 1));
            return;
        }
        TopologyHost topology = state.topology;
        Throwable failure = state.invalid;
        // The log guard per container, each with its own pack's patterns (ADR-0016 section 7).
        failure = CellInvocation.combine(failure, logGuard(TopologyHost.PROXY_ALIAS, topology.proxy(),
                cell.proxy().id(), state));
        for (String name : topology.backendNames()) {
            failure = CellInvocation.combine(failure, logGuard(name, topology.backend(name), cell.backend().platform(),
                    state));
        }
        boolean bodyFailed = context.getExecutionException().isPresent();
        if (failure == null && !bodyFailed) {
            failure = CellExpectations.check(context.getRequiredTestClass(), context.getRequiredTestMethod(),
                    cellView(state), new HostAgent(topology.backend(state.defaultBackend()),
                            state.patched.get(state.defaultBackend())));
        }
        Attempt outcome = failure == null && !bodyFailed ? Attempt.pass()
                : Attempt.fail(CellInvocation.detail(failure != null ? failure : context.getExecutionException().get()));
        if (failure == null && !bodyFailed) {
            failure = VerdictGate.judgePass(context.getRequiredTestMethod(), cell.identity(), controlRun,
                    state.conformance);
        }
        if (failure != null || bodyFailed) {
            failure = CellInvocation.combine(failure, bundle(context, state));
        }
        for (Map.Entry<String, Set<String>> container : state.patched.entrySet()) {
            AgentEndpoint agent = endpoint(topology, container.getKey());
            for (String plugin : container.getValue()) {
                try {
                    HostPlugin.restore(agent, plugin);
                } catch (RuntimeException e) {
                    failure = CellInvocation.combine(failure, new IllegalStateException("cannot restore the config of "
                            + plugin + " on " + container.getKey(), e));
                }
            }
        }
        failure = CellInvocation.combine(failure, leave(state));
        // The leak detector compares every backend after the cleanup (robustness R6, ADR-0016 section 7).
        if (failure == null && !bodyFailed && state.baselines != null) {
            Throwable leak = null;
            try {
                List<String> before = new ArrayList<>();
                List<String> after = new ArrayList<>();
                for (String name : topology.backendNames()) {
                    state.baselines.get(name).forEach(entry -> before.add(name + " " + entry));
                    LeakDetector.snapshot(topology.backend(name)).forEach(entry -> after.add(name + " " + entry));
                }
                leak = VerdictGate.judgeLeak(context.getRequiredTestMethod(), cell.identity(), before, after);
            } catch (RuntimeException e) {
                leak = new IllegalStateException("cannot read the backends' state after the test", e);
            }
            if (leak != null) {
                failure = leak;
                outcome = Attempt.fail(CellInvocation.detail(leak));
                failure = CellInvocation.combine(failure, bundle(context, state));
            }
        }
        CellInvocation.report(context, CellInvocation.settle(VerdictGate.single(reportName(context),
                context.getRequiredTestMethod(), cell.identity(), controlRun, outcome, false, state.conformance),
                failure));
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

    private static Throwable logGuard(String container, AgentEndpoint agent, String platform, State state) {
        try {
            ErrorLogGuard.check(agent, state.cursors.get(container), ErrorLogGuard.Patterns.of(platform));
            return null;
        } catch (AssertionError e) {
            return new AssertionError(container + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            return new IllegalStateException("cannot check the log of " + container, e);
        }
    }

    /** Disconnects the test's clients and awaits the proxy agent listing none of them, before the next test. */
    private static Throwable leave(State state) {
        List<String> names = new ArrayList<>();
        if (state.client != null) {
            names.add(state.client.name());
        }
        Throwable failure = null;
        try {
            names.addAll(state.clients.names());
            if (state.client != null) {
                state.client.close();
            }
            RuntimeException closing = state.clients.closeAll();
            if (closing != null) {
                throw closing;
            }
            Await.until("proxy agent lists none of " + names, () -> names.stream()
                    .filter(name -> lists(state.topology.proxy(), name)).toList(), List::isEmpty, AGENT_WAIT);
        } catch (RuntimeException e) {
            failure = new IllegalStateException("cannot disconnect the client", e);
        }
        return failure;
    }

    private Throwable bundle(ExtensionContext context, State state) {
        try {
            Path bundle = BundleWriter.captureTopology(context, cell, state.topology,
                    state.client == null ? List.of() : state.client.transcript(), catalog, driverId, joinStall);
            System.err.println("PROOFSTONE BUNDLE: " + bundle);
            return null;
        } catch (RuntimeException e) {
            return new IllegalStateException("cannot write the repro bundle", e);
        }
    }

    private static AgentEndpoint endpoint(TopologyHost topology, String container) {
        return TopologyHost.PROXY_ALIAS.equals(container) ? topology.proxy() : topology.backend(container);
    }

    private String reportName(ExtensionContext context) {
        return context.getRequiredTestClass().getName() + "." + context.getRequiredTestMethod().getName();
    }

    private TopologyResource resource(ExtensionContext context) {
        ExtensionContext owner = context;
        if (isolation == Isolation.Mode.PER_CLASS) {
            while (owner.getTestMethod().isPresent()) {
                owner = owner.getParent().orElseThrow();
            }
        }
        return owner.getStore(CellInvocation.NAMESPACE).getOrComputeIfAbsent("topology:" + cell.identity(),
                key -> boot(), TopologyResource.class);
    }

    private TopologyResource boot() {
        String jar = property("proofstone.agentJar", "the shaded Bukkit agent jar");
        String proxyProperty = PROXY_AGENT_JAR.get(cell.proxy().id());
        if (proxyProperty == null) {
            throw new IllegalStateException("no proxy agent for proxy " + cell.proxy().id());
        }
        String proxyJar = property(proxyProperty, "the shaded " + cell.proxy().id() + " agent jar");
        List<String> plugins = List.of(config.plugins());
        if (controlRun) {
            System.err.println("PROOFSTONE CONTROL RUN: plugin jars are not installed; a passing test is VACUOUS");
            plugins = List.of();
        }
        PaperHostOptions options = PaperHostOptions.of(Path.of(jar), true)
                .withArtefacts(Path.of("").toAbsolutePath(), plugins);
        // Test-only (T-M7-13): a deliberately broken topology, so the conformance must come back red. Loud on stderr.
        TopologyFault fault = TopologyFault.fromSystemProperty();
        if (fault != TopologyFault.NONE) {
            System.err.println("PROOFSTONE TOPOLOGY FAULT injected on " + cell.identity() + ": " + fault.value());
        }
        return new TopologyResource(TopologyHost.start(cell, name -> options, Path.of(proxyJar), fault));
    }

    private static String property(String name, String what) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new ParameterResolutionException("system property " + name + " is not set: it points at " + what);
        }
        return value;
    }

    /** Closes the topology when the owning store closes. */
    private record TopologyResource(TopologyHost topology) implements AutoCloseable {
        @Override
        public void close() {
            topology.close();
        }
    }
}
