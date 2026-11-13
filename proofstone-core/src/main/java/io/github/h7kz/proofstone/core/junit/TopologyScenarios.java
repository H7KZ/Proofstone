package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.DisagreeException;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import io.github.h7kz.proofstone.core.host.TopologyConfig;
import io.github.h7kz.proofstone.core.verdict.ConformanceResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The topology conformance scenarios P01 to P05 (docs/specs/self-tests.md section 1b, ADR-0016 sections 6 and 7),
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
 *   <li><b>P03</b> switch. The client types {@code /server survival}, then {@code /server lobby}. Each time the driver sees
 *       the new join, the proxy agent says the target, the target's agent lists the player, the source's does not
 *       ({@link ServerSwitch}), and the proxy agent's {@code previousServer} is the backend the player left.</li>
 *   <li><b>P04</b> plugin messaging. The fixture plugin on the lobby sends {@code GetServer} on the {@code BungeeCord}
 *       channel; the proxy answers; the fixture logs the reply, which equals the proxy agent's current server; and a proxy
 *       event probe saw a plugin message on that channel. Skipped, with its reason, on a backend without the fixture.</li>
 *   <li><b>P05</b> proxy command. A console command through the proxy agent runs on the proxy (its output names the
 *       proxy; output is best effort, so this is what the agent ITs observed), and a client-typed {@code /server} shows in
 *       a proxy command-event probe.</li>
 * </ul>
 */
final class TopologyScenarios {

    /** A {@code T<8 hex>} name: the log guard knows that a proxied harness client's first lobby moves WARN "moved too quickly" (velocity.md, Gotchas). */
    static final String PLAYER = "T7e5c0f01";
    static final String DIRECT_PLAYER = "PSDirect";
    private static final Duration AGENT_WAIT = Duration.ofSeconds(20);

    /** What Paper tells a client that skips the Velocity forward (docs/platforms/velocity.md, Direct join). */
    static final String VELOCITY_DIRECT_REFUSAL = "This server requires you to connect with Velocity";
    /** What a Paper behind BungeeCord legacy forwarding tells a direct client (docs/platforms/bungeecord.md, Direct join). */
    static final String BUNGEE_DIRECT_REFUSAL = "If you wish to use IP forwarding, please enable it in your BungeeCord config as well!";

    private static final Map<String, String> JOIN_EVENT = Map.of(
            "velocity", "com.velocitypowered.api.event.player.ServerConnectedEvent",
            "bungeecord", "net.md_5.bungee.api.event.ServerConnectedEvent");
    private static final Map<String, String> MESSAGE_EVENT = Map.of(
            "velocity", "com.velocitypowered.api.event.connection.PluginMessageEvent",
            "bungeecord", "net.md_5.bungee.api.event.PluginMessageEvent");
    /** Velocity fires a command event for every command a player types; BungeeCord reports it as a chat event. */
    private static final Map<String, String> COMMAND_EVENT = Map.of(
            "velocity", "com.velocitypowered.api.event.command.CommandExecuteEvent",
            "bungeecord", "net.md_5.bungee.api.event.ChatEvent");
    /** A console command whose reply names the proxy (docs/platforms/velocity.md and bungeecord.md, Gotchas). */
    private static final Map<String, String> CONSOLE_COMMAND = Map.of("velocity", "velocity info", "bungeecord", "bungee");
    private static final Map<String, String> CONSOLE_NAMES = Map.of("velocity", "Velocity", "bungeecord", "BungeeCord");

    /** The plugin the P04 backend runs, and the log line it records a reply with (platforms/bukkit/fixture). */
    static final String FIXTURE_PLUGIN = "proofstone-fixture";
    private static final Pattern FIXTURE_REPLY = Pattern.compile("fixture BungeeCord reply: GetServer (\\S+)");

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

        /** The client types a command (no leading slash) in its chat. */
        void typeCommand(String command);

        /** Returns once the client's driver saw the server switch complete, or throws with the stalled stage and hop. */
        void awaitSwitch();

        /** The proxy agent's {@code previousServer} of the player, or {@code null} when it reports none. */
        String previousServer(String player);

        /** Whether the backend's agent lists the fixture plugin. */
        boolean fixtureInstalled(String backend);

        /** Subscribes a proxy probe on the plugin message event, before the fixture sends; returns its id. */
        String subscribeMessageProbe();

        /** Asks the fixture on the backend to send {@code GetServer} for the player (the only trigger P04 has). */
        void requestServerName(String backend, String player);

        /** The server names the fixture on the backend recorded from a {@code GetServer} reply, since the request. */
        List<String> fixtureReplies(String backend);

        /** The channel of each plugin message the proxy probe saw. */
        List<String> messageChannels(String probe);

        /** Subscribes a proxy probe on the command a player types, before the client types it; returns its id. */
        String subscribeCommandProbe();

        /** {@code <player>:/<command>} for each player command the probe saw. */
        List<String> commandEvents(String probe);

