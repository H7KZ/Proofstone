package io.github.h7kz.proofstone.agent.bukkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Player teleports for the agent. Folia refuses a synchronous teleport on a region thread ("Must use teleportAsync
 * while in region threading", docs/platforms/folia.md), so on Folia the async form is used and its result is returned
 * for the caller to wait on. Paper keeps its synchronous teleport, unchanged. {@code teleportAsync} is reached through
 * reflection, because the agent compiles against the Bukkit 1.8.8 API.
 */
final class Teleports {

    /** {@code Player.teleportAsync(Location)}, or {@code null} when the teleport is synchronous (every platform but Folia). */
    private final Method teleportAsync;

    Teleports(boolean regional) {
        this.teleportAsync = regional ? findTeleportAsync() : null;
    }

    /** The teleport's result. On Folia the player has not moved yet until the future completes. */
    @SuppressWarnings("unchecked")
    CompletableFuture<Boolean> teleport(Player player, Location location) {
        if (teleportAsync == null) {
            return CompletableFuture.completedFuture(player.teleport(location));
        }
        try {
            return (CompletableFuture<Boolean>) teleportAsync.invoke(player, location);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot call teleportAsync", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("teleportAsync failed: " + e.getCause(), e.getCause());
        }
    }

    private static Method findTeleportAsync() {
        try {
            return Player.class.getMethod("teleportAsync", Location.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Folia must offer Player.teleportAsync: " + e, e);
        }
    }
}
