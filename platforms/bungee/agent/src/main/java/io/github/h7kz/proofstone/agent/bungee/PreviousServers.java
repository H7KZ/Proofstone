package io.github.h7kz.proofstone.agent.bungee;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

/**
 * Remembers each player's previous server (proxy.json {@code previousServer}), which BungeeCord reports only inside
 * {@link ServerSwitchEvent#getFrom()}. Two read-only handlers at {@link EventPriority#HIGHEST}, the last to run on
 * BungeeCord (a higher number runs later, the reverse of the Bukkit names): they record and return, and they neither
 * change the event nor wait. Public with public handlers because BungeeCord's event bus invokes them by reflection.
 */
public final class PreviousServers implements Listener {

    /** Only players that switched have an entry: a first join has no previous server. */
    private final Map<UUID, String> previous = new ConcurrentHashMap<>();

    static PreviousServers track(Plugin plugin) {
        PreviousServers tracker = new PreviousServers();
        plugin.getProxy().getPluginManager().registerListener(plugin, tracker);
        return tracker;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwitch(ServerSwitchEvent event) {
        ServerInfo from = event.getFrom();
        if (from == null) {
            previous.remove(event.getPlayer().getUniqueId());
        } else {
            previous.put(event.getPlayer().getUniqueId(), from.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeave(PlayerDisconnectEvent event) {
        previous.remove(event.getPlayer().getUniqueId());
    }

    /** The name of the server the player switched from, or {@code null} until the player has switched. */
    String of(UUID player) {
        return previous.get(player);
    }

    void close(Plugin plugin) {
        plugin.getProxy().getPluginManager().unregisterListener(this);
        // Clearing keeps a stale name from surviving.
        previous.clear();
    }
}
