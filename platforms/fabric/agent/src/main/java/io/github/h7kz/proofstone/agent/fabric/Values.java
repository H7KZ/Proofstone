package io.github.h7kz.proofstone.agent.fabric;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;

/**
 * One captured value as event.json {@code fieldValue}: JSON primitives as they are, item.json for an item stack,
 * {@code {name}} for a player (and for a player's connection, which is how Fabric API's connection callbacks name the
 * player), and {@code {$type, value}} with {@code toString()} for anything else. Reads only.
 */
final class Values {

    private Values() {
    }

    static Object json(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (value instanceof ServerPlayer) {
            return player((ServerPlayer) value);
        }
        if (value instanceof ServerGamePacketListenerImpl) {
            return player(((ServerGamePacketListenerImpl) value).getPlayer());
        }
        if (value instanceof ItemStack) {
            return ItemJson.readOrNull((ItemStack) value);
        }
        Map<String, Object> typed = new LinkedHashMap<>();
        typed.put("$type", value.getClass().getName());
        typed.put("value", String.valueOf(value));
        return typed;
    }

    private static Map<String, Object> player(ServerPlayer player) {
        return Collections.<String, Object>singletonMap("name", player.getPlainTextName());
    }
}
