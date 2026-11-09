package io.github.h7kz.proofstone.agent.velocity;

import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers each player's previous server (proxy.json {@code previousServer}), which Velocity only reports inside
 * {@link ServerConnectedEvent}. Two read-only handlers at the last priority: they record and return, and they neither
 * change the event nor wait.
 */
final class PreviousServers {

    private final EventHandler<ServerConnectedEvent> connected;
    private final EventHandler<DisconnectEvent> left;
    /** Only players that switched have an entry: a first join has no previous server. */
    private final Map<UUID, String> previous = new ConcurrentHashMap<>();

    private PreviousServers() {
        this.connected = event -> {
            String name = event.getPreviousServer().map(server -> server.getServerInfo().getName()).orElse(null);
            if (name == null) {
                previous.remove(event.getPlayer().getUniqueId());
            } else {
                previous.put(event.getPlayer().getUniqueId(), name);
            }
        };
        this.left = event -> previous.remove(event.getPlayer().getUniqueId());
    }

    static PreviousServers track(ProxyServer proxy, Object plugin) {
        PreviousServers tracker = new PreviousServers();
        proxy.getEventManager().register(plugin, ServerConnectedEvent.class, PostOrder.LAST, tracker.connected);
        proxy.getEventManager().register(plugin, DisconnectEvent.class, PostOrder.LAST, tracker.left);
        return tracker;
    }

    /** The name of the server the player switched from, or {@code null} until the player has switched. */
    String of(UUID player) {
        return previous.get(player);
    }

    void close() {
        // The handlers belong to the plugin, which Velocity drops with it. Clearing keeps a stale name from surviving.
        previous.clear();
    }
}
