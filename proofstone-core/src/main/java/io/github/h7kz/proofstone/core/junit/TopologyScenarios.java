package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.DisagreeException;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import io.github.h7kz.proofstone.core.verdict.ConformanceResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The topology conformance scenarios P01 and P02 (docs/specs/self-tests.md section 1b, ADR-0016 sections 6 and 7),
 * cached per cell like the Bukkit conformance and red for every test on the cell. They read only what the agents and the
 * logs say; {@link World} is the seam, so the verdicts are unit tested without Docker.
 *
 * <ul>
 *   <li><b>P01</b> join through the proxy. The client joins; the proxy agent lists it and its current server; the default
 *       backend's agent lists it; a proxy event probe, subscribed before the join, saw the proxy connect it to that server.
 *       A client in play that an agent does not show is DISAGREE.</li>
 *   <li><b>P02</b> forwarding. The name and UUID the backend sees equal the proxy's, the UUID is the offline UUID of the
 *       name (every test cell runs with online mode off, so the proxy derives the UUID from the name), and the client's
 *       address the backend logs is the one the proxy logs, not the proxy container's. Then a client joins the backend
 *       directly: Velocity modern must refuse it; BungeeCord legacy is asserted as observed (docs/platforms/bungeecord.md).</li>
 * </ul>
 */
final class TopologyScenarios {

    static final String PLAYER = "PSConf";
    static final String DIRECT_PLAYER = "PSDirect";
    private static final Duration AGENT_WAIT = Duration.ofSeconds(20);

    /** What Paper tells a client that skips the Velocity forward (docs/platforms/velocity.md, Direct join). */
    static final String VELOCITY_DIRECT_REFUSAL = "This server requires you to connect with Velocity";
    /** What a Paper behind BungeeCord legacy forwarding tells a direct client (docs/platforms/bungeecord.md, Direct join). */
    static final String BUNGEE_DIRECT_REFUSAL = "If you wish to use IP forwarding, please enable it in your BungeeCord config as well!";

    private static final Map<String, String> JOIN_EVENT = Map.of(
            "velocity", "com.velocitypowered.api.event.player.ServerConnectedEvent",
            "bungeecord", "net.md_5.bungee.api.event.ServerConnectedEvent");

    private TopologyScenarios() {
    }

    /** A name and UUID as one container reports it. */
    record Identity(String name, String uuid) {
        @Override
        public String toString() {
            return name + "/" + uuid;
        }
    }

    /** The outcome of a client that joins a backend's own game port, around the proxy. */
    record DirectJoin(boolean joined, String message) {
    }

    /** What the topology says, read fresh on every call. Any of it may throw when its agent or the join fails. */
    interface World {
        /** Subscribes the proxy's join-event probe, before the client joins; returns its id. */
        String subscribeJoinProbe();

        /** The client joins through the proxy and is in play when this returns. */
        void join(String player);

        void leave(String player);

        /** The proxy agent's current server of the player, or {@code null} when it does not list the player. */
        String proxyServer(String player);

        List<String> proxyPlayers();

        List<String> backendPlayers(String backend);

        Identity proxyIdentity(String player);

        Identity backendIdentity(String backend, String player);

        /** {@code <player>@<server>} for each join the proxy's event probe saw. */
        List<String> joinEvents(String probe);

        String clientAddressSeenByProxy(String player);

        String clientAddressSeenByBackend(String backend, String player);

        /** A client joins the backend's game port directly; a refusal is a result, not an exception. */
        DirectJoin directJoin(String backend, String player);
    }

    /**
     * Runs P01 then P02 on the topology. The first failing scenario decides the result and is named in it; a scenario that
     * cannot be checked fails, it never passes. The client always leaves.
     */
    static ConformanceResult run(String cell, String proxy, List<String> backends, World world) {
        return run(cell, proxy, backends, world, AGENT_WAIT);
    }

    /** As {@link #run(String, String, List, World)} with the bound of the agent awaits, so a unit test need not wait 20 s. */
    static ConformanceResult run(String cell, String proxy, List<String> backends, World world, Duration wait) {
        String lobby = backends.get(0);
        String current = "P01";
        try {
            String probe = world.subscribeJoinProbe();
            world.join(PLAYER);
            timed(cell, "P01", () -> p01(world, probe, lobby, wait));
            current = "P02";
            timed(cell, "P02", () -> p02(world, proxy, lobby));
        } catch (RuntimeException | AssertionError e) {
            return failed(cell, current, e, world);
        }
        return finish(cell, world, null);
    }

