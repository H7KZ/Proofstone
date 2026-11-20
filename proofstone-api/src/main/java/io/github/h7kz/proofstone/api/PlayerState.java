package io.github.h7kz.proofstone.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A snapshot of a player as the server reports it (test-api spec section 3). {@code inventory} maps the server's raw
 * player-inventory slot (0 to 8 hotbar, 9 to 35 main, 36 to 39 armour, 40 off hand) to its item; empty slots are
 * absent. {@code cursor} is {@link ItemView#EMPTY} when nothing is carried. {@code openWindow} is the container the
 * server says the player has open, empty when only the player's own inventory is (T-M11-02).
 */
public record PlayerState(String name, int level, double exp, String gamemode, double health,
        Map<Integer, ItemView> inventory, ItemView cursor, Optional<ServerWindow> openWindow) {

    public PlayerState {
        inventory = Map.copyOf(inventory);
        Objects.requireNonNull(openWindow, "openWindow");
    }

    /** A state with no open window; kept so the constructor of v0 still compiles. */
    public PlayerState(String name, int level, double exp, String gamemode, double health,
            Map<Integer, ItemView> inventory, ItemView cursor) {
        this(name, level, exp, gamemode, health, inventory, cursor, Optional.empty());
    }
}
