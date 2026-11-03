package dev.proofstone.driver.mcpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;

/**
 * Typed reads of one stack's component patch. A stack carries a patch over its item's defaults, so an absent
 * component, and a removed one (the server sends it as null), both read as "not set".
 */
final class ComponentReader {

    private final DataComponents components;
    private final ItemRegistries registries;

    /** @param components the stack's patch; {@code null} when the stack has no components at all */
    ComponentReader(DataComponents components, ItemRegistries registries) {
        this.components = components;
        this.registries = registries;
    }

    <T> T get(DataComponentType<T> type) {
        return components == null ? null : components.get(type);
    }

    int intOr(DataComponentType<Integer> type, int fallback) {
        Integer value = get(type);
        return value == null ? fallback : value;
    }

    /** Plain text of a text component, or {@code null} when the component is not set. */
    String text(DataComponentType<Component> type) {
        Component value = get(type);
        return value == null ? null : McplSession.flatten(value);
    }

    List<String> textLines(DataComponentType<List<Component>> type) {
        List<Component> value = get(type);
        List<String> out = new ArrayList<>();
        if (value != null) {
            for (Component line : value) {
                out.add(McplSession.flatten(line));
            }
        }
        return out;
    }

    /** Enchantment key to level; the ids come from the configuration-phase registry. Levels pass unchanged. */
    Map<String, Integer> enchantments(DataComponentType<ItemEnchantments> type) {
        ItemEnchantments value = get(type);
        Map<String, Integer> out = new LinkedHashMap<>();
        if (value != null) {
            for (Map.Entry<Integer, Integer> entry : value.getEnchantments().entrySet()) {
                out.put(registries.enchantment(entry.getKey()), entry.getValue());
            }
        }
        return out;
    }
}
