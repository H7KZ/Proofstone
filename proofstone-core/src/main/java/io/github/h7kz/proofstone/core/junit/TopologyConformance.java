package io.github.h7kz.proofstone.core.junit;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import io.github.h7kz.proofstone.core.host.AgentEndpoint;
import io.github.h7kz.proofstone.core.host.TopologyConfig;
import io.github.h7kz.proofstone.core.host.TopologyHost;
import io.github.h7kz.proofstone.core.verdict.ConformanceResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;

/**
 * The per-cell conformance of a topology cell (ADR-0016 section 7): the readiness check T00, then P01 (join through the
 * proxy), P02 (forwarding), P03 (switch), P04 (plugin messaging) and P05 (proxy command) from {@link TopologyScenarios}.
 * A red makes every test on the cell INVALID, as conformance does. Its result is cached per cell per run.
 *
 * <p>T00 stays: the proxy agent lists exactly the topology's backends and its own ping reaches each one, which is the
 * check that the second backend is up before P03 switches to it.
 */
final class TopologyConformance {

    static final String NAME = "T00 proxy lists every backend online";
    private static final Duration WAIT = Duration.ofSeconds(20);
    /** A switch completes in a few seconds in the spike (T-M7-01); the same bound as {@code Client.transferTo}. */
    private static final Duration SWITCH_TIMEOUT = Duration.ofSeconds(30);

    /** A client joining a backend's own game port. */
    @FunctionalInterface
    interface DirectJoiner {
        ClientSession join(String host, int port, String player);
    }

    private TopologyConformance() {
    }

    /**
     * @param identity the cell's display id
     * @param proxy the proxy platform id ({@code velocity}, {@code bungeecord})
     * @param joinProxy joins the proxy as the given player and returns once in play
     * @param joinDirect joins a backend directly, around the proxy
     */
    static ConformanceResult run(String identity, String proxy, TopologyHost topology,
            BiFunction<TopologyHost, String, ClientSession> joinProxy, DirectJoiner joinDirect) {
        Set<String> expected = new TreeSet<>(topology.backendNames());
        try {
            Await.until(NAME, () -> servers(topology), seen -> seen.equals(expected.stream()
                    .map(name -> name + " online").toList()), WAIT);
        } catch (AwaitTimeoutException e) {
            Object last = e.observations().isEmpty() ? null : e.observations().get(0).lastValue();
            @SuppressWarnings("unchecked")
            List<String> seen = last instanceof List<?> ? (List<String>) last : null;
            return new ConformanceResult(identity, false, NAME + " failed: " + e.getMessage() + "; "
                    + unreachable(seen, topology.backendNames()));
        } catch (IllegalStateException e) {
            return new ConformanceResult(identity, false, NAME + " failed: " + e.getMessage());
        }
        ConformanceResult scenarios = TopologyScenarios.run(identity, proxy, topology.backendNames(),
                new Live(proxy, topology, joinProxy, joinDirect));
        System.err.println("PROOFSTONE CONFORMANCE " + identity + ": " + (scenarios.passed() ? "passed (T00, P01 to P05; "
                + "P04 is reported as skipped above when a backend has no fixture)" : "failed"));
        return scenarios;
    }

    /**
     * Which backends the proxy does not reach, named with their container and the hop (T-M7-13), from the last reading of
     * the proxy's server list ({@code <name> online|offline}). Evidence of where, never a reason why.
     *
     * @param seen the last reading, or {@code null} when there was none
     */
    static String unreachable(List<String> seen, List<String> expected) {
        if (seen == null) {
            return "no reading of the proxy's server list, so no backend can be named";
        }
        List<String> named = new ArrayList<>();
        for (String backend : expected) {
            String alias = TopologyConfig.alias(backend);
            if (seen.contains(backend + " offline")) {
                named.add("backend " + backend + " is offline at the proxy (container " + alias
                        + ", hop: proxy to backend " + backend + ")");
            } else if (!seen.contains(backend + " online")) {
                named.add("backend " + backend + " is not listed by the proxy (container " + alias
                        + ", hop: proxy to backend " + backend + ")");
            }
        }
        return named.isEmpty() ? "the proxy lists " + seen + ", not the topology's backends " + expected
                : String.join("; ", named);
    }

