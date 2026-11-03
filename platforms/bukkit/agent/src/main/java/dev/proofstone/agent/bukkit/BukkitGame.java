package dev.proofstone.agent.bukkit;

import dev.proofstone.agent.game.Game;
import dev.proofstone.agent.protocol.AgentException;
import dev.proofstone.agent.scheduler.MainThread;
import dev.proofstone.agent.scheduler.Scheduler;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Reads game state through the Bukkit 1.8.8 API. Every call runs on the main thread (via {@link MainThread}).
 * Observes only (ADR-0002).
 */
public final class BukkitGame implements Game {

    private static final String SENDER_NAME = "ProofstoneAgent";
    private static final int ARMOUR_FIRST_SLOT = 36;
    private static final Map<String, String> ENCHANTMENTS = enchantments();

    private final Scheduler scheduler;

    public BukkitGame(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String command(String command, long timeoutMs) {
        return MainThread.call(scheduler, () -> {
            List<String> lines = new ArrayList<>();
            Bukkit.dispatchCommand(captureSender(lines), command);
            return String.join("\n", lines);
        }, timeoutMs);
    }

    @Override
    public List<String> players(long timeoutMs) {
        return MainThread.call(scheduler, () -> {
            List<String> names = new ArrayList<>();
            for (Player player : onlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }, timeoutMs);
    }

    @Override
    public Map<String, Object> player(String name, long timeoutMs) {
        return MainThread.call(scheduler, () -> {
            Player player = Bukkit.getPlayerExact(name);
            if (player == null) {
                throw new AgentException(404, "PLAYER_NOT_FOUND", "no player named " + name,
                        Collections.singletonMap("name", name));
            }
            return state(player);
        }, timeoutMs);
    }

    /**
     * Captures what the command writes to its sender (best-effort, spec §2). Everything else on the
     * {@link CommandSender} is a fixed answer: a console-like sender with full permissions.
     */
    private static CommandSender captureSender(List<String> lines) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[] {CommandSender.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "sendMessage":
                            for (Object arg : args) {
                                if (arg instanceof String[]) {
                                    lines.addAll(Arrays.asList((String[]) arg));
                                } else if (arg instanceof String) {
                                    lines.add((String) arg);
                                }
                            }
                            return null;
                        case "getName":
                        case "toString":
                            return SENDER_NAME;
                        case "isOp":
                        case "hasPermission":
                        case "isPermissionSet":
                            return true;
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        return type == void.class ? null : Array.get(Array.newInstance(type, 1), 0);
    }

    /**
     * 1.8.8 declares {@code Player[] getOnlinePlayers()}, but later Bukkit APIs return a Collection. Reflection
     * reads whichever the running server has, so one jar works on both (docs/platforms/paper.md).
     */
    @SuppressWarnings("unchecked")
    private static Collection<? extends Player> onlinePlayers() throws ReflectiveOperationException {
        Object result = Bukkit.class.getMethod("getOnlinePlayers").invoke(null);
        if (result instanceof Collection) {
            return (Collection<? extends Player>) result;
        }
        return Arrays.asList((Player[]) result);
    }

    /** {@code PlayerState} (spec §3). Raw inventory slots: 0–35 storage, 36–39 armour (boots to helmet). */
    private static Map<String, Object> state(Player player) {
        InventoryView view = player.getOpenInventory();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", player.getName());
        state.put("uuid", player.getUniqueId().toString());
        state.put("level", player.getLevel());
        state.put("exp", (double) player.getExp());
        state.put("gamemode", player.getGameMode().name().toLowerCase(Locale.ROOT));
        state.put("health", player.getHealth());
        state.put("location", location(player.getLocation()));
        state.put("inventory", inventory(player.getInventory()));
        state.put("cursor", stackOrNull(view.getCursor()));
        state.put("openWindow", openWindow(view));
        return state;
    }

    private static Map<String, Object> location(Location at) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("world", at.getWorld().getName());
        out.put("x", at.getX());
        out.put("y", at.getY());
        out.put("z", at.getZ());
        out.put("yaw", (double) at.getYaw());
        out.put("pitch", (double) at.getPitch());
        return out;
    }

    private static Map<String, Object> inventory(PlayerInventory inventory) {
        Map<String, Object> slots = new LinkedHashMap<>();
        ItemStack[] storage = inventory.getContents();
        for (int i = 0; i < storage.length; i++) {
            putIfPresent(slots, i, storage[i]);
        }
        ItemStack[] armour = inventory.getArmorContents();
        for (int i = 0; i < armour.length; i++) {
            putIfPresent(slots, ARMOUR_FIRST_SLOT + i, armour[i]);
        }
        return slots;
    }

    private static void putIfPresent(Map<String, Object> slots, int index, ItemStack stack) {
        Object item = stackOrNull(stack);
        if (item != null) {
            slots.put(String.valueOf(index), item);
        }
    }

    /** Null when no window is open: the view's top inventory is the player's own crafting grid. */
    private static Map<String, Object> openWindow(InventoryView view) {
        Inventory top = view.getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) {
            return null;
        }
        List<Object> slots = new ArrayList<>();
        for (ItemStack stack : top.getContents()) {
            slots.add(stackOrNull(stack));
        }
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("type", top.getType().name().toLowerCase(Locale.ROOT));
        window.put("title", view.getTitle());
        window.put("slots", slots);
        return window;
    }

