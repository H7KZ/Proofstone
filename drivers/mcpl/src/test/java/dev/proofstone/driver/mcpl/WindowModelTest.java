package dev.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.proofstone.api.Click;
import dev.proofstone.core.driver.SlotStack;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.DropItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
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
    void everyClickMapsToAPacketWithNoPredictedChanges() {
        for (Click click : Click.values()) {
            var packet = McplSession.clickPacket(4, 11, 2, click);
            assertThat(packet.getContainerId()).isEqualTo(4);
            assertThat(packet.getStateId()).isEqualTo(11);
            assertThat(packet.getSlot()).isEqualTo(2);
            assertThat(packet.getChangedSlots()).isEmpty();
            assertThat(packet.getCarriedItem()).as("an unknown carried item forces a full resync").isNotNull();
        }
        assertThat(McplSession.clickPacket(1, 0, 0, Click.number(3)).getParam()).isEqualTo(MoveToHotbarAction.SLOT_3);
        assertThat(McplSession.clickPacket(1, 0, 0, Click.SWAP_OFFHAND).getParam())
                .isEqualTo(MoveToHotbarAction.OFF_HAND);
        assertThat(McplSession.clickPacket(1, 0, 0, Click.RIGHT).getParam()).isEqualTo(ClickItemAction.RIGHT_CLICK);
        assertThat(McplSession.clickPacket(1, 0, 0, Click.CTRL_DROP).getParam())
                .isEqualTo(DropItemAction.DROP_SELECTED_STACK);
    }

    @Test
    void closingAContainerHandsItsPlayerSlotsToWindowZero() {
        WindowModel inventory = new WindowModel(0, "minecraft:inventory", "Inventory");
        inventory.setContent(1, new ItemStack[46], null);
        ItemStack[] chestSlots = new ItemStack[3 + 36];
        chestSlots[3 + 5] = new ItemStack(7, 2);
        WindowModel chest = new WindowModel(2, "minecraft:generic_9x1", "Chest");
        chest.setContent(1, chestSlots, null);

        assertThat(inventory.adoptPlayerSlots(chest.slots())).isTrue();
        assertThat(inventory.slots().get(9 + 5)).isEqualTo(new SlotStack(7, 2));
        assertThat(inventory.slots()).hasSize(46);
        assertThat(new WindowModel(0, "minecraft:inventory", "Inventory").adoptPlayerSlots(chest.slots()))
                .as("unsynced window 0 is not invented").isFalse();
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