    /** {@code <name> online|offline} per server the proxy agent lists, sorted by name. */
    private static List<String> servers(TopologyHost topology) {
        JsonNode servers = topology.proxy().request("GET", "/v1/proxy/servers", null).get("servers");
        if (servers == null || !servers.isArray()) {
            throw new IllegalStateException("the proxy agent's /v1/proxy/servers reply has no servers list");
        }
        List<String> seen = new ArrayList<>();
        for (JsonNode server : servers) {
            seen.add(server.path("name").asText() + (server.path("online").asBoolean(false) ? " online" : " offline"));
        }
        seen.sort(null);
        return seen;
    }

    /** The real topology behind {@link TopologyScenarios.World}: agents over HTTP, container logs, the driver. */
    private static final class Live implements TopologyScenarios.World {

        private final String proxy;
        private final TopologyHost topology;
        private final BiFunction<TopologyHost, String, ClientSession> joinProxy;
        private final DirectJoiner joinDirect;
        private ClientSession session;
        private TopologyHost.JoinMarks marks;
        private TopologyHost.JoinMarks switchMarks;
        private String switchTarget;
        private int fixtureMark;

        Live(String proxy, TopologyHost topology, BiFunction<TopologyHost, String, ClientSession> joinProxy,
                DirectJoiner joinDirect) {
            this.proxy = proxy;
            this.topology = topology;
            this.joinProxy = joinProxy;
            this.joinDirect = joinDirect;
        }

        @Override
        public String subscribeJoinProbe() {
            return subscribe(TopologyScenarios.joinEventClass(proxy));
        }

        private String subscribe(String eventClass) {
            JsonNode reply = topology.proxy().request("POST", "/v1/events/subscribe", Map.of("class", eventClass));
            JsonNode id = reply.get("probeId");
            if (id == null || !id.isTextual()) {
                throw new IllegalStateException("the proxy agent's subscribe reply has no probeId: " + reply);
            }
            return id.asText();
        }

        private List<JsonNode> events(String probe) {
            JsonNode events = topology.proxy().request("GET", "/v1/events/" + probe + "?since=0", null).get("events");
            if (events == null || !events.isArray()) {
                throw new IllegalStateException("the proxy agent's event page of " + probe + " has no events array");
            }
            List<JsonNode> out = new ArrayList<>();
            events.forEach(out::add);
            return out;
        }

        @Override
        public void join(String player) {
            marks = topology.mark();
            session = joinProxy.apply(topology, player);
        }

        @Override
        public void leave(String player) {
            if (session != null) {
                session.close();
                session = null;
                Await.until("proxy agent lists no " + player, () -> names(topology.proxy()).contains(player),
                        listed -> !listed, WAIT);
            }
        }

        @Override
        public String proxyServer(String player) {
            return TopologyInvocation.proxyServer(topology, player);
        }

        @Override
        public List<String> proxyPlayers() {
            return names(topology.proxy());
        }

        @Override
        public List<String> backendPlayers(String backend) {
            return names(topology.backend(backend));
        }

        @Override
        public TopologyScenarios.Identity proxyIdentity(String player) {
            return identity(topology.proxy(), player);
        }

        @Override
        public TopologyScenarios.Identity backendIdentity(String backend, String player) {
            return identity(topology.backend(backend), player);
        }

        @Override
        public List<String> joinEvents(String probe) {
            List<String> seen = new ArrayList<>();
            for (JsonNode event : events(probe)) {
                JsonNode fields = event.path("fields");
                seen.add(fields.path("getPlayer").path("name").asText("?") + "@"
                        + fields.path("getServer").path("name").asText("?"));
            }
            return seen;
        }

        @Override
        public void typeCommand(String command) {
            // The marks make a stalled switch name its stage and hop, as Client.transferTo does.
            switchMarks = topology.mark();
            switchTarget = command.startsWith("server ") ? command.substring("server ".length()) : null;
            session.command(command);
        }

        @Override
        public void awaitSwitch() {
            try {
                session.awaitServerSwitch(SWITCH_TIMEOUT);
            } catch (JoinTimeoutException e) {
                throw topology.describeSwitch(e, switchMarks, switchTarget);
            }
        }

        @Override
        public String previousServer(String player) {
            JsonNode previous = topology.proxy().request("GET", "/v1/players/" + player, null).path("proxy")
                    .path("previousServer");
            return previous.isTextual() ? previous.asText() : null;
        }

