package dev.proofstone.agent.bukkit;

import dev.proofstone.agent.game.Fields;
import dev.proofstone.agent.game.Game;
import dev.proofstone.agent.protocol.AgentException;
import dev.proofstone.agent.scheduler.MainThread;
import dev.proofstone.agent.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Reads and changes game state through the Bukkit 1.8.8 API. Every call runs on the main thread (via
 * {@link MainThread}). The write endpoints (give, reset, PATCH) are harness setup, not the act path (ADR-0002).
 */
public final class BukkitGame implements Game {

    private static final int ARMOUR_FIRST_SLOT = 36;
    private static final int ARMOUR_SLOTS = 4;
    private static final List<String> PATCH_FIELDS = Arrays.asList("level", "exp", "gamemode", "location", "op");

    private final Scheduler scheduler;
    private final EventProbes probes;
    private final LogBuffer logs;
    private final TickCounter ticks;

    public BukkitGame(Scheduler scheduler, EventProbes probes, LogBuffer logs, TickCounter ticks) {
        this.scheduler = scheduler;
        this.probes = probes;
        this.logs = logs;
        this.ticks = ticks;
    }

    @Override
    public Map<String, Object> block(String world, int x, int y, int z, long timeoutMs) {
        return MainThread.call(scheduler, () -> WorldAccess.block(world, x, y, z), timeoutMs);
    }

    @Override
    public Map<String, Object> setBlock(String world, int x, int y, int z, Map<String, Object> write,
            long timeoutMs) {
        return MainThread.call(scheduler, () -> WorldAccess.setBlock(world, x, y, z, write), timeoutMs);
    }

    @Override
    public String subscribe(String className, long timeoutMs) {
        return MainThread.call(scheduler, () -> probes.subscribe(className), timeoutMs);
    }

    /** Reads the probe's buffer directly: it is synchronised, so the main thread is not needed. */
    @Override
    public Map<String, Object> events(String probeId, long since, long timeoutMs) {
        return probes.events(probeId, since);
    }

    @Override
    public void unsubscribe(String probeId, long timeoutMs) {
        MainThread.call(scheduler, () -> {
            probes.unsubscribe(probeId);
            return null;
        }, timeoutMs);
    }

    @Override
    public Map<String, Object> log(long since, String minLevel, long timeoutMs) {
        return logs.read(since, minLevel);
    }

