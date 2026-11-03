package dev.proofstone.protocol;

import java.util.Map;

/**
 * Reply of {@code GET /v1/players/{name}} (spec §3). {@code inventory} maps the Bukkit raw player-inventory
 * slot index, as a string, to the item in it. Empty slots are absent.
 */
public record PlayerState(
        String name,
        String uuid,
        int level,
        double exp,
        String gamemode,
        double health,
        Location location,
        Map<String, Item> inventory,
        Item cursor,
        OpenWindow openWindow) {
}
