package dev.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.core.driver.SlotStack;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.junit.jupiter.api.Test;

class WindowModelTest {

    @Test
    void contentStoresStateIdSlotsAndCursorAndSlotUpdatesOnlyTouchTheirWindow() {
        WindowModel inventory = new WindowModel(0, "minecraft:inventory", "Inventory");
        WindowModel chest = new WindowModel(3, "minecraft:generic_9x1", "Chest");

        chest.setContent(7, new ItemStack[] {new ItemStack(5, 2), null}, new ItemStack(9, 1));
        assertThat(chest.claim()).isTrue();
        assertThat(chest.claim()).as("handed out once").isFalse();
        assertThat(chest.stateId()).isEqualTo(7);
        assertThat(chest.slots()).containsExactly(new SlotStack(5, 2), SlotStack.EMPTY);
        assertThat(chest.cursor()).isEqualTo(new SlotStack(9, 1));

        assertThat(chest.setSlot(8, 1, new ItemStack(6, 4))).isTrue();
        assertThat(chest.stateId()).isEqualTo(8);
        assertThat(chest.slots().get(1)).isEqualTo(new SlotStack(6, 4));
        assertThat(inventory.slots()).isEmpty();
        assertThat(inventory.stateId()).isZero();
    }

    @Test
    void aSlotBeforeContentOrOutOfRangeIsRefusedNotInvented() {
        WindowModel window = new WindowModel(1, "minecraft:anvil", "Repair");
        assertThat(window.setSlot(1, 0, new ItemStack(1, 1))).isFalse();
        assertThat(window.claim()).as("unsynced window is not handed out").isFalse();
        window.setContent(1, new ItemStack[1], null);
        assertThat(window.setSlot(2, 5, new ItemStack(1, 1))).isFalse();
    }

    @Test
    void everyMenuTypeHasANamespacedKey() {
        for (ContainerType type : ContainerType.values()) {
            assertThat(McplSession.namespaced(type)).startsWith("minecraft:").doesNotContain(" ");
        }
        assertThat(McplSession.namespaced(ContainerType.GENERIC_9X3)).isEqualTo("minecraft:generic_9x3");
        assertThat(McplSession.namespaced(ContainerType.CARTOGRAPHY)).isEqualTo("minecraft:cartography_table");
    }
}
