package dev.proofstone.driver.mineflayer;

import com.fasterxml.jackson.databind.JsonNode;
import dev.proofstone.api.ItemView;
import dev.proofstone.core.driver.SlotStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the sidecar's stack JSON into the client-side views (T-M4-05b). The sidecar resolves registry ids to names
 * with minecraft-data; this class only adds the namespace the API uses. An empty stack is {@link ItemView#EMPTY}.
 */
final class MineflayerRefItems {

    private MineflayerRefItems() {
    }

    static ItemView view(JsonNode stack) {
        int count = stack.path("count").asInt();
        if (count <= 0) {
            return ItemView.EMPTY;
        }
        List<String> lore = new ArrayList<>();
        for (JsonNode line : stack.path("lore")) {
            lore.add(line.asText());
        }
        String name = stack.hasNonNull("name") ? stack.get("name").asText() : null;
        return new ItemView("minecraft:" + stack.path("type").asText(), count, enchants(stack.path("enchants")),
                enchants(stack.path("storedEnchants")), name, lore, stack.path("damage").asInt(),
                stack.path("repairCost").asInt(), null);
    }

    static SlotStack slot(JsonNode stack) {
        return new SlotStack(stack.path("itemId").asInt(), stack.path("count").asInt());
    }

    private static Map<String, Integer> enchants(JsonNode object) {
        Map<String, Integer> out = new LinkedHashMap<>();
        object.fields().forEachRemaining(e -> out.put("minecraft:" + e.getKey(), e.getValue().asInt()));
        return out;
    }
}
