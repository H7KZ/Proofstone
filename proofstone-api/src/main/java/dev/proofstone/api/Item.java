package dev.proofstone.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An item to build on the server (test-api spec section 6), immutable: every method returns a new {@code Item}.
 * Types and enchantments are namespaced; a bare {@code "sharpness"} gets {@code minecraft:}. Levels are never clamped.
 *
 * <p>Only the fields the agent's {@code give} accepts today exist here; {@code repairCost} and {@code pdc} follow
 * with the agent (a freeze candidate, see docs/specs/test-api.md).
 */
public final class Item {

    private final String type;
    private final int amount;
    private final Map<String, Integer> enchants;
    private final Map<String, Integer> storedEnchants;
    private final String name;
    private final List<String> lore;
    private final int damage;

    private Item(String type, int amount, Map<String, Integer> enchants, Map<String, Integer> storedEnchants,
            String name, List<String> lore, int damage) {
        this.type = type;
        this.amount = amount;
        this.enchants = Map.copyOf(enchants);
        this.storedEnchants = Map.copyOf(storedEnchants);
        this.name = name;
        this.lore = List.copyOf(lore);
        this.damage = damage;
    }

    /** An item of the given type, one of it. */
    public static Item of(String type) {
        return new Item(namespaced(type, "type"), 1, Map.of(), Map.of(), null, List.of(), 0);
    }

    public Item amount(int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least 1, was " + amount);
        }
        return new Item(type, amount, enchants, storedEnchants, name, lore, damage);
    }

    public Item enchant(String enchantment, int level) {
        return new Item(type, amount, with(enchants, enchantment, level), storedEnchants, name, lore, damage);
    }

    /** An enchantment stored on an enchanted book. */
    public Item storedEnchant(String enchantment, int level) {
        return new Item(type, amount, enchants, with(storedEnchants, enchantment, level), name, lore, damage);
    }

    public Item name(String name) {
        return new Item(type, amount, enchants, storedEnchants, Objects.requireNonNull(name, "name"), lore, damage);
    }

    public Item lore(String... lines) {
        return new Item(type, amount, enchants, storedEnchants, name, List.of(lines), damage);
    }

    /** Durability damage taken. */
    public Item damage(int damage) {
        if (damage < 0) {
            throw new IllegalArgumentException("damage must not be negative, was " + damage);
        }
        return new Item(type, amount, enchants, storedEnchants, name, lore, damage);
    }

    public String type() {
        return type;
    }

    public int amount() {
        return amount;
    }

    public Map<String, Integer> enchants() {
        return enchants;
    }

    public Map<String, Integer> storedEnchants() {
        return storedEnchants;
    }

    /** The custom name, or {@code null}. */
    public String name() {
        return name;
    }

    public List<String> lore() {
        return lore;
    }

    public int damage() {
        return damage;
    }

    private static Map<String, Integer> with(Map<String, Integer> levels, String enchantment, int level) {
        if (level < 1) {
            throw new IllegalArgumentException("enchantment level must be at least 1, was " + level);
        }
        Map<String, Integer> next = new LinkedHashMap<>(levels);
        next.put(namespaced(enchantment, "enchantment"), level);
        return next;
    }

    private static String namespaced(String id, String what) {
        Objects.requireNonNull(id, what);
        if (id.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    @Override
    public String toString() {
        return "Item{" + type + " x" + amount + ", enchants=" + enchants + ", storedEnchants=" + storedEnchants
                + ", name=" + name + ", lore=" + lore + ", damage=" + damage + "}";
    }
}
