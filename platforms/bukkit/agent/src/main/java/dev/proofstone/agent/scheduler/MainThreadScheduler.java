package dev.proofstone.agent.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Runs tasks on the server main thread through the Bukkit scheduler. */
public final class MainThreadScheduler implements Scheduler {

    private final Plugin plugin;

    public MainThreadScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
