package io.github.h7kz.proofstone.api;

import java.util.List;

/**
 * The container window a player has open, as the server reports it (test-api spec section 3, T-M11-01 follow-up
 * T-M11-02): the server-side counterpart of {@link Window}, which is the client's view. {@code type} is the
 * platform's name for the container as the agent reports it (the Bukkit family sends the lower-cased
 * {@code InventoryType}, such as {@code chest} or {@code anvil}), so it is not the client's namespaced
 * {@code minecraft:generic_9x3}. {@code title} is the window title as the server holds it. {@code slots} are the
 * container's own slots in server order, without the player's 36; an empty slot is {@link ItemView#EMPTY}.
 */
public record ServerWindow(String type, String title, List<ItemView> slots) {

    public ServerWindow {
        slots = List.copyOf(slots);
    }
}