    private static ConformanceResult failed(String cell, String scenario, Throwable e, World world) {
        String message = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getName() : e.getMessage();
        return finish(cell, world, new ConformanceResult(cell, false, scenario + " failed: " + message));
    }

    private static ConformanceResult finish(String cell, World world, ConformanceResult failure) {
        try {
            world.leave(PLAYER);
        } catch (RuntimeException e) {
            if (failure == null) {
                return new ConformanceResult(cell, false, "the conformance client could not leave: " + e.getMessage());
            }
        }
        return failure == null ? ConformanceResult.pass(cell) : failure;
    }

    private static void timed(String cell, String name, Runnable scenario) {
        long started = System.nanoTime();
        scenario.run();
        System.err.println("PROOFSTONE CONFORMANCE " + cell + " " + name + " passed in "
                + (System.nanoTime() - started) / 1_000_000L + " ms");
    }

    // P01

    /** One reading of every oracle for P01. */
    private record JoinReading(String proxyServer, boolean proxyLists, boolean backendLists, List<String> events) {
        boolean agrees(String player, String lobby) {
            return proxyLists && lobby.equals(proxyServer) && backendLists && events.contains(player + "@" + lobby);
        }
    }

    private static void p01(World world, String probe, String lobby, Duration wait) {
        try {
            Await.until(PLAYER + " on " + lobby + " by the proxy agent, the backend agent and the proxy event probe",
                    () -> readJoin(world, probe, lobby), reading -> reading.agrees(PLAYER, lobby), wait);
        } catch (AwaitTimeoutException e) {
            Object last = e.observations().isEmpty() ? null : e.observations().get(0).lastValue();
            Throwable cause = e.observations().isEmpty() ? null : e.observations().get(0).lastException();
            throw judgeJoin(PLAYER, lobby, (JoinReading) last, cause);
        }
    }

    private static JoinReading readJoin(World world, String probe, String lobby) {
        String server = world.proxyServer(PLAYER);
        return new JoinReading(server, world.proxyPlayers().contains(PLAYER) && server != null,
                world.backendPlayers(lobby).contains(PLAYER), world.joinEvents(probe));
    }

    /**
     * The verdict once the agents did not agree in time, with the client in play. An agent that never answered is ORACLE
     * MISSING. The client in play that an agent does not show, or agents that contradict each other, is DISAGREE. Only a
     * probe that missed an otherwise agreed join is a plain failure (nothing contradicts it, the probe is simply wrong).
     */
    private static AssertionError judgeJoin(String player, String lobby, JoinReading last, Throwable cause) {
        String head = player + " joined through the proxy (the client is in play); ";
        if (last == null) {
            return new AssertionError("ORACLE MISSING " + head + "no agent reading: " + cause);
        }
        String shown = "proxy agent " + (last.proxyLists() ? "lists it on " + last.proxyServer() : "does not list it")
                + "; backend " + lobby + " agent " + (last.backendLists() ? "lists it" : "does not list it")
                + "; proxy event probe saw " + last.events();
        boolean agentsShow = last.proxyLists() && last.backendLists() && lobby.equals(last.proxyServer());
        if (agentsShow) {
            return new AssertionError("FAIL " + head + "both agents show it on " + lobby + ", but the proxy event probe saw no "
                    + player + "@" + lobby + " join: " + shown);
        }
        return new DisagreeException("DISAGREE " + head + "expected it on " + lobby + ": " + shown, shown,
                "the client joined (play state)");
    }

    // P02

    private static void p02(World world, String proxy, String lobby) {
        Identity seenByProxy = world.proxyIdentity(PLAYER);
        Identity seenByBackend = world.backendIdentity(lobby, PLAYER);
        String proxyAddress = world.clientAddressSeenByProxy(PLAYER);
        String backendAddress = world.clientAddressSeenByBackend(lobby, PLAYER);
        judgeForwarding(PLAYER, seenByProxy, seenByBackend, proxyAddress, backendAddress);
        DirectJoin direct = world.directJoin(lobby, DIRECT_PLAYER);
        judgeDirectJoin(proxy, lobby, direct);
    }

