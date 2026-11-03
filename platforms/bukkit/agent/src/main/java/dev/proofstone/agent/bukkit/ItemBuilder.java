package dev.proofstone.agent.bukkit;

import dev.proofstone.agent.game.Fields;
import dev.proofstone.agent.protocol.AgentException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds an Item (test API section 6, item.json) through the Bukkit API. Every key is resolved and every field
 * checked before the stack exists, so UNKNOWN_KEY never leaves a partial item (spec section 2). Levels pass
 * through unchanged: they are never clamped.
 */
final class ItemBuilder {

    private static final List<String> FIELDS = Arrays.asList(
            "type", "amount", "name", "lore", "damage", "enchants", "storedEnchants", "raw");

    private ItemBuilder() {
    }

    static ItemStack build(Map<String, Object> item) {
        Fields.onlyFields(item, FIELDS, "item");
        Material material = Keys.material(Fields.text(item, "type"));
        Integer amount = Fields.integer(item, "amount");
        if (amount == null || amount < 1) {
            throw Fields.invalid("\"amount\" must be a positive integer");
        }
        Integer damage = Fields.integer(item, "damage");
        if (damage != null && (damage < 0 || damage > Short.MAX_VALUE)) {
            throw Fields.invalid("\"damage\" is out of range");
        }
        String name = Fields.optionalText(item, "name");
        List<String> lore = lore(item);
        Map<String, Object> storedKeys = Fields.optionalObject(item, "storedEnchants");
        if (storedKeys != null && !storedKeys.isEmpty() && material != Material.ENCHANTED_BOOK) {
            throw Fields.invalid("\"storedEnchants\" needs an enchanted book");
        }
        Map<Enchantment, Integer> enchants = levels(item, "enchants");
        Map<Enchantment, Integer> stored = levels(item, "storedEnchants");

        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            throw new AgentException(500, "INTERNAL", "no item meta for " + material, null);
        }
        if (name != null) {
            meta.setDisplayName(name);
        }
        if (lore != null) {
            meta.setLore(lore);
        }
        if (!stored.isEmpty()) {
            EnchantmentStorageMeta book = (EnchantmentStorageMeta) meta;
            for (Map.Entry<Enchantment, Integer> level : stored.entrySet()) {
                // Ignore the vanilla maximum: the level is what the test asked for.
                book.addStoredEnchant(level.getKey(), level.getValue(), true);
            }
        }
        stack.setItemMeta(meta);
        if (damage != null) {
            stack.setDurability(damage.shortValue());
        }
        for (Map.Entry<Enchantment, Integer> level : enchants.entrySet()) {
            stack.addUnsafeEnchantment(level.getKey(), level.getValue());
        }
        return stack;
    }

    private static List<String> lore(Map<String, Object> item) {
        Object value = item.get("lore");
        if (value == null) {
            return null;
        }
        if (!(value instanceof List)) {
            throw Fields.invalid("\"lore\" must be an array of strings");
        }
        List<String> lines = new ArrayList<>();
        for (Object line : (List<?>) value) {
            if (!(line instanceof String)) {
                throw Fields.invalid("\"lore\" must be an array of strings");
            }
            lines.add((String) line);
        }
        return lines;
    }

    /** Checks each level is an integer of at least 1, then resolves its key (UNKNOWN_KEY). */
    private static Map<Enchantment, Integer> levels(Map<String, Object> item, String field) {
        Map<String, Object> keys = Fields.optionalObject(item, field);
        if (keys == null) {
            return Collections.emptyMap();
        }
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : keys.entrySet()) {
            Object level = entry.getValue();
            if (!(level instanceof Integer) || (Integer) level < 1) {
                throw Fields.invalid("level of " + entry.getKey() + " must be an integer of at least 1");
            }
            out.put(Keys.enchantment(entry.getKey()), (Integer) level);
        }
        return out;
    }
}
