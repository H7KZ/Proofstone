package dev.proofstone.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An item as one oracle sees it (test-api spec section 6), immutable. The agent and the driver both produce it, so
 * a dual-oracle assertion compares like with like. It carries no driver or agent types.
 *
 * <p>Keys are namespaced lowercase strings and levels are never clamped. {@code name} is the custom name as plain
 * text, or {@code null} when the item has none. Two fields exist on one side only: {@link #pdc()} is read from the
 * server (clients do not receive it), {@link #customData()} from the client; each is {@code null} on the other.
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
    private final Map<String, Object> pdc;
    private final String raw;

    /** A client-side view: {@code customData} is the {@code minecraft:custom_data} compound, or {@code null}. */
    public ItemView(String type, int amount, Map<String, Integer> enchants, Map<String, Integer> storedEnchants,
            String name, List<String> lore, int damage, int repairCost, Map<String, Object> customData) {
        this(type, amount, enchants, storedEnchants, name, lore, damage, repairCost, customData, null, null);
    }

    /**
     * @param customData the client's {@code minecraft:custom_data}, or {@code null}
     * @param pdc the server's persistent data container values, or {@code null}
     * @param raw the server's native serialisation for debugging, or {@code null}
     */
    public ItemView(String type, int amount, Map<String, Integer> enchants, Map<String, Integer> storedEnchants,
            String name, List<String> lore, int damage, int repairCost, Map<String, Object> customData,
            Map<String, Object> pdc, String raw) {
        this.type = Objects.requireNonNull(type, "type");
        this.amount = amount;
        this.enchants = Map.copyOf(enchants);
        this.storedEnchants = Map.copyOf(storedEnchants);
        this.name = name;
        this.lore = List.copyOf(lore);
        this.damage = damage;
        this.repairCost = repairCost;
        this.customData = customData == null ? null : Map.copyOf(customData);
        this.pdc = pdc == null ? null : Map.copyOf(pdc);
        this.raw = raw;
    }

    public String type() {
        return type;
    }

    /** True when the type is {@code id}, with or without the {@code minecraft:} prefix. */
    public boolean is(String id) {
        return type.equals(id.indexOf(':') < 0 ? "minecraft:" + id : id);
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

    /** Durability damage; 0 when absent. */
    public int damage() {
        return damage;
    }

    /** Anvil repair cost; 0 when absent. */
    public int repairCost() {
        return repairCost;
    }

    /** The client's {@code minecraft:custom_data} compound, or {@code null} when absent or from the server. */
    public Map<String, Object> customData() {
        return customData;
    }

    /** The server's persistent data container values, or {@code null} when absent or from the client. */
    public Map<String, Object> pdc() {
        return pdc;
    }

    /** The server's native serialisation, for debugging only; {@code null} from the client. */
    public String raw() {
        return raw;
    }

    /** Equal when every observable field matches. {@code pdc}, {@code customData} and {@code raw} are one-sided. */
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
                + ", customData=" + customData + ", pdc=" + pdc + "}";
    }
}
