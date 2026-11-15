package io.github.h7kz.proofstone.agent.fabric;

import io.github.h7kz.proofstone.agent.vanilla.Values;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * One captured value as event.json {@code fieldValue}: the shared {@link Values} rules, plus a player's connection, which
 * is how Fabric API's connection callbacks name the player. Reads only.
 */
final class FabricValues {

    private FabricValues() {
    }

    static Object json(Object value) {
        if (value instanceof ServerGamePacketListenerImpl) {
            return Values.player(((ServerGamePacketListenerImpl) value).getPlayer());
        }
        return Values.json(value);
    }
}
