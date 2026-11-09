package io.github.h7kz.proofstone.agent.bungee;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.api.scheduler.TaskScheduler;
import net.md_5.bungee.event.EventBus;
import org.yaml.snakeyaml.Yaml;

/**
 * A BungeeCord proxy as the real API describes it. {@link ProxyServer} is an abstract class, so the mock subclasses it; the
 * rest are the API's interfaces, built with {@link Proxy}. The {@link PluginManager} and its {@link EventBus} are the REAL
 * classes (the manager's four-argument constructor is public), so commands, listeners and event dispatch behave as on
 * BungeeCord, and the tests check the agent against the real dispatch rather than against a guess of it. A call the mock
 * was not told to expect throws, so the agent cannot quietly use API the tests do not cover. Every listener and command
 * registration is recorded for the observe-only test (AGENTS.md section 3).
 */
final class MockProxy extends ProxyServer {

    /** The real event bus, plus a record of every listener registered on it. */
    static final class RecordingBus extends EventBus {

        final List<Object> registered = Collections.synchronizedList(new ArrayList<>());
        final List<Object> unregistered = Collections.synchronizedList(new ArrayList<>());

        RecordingBus(Logger logger) {
            super(logger);
        }

        @Override
        public void register(Object listener) {
            registered.add(listener);
            super.register(listener);
        }

        @Override
        public void unregister(Object listener) {
            unregistered.add(listener);
            super.unregister(listener);
        }
    }

    /** A plugin of the test's own: the owner of the commands the mock proxy has, and of the agent's tasks. */
    static final class HostPlugin extends Plugin {
        HostPlugin(ProxyServer proxy) {
            super(proxy, new PluginDescription());
            // BungeeCord's loader calls the package-private init(proxy, description); the test constructor of this
            // API version does not, so a plugin built here would have no proxy. The same call, by reflection.
            try {
                java.lang.reflect.Method init = Plugin.class.getDeclaredMethod("init", ProxyServer.class,
                        PluginDescription.class);
                init.setAccessible(true);
                PluginDescription description = new PluginDescription();
                description.setName("Host");
                init.invoke(this, proxy, description);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** The proxy's own logger. BungeeCord builds it with a null parent, so the JUL root logger never sees its records. */
    final Logger logger = new Logger("BungeeCord", null) { };
    final RecordingBus bus = new RecordingBus(logger);
    final PluginManager plugins = new PluginManager(this, new Yaml(), bus, null);
    final HostPlugin host;
    final List<String> commandsRun = Collections.synchronizedList(new ArrayList<>());
    final List<ProxiedPlayer> players = new ArrayList<>();
    final Map<String, ServerInfo> servers = new LinkedHashMap<>();
    /** {@code true} makes the scheduler accept tasks and never run them, so a call can only time out. */
    volatile boolean schedulerStalls;
    volatile int tasksScheduled;

    private final TaskScheduler scheduler = create(TaskScheduler.class, (self, method, args) -> {
        if (!"runAsync".equals(method.getName())) {
            throw new UnsupportedOperationException("TaskScheduler." + method.getName() + " is not mocked");
        }
        tasksScheduled++;
        if (!schedulerStalls) {
            Thread thread = new Thread((Runnable) args[1], "mock-bungee-scheduler");
            thread.setDaemon(true);
            thread.start();
        }
        return create(ScheduledTask.class, (t, m, a) -> null);
    });

    MockProxy() {
        logger.setLevel(Level.ALL);
        host = new HostPlugin(this);
        // A command of the proxy's own that prints through the component API, as BungeeCord's do.
        command("version", (sender, args) -> sender.sendMessage(new TextComponent("ran version")));
    }

    /** Registers a proxy command that the unit tests run; it records every call. */
    MockProxy command(String name, BiConsumer<CommandSender, String[]> body) {
        plugins.registerCommand(host, new Command(name) {
            @Override
            public void execute(CommandSender sender, String[] args) {
                commandsRun.add(name + (args.length == 0 ? "" : " " + String.join(" ", args)));
                body.accept(sender, args);
            }
        });
        return this;
    }

    /** Adds a backend; {@code online} decides what its ping does. */
    MockProxy server(String name, int port, boolean online) {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", port);
        servers.put(name, create(ServerInfo.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return name;
                case "getAddress":
                case "getSocketAddress":
                    return address;
                case "getPlayers":
                    return players.stream().filter(p -> p.getServer() != null
                            && p.getServer().getInfo().getName().equals(name)).collect(Collectors.toList());
                case "ping":
                    @SuppressWarnings("unchecked")
                    Callback<ServerPing> callback = (Callback<ServerPing>) args[0];
                    if (online) {
                        callback.done(new ServerPing(new ServerPing.Protocol("mock", 777),
                                new ServerPing.Players(20, 0, null), "mock", (String) null), null);
                    } else {
                        callback.done(null, new ConnectException("Connection refused"));
                    }
                    return null;
                default:
                    throw new UnsupportedOperationException("ServerInfo." + method.getName() + " is not mocked");
            }
        }));
        return this;
    }

    ServerInfo server(String name) {
        return servers.get(name);
    }

