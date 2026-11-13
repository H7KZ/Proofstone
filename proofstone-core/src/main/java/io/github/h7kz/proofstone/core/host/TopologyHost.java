package io.github.h7kz.proofstone.core.host;

import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.TopologyCell;
import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.testcontainers.containers.Network;

/**
 * A topology (ADR-0016): one proxy and N Paper backends on one Docker network, the backends as
 * {@code server-<name>} and the proxy as {@code proxy}. Backends start first, then the proxy; each is ready only with
 * its log line and agent health. Forwarding is set through copied files (TopologyConfig), never mounts. The forwarding
 * secret is generated here per topology and written only into containers: never to a file on the host, a log line, an
 * exception message or a bundle (ADR-0016 section 8). A start that fails stops every container already started and
 * reports each one's log tail; nothing of ours is left running.
 */
public final class TopologyHost implements AutoCloseable {

    /** The proxy's network alias (ADR-0016 section 1). */
    public static final String PROXY_ALIAS = "proxy";
    static final String REDACTED = "<redacted: forwarding secret>";

    private final Network network;
    private final Map<String, PaperHost> backends;
    private final ProxyHost proxy;
    private final String secret;

    private TopologyHost(Network network, Map<String, PaperHost> backends, ProxyHost proxy, String secret) {
        this.network = network;
        this.backends = Collections.unmodifiableMap(backends);
        this.proxy = proxy;
        this.secret = secret;
    }

    /** One started container, as the start order sees it. */
    record Node(String id, AutoCloseable host, Supplier<String> logTail) {
    }

    /** Starts one container ready, or closes it and throws. The seam the start-order unit tests replace. */
    interface Launcher {
        Node backend(String name);

        Node proxy();
    }

    /**
     * Starts a planned topology cell (ADR-0016): its backends in order, all on its backend row, then its proxy from the
     * proxy pins of {@code versions.yml}. Returns them ready.
     *
     * @param backendOptions each backend's options by name; network, alias and forwarding files are set here
     * @param proxyAgentJar the shaded proxy agent jar
     */
    public static TopologyHost start(TopologyCell cell, Function<String, PaperHostOptions> backendOptions,
            Path proxyAgentJar) {
        return start(cell, backendOptions, proxyAgentJar, TopologyFault.NONE);
    }

    /** As {@link #start(TopologyCell, Function, Path)} with an injected misconfiguration (T-M7-13); {@code NONE} is clean. */
    public static TopologyHost start(TopologyCell cell, Function<String, PaperHostOptions> backendOptions,
            Path proxyAgentJar, TopologyFault fault) {
        return start(cell.backend(), cell.backendNames(), backendOptions,
                ProxyHostOptions.of(cell, proxyAgentJar), fault);
    }

    /**
     * Starts the proxy and its backends, all on {@code cell}'s row, and returns them ready.
     *
     * @param backendNames the backends in order; the first is the proxy's default server
     * @param backendOptions each backend's options by name; network, alias and forwarding files are set here
     */
    static TopologyHost start(Cell cell, List<String> backendNames,
            Function<String, PaperHostOptions> backendOptions, ProxyHostOptions proxyOptions) {
        return start(cell, backendNames, backendOptions, proxyOptions, TopologyFault.NONE);
    }

    static TopologyHost start(Cell cell, List<String> backendNames,
            Function<String, PaperHostOptions> backendOptions, ProxyHostOptions proxyOptions, TopologyFault fault) {
        TopologyConfig.requireBackendNames(backendNames);
        fault.requireApplicable(proxyOptions.kind());
        String secret = TopologyConfig.newSecret();
        Network network = Network.newNetwork();
        Map<String, PaperHost> backends = new LinkedHashMap<>();
        List<ProxyHost> proxy = new ArrayList<>();
        startInOrder(backendNames, new Launcher() {
            @Override
            public Node backend(String name) {
                PaperHostOptions options = backendOptions.apply(name);
                Map<String, String> files = new LinkedHashMap<>(options.files());
                files.putAll(TopologyConfig.backendFiles(proxyOptions.kind(), secret, fault,
                        name.equals(backendNames.get(0))));
                PaperHost host = PaperHost.start(cell,
                        options.withNetwork(network, TopologyConfig.alias(name)).withFiles(files));
                try {
                    requireOfflineMode(name, host);
                    requireNoProfileLookup(name, host, proxyOptions.kind());
                } catch (RuntimeException e) {
                    host.close();
                    throw e;
                }
                backends.put(name, host);
                return new Node(TopologyConfig.alias(name), host, host::logTail);
            }

            @Override
            public Node proxy() {
                ProxyHost host = ProxyHost.start(proxyOptions, network,
                        TopologyConfig.proxyFiles(proxyOptions.kind(), backendNames, secret, fault));
                proxy.add(host);
                return new Node(PROXY_ALIAS, host, host::logTail);
            }
        }, network, secret);
        return new TopologyHost(network, backends, proxy.get(0), secret);
    }