        /** Runs a console command on the proxy through its agent and returns the output the agent collected. */
        String consoleCommand(String command);
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
            current = "P03";
            timed(cell, "P03", () -> p03(world, backends, wait));
            current = "P04";
            if (world.fixtureInstalled(lobby)) {
                timed(cell, "P04", () -> p04(world, proxy, lobby, wait));
            } else {
                System.err.println("PROOFSTONE CONFORMANCE " + cell + " P04 skipped: " + skipReason(lobby));
            }
            current = "P05";
            timed(cell, "P05", () -> p05(world, proxy, wait));
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
        // The failure names the container and the hop, so a fault run is satisfied by this red and no other (T-M7-13).
        String where = "; container " + TopologyConfig.alias(lobby) + ", hop: proxy to backend " + lobby;
        try {
            judgeForwarding(PLAYER, seenByProxy, seenByBackend, proxyAddress, backendAddress);
        } catch (DisagreeException e) {
            throw new DisagreeException(e.getMessage() + where, e.serverObserved(), e.clientObserved());
        } catch (AssertionError e) {
            throw new AssertionError(e.getMessage() + where, e);
        }
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

    // P03

    private static void p03(World world, List<String> backends, Duration wait) {
        if (backends.size() < 2) {
            throw new IllegalStateException("P03 needs two backends, the topology has " + backends);
        }
        String lobby = backends.get(0);
        String other = backends.get(1);
        switchTo(world, backends, lobby, other, wait);
        switchTo(world, backends, other, lobby, wait);
    }

    private static void switchTo(World world, List<String> backends, String from, String to, Duration wait) {
        ServerSwitch.transfer(PLAYER, to, backends, new ServerSwitch.Agents() {
            @Override
            public String proxyServer(String player) {
                return world.proxyServer(player);
            }

            @Override
            public boolean lists(String backend, String player) {
                return world.backendPlayers(backend).contains(player);
            }
        }, new ServerSwitch.ClientSide() {
            @Override
            public void type(String command) {
                world.typeCommand(command);
            }

            @Override
            public void awaitSwitch() {
                world.awaitSwitch();
            }
        }, wait);
        String previous;
        try {
            previous = Await.until("proxy agent says " + PLAYER + " came from " + from,
                    () -> world.previousServer(PLAYER), from::equals, wait);
        } catch (AwaitTimeoutException e) {
            Object last = e.observations().isEmpty() ? null : e.observations().get(0).lastValue();
            previous = last == null ? null : last.toString();
        }
        judgePreviousServer(PLAYER, to, from, previous);
    }

    /**
     * The proxy agent's {@code previousServer} after a real switch. Absent is a failure (the agent would then have nothing
     * but its mock-driven test behind it); a different backend than the one the other agents saw the player leave is
     * DISAGREE.
     */
    static void judgePreviousServer(String player, String current, String expectedPrevious, String reported) {
        String head = player + " switched to " + current + " and the agents show it there; ";
        if (reported == null) {
            throw new AssertionError("FAIL " + head + "the proxy agent reports no previousServer, expected "
                    + expectedPrevious);
        }
        if (!expectedPrevious.equals(reported)) {
            throw new DisagreeException("DISAGREE " + head + "the proxy agent's previousServer is " + reported
                    + ", the player left " + expectedPrevious + " (its agent listed the player before the switch)",
                    "left " + expectedPrevious, "previousServer " + reported);
        }
    }

    // P04

    /** Observed on velocity@4.3.1 (docs/platforms/velocity.md, Plugin messaging): the reply came back, the probe saw nothing. */
    static final String NO_EVENT_REASON = "Velocity answers BungeeCord-channel messages itself and fires no PluginMessageEvent "
            + "for them, so the proxy agent's event probe cannot see GetServer there";

    /** Whether the proxy's plugin message event fires for a BungeeCord-channel message the proxy answers itself. */
    static boolean proxyFiresMessageEvent(String proxy) {
        return !"velocity".equals(proxy);
    }

    static String skipReason(String backend) {
        return "backend " + backend + " has no " + FIXTURE_PLUGIN + " plugin, so nothing can send GetServer on the "
                + "BungeeCord channel; the conformance suite installs it, a user's topology cell does not";
    }

    /** One reading for P04: what the fixture recorded and what the proxy's probe saw. */
    private record MessageReading(List<String> replies, List<String> channels, String proxyServer) {
        boolean sawMessage() {
            return channels.stream().anyMatch(channel -> channel.toLowerCase(Locale.ROOT).contains("bungeecord"));
        }

        boolean agrees(String lobby, boolean eventExpected) {
            return replies.contains(lobby) && lobby.equals(proxyServer) && (!eventExpected || sawMessage());
        }
    }