    /** Adds a player connected to {@code serverName} ({@code null}: connected to the proxy only). */
    ProxiedPlayer player(String name, UUID id, String serverName) {
        Server connection = serverName == null ? null : create(Server.class, (self, method, args) -> {
            if ("getInfo".equals(method.getName())) {
                return server(serverName);
            }
            throw new UnsupportedOperationException("Server." + method.getName() + " is not mocked");
        });
        ProxiedPlayer player = create(ProxiedPlayer.class, (self, method, args) -> {
            switch (method.getName()) {
                case "getName":
                case "getDisplayName":
                    return name;
                case "getUniqueId":
                    return id;
                case "getServer":
                    return connection;
                default:
                    throw new UnsupportedOperationException("ProxiedPlayer." + method.getName() + " is not mocked");
            }
        });
        players.add(player);
        return player;
    }

    /** The listeners the agent registered on the real bus. */
    List<Object> listeners() {
        return new ArrayList<>(bus.registered);
    }

    // ProxyServer: what the agent reads, and a refusal for everything else.

    @Override
    public String getName() {
        return "BungeeCord";
    }

    @Override
    public String getVersion() {
        return "git:BungeeCord-Bootstrap:1.21-R0.4-SNAPSHOT:mock:2111";
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public Collection<ProxiedPlayer> getPlayers() {
        return new ArrayList<>(players);
    }

    @Override
    public ProxiedPlayer getPlayer(String name) {
        return players.stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
    }

    @Override
    public ProxiedPlayer getPlayer(UUID uuid) {
        return players.stream().filter(p -> p.getUniqueId().equals(uuid)).findFirst().orElse(null);
    }

    @Override
    public Map<String, ServerInfo> getServers() {
        return new LinkedHashMap<>(servers);
    }

    @Override
    public ServerInfo getServerInfo(String name) {
        return servers.get(name);
    }

    @Override
    public PluginManager getPluginManager() {
        return plugins;
    }

    @Override
    public TaskScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public Collection<String> getDisabledCommands() {
        return Collections.emptyList();
    }

    @Override
    public CommandSender getConsole() {
        throw unsupported("getConsole");
    }

    @Override
    public String getTranslation(String name, Object... args) {
        return name;
    }

    @Override
    public net.md_5.bungee.api.config.ConfigurationAdapter getConfigurationAdapter() {
        throw unsupported("getConfigurationAdapter");
    }

    @Override
    public void setConfigurationAdapter(net.md_5.bungee.api.config.ConfigurationAdapter adapter) {
        throw unsupported("setConfigurationAdapter");
    }

    @Override
    public net.md_5.bungee.api.ReconnectHandler getReconnectHandler() {
        throw unsupported("getReconnectHandler");
    }

    @Override
    public void setReconnectHandler(net.md_5.bungee.api.ReconnectHandler handler) {
        throw unsupported("setReconnectHandler");
    }

    @Override
    public void stop() {
        throw unsupported("stop");
    }

    @Override
    public void stop(String reason) {
        throw unsupported("stop");
    }

    @Override
    public void registerChannel(String channel) {
        throw unsupported("registerChannel");
    }

    @Override
    public void unregisterChannel(String channel) {
        throw unsupported("unregisterChannel");
    }

    @Override
    public Collection<String> getChannels() {
        throw unsupported("getChannels");
    }

    @Override
    public String getGameVersion() {
        throw unsupported("getGameVersion");
    }

    @Override
    public int getProtocolVersion() {
        throw unsupported("getProtocolVersion");
    }

    @Override
    public ServerInfo constructServerInfo(String name, InetSocketAddress address, String motd, boolean restricted) {
        throw unsupported("constructServerInfo");
    }

    @Override
    public ServerInfo constructServerInfo(String name, SocketAddress address, String motd, boolean restricted) {
        throw unsupported("constructServerInfo");
    }

    @Override
    public java.io.File getPluginsFolder() {
        throw unsupported("getPluginsFolder");
    }

    @Override
    public int getOnlineCount() {
        throw unsupported("getOnlineCount");
    }

    @Override
    public void broadcast(String message) {
        throw unsupported("broadcast");
    }

    @Override
    public void broadcast(BaseComponent... message) {
        throw unsupported("broadcast");
    }

    @Override
    public void broadcast(BaseComponent message) {
        throw unsupported("broadcast");
    }

    @Override
    public net.md_5.bungee.api.ProxyConfig getConfig() {
        // PluginManager.dispatchCommand asks whether to log the command; the real proxy's answer is its log_commands setting.
        return config;
    }

    private final net.md_5.bungee.api.ProxyConfig config = create(net.md_5.bungee.api.ProxyConfig.class,
            (self, method, args) -> {
                if ("isLogCommands".equals(method.getName())) {
                    return false;
                }
                throw new UnsupportedOperationException("ProxyConfig." + method.getName() + " is not mocked");
            });

    @Override
    public Collection<ProxiedPlayer> matchPlayer(String partial) {
        throw unsupported("matchPlayer");
    }

    @Override
    public net.md_5.bungee.api.Title createTitle() {
        throw unsupported("createTitle");
    }

    @Override
    public ProxyServer.Unsafe unsafe() {
        throw unsupported("unsafe");
    }

    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException("ProxyServer." + method + " is not mocked");
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
