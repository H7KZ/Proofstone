package io.github.h7kz.proofstone.agent.neoforge;

import io.github.h7kz.proofstone.agent.game.Fields;
import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * item.json on the game's own item stacks (test API section 6), with the same fields and the same omissions as the
 * Bukkit agent, so both oracles read one shape. Levels pass through: the agent never clamps (the server may).
 * Enchantments are data-driven since 1.21, so they resolve through the server's registries.
 */
final class ItemJson {

    private static final List<String> FIELDS = Arrays.asList(
            "type", "amount", "name", "lore", "damage", "enchants", "storedEnchants", "raw");

    private ItemJson() {
    }

    static Map<String, Object> readOrNull(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : read(stack);
    }

    static Map<String, Object> read(ItemStack stack) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        out.put("amount", stack.getCount());
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null) {
            out.put("name", name.getString());
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (Component line : lore.lines()) {
                lines.add(line.getString());
            }
            out.put("lore", lines);
        }
        if (stack.isDamageableItem() && stack.getDamageValue() > 0) {
            out.put("damage", stack.getDamageValue());
        }
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) {
            out.put("storedEnchants", levels(stored));
        }
        out.put("enchants", levels(stack.getEnchantments()));
        out.put("raw", String.valueOf(stack));
        return out;
    }

    /** Builds the whole stack before anything is placed, so a bad field or key changes nothing. */
    static ItemStack build(Map<String, Object> item, HolderLookup.Provider registries) {
        Fields.onlyFields(item, FIELDS, "item");
        String type = Fields.text(item, "type");
        Identifier id = Identifier.tryParse(type);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            throw unknownKey(type);
        }
        Integer amount = Fields.integer(item, "amount");
        if (amount == null || amount < 1) {
            throw Fields.invalid("\"amount\" must be a positive integer");
        }
        Integer damage = Fields.integer(item, "damage");
        if (damage != null && damage < 0) {
            throw Fields.invalid("\"damage\" must not be negative");
        }
        String name = Fields.optionalText(item, "name");
        List<Component> lore = lore(item);
        ItemEnchantments enchants = enchantments(item, "enchants", registries);
        ItemEnchantments stored = enchantments(item, "storedEnchants", registries);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id), amount);
        if (!stored.isEmpty() && !stack.is(Items.ENCHANTED_BOOK)) {
            throw Fields.invalid("\"storedEnchants\" needs an enchanted book");
        }
        if (name != null) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        if (lore != null) {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        if (damage != null) {
            stack.set(DataComponents.DAMAGE, damage);
        }
        if (!enchants.isEmpty()) {
            stack.set(DataComponents.ENCHANTMENTS, enchants);
        }
        if (!stored.isEmpty()) {
            stack.set(DataComponents.STORED_ENCHANTMENTS, stored);
        }
        return stack;
    }

    private static Map<String, Object> levels(ItemEnchantments enchantments) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            String key = holder.unwrapKey().map(k -> k.identifier().toString())
                    .orElseThrow(() -> new AgentException(500, "INTERNAL", "an enchantment has no registry key", null));
            out.put(key, enchantments.getLevel(holder));
        }
        return out;
    }

    private static List<Component> lore(Map<String, Object> item) {
        Object value = item.get("lore");
        if (value == null) {
            return null;
        }
        if (!(value instanceof List)) {
            throw Fields.invalid("\"lore\" must be an array of strings");
        }
        List<Component> lines = new ArrayList<>();
        for (Object line : (List<?>) value) {
            if (!(line instanceof String)) {
                throw Fields.invalid("\"lore\" must be an array of strings");
            }
            lines.add(Component.literal((String) line));
        }
        return lines;
    }

    private static ItemEnchantments enchantments(Map<String, Object> item, String field,
            HolderLookup.Provider registries) {
        Map<String, Object> keys = Fields.optionalObject(item, field);
        if (keys == null || keys.isEmpty()) {
            return ItemEnchantments.EMPTY;
        }
        HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments.Mutable out = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Map.Entry<String, Object> entry : keys.entrySet()) {
            Object level = entry.getValue();
            if (!(level instanceof Integer) || (Integer) level < 1) {
                throw Fields.invalid("level of " + entry.getKey() + " must be an integer of at least 1");
            }
            Identifier id = Identifier.tryParse(entry.getKey());
            Holder<Enchantment> holder = id == null ? null
                    : lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id)).orElse(null);
            if (holder == null) {
                throw unknownKey(entry.getKey());
            }
            out.set(holder, (Integer) level);
        }
        return out.toImmutable();
    }

    static AgentException unknownKey(String key) {
        return new AgentException(400, "UNKNOWN_KEY", "unknown key " + key, Collections.singletonMap("key", key));
    }
}