    private static void p04(World world, String proxy, String lobby, Duration wait) {
        boolean eventExpected = proxyFiresMessageEvent(proxy);
        if (!eventExpected) {
            System.err.println("PROOFSTONE CONFORMANCE P04 event probe not required on " + proxy + ": " + NO_EVENT_REASON);
        }
        String probe = world.subscribeMessageProbe();
        world.requestServerName(lobby, PLAYER);
        try {
            Await.until("fixture on " + lobby + " records the proxy's GetServer reply and the proxy probe sees the message",
                    () -> new MessageReading(world.fixtureReplies(lobby), world.messageChannels(probe),
                            world.proxyServer(PLAYER)), reading -> reading.agrees(lobby, eventExpected), wait);
        } catch (AwaitTimeoutException e) {
            Object last = e.observations().isEmpty() ? null : e.observations().get(0).lastValue();
            Throwable cause = e.observations().isEmpty() ? null : e.observations().get(0).lastException();
            throw judgeMessage(proxy, lobby, (MessageReading) last, cause);
        }
    }

    private static AssertionError judgeMessage(String proxy, String lobby, MessageReading last, Throwable cause) {
        String head = "GetServer from " + lobby + " through " + proxy + " for " + PLAYER + "; ";
        if (last == null) {
            return new AssertionError("ORACLE MISSING " + head + "no reading: " + cause);
        }
        String shown = "the fixture recorded " + last.replies() + "; the proxy event probe saw channels "
                + last.channels() + "; the proxy agent says " + PLAYER + " is on " + last.proxyServer();
        if (last.replies().isEmpty()) {
            return new AssertionError("FAIL " + head + "the fixture recorded no GetServer reply: " + shown);
        }
        if (!last.replies().contains(lobby) || !lobby.equals(last.proxyServer())) {
            return new DisagreeException("DISAGREE " + head + "the proxy's answer and its own agent differ: " + shown,
                    "fixture: " + last.replies(), "proxy agent: " + last.proxyServer());
        }
        return new AssertionError("FAIL " + head + "the fixture has the reply, but the proxy agent's event probe saw no "
                + "plugin message on the BungeeCord channel: " + shown);
    }

    /** The fixture's recorded replies in {@code lines}, in order. */
    static List<String> fixtureReplies(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            Matcher found = FIXTURE_REPLY.matcher(line);
            if (found.find()) {
                out.add(found.group(1));
            }
        }
        return out;
    }

    // P05

    private static void p05(World world, String proxy, Duration wait) {
        String command = consoleCommand(proxy);
        String output = world.consoleCommand(command);
        String name = consoleOutputNames(proxy);
        if (output == null || !output.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
            throw new AssertionError("FAIL console command \"" + command + "\" through the " + proxy + " agent: its "
                    + "output does not name " + name + " (so it is not shown to have run on the proxy): \"" + output
                    + "\"");
        }
        String probe = world.subscribeCommandProbe();
        world.typeCommand("server");
        String expected = PLAYER + ":/server";
        try {
            Await.until("proxy command probe sees " + expected, () -> world.commandEvents(probe),
                    events -> events.contains(expected), wait);
        } catch (AwaitTimeoutException e) {
            Object last = e.observations().isEmpty() ? null : e.observations().get(0).lastValue();
            throw new AssertionError("FAIL " + PLAYER + " typed /server in play, but the " + proxy + " agent's command "
                    + "event probe saw " + last + ", expected " + expected);
        }
    }

    // Probe and log reading

    /** The proxy's plugin message event class. @throws IllegalStateException for an unknown proxy */
    static String messageEventClass(String proxy) {
        return known(MESSAGE_EVENT, proxy, "plugin message event");
    }

    /** The proxy's event class of a command a player typed. @throws IllegalStateException for an unknown proxy */
    static String commandEventClass(String proxy) {
        return known(COMMAND_EVENT, proxy, "command event");
    }

    /** @throws IllegalStateException for an unknown proxy */
    static String consoleCommand(String proxy) {
        return known(CONSOLE_COMMAND, proxy, "console command");
    }

    /** What the console command's output must contain. @throws IllegalStateException for an unknown proxy */
    static String consoleOutputNames(String proxy) {
        return known(CONSOLE_NAMES, proxy, "console command output");
    }

    private static String known(Map<String, String> table, String proxy, String what) {
        String value = table.get(proxy);
        if (value == null) {
            throw new IllegalStateException("no " + what + " known for proxy " + proxy);
        }
        return value;
    }

    /** The proxy's own event class of a player connecting to a backend. @throws IllegalStateException for an unknown proxy */
    static String joinEventClass(String proxy) {
        String type = JOIN_EVENT.get(proxy);
        if (type == null) {
            throw new IllegalStateException("no join event known for proxy " + proxy);
        }
        return type;
    }

    private static final Pattern VELOCITY_CONNECTED = Pattern.compile("\\[connected player\\] (\\S+) \\(/([^:)]+):\\d+\\) has connected");
    private static final Pattern BUNGEE_CONNECTED = Pattern.compile("\\[(?:([^,\\]]+),)?/([^:\\]]+):\\d+\\] <-> InitialHandler has connected");
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
                // The line names the player when BungeeCord already knows it (observed in T-M7-12:
                // "[T7e5c0f01,/172.26.0.1:47956]"); a line that names another player is not this client's.
                if (found.find() && (found.group(1) == null || found.group(1).equals(player))) {
                    return found.group(2);
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
