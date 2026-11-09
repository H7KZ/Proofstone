package io.github.h7kz.proofstone.driver.mcpl;

import io.github.h7kz.proofstone.api.ItemView;
import java.util.Map;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;

/** Turns a stack from the newest protocol into an {@link ItemView}. NBT-era items are out of scope (ADR-0004). */
final class ItemParser {

    private final ItemRegistries registries;

    ItemParser(ItemRegistries registries) {
        this.registries = registries;
    }

    ItemView parse(ItemStack stack) {
        // MCProtocolLib sends null for an empty slot.
        if (stack == null || stack.getAmount() <= 0) {
            return ItemView.EMPTY;
        }
        ComponentReader components = new ComponentReader(stack.getDataComponentsPatch(), registries);
        Map<String, Object> custom = components.get(DataComponentTypes.CUSTOM_DATA);
        return new ItemView(
                registries.item(stack.getId()),
                stack.getAmount(),
                components.enchantments(DataComponentTypes.ENCHANTMENTS),
                components.enchantments(DataComponentTypes.STORED_ENCHANTMENTS),
                components.text(DataComponentTypes.CUSTOM_NAME),
                components.textLines(DataComponentTypes.LORE),
                components.intOr(DataComponentTypes.DAMAGE, 0),
                components.intOr(DataComponentTypes.REPAIR_COST, 0),
                custom);
    }
}
