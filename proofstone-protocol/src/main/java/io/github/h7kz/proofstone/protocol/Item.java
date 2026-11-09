package io.github.h7kz.proofstone.protocol;

import java.util.List;
import java.util.Map;

/**
 * Wire form of an item (test-api.md §6). Enchantment levels are carried as given and never clamped.
 * Optional fields are {@code null} when absent.
 */
public record Item(
        String type,
        int amount,
        Map<String, Integer> enchants,
        Map<String, Integer> storedEnchants,
        String name,
        List<String> lore,
        Integer damage,
        Integer repairCost,
        Map<String, Object> pdc,
        String raw) {
}
