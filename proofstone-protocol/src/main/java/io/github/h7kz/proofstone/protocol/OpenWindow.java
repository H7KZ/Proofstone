package io.github.h7kz.proofstone.protocol;

import java.util.List;

/** The inventory a player has open. A {@code null} slot is an empty slot. */
public record OpenWindow(String type, String title, List<Item> slots) {
}
