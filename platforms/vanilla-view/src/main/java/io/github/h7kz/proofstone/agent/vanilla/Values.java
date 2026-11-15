package io.github.h7kz.proofstone.agent.vanilla;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * One captured value as event.json {@code fieldValue}: JSON primitives as they are, item.json for an item stack,
 * {@code {name}} for a player, and {@code {$type, value}} with {@code toString()} for anything else, as the Bukkit agent
 * records them. A loader that names players some other way (Fabric API's connection callbacks pass the connection) maps
 * that value to a player first and then calls this. Reads only.
 */
public final class Values {

    private Values() {
    }

    public static Object json(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (value instanceof ServerPlayer) {
            return player((ServerPlayer) value);
        }
        if (value instanceof ItemStack) {
            return ItemJson.readOrNull((ItemStack) value);
        }
        return typed(value.getClass().getName(), String.valueOf(value));
    }

    /** {@code {name}}: how a player is told apart in a captured value. */
    public static Map<String, Object> player(ServerPlayer player) {
        return Collections.<String, Object>singletonMap("name", player.getPlainTextName());
    }

    /** {@code {$type, value}}: the fallback for any value the wire has no shape for. */
    public static Map<String, Object> typed(String type, String value) {
        Map<String, Object> typed = new LinkedHashMap<>();
        typed.put("$type", type);
        typed.put("value", value);
        return typed;
    }
}