        @Override
        public boolean fixtureInstalled(String backend) {
            JsonNode plugins = topology.backend(backend).request("GET", "/v1/snapshot", null).get("plugins");
            if (plugins == null || !plugins.isArray()) {
                throw new IllegalStateException("the agent's /v1/snapshot reply has no plugins list: " + plugins);
            }
            for (JsonNode plugin : plugins) {
                if (TopologyScenarios.FIXTURE_PLUGIN.equals(plugin.path("name").asText())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String subscribeMessageProbe() {
            return subscribe(TopologyScenarios.messageEventClass(proxy));
        }

        @Override
        public void requestServerName(String backend, String player) {
            fixtureMark = topology.backend(backend).logs().size();
            topology.backend(backend).command("fixture-getserver " + player);
        }

        @Override
        public List<String> fixtureReplies(String backend) {
            return TopologyScenarios.fixtureReplies(topology.backend(backend).logs().since(fixtureMark));
        }

        @Override
        public List<String> messageChannels(String probe) {
            List<String> channels = new ArrayList<>();
            for (JsonNode event : events(probe)) {
                JsonNode fields = event.path("fields");
                // Velocity names the channel in getIdentifier, BungeeCord in getTag.
                JsonNode channel = fields.has("getTag") ? fields.get("getTag") : fields.path("getIdentifier");
                channels.add(channel.isObject() ? channel.path("value").asText("?") : channel.asText("?"));
            }
            return channels;
        }

        @Override
        public String subscribeCommandProbe() {
            return subscribe(TopologyScenarios.commandEventClass(proxy));
        }

        @Override
        public List<String> commandEvents(String probe) {
            List<String> seen = new ArrayList<>();
            for (JsonNode event : events(probe)) {
                JsonNode fields = event.path("fields");
                // Velocity: getCommand "server" and getCommandSource; BungeeCord: getMessage "/server" and getSender.
                JsonNode who = fields.has("getCommandSource") ? fields.get("getCommandSource") : fields.path("getSender");
                String text = fields.has("getCommand") ? "/" + fields.get("getCommand").asText("") : fields.path("getMessage")
                        .asText("");
                seen.add(who.path("name").asText("?") + ":" + text);
            }
            return seen;
        }

        @Override
        public String consoleCommand(String command) {
            return topology.proxy().command(command);
        }

        // Log lines reach the buffer a moment after the agent can answer, so the reads below are awaited.

        @Override
        public String clientAddressSeenByProxy(String player) {
            return Await.until("the " + proxy + " log shows the address " + player + " connected from",
                    () -> TopologyScenarios.proxyAddress(proxy, player, topology.proxy().logs().since(marks.proxy())),
                    address -> true, WAIT);
        }

        @Override
        public String clientAddressSeenByBackend(String backend, String player) {
            return Await.until("backend " + backend + " log shows the address " + player + " logged in from",
                    () -> TopologyScenarios.backendAddress(backend, player,
                            topology.backend(backend).logs().since(marks.backends().get(backend))),
                    address -> true, WAIT);
        }

        @Override
        public TopologyScenarios.DirectJoin directJoin(String backend, String player) {
            ClientSession direct;
            try {
                direct = joinDirect.join(topology.backend(backend).gameHost(), topology.backend(backend).gamePort(),
                        player);
            } catch (ClientDriverException e) {
                // A kick, or a timeout (whose message says so): the scenario judges the text, never "it threw". The text
                // is printed so the platform doc records the message as observed.
                System.err.println("PROOFSTONE CONFORMANCE " + proxy + " direct join to backend " + backend + ": " + e.getMessage());
                return new TopologyScenarios.DirectJoin(false, e.getMessage());
            }
            direct.close();
            return new TopologyScenarios.DirectJoin(true, player + " reached the play state on backend " + backend);
        }

        private static List<String> names(AgentEndpoint agent) {
            JsonNode players = agent.request("GET", "/v1/players", null).get("players");
            if (players == null || !players.isArray()) {
                throw new IllegalStateException("the agent's /v1/players reply has no players list: " + players);
            }
            List<String> out = new ArrayList<>();
            players.forEach(name -> out.add(name.asText()));
            return out;
        }

        private static TopologyScenarios.Identity identity(AgentEndpoint agent, String player) {
            JsonNode state = agent.request("GET", "/v1/players/" + player, null);
            JsonNode name = state.get("name");
            JsonNode uuid = state.get("uuid");
            if (name == null || !name.isTextual() || uuid == null || !uuid.isTextual()) {
                throw new IllegalStateException("the agent's reply for " + player + " has no name and uuid: " + state);
            }
            return new TopologyScenarios.Identity(name.asText(), uuid.asText());
        }
    }
}
