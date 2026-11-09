package io.github.h7kz.proofstone.agent.velocity;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.velocitypowered.api.util.ProxyVersion;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;

/**
 * A Velocity proxy as the interfaces of the real API describe it, built with {@link Proxy}: no Velocity code runs, so
 * the contract test checks what the agent asks of the API and what it answers, not Velocity. A call the mock was not
 * told to expect throws, so the agent cannot quietly use API the tests do not cover. It records every command, event and
 * task registration, which the observe-only test reads (AGENTS.md section 3).
 */
final class MockProxy {

    /** One {@code EventManager.register}: the event class, the order, the handler. */
    static final class Registration {
        final Class<?> type;
        final PostOrder order;
        final EventHandler<Object> handler;

        Registration(Class<?> type, PostOrder order, EventHandler<Object> handler) {
            this.type = type;
            this.order = order;
            this.handler = handler;
        }
    }

    final List<Registration> registrations = Collections.synchronizedList(new ArrayList<>());
    final List<EventHandler<?>> unregistered = Collections.synchronizedList(new ArrayList<>());
    final List<String> commandRegistrations = Collections.synchronizedList(new ArrayList<>());
    final List<String> commandsRun = Collections.synchronizedList(new ArrayList<>());
    final List<Player> players = new ArrayList<>();
    final List<RegisteredServer> servers = new ArrayList<>();
    /** What the proxy's command manager does with a command line: print to the source. */
    BiConsumer<CommandSource, String> commands = (source, line) -> source.sendMessage(Component.text("ran " + line));
    /** {@code false} makes the command manager say the proxy has no such command. */
    boolean commandKnown = true;
    /** {@code true} makes the scheduler accept tasks and never run them, so a call can only time out. */
    volatile boolean schedulerStalls;
    volatile int tasksScheduled;

    final ProxyServer proxy = create(ProxyServer.class, (self, method, args) -> {
        switch (method.getName()) {
            case "getAllServers":
                return new ArrayList<>(servers);
            case "getServer":
                return servers.stream().filter(s -> s.getServerInfo().getName().equals(args[0])).findFirst();
            case "getAllPlayers":
                return new ArrayList<>(players);
            case "getPlayer":
                return players.stream().filter(p -> args[0] instanceof String
                        ? p.getUsername().equalsIgnoreCase((String) args[0]) : p.getUniqueId().equals(args[0])).findFirst();
            case "getEventManager":
                return MockProxy.this.events;
            case "getCommandManager":
                return MockProxy.this.commandManager;
            case "getScheduler":
                return MockProxy.this.scheduler;
            case "getPluginManager":
                return MockProxy.this.plugins;
            case "getVersion":
                return new ProxyVersion("Velocity", "Velocity Contributors", "4.3.1-mock");
            default:
                throw new UnsupportedOperationException("ProxyServer." + method.getName() + " is not mocked");
        }
    });

    private final EventManager events = create(EventManager.class, (self, method, args) -> {
        switch (method.getName()) {
            case "register":
                if (args.length == 4 && args[2] instanceof PostOrder) {
                    @SuppressWarnings("unchecked")
                    EventHandler<Object> handler = (EventHandler<Object>) args[3];
                    registrations.add(new Registration((Class<?>) args[1], (PostOrder) args[2], handler));
                    return null;
                }
                throw new AssertionError("an event handler was registered without an explicit PostOrder: " + method);
            case "unregister":
                unregistered.add((EventHandler<?>) args[1]);
                return null;
            case "unregisterListeners":
            case "unregisterListener":
                return null;
            default:
                throw new UnsupportedOperationException("EventManager." + method.getName() + " is not mocked");
        }
    });

    private final CommandManager commandManager = create(CommandManager.class, (self, method, args) -> {
        switch (method.getName()) {
            case "executeAsync":
                commandsRun.add((String) args[1]);
                if (!commandKnown) {
                    return CompletableFuture.completedFuture(false);
                }
                commands.accept((CommandSource) args[0], (String) args[1]);
                return CompletableFuture.completedFuture(true);
            case "register":
            case "unregister":
            case "metaBuilder":
                commandRegistrations.add(method.getName());
                throw new AssertionError("the agent touched the command registry: " + method);
            default:
                throw new UnsupportedOperationException("CommandManager." + method.getName() + " is not mocked");
        }
    });

