package dev.proofstone.driver.mcpl;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An item as the client sees it, parsed from the stack and its data components (T-M2-06). It is immutable and has no
 * driver types in it, so T-M2-07 can move it into {@code proofstone-api} as the public {@code ItemView}.
 *
 * <p>Keys are namespaced lowercase strings and levels are never clamped (test API spec section 6). {@code name} is
 * the {@code minecraft:custom_name} text flattened to plain text, or {@code null} when the item has none.
 */
public final class ItemView {

    /** The empty slot. */
    public static final ItemView EMPTY = new ItemView("minecraft:air", 0, Map.of(), Map.of(), null, List.of(), 0, 0,
            null);

    private final String type;
    private final int amount;
    private final Map<String, Integer> enchants;
    private final Map<String, Integer> storedEnchants;
    private final String name;
    private final List<String> lore;
    private final int damage;
    private final int repairCost;
    private final Map<String, Object> customData;

    /** @param customData the {@code minecraft:custom_data} compound, or {@code null} when the item has none */
    public ItemView(String type, int amount, Map<String, Integer> enchants, Map<String, Integer> storedEnchants,
            String name, List<String> lore, int damage, int repairCost, Map<String, Object> customData) {
        this.type = Objects.requireNonNull(type, "type");
        this.amount = amount;
        this.enchants = Map.copyOf(enchants);
        this.storedEnchants = Map.copyOf(storedEnchants);
        this.name = name;
        this.lore = List.copyOf(lore);
        this.damage = damage;
        this.repairCost = repairCost;
        this.customData = customData == null ? null : Map.copyOf(customData);
    }

    public String type() {
        return type;
    }

    public int amount() {
        return amount;
    }

    public boolean isEmpty() {
        return amount <= 0;
    }

    public Map<String, Integer> enchants() {
        return enchants;
    }

    public Map<String, Integer> storedEnchants() {
        return storedEnchants;
    }

    /** The custom name as plain text, or {@code null} when none is set. */
    public String name() {
        return name;
    }

    public List<String> lore() {
        return lore;
    }

    /** Durability damage; 0 when the component is absent. */
    public int damage() {
        return damage;
    }

    /** Anvil repair cost; 0 when the component is absent. */
    public int repairCost() {
        return repairCost;
    }

    /** The {@code minecraft:custom_data} compound, or {@code null} when absent. */
    public Map<String, Object> customData() {
        return customData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemView other)) {
            return false;
        }
        return amount == other.amount && damage == other.damage && repairCost == other.repairCost
                && type.equals(other.type) && enchants.equals(other.enchants)
                && storedEnchants.equals(other.storedEnchants) && Objects.equals(name, other.name)
                && lore.equals(other.lore) && Objects.equals(customData, other.customData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, amount, enchants, storedEnchants, name, lore, damage, repairCost, customData);
    }

    @Override
    public String toString() {
        return "ItemView{" + type + " x" + amount + ", enchants=" + enchants + ", storedEnchants=" + storedEnchants
                + ", name=" + name + ", lore=" + lore + ", damage=" + damage + ", repairCost=" + repairCost
                + ", customData=" + customData + "}";
    }
}