    /** {@code online-mode} is set on every backend (T-M7-07); a server that runs with another value is an error. */
    private static void requireOfflineMode(String name, PaperHost host) {
        String properties = host.exec("cat", "/data/server.properties");
        if (!properties.lines().anyMatch("online-mode=false"::equals)) {
            throw new PaperHostException("backend " + name + " does not run with online-mode=false; "
                    + "forwarding needs the backend offline (docs/platforms/velocity.md)");
        }
    }

    /**
     * The backend must not fetch profiles from Mojang at login (T-M7-10 finding, bungeecord.md Gotchas): Paper does when
     * its proxy online-mode for the active forwarding is true. Read from the config Paper wrote after start, so a copied
     * file that Paper ignored is an error, never a silent network dependency.
     */
    static void requireNoProfileLookup(String name, PaperHost host, ProxyKind kind) {
        String config = host.exec("cat", "/data/config/paper-global.yml");
        if (!proxyOnlineModeOff(config, kind)) {
            throw new PaperHostException("backend " + name + " runs with proxies." + section(kind) + ".online-mode not "
                    + "false in paper-global.yml; Paper would fetch every profile from sessionserver.mojang.com");
        }
    }

    /** Whether {@code proxies.<velocity|bungee-cord>.online-mode} is {@code false} in a written paper-global.yml. */
    static boolean proxyOnlineModeOff(String paperGlobal, ProxyKind kind) {
        // Paper writes two-space YAML: proxies: / <2 spaces>section: / <4 spaces>online-mode: false.
        String block = null;
        for (String line : paperGlobal.lines().toList()) {
            if (!line.startsWith(" ")) {
                block = line.equals("proxies:") ? "proxies" : null;
            } else if (block != null && !line.startsWith("    ")) {
                block = line.equals("  " + section(kind) + ":") ? "section" : "proxies";
            } else if ("section".equals(block) && line.equals("    online-mode: false")) {
                return true;
            }
        }
        return false;
    }

    private static String section(ProxyKind kind) {
        return kind == ProxyKind.VELOCITY ? "velocity" : "bungee-cord";
    }

    /**
     * Starts each backend in order, then the proxy. On a failure, closes what started in reverse order and the network,
     * and throws with the failed container's name and every started container's log tail, the secret redacted.
     */
    static List<Node> startInOrder(List<String> backendNames, Launcher launcher, AutoCloseable network,
            String secret) {
        List<Node> started = new ArrayList<>();
        String current = null;
        try {
            for (String name : backendNames) {
                current = TopologyConfig.alias(name);
                started.add(launcher.backend(name));
            }
            current = PROXY_ALIAS;
            started.add(launcher.proxy());
            return started;
        } catch (RuntimeException e) {
            StringBuilder message = new StringBuilder("topology failed to start: container ").append(current)
                    .append(" did not become ready: ").append(e.getMessage());
            for (Node node : started) {
                message.append("\n--- ").append(node.id()).append(" log tail ---\n").append(node.logTail().get());
            }
            PaperHostException failure = new PaperHostException(redact(message.toString(), secret), e);
            Map<String, AutoCloseable> parts = new LinkedHashMap<>();
            for (int i = started.size() - 1; i >= 0; i--) {
                parts.put(started.get(i).id(), started.get(i).host());
            }
            parts.put("the network", network);
            closeAll(parts, failure);
            throw failure;
        }
    }

    /** Closes every part in order; a failure to close is added to {@code failure} and the rest still close. */
    private static void closeAll(Map<String, AutoCloseable> parts, RuntimeException failure) {
        parts.forEach((what, part) -> {
            try {
                part.close();
            } catch (Exception e) {
                failure.addSuppressed(new IllegalStateException("cannot stop " + what + ": " + e.getMessage(), e));
            }
        });
    }

    static String redact(String text, String secret) {
        return text.replace(secret, REDACTED);
    }

    /** Where each log stands now; take it before a join, so a failure reads only what was written after. */
    public record JoinMarks(int proxy, Map<String, Integer> backends) {
    }

    public JoinMarks mark() {
        Map<String, Integer> marks = new LinkedHashMap<>();
        backends.forEach((name, host) -> marks.put(name, host.logs().size()));
        return new JoinMarks(proxy.logs().size(), marks);
    }

    /** The hop a join of {@code player} to {@code target} stopped at, from the logs since {@code marks}. */
    public JoinHop hop(String player, JoinMarks marks, String target) {
        Map<String, List<String>> lines = new LinkedHashMap<>();
        backends.forEach((name, host) -> lines.put(name, host.logs().since(marks.backends().get(name))));
        return JoinHop.of(player, proxy.logs().since(marks.proxy()), lines, target);
    }

