package io.github.h7kz.proofstone.agent.scheduler;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Runs every task on the one server main thread through the Bukkit scheduler. The affinity does not matter on Paper,
 * which has no regions, so {@link #regionTicks()} is absent.
 */
public final class MainThreadScheduler implements Scheduler {

    private final Plugin plugin;

    public MainThreadScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run(Affinity affinity, Runnable task, Consumer<AgentException> dropped) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void repeatGlobally(Runnable task) {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, 1L, 1L);
    }

    @Override
    public Map<String, Long> regionTicks() {
        return null;
    }

    @Override
    public void close() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
