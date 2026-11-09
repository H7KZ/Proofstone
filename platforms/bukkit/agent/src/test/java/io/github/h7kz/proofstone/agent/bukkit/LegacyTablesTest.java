package io.github.h7kz.proofstone.agent.bukkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * The pre-1.13 name and block state tables (T-M3-06). The unit tests run against the 1.8.8 API, which has no
 * NamespacedKey, so {@link Keys} is in legacy mode here, exactly as on a 1.8.8 to 1.12.2 server.
 */
class LegacyTablesTest {

    @Test
    void materialsWhoseKeyDiffersFromTheEnumNameMapBothWays() {
        assertThat(Keys.materialKey(Material.GOLD_SWORD)).isEqualTo("golden_sword");
        assertThat(Keys.materialKey(Material.IRON_SPADE)).isEqualTo("iron_shovel");
        assertThat(Keys.material("minecraft:golden_sword")).isEqualTo(Material.GOLD_SWORD);
        assertThat(Keys.material("minecraft:iron_shovel")).isEqualTo(Material.IRON_SPADE);
    }

    @Test
    void aMaterialWhoseKeyEqualsItsLowerCaseNameNeedsNoRow() {
        assertThat(Keys.materialKey(Material.DIAMOND_SWORD)).isEqualTo("diamond_sword");
        assertThat(Keys.material("minecraft:diamond_sword")).isEqualTo(Material.DIAMOND_SWORD);
    }

    @Test
    void aLegacyEnumNameThatIsNotTheKeyIsUnknownKey() {
        // GOLD_SWORD is the 1.12 enum name; the 1.13+ key is golden_sword, so a test written for one version
        // must not pass by luck on the other.
        assertThatThrownBy(() -> Keys.material("minecraft:gold_sword"))
                .isInstanceOfSatisfying(AgentException.class, e -> assertThat(e.code()).isEqualTo("UNKNOWN_KEY"));
    }

    @Test
    void everyEnchantmentRowResolvesBothWays() {
        for (Map.Entry<String, String> row : LegacyNames.enchantments().entrySet()) {
            assertThat(LegacyNames.enchantmentName(row.getValue())).isEqualTo(row.getKey());
            assertThat(LegacyNames.enchantmentKey(row.getKey())).isEqualTo(row.getValue());
        }
        assertThat(LegacyNames.enchantmentKey("DAMAGE_ALL")).isEqualTo("sharpness");
        assertThat(LegacyNames.enchantmentKey("MENDING")).isEqualTo("mending");
    }

    @Test
    void everyMaterialRowNamesARealMaterialOnThe188Api() {
        for (String name : LegacyNames.materials().keySet()) {
            assertThat(Material.getMaterial(name)).as(name).isNotNull();
        }
    }

    @Test
    void anAnvilFacingIsTheLowTwoBitsOfItsDataValue() {
        assertThat(LegacyBlocks.data("ANVIL", state("facing", "west"), 0)).isEqualTo(1);
        assertThat(LegacyBlocks.data("ANVIL", state("facing", "north"), 0)).isEqualTo(2);
        // A damaged anvil keeps its damage bits.
        assertThat(LegacyBlocks.data("ANVIL", state("facing", "east"), 4)).isEqualTo(7);
        assertThat(LegacyBlocks.state("ANVIL", 1)).isEqualTo(state("facing", "west"));
    }

    @Test
    void aStateTheTableDoesNotHaveIsUnknownKeyNotDropped() {
        assertThatThrownBy(() -> LegacyBlocks.data("ANVIL", state("facing", "up"), 0))
                .isInstanceOf(AgentException.class);
        assertThatThrownBy(() -> LegacyBlocks.data("ANVIL", state("waterlogged", "true"), 0))
                .isInstanceOf(AgentException.class);
        assertThatThrownBy(() -> LegacyBlocks.data("STONE", state("facing", "west"), 0))
                .isInstanceOf(AgentException.class);
    }

    @Test
    void aBlockWithoutRowsReadsAsAnEmptyState() {
        assertThat(LegacyBlocks.state("STONE", 0)).isEqualTo(Collections.<String, String>emptyMap());
    }

    private static Map<String, String> state(String key, String value) {
        Map<String, String> state = new TreeMap<>();
        state.put(key, value);
        return state;
    }
}