    /** The hop a server switch of {@code player} to {@code target} stopped at, from the logs since {@code marks}. */
    public JoinHop switchHop(String player, JoinMarks marks, String target) {
        return JoinHop.ofSwitch(player, proxy.logs().since(marks.proxy()),
                backend(target).logs().since(marks.backends().get(target)), target);
    }

    /**
     * A switch timeout (T-M7-09) with the topology's side added: the hop, the stalled stage and the log tails. Marks come
     * from {@link #mark()}, taken before the {@code /server} command. The timeout stays a failure.
     */
    public JoinTimeoutException describeSwitch(JoinTimeoutException timeout, JoinMarks marks, String target) {
        Map<String, String> tails = new LinkedHashMap<>();
        backends.forEach((name, host) -> tails.put(name, host.logTail()));
        return describe(timeout, switchHop(timeout.player(), marks, target), proxy.logTail(), tails, secret);
    }

    /**
     * The T-M6-14 timeout with the topology's side added: the hop, the stalled stage and the proxy and backend log
     * tails. Explains nothing and fixes nothing; the timeout stays a failure.
     */
    public JoinTimeoutException describe(JoinTimeoutException timeout, JoinMarks marks, String target) {
        Map<String, String> tails = new LinkedHashMap<>();
        backends.forEach((name, host) -> tails.put(name, host.logTail()));
        return describe(timeout, hop(timeout.player(), marks, target), proxy.logTail(), tails, secret);
    }

    /**
     * A join that the proxy or a backend refused (a kick, not a timeout) with the topology's side added: the hop and the
     * proxy and backend log tails (T-M7-13). Marks come from {@link #mark()}, taken before the join. Evidence of where the
     * join stopped, never a reason why; the driver's own text, with the kick reason, stays first.
     */
    public ClientDriverException describeKick(ClientDriverException kicked, String player, JoinMarks marks, String target) {
        Map<String, String> tails = new LinkedHashMap<>();
        backends.forEach((name, host) -> tails.put(name, host.logTail()));
        return describeKick(kicked, hop(player, marks, target), proxy.logTail(), tails, secret);
    }

    static ClientDriverException describeKick(ClientDriverException kicked, JoinHop hop, String proxyTail,
            Map<String, String> backendTails, String secret) {
        StringBuilder suffix = new StringBuilder("; hop: ").append(hop.name()).append(" (").append(hop.evidence())
                .append(")\n--- proxy log tail ---\n").append(proxyTail);
        backendTails.forEach((name, tail) -> suffix.append("\n--- ").append(TopologyConfig.alias(name))
                .append(" log tail ---\n").append(tail));
        return new ClientDriverException(redact(kicked.getMessage() + suffix, secret), kicked);
    }

    static JoinTimeoutException describe(JoinTimeoutException timeout, JoinHop hop, String proxyTail,
            Map<String, String> backendTails, String secret) {
        StringBuilder suffix = new StringBuilder("; hop: ").append(hop.name()).append(" (").append(hop.evidence())
                .append("); stalled stage: ").append(timeout.stage())
                .append("\n--- proxy log tail ---\n").append(proxyTail);
        backendTails.forEach((name, tail) -> suffix.append("\n--- ").append(TopologyConfig.alias(name))
                .append(" log tail ---\n").append(tail));
        return timeout.withSuffix(redact(suffix.toString(), secret));
    }

    public ProxyHost proxy() {
        return proxy;
    }

    /** The backend called {@code name}; an unknown name is an error. */
    public PaperHost backend(String name) {
        PaperHost host = backends.get(name);
        if (host == null) {
            throw new IllegalArgumentException("no backend \"" + name + "\" in this topology; backends are "
                    + backends.keySet());
        }
        return host;
    }

    /** The backend names in start order; the first is the proxy's default server. */
    public List<String> backendNames() {
        return List.copyOf(backends.keySet());
    }

    /** The forwarding secret, for redaction only (BundleWriter). Never log or write it. */
    public String forwardingSecret() {
        return secret;
    }

    /** Stops the proxy, then the backends in reverse order, then the network; every part is tried. */
    @Override
    public void close() {
        Map<String, AutoCloseable> parts = new LinkedHashMap<>();
        parts.put(PROXY_ALIAS, proxy);
        List<String> names = new ArrayList<>(backends.keySet());
        Collections.reverse(names);
        names.forEach(name -> parts.put(TopologyConfig.alias(name), backends.get(name)));
        parts.put("the network", network);
        RuntimeException failure = new IllegalStateException("cannot stop the topology");
        closeAll(parts, failure);
        if (failure.getSuppressed().length > 0) {
            throw failure;
        }
    }

    @Override
    public String toString() {
        return "TopologyHost[" + proxy.kind().id() + " + " + backends.keySet() + "]";
    }
}
