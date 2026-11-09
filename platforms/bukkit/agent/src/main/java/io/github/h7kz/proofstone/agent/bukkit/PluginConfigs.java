package io.github.h7kz.proofstone.agent.bukkit;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Config read, patch, snapshot, restore and reload for a plugin under test (spec §2, ADR-0002). Callers run these on
 * the main thread, through {@link BukkitGame}.
 *
 * <p>The snapshot is the {@code config.yml} bytes, taken before the first patch. Restore writes them back and then
 * calls {@code reloadConfig()}, which rebuilds the in-memory config from the file, so a separate copy of
 * {@code getConfig()} would add nothing. The whole-server {@code /reload} is never used: one plugin is reloaded by its
 * declared hook, or by disabling and enabling it.
 */
final class PluginConfigs {

    private static final Pattern PATH = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*");
    private static final String CONFIG_FILE = "config.yml";

    /** Snapshots per plugin name, held until the next restore. */
    private final Map<String, Snapshot> snapshots = new HashMap<>();

    /** The value at {@code path}, as JSON-ready values. Throws {@code CONFIG_PATH_NOT_FOUND} when there is none. */
    Object read(String pluginId, String path) {
        Plugin plugin = requirePlugin(pluginId);
        requirePath(path);
        Object value = plugin.getConfig().get(path);
        if (value == null) {
            throw new AgentException(404, "CONFIG_PATH_NOT_FOUND", "no config value at " + path,
                    Collections.singletonMap("path", path));
        }
        return plain(value);
    }

    /** Snapshots the file on the first patch, then sets the value and saves it. */
    void patch(String pluginId, String path, Object value) {
        Plugin plugin = requirePlugin(pluginId);
        requirePath(path);
        if (!snapshots.containsKey(plugin.getName())) {
            snapshots.put(plugin.getName(), takeSnapshot(plugin));
        }
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }

    /**
     * Writes the snapshot back, reloads the config and forgets the snapshot. No-op without one. It does not disable
     * and enable the plugin: Paper closes a disabled plugin's classloader, so a real plugin that loads classes lazily
     * (Disenchantment, T-M2-10) fails to enable again. A patch never reloads the plugin either, so the two stay
     * symmetric. A plugin that caches config in {@code onEnable} needs a reload hook (spec §2, reload).
     */
    void restore(String pluginId) {
        Plugin plugin = requirePlugin(pluginId);
        Snapshot snapshot = snapshots.get(plugin.getName());
        if (snapshot == null) {
            return;
        }
        writeSnapshot(plugin, snapshot);
        snapshots.remove(plugin.getName());
        plugin.reloadConfig();
    }

    /** Runs the declared hook when one is given (an unknown command is an error), else disables and enables. */
    void reload(String pluginId, String hookCommand) {
        Plugin plugin = requirePlugin(pluginId);
        if (hookCommand == null) {
            disableAndEnable(plugin);
            return;
        }
        boolean handled;
        try {
            handled = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), hookCommand);
        } catch (CommandException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new AgentException(500, "INTERNAL", "reload hook failed: " + cause, null);
        }
        if (!handled) {
            throw new AgentException(400, "INVALID_REQUEST", "reload hook is not a registered command: " + hookCommand,
                    Collections.singletonMap("proofstone-reload-command", hookCommand));
        }
    }

    private static Plugin requirePlugin(String id) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(id);
        if (plugin == null) {
            throw new AgentException(404, "PLUGIN_NOT_FOUND", "no plugin named " + id,
                    Collections.singletonMap("id", id));
        }
        return plugin;
    }

    private static void requirePath(String path) {
        if (!PATH.matcher(path).matches()) {
            throw new AgentException(400, "INVALID_REQUEST", "\"path\" must be dotted config keys: " + path, null);
        }
    }

    private static void disableAndEnable(Plugin plugin) {
        PluginManager manager = Bukkit.getPluginManager();
        manager.disablePlugin(plugin);
        manager.enablePlugin(plugin);
        if (!plugin.isEnabled()) {
            throw new AgentException(500, "INTERNAL", plugin.getName() + " did not enable after reload", null);
        }
    }

    private static Snapshot takeSnapshot(Plugin plugin) {
        File file = configFile(plugin);
        if (!file.isFile()) {
            return new Snapshot(null);
        }
        try {
            return new Snapshot(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            throw new AgentException(500, "INTERNAL", "cannot read " + file + ": " + e, null);
        }
    }

    private static void writeSnapshot(Plugin plugin, Snapshot snapshot) {
        File file = configFile(plugin);
        try {
            if (snapshot.bytes == null) {
                Files.deleteIfExists(file.toPath());
            } else {
                Files.write(file.toPath(), snapshot.bytes);
            }
        } catch (IOException e) {
            throw new AgentException(500, "INTERNAL", "cannot restore " + file + ": " + e, null);
        }
    }

    private static File configFile(Plugin plugin) {
        return new File(plugin.getDataFolder(), CONFIG_FILE);
    }

    /** YAML sections become maps and lists recurse, so the reply is JSON-ready (spec §2 config value). */
    static Object plain(Object value) {
        if (value instanceof ConfigurationSection) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((ConfigurationSection) value).getValues(false).entrySet()) {
                out.put(entry.getKey(), plain(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List) {
            List<Object> out = new ArrayList<>();
            for (Object item : (List<?>) value) {
                out.add(plain(item));
            }
            return out;
        }
        return value;
    }

    /** The file bytes before the first patch. {@code bytes} is null when there was no file. */
    private static final class Snapshot {

        final byte[] bytes;

        Snapshot(byte[] bytes) {
            this.bytes = bytes;
        }
    }
}