    private static Map<String, Object> stackOrNull(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR ? null : item(stack);
    }

    private static Map<String, Object> item(ItemStack stack) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "minecraft:" + stack.getType().name().toLowerCase(Locale.ROOT));
        out.put("amount", stack.getAmount());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            out.put("name", meta.getDisplayName());
        }
        out.put("enchants", enchants(stack.getEnchantments()));
        out.put("raw", String.valueOf(stack.serialize()));
        return out;
    }

    /** Levels pass through unchanged (spec §6 of the test API: never clamped). */
    private static Map<String, Object> enchants(Map<Enchantment, Integer> levels) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> level : levels.entrySet()) {
            String legacy = level.getKey().getName();
            String key = ENCHANTMENTS.get(legacy);
            if (key == null) {
                throw new AgentException(400, "UNKNOWN_KEY", "unknown enchantment " + legacy,
                        Collections.singletonMap("key", legacy));
            }
            out.put("minecraft:" + key, level.getValue());
        }
        return out;
    }

    /** Bukkit enchantment names to Minecraft keys, for the 1.8 set. Newer enchantments fail loudly until added. */
    private static Map<String, String> enchantments() {
        Map<String, String> table = new HashMap<>();
        table.put("PROTECTION_ENVIRONMENTAL", "protection");
        table.put("PROTECTION_FIRE", "fire_protection");
        table.put("PROTECTION_FALL", "feather_falling");
        table.put("PROTECTION_EXPLOSIONS", "blast_protection");
        table.put("PROTECTION_PROJECTILE", "projectile_protection");
        table.put("OXYGEN", "respiration");
        table.put("WATER_WORKER", "aqua_affinity");
        table.put("THORNS", "thorns");
        table.put("DEPTH_STRIDER", "depth_strider");
        table.put("DAMAGE_ALL", "sharpness");
        table.put("DAMAGE_UNDEAD", "smite");
        table.put("DAMAGE_ARTHROPODS", "bane_of_arthropods");
        table.put("KNOCKBACK", "knockback");
        table.put("FIRE_ASPECT", "fire_aspect");
        table.put("LOOT_BONUS_MOBS", "looting");
        table.put("DIG_SPEED", "efficiency");
        table.put("SILK_TOUCH", "silk_touch");
        table.put("DURABILITY", "unbreaking");
        table.put("LOOT_BONUS_BLOCKS", "fortune");
        table.put("ARROW_DAMAGE", "power");
        table.put("ARROW_KNOCKBACK", "punch");
        table.put("ARROW_FIRE", "flame");
        table.put("ARROW_INFINITE", "infinity");
        table.put("LUCK", "luck_of_the_sea");
        table.put("LURE", "lure");
        return table;
    }
}
