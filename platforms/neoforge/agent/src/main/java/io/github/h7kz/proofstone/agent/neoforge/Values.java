package io.github.h7kz.proofstone.agent.neoforge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * One captured value as event.json {@code fieldValue}: JSON primitives as they are, item.json for an item stack,
 * {@code {name}} for a player (NeoForge's player events return the player from {@code getEntity}), and
 * {@code {$type, value}} with {@code toString()} for anything else, as the Bukkit agent records them. Reads only.
 */
final class Values {

    private Values() {
    }

    static Object json(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (value instanceof ServerPlayer) {
            return Collections.<String, Object>singletonMap("name", ((ServerPlayer) value).getPlainTextName());
        }
        if (value instanceof ItemStack) {
            return ItemJson.readOrNull((ItemStack) value);
        }
        return typed(value.getClass().getName(), String.valueOf(value));
    }

    static Map<String, Object> typed(String type, String value) {
        Map<String, Object> typed = new LinkedHashMap<>();
        typed.put("$type", type);
        typed.put("value", value);
        return typed;
    }
}
