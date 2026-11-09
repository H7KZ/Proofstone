package io.github.h7kz.proofstone.api;

import java.util.Map;

/**
 * A snapshot of a player as the server reports it (test-api spec section 3). {@code inventory} maps the server's raw
 * player-inventory slot (0 to 8 hotbar, 9 to 35 main, 36 to 39 armour, 40 off hand) to its item; empty slots are
 * absent. {@code cursor} is {@link ItemView#EMPTY} when nothing is carried. {@code openWindow} is not in v0 yet.
 */
public record PlayerState(String name, int level, double exp, String gamemode, double health,
        Map<Integer, ItemView> inventory, ItemView cursor) {

    public PlayerState {
        inventory = Map.copyOf(inventory);
    }
}
