package dev.proofstone.driver.mcpl;

import dev.proofstone.api.ItemView;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;
import org.junit.jupiter.api.Test;

/**
 * Item parsing with constructed components, one test per component kind. The ids below are positions in the lists
 * given to {@link ItemRegistries}, which is how the real ones are resolved; none is a protocol constant. The real
 * server comparison is {@code McplItemsIT}.
 */
class ItemParserTest {

    private static final int SWORD = 2;
    private static final int BOOK = 1;
    private static final ItemRegistries REGISTRIES = registries();
    private static final ItemParser PARSER = new ItemParser(REGISTRIES);

    private static ItemRegistries registries() {
        ItemRegistries registries = new ItemRegistries(List.of("minecraft:air", "minecraft:enchanted_book",
                "minecraft:diamond_sword"));
        registries.record(ItemRegistries.ENCHANTMENT, List.of("minecraft:sharpness", "minecraft:unbreaking",
                "minecraft:mending"));
        return registries;
    }

    private static DataComponents components() {
        return new DataComponents(new HashMap<>());
    }

    private static ItemStack stack(int id, int amount, DataComponents components) {
        return new ItemStack(id, amount, components);
    }

    @Test
    void aStackWithoutComponentsHasTypeAndAmountOnly() {
        ItemView view = PARSER.parse(new ItemStack(SWORD, 3));
        assertThat(view.type()).isEqualTo("minecraft:diamond_sword");
        assertThat(view.amount()).isEqualTo(3);
        assertThat(view.enchants()).isEmpty();
        assertThat(view.storedEnchants()).isEmpty();
        assertThat(view.name()).isNull();
        assertThat(view.lore()).isEmpty();
        assertThat(view.damage()).isZero();
        assertThat(view.repairCost()).isZero();
        assertThat(view.customData()).isNull();
    }

    @Test
    void nullAndZeroAmountAreEmpty() {
        assertThat(PARSER.parse(null)).isSameAs(ItemView.EMPTY);
        assertThat(PARSER.parse(new ItemStack(SWORD, 0))).isSameAs(ItemView.EMPTY);
        assertThat(ItemView.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void aSwordWithTwoEnchantsNameLoreDamageAndRepairCost() {
        DataComponents components = components();
        // Level 255 is what Paper 26.3 stores; the parser never clamps.
        components.put(DataComponentTypes.ENCHANTMENTS, new ItemEnchantments(Map.of(0, 255, 1, 3)));
        components.put(DataComponentTypes.CUSTOM_NAME, Component.text("Blade"));
        components.put(DataComponentTypes.LORE, List.of(Component.text("first"), Component.text("second")));
        components.put(DataComponentTypes.DAMAGE, 12);
        components.put(DataComponentTypes.REPAIR_COST, 7);

        ItemView view = PARSER.parse(stack(SWORD, 1, components));

        assertThat(view.enchants()).containsOnly(Map.entry("minecraft:sharpness", 255),
                Map.entry("minecraft:unbreaking", 3));
        assertThat(view.storedEnchants()).isEmpty();
        assertThat(view.name()).isEqualTo("Blade");
        assertThat(view.lore()).containsExactly("first", "second");
        assertThat(view.damage()).isEqualTo(12);
        assertThat(view.repairCost()).isEqualTo(7);
    }

    @Test
    void anEnchantedBookKeepsStoredEnchantsApartFromEnchants() {
        DataComponents components = components();
        components.put(DataComponentTypes.STORED_ENCHANTMENTS, new ItemEnchantments(Map.of(0, 5, 2, 1)));

        ItemView view = PARSER.parse(stack(BOOK, 1, components));

        assertThat(view.type()).isEqualTo("minecraft:enchanted_book");
        assertThat(view.storedEnchants()).containsOnly(Map.entry("minecraft:sharpness", 5),
                Map.entry("minecraft:mending", 1));
        assertThat(view.enchants()).isEmpty();
    }

    @Test
    void customDataIsKeptAsTheCompound() {
        DataComponents components = components();
        components.put(DataComponentTypes.CUSTOM_DATA, NbtMap.builder().putString("owner", "alex").putInt("n", 4)
                .build());

        ItemView view = PARSER.parse(stack(SWORD, 1, components));

        assertThat(view.customData()).containsOnly(Map.entry("owner", "alex"), Map.entry("n", 4));
    }

    @Test
    void anEnchantIdTheRegistryDoesNotHoldFailsLoudly() {
        DataComponents components = components();
        components.put(DataComponentTypes.ENCHANTMENTS, new ItemEnchantments(Map.of(9, 1)));
        ItemStack bad = stack(SWORD, 1, components);

        assertThatThrownBy(() -> PARSER.parse(bad)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minecraft:enchantment id 9");
    }

    @Test
    void enchantsWithoutAReceivedRegistryFailLoudly() {
        ItemParser bare = new ItemParser(new ItemRegistries(List.of("minecraft:air", "minecraft:stick")));
        DataComponents components = components();
        components.put(DataComponentTypes.ENCHANTMENTS, new ItemEnchantments(Map.of(0, 1)));

        assertThatThrownBy(() -> bare.parse(stack(1, 1, components))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no minecraft:enchantment registry");
    }

    @Test
    void anItemIdOutsideTheRegistryFailsLoudly() {
        assertThatThrownBy(() -> PARSER.parse(new ItemStack(99, 1))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown minecraft:item id 99");
    }

    @Test
    void theBundledItemListMatchesTheProtocolPinAndStartsWithAir() {
        ItemRegistries bundled = ItemRegistries.forProtocol(McplDriver.PROTOCOL_VERSION);
        assertThat(bundled.item(0)).isEqualTo("minecraft:air");
        assertThatThrownBy(() -> ItemRegistries.forProtocol("0.0")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aWindowModelParsesItsSlotsAndCursorOnDemandAndKeepsSlotUpdates() {
        WindowModel window = new WindowModel(1, "minecraft:anvil", "Repair", REGISTRIES);
        DataComponents components = components();
        components.put(DataComponentTypes.CUSTOM_NAME, Component.text("Named"));
        window.setContent(1, new ItemStack[] {stack(SWORD, 1, components), null}, new ItemStack(BOOK, 2));

        assertThat(window.itemViews()).extracting(ItemView::name).containsExactly("Named", null);
        assertThat(window.itemViews().get(1)).isSameAs(ItemView.EMPTY);
        assertThat(window.cursorView().type()).isEqualTo("minecraft:enchanted_book");

        window.setSlot(2, 1, new ItemStack(BOOK, 4));
        window.setCursor(null);
        assertThat(window.itemViews().get(1).amount()).isEqualTo(4);
        assertThat(window.cursorView()).isSameAs(ItemView.EMPTY);
    }

    @Test
    void aWindowWithoutRegistriesRefusesToParse() {
        WindowModel window = new WindowModel(1, "minecraft:anvil", "Repair");
        window.setContent(1, new ItemStack[] {new ItemStack(SWORD, 1)}, null);
        assertThatThrownBy(window::itemViews).isInstanceOf(IllegalStateException.class);
    }
}