    @Override
    public Map<String, Object> ticks(long timeoutMs) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("global", ticks.current());
        return out;
    }

    /** Waits on the calling thread, not the server thread, so the tick task can keep running. */
    @Override
    public Map<String, Object> awaitTicks(long n, long timeoutMs) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("global", ticks.await(n, timeoutMs));
        return out;
    }

    @Override
    public String command(String command, long timeoutMs) {
        return MainThread.call(scheduler, () -> {
            // The real console, not a custom sender: the server refuses to run vanilla commands for a sender it
            // cannot turn into a command source (docs/platforms/paper.md). Output is read from the log instead.
            LogCapture capture = LogCapture.start();
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (org.bukkit.command.CommandException e) {
                // Bukkit wraps the real failure; surface it, or the caller only sees "Unhandled exception".
                Throwable cause = e.getCause() == null ? e : e.getCause();
                throw new AgentException(500, "INTERNAL", "command failed: " + cause, null);
            } finally {
                capture.close();
            }
            return String.join("\n", capture.lines());
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
        return MainThread.call(scheduler, () -> state(requirePlayer(name)), timeoutMs);
    }

    /** Adds the item to the first empty storage slot. The item is built first, so a bad key changes nothing. */
    @Override
    public void give(String name, Map<String, Object> item, long timeoutMs) {
        MainThread.call(scheduler, () -> {
            Player player = requirePlayer(name);
            ItemStack stack = ItemBuilder.build(item);
            PlayerInventory inventory = player.getInventory();
            int slot = inventory.firstEmpty();
            if (slot < 0) {
                throw new AgentException(409, "INVENTORY_FULL", "no empty slot for the item",
                        Collections.singletonMap("name", name));
            }
            inventory.setItem(slot, stack);
            return null;
        }, timeoutMs);
    }

    /**
     * Closes any open window first (the cursor goes back into the inventory), then clears everything the
     * fixture state must not carry over.
     */
    @Override
    public void reset(String name, long timeoutMs) {
        MainThread.call(scheduler, () -> {
            Player player = requirePlayer(name);
            player.closeInventory();
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setArmorContents(new ItemStack[ARMOUR_SLOTS]);
            clearOffhand(inventory);
            player.getOpenInventory().setCursor(null);
            player.setLevel(0);
            player.setExp(0f);
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.teleport(spawn());
            return null;
        }, timeoutMs);
    }

    /** Validates every present field before applying any of them, so a bad field changes nothing. */
    @Override
    public void patch(String name, Map<String, Object> changes, long timeoutMs) {
        MainThread.call(scheduler, () -> {
            Player player = requirePlayer(name);
            Fields.onlyFields(changes, PATCH_FIELDS, "patch");
            Integer level = Fields.integer(changes, "level");
            if (level != null && level < 0) {
                throw Fields.invalid("\"level\" must not be negative");
            }
            Double exp = Fields.number(changes, "exp");
            if (exp != null && (exp < 0 || exp > 1)) {
                throw Fields.invalid("\"exp\" must be between 0 and 1");
            }
            GameMode gamemode = gameMode(Fields.optionalText(changes, "gamemode"));
            Location location = location(player, Fields.optionalObject(changes, "location"));
            Boolean op = Fields.bool(changes, "op");

            if (level != null) {
                player.setLevel(level);
            }
            if (exp != null) {
                player.setExp(exp.floatValue());
            }
            if (gamemode != null) {
                player.setGameMode(gamemode);
            }
            if (location != null) {
                player.teleport(location);
            }
            if (op != null) {
                player.setOp(op);
            }
            return null;
        }, timeoutMs);
    }

    private static Player requirePlayer(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            throw new AgentException(404, "PLAYER_NOT_FOUND", "no player named " + name,
                    Collections.singletonMap("name", name));
        }
        return player;
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

    /**
     * Before 1.9 there is no offhand slot, so the method is absent and there is nothing to clear. Reflection
     * finds it on the newer servers (docs/platforms/paper.md).
     */
    private static void clearOffhand(PlayerInventory inventory) {
        try {
            PlayerInventory.class.getMethod("setItemInOffHand", ItemStack.class).invoke(inventory, (Object) null);
        } catch (NoSuchMethodException e) {
            // Pre-1.9: no offhand.
        } catch (ReflectiveOperationException e) {
            throw new AgentException(500, "INTERNAL", "cannot clear the offhand: " + e, null);
        }
    }

    /**
     * The main world's spawn block, one block up, centred on the block (spec section 2, reset). Bukkit gives block
     * corners, and the golden reset is (0.5, 64.0, 0.5), so a corner would not match (golden/README.md).
     */
    private static Location spawn() {
        return Bukkit.getWorlds().get(0).getSpawnLocation().add(0.5, 1, 0.5);
    }

    /** Null when the body has no game mode. An unknown name is INVALID_REQUEST, never a guess. */
    private static GameMode gameMode(String name) {
        if (name == null) {
            return null;
        }
        for (GameMode mode : GameMode.values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        throw Fields.invalid("\"gamemode\" must be survival, creative, adventure or spectator");
    }

    /** Null when the body has no location. Yaw and pitch default to the player's current values. */
    private static Location location(Player player, Map<String, Object> at) {
        if (at == null) {
            return null;
        }
        String worldName = Fields.text(at, "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw Fields.invalid("no world named " + worldName);
        }
        Location current = player.getLocation();
        Double yaw = Fields.number(at, "yaw");
        Double pitch = Fields.number(at, "pitch");
        return new Location(world,
                Fields.requiredNumber(at, "x"),
                Fields.requiredNumber(at, "y"),
                Fields.requiredNumber(at, "z"),
                yaw == null ? current.getYaw() : yaw.floatValue(),
                pitch == null ? current.getPitch() : pitch.floatValue());
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

    static Map<String, Object> stackOrNull(ItemStack stack) {
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

    /** Levels pass through unchanged (test API section 6: never clamped). */
    private static Map<String, Object> enchants(Map<Enchantment, Integer> levels) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> level : levels.entrySet()) {
            String legacy = level.getKey().getName();
            String key = Keys.enchantmentKey(legacy);
            if (key == null) {
                throw Keys.unknownKey(legacy);
            }
            out.put("minecraft:" + key, level.getValue());
        }
        return out;
    }
}