    /** The UUID Minecraft derives for a player of this name in offline mode. */
    static String offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * @throws DisagreeException when the backend's name, UUID or client address differ from the proxy's
     * @throws AssertionError when both agree but the UUID is not the offline one (online mode is off in every test cell)
     */
    static void judgeForwarding(String player, Identity proxy, Identity backend, String proxyAddress,
            String backendAddress) {
        String expected = offlineUuid(player);
        String shown = "proxy sees " + proxy + ", backend sees " + backend + ", offline UUID of " + player + " is "
                + expected + "; client address: proxy log " + proxyAddress + ", backend log " + backendAddress;
        if (!proxy.equals(backend) || !proxyAddress.equals(backendAddress)) {
            throw new DisagreeException("DISAGREE forwarding of " + player + ": " + shown, "backend: " + backend
                    + " from " + backendAddress, "proxy: " + proxy + " from " + proxyAddress);
        }
        if (!expected.equals(proxy.uuid()) || !player.equals(proxy.name())) {
            throw new AssertionError("FAIL forwarding of " + player + ": proxy and backend agree, but the identity is not "
                    + "the offline one (online mode is off in tests): " + shown);
        }
    }

    /**
     * A direct join to a backend (docs/platforms/velocity.md and bungeecord.md, Direct join). Velocity modern must refuse
     * with Paper's own message. BungeeCord legacy is asserted as observed: the same Paper refuses a handshake with no
     * forwarded data, and the message is quoted. A refusal with any other text, a join, or no reason is not a pass.
     */
    static void judgeDirectJoin(String proxy, String backend, DirectJoin direct) {
        String expected = switch (proxy) {
            case "velocity" -> VELOCITY_DIRECT_REFUSAL;
            case "bungeecord" -> BUNGEE_DIRECT_REFUSAL;
            default -> throw new IllegalStateException("no direct-join expectation for proxy " + proxy);
        };
        if (direct.joined()) {
            throw new AssertionError("FAIL direct join to backend " + backend + " with " + proxy + " forwarding on: the "
                    + "backend let the client in (" + direct.message() + "), expected a refusal with \"" + expected + "\"");
        }
        if (direct.message() == null || direct.message().isBlank()) {
            throw new AssertionError("FAIL direct join to backend " + backend + ": refused with no reason, so the "
                    + "refusal cannot be told from a dropped connection");
        }
        if (!direct.message().contains(expected)) {
            throw new AssertionError("FAIL direct join to backend " + backend + ": refused, but with \"" + direct.message()
                    + "\" and not \"" + expected + "\"");
        }
    }

    // Probe and log reading

    /** The proxy's own event class of a player connecting to a backend. @throws IllegalStateException for an unknown proxy */
    static String joinEventClass(String proxy) {
        String type = JOIN_EVENT.get(proxy);
        if (type == null) {
            throw new IllegalStateException("no join event known for proxy " + proxy);
        }
        return type;
    }

    private static final Pattern VELOCITY_CONNECTED = Pattern.compile("\\[connected player\\] (\\S+) \\(/([^:)]+):\\d+\\) has connected");
    private static final Pattern BUNGEE_CONNECTED = Pattern.compile("\\[/([^:\\]]+):\\d+\\] <-> InitialHandler has connected");
    private static final Pattern PAPER_LOGGED_IN = Pattern.compile(
            "(?:^|[\\s:])([A-Za-z0-9_.]{1,16})\\[/([^:\\]]+):\\d+\\] logged in with entity id");

    /**
     * The client address the proxy logged when the player connected, from the proxy's log lines since the join began.
     * Velocity names the player on the line; BungeeCord logs the address before it knows the name, so the first such line
     * is taken (the conformance client is the only one joining then).
     *
     * @throws IllegalStateException when no line gives an address, never a guess
     */
    static String proxyAddress(String proxy, String player, List<String> lines) {
        for (String line : lines) {
            if ("velocity".equals(proxy)) {
                Matcher found = VELOCITY_CONNECTED.matcher(line);
                if (found.find() && found.group(1).equals(player)) {
                    return found.group(2);
                }
            } else if ("bungeecord".equals(proxy)) {
                Matcher found = BUNGEE_CONNECTED.matcher(line);
                if (found.find()) {
                    return found.group(1);
                }
            } else {
                throw new IllegalStateException("no proxy log format known for proxy " + proxy);
            }
        }
        throw new IllegalStateException("the " + proxy + " log has no line that gives the address " + player
                + " connected from, in " + lines.size() + " line(s)");
    }

    /**
     * The client address the backend logged at {@code logged in}, for exactly this player name.
     *
     * @throws IllegalStateException when no such line exists
     */
    static String backendAddress(String backend, String player, List<String> lines) {
        for (String line : lines) {
            Matcher found = PAPER_LOGGED_IN.matcher(line);
            if (found.find() && found.group(1).equals(player)) {
                return found.group(2);
            }
        }
        throw new IllegalStateException("backend " + backend + " logged no \"" + player + "[/address] logged in\" line, in "
                + lines.size() + " line(s)");
    }
}
