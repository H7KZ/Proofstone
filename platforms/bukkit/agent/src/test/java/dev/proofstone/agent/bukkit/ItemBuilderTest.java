package dev.proofstone.agent.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.agent.protocol.AgentException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Key resolution and item validation run without a server: the Bukkit calls used here are static lookups in
 * the API jar. Full item construction (meta, levels) needs a server and is checked by the T-M1-05 replay.
 */
class ItemBuilderTest {

    @Test
    void aKnownMaterialResolvesByItsKey() {
        assertThat(Keys.material("minecraft:diamond_sword")).isEqualTo(Material.DIAMOND_SWORD);
    }

    @Test
    void anUnknownMaterialIsUnknownKeyWithNoItem() {
        AgentException failure = failure(item("minecraft:not_a_thing"));

        assertThat(failure.status()).isEqualTo(400);
        assertThat(failure.code()).isEqualTo("UNKNOWN_KEY");
    }

    @Test
    void aMaterialOutsideTheMinecraftNamespaceOrAsANumericIdIsUnknownKey() {
        assertThat(failure(item("other:diamond_sword")).code()).isEqualTo("UNKNOWN_KEY");
        assertThat(failure(item("minecraft:276")).code()).isEqualTo("UNKNOWN_KEY");
    }

    @Test
    void anUnknownEnchantmentIsUnknownKeyBeforeAnyItemExists() {
        Map<String, Object> item = item("minecraft:diamond_sword");
        item.put("enchants", levels("minecraft:not_real", 1));

        AgentException failure = failure(item);

        assertThat(failure.code()).isEqualTo("UNKNOWN_KEY");
        assertThat(failure.detail()).isEqualTo(Collections.singletonMap("key", "minecraft:not_real"));
    }

    @Test
    void aLevelBelowOneIsInvalidRequestNotAClamp() {
        Map<String, Object> item = item("minecraft:diamond_sword");
        item.put("enchants", levels("minecraft:sharpness", 0));

        assertThat(failure(item).code()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void storedEnchantsNeedAnEnchantedBook() {
        Map<String, Object> item = item("minecraft:diamond_sword");
        item.put("storedEnchants", levels("minecraft:sharpness", 5));

        assertThat(failure(item).code()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void aFieldTheAgentDoesNotBuildIsInvalidRequestNotDropped() {
        Map<String, Object> item = item("minecraft:diamond_sword");
        item.put("pdc", new LinkedHashMap<String, Object>());

        assertThat(failure(item).code()).isEqualTo("INVALID_REQUEST");
    }

    private static Map<String, Object> item(String type) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("amount", 1);
        return item;
    }

    private static Map<String, Object> levels(String key, int level) {
        Map<String, Object> levels = new LinkedHashMap<>();
        levels.put(key, level);
        return levels;
    }

    private static AgentException failure(Map<String, Object> item) {
        try {
            ItemBuilder.build(item);
        } catch (AgentException e) {
            return e;
        }
        throw new AssertionError("expected an AgentException for " + item);
    }
}