    private final PluginManager plugins = create(PluginManager.class, (self, method, args) -> {
        if ("getPlugins".equals(method.getName())) {
            return Collections.emptyList();
        }
        throw new UnsupportedOperationException("PluginManager." + method.getName() + " is not mocked");
    });

    private final Scheduler scheduler = create(Scheduler.class, (self, method, args) -> {
        if (!"buildTask".equals(method.getName())) {
            throw new UnsupportedOperationException("Scheduler." + method.getName() + " is not mocked");
        }
        Runnable task = (Runnable) args[1];
        return create(Scheduler.TaskBuilder.class, (builder, call, none) -> {
            if (!"schedule".equals(call.getName())) {
                throw new UnsupportedOperationException("TaskBuilder." + call.getName() + " is not mocked");
            }
            tasksScheduled++;
            if (!schedulerStalls) {
                Thread thread = new Thread(task, "mock-velocity-scheduler");
                thread.setDaemon(true);
                thread.start();
            }
            return create(ScheduledTask.class, (t, m, a) -> null);
        });
    });

    /** Adds a backend; {@code online} decides what its ping does. */
    MockProxy server(String name, int port, boolean online) {
        ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", port));
        servers.add(create(RegisteredServer.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getServerInfo":
                    return info;
                case "getPlayersConnected":
                    return players.stream().filter(p -> p.getCurrentServer()
                            .map(c -> c.getServerInfo().getName().equals(name)).orElse(false))
                            .collect(Collectors.toList());
                case "ping":
                    if (online) {
                        return CompletableFuture.completedFuture(new ServerPing(new ServerPing.Version(777, "mock"),
                                new ServerPing.Players(0, 20, Collections.emptyList()), Component.text("mock"), null));
                    }
                    CompletableFuture<ServerPing> refused = new CompletableFuture<>();
                    refused.completeExceptionally(new ConnectException("Connection refused"));
                    return refused;
                default:
                    throw new UnsupportedOperationException("RegisteredServer." + method.getName() + " is not mocked");
            }
        }));
        return this;
    }

    RegisteredServer server(String name) {
        return servers.stream().filter(s -> s.getServerInfo().getName().equals(name)).findFirst().orElseThrow();
    }

    /** Adds a player connected to {@code serverName} ({@code null}: connected to the proxy only). */
    Player player(String name, UUID id, String serverName) {
        Player player = create(Player.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getUsername":
                    return name;
                case "getUniqueId":
                    return id;
                case "getCurrentServer":
                    if (serverName == null) {
                        return Optional.empty();
                    }
                    return Optional.of(create(ServerConnection.class, (c, m, a) -> {
                        switch (m.getName()) {
                            case "getServerInfo":
                                return server(serverName).getServerInfo();
                            case "getServer":
                                return server(serverName);
                            default:
                                throw new UnsupportedOperationException("ServerConnection." + m.getName());
                        }
                    }));
                default:
                    throw new UnsupportedOperationException("Player." + method.getName() + " is not mocked");
            }
        });
        players.add(player);
        return player;
    }

    /** The handler the agent registered for the event class, or fails. */
    EventHandler<Object> handlerFor(Class<?> type) {
        return registrations.stream().filter(r -> r.type == type).map(r -> r.handler).findFirst()
                .orElseThrow(() -> new AssertionError("no handler registered for " + type.getName()));
    }

    @SuppressWarnings("unchecked")
    static <T> T create(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "hashCode":
                        return System.identityHashCode(self);
                    case "equals":
                        return self == args[0];
                    default:
                        return type.getSimpleName() + "@mock";
                }
            }
            return handler.invoke(self, method, args == null ? new Object[0] : args);
        });
    }
}
