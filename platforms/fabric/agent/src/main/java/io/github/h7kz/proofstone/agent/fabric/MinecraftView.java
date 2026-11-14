package io.github.h7kz.proofstone.agent.fabric;

import io.github.h7kz.proofstone.agent.game.Fields;
import io.github.h7kz.proofstone.agent.protocol.AgentException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.LevelData;

/**
 * The real server behind {@link GameView}, on Mojang's own names (26.x ships unobfuscated). Runs on the server thread.
 *
 * <p>World names follow Bukkit's, so both oracles and every test name worlds alike: the overworld is the level name
 * ({@code world}), the nether and the end add {@code _nether} and {@code _the_end}, any other dimension is its id.
 * Inventory slots are the vanilla player-inventory indexes, which are Bukkit's raw ones: 0-35 storage, 36-39 armour
 * (feet to head), 40 offhand.
 */
final class MinecraftView implements GameView {

    private static final List<String> PATCH_FIELDS = Arrays.asList("level", "exp", "gamemode", "location", "op");

    private final MinecraftServer server;

    MinecraftView(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String command(String command) {
        String line = command.startsWith("/") ? command.substring(1) : command;
        String root = line.split(" ", 2)[0];
        if (server.getCommands().getDispatcher().getRoot().getChild(root) == null) {
            throw Fields.invalid("unknown command " + root);
        }
        Capture capture = new Capture();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture), line);
        return String.join("\n", capture.lines);
    }

    @Override
    public List<String> players() {
        List<String> names = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            names.add(player.getPlainTextName());
        }
        return names;
    }

    @Override
    public Map<String, Object> player(String name) {
        ServerPlayer player = requirePlayer(name);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", player.getPlainTextName());
        state.put("uuid", player.getUUID().toString());
        state.put("level", player.experienceLevel);
        state.put("exp", (double) player.experienceProgress);
        state.put("gamemode", player.gameMode().getName());
        state.put("health", (double) player.getHealth());
        state.put("location", location(player));
        state.put("inventory", inventory(player.getInventory()));
        state.put("cursor", ItemJson.readOrNull(player.containerMenu.getCarried()));
        state.put("openWindow", openWindow(player));
        return state;
    }

    @Override
    public void give(String name, Map<String, Object> item) {
        ServerPlayer player = requirePlayer(name);
        ItemStack stack = ItemJson.build(item, server.registryAccess());
        int slot = player.getInventory().getFreeSlot();
        if (slot < 0) {
            throw new AgentException(409, "INVENTORY_FULL", "no empty slot for the item",
                    Collections.singletonMap("name", name));
        }
        player.getInventory().setItem(slot, stack);
    }

    /** Closes any window first (its cursor item goes back to the inventory), then clears what must not carry over. */
    @Override
    public void reset(String name) {
        ServerPlayer player = requirePlayer(name);
        player.closeContainer();
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        // The world spawn block, one up, centred, as the Bukkit agent resets (golden/README.md: (0.5, 64.0, 0.5)).
        LevelData.RespawnData spawn = server.overworld().getRespawnData();
        ServerLevel level = server.getLevel(spawn.dimension());
        BlockPos at = spawn.pos();
        teleport(player, level == null ? server.overworld() : level,
                at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, spawn.yaw(), spawn.pitch());
    }

    @Override
    public void patch(String name, Map<String, Object> changes) {
        ServerPlayer player = requirePlayer(name);
        Fields.onlyFields(changes, PATCH_FIELDS, "patch");
        Integer level = Fields.integer(changes, "level");
        if (level != null && level < 0) {
            throw Fields.invalid("\"level\" must not be negative");
        }
        Double exp = Fields.number(changes, "exp");
        if (exp != null && (exp < 0 || exp > 1)) {
            throw Fields.invalid("\"exp\" must be between 0 and 1");
        }
        GameType gamemode = gameMode(Fields.optionalText(changes, "gamemode"));
        Map<String, Object> at = Fields.optionalObject(changes, "location");
        ServerLevel world = at == null ? null : level(Fields.text(at, "world"));
        double[] xyz = at == null ? null : new double[] {
            Fields.requiredNumber(at, "x"), Fields.requiredNumber(at, "y"), Fields.requiredNumber(at, "z")};
        Double yaw = at == null ? null : Fields.number(at, "yaw");
        Double pitch = at == null ? null : Fields.number(at, "pitch");
        Boolean op = Fields.bool(changes, "op");

        if (exp != null) {
            player.experienceProgress = exp.floatValue();
        }
        if (level != null || exp != null) {
            // setExperienceLevels also makes the server resend the experience bar, so a bare progress change shows.
            player.setExperienceLevels(level != null ? level : player.experienceLevel);
        }
        if (gamemode != null) {
            player.setGameMode(gamemode);
        }
        if (world != null) {
            teleport(player, world, xyz[0], xyz[1], xyz[2],
                    yaw == null ? player.getYRot() : yaw.floatValue(),
                    pitch == null ? player.getXRot() : pitch.floatValue());
        }
        if (op != null) {
            if (op) {
                server.getPlayerList().op(player.nameAndId());
            } else {
                server.getPlayerList().deop(player.nameAndId());
            }
        }
    }

    @Override
    public Map<String, Object> block(String world, int x, int y, int z) {
        BlockState state = level(world).getBlockState(new BlockPos(x, y, z));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("world", world);
        out.put("x", x);
        out.put("y", y);
        out.put("z", z);
        out.put("type", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        Map<String, Object> properties = new TreeMap<>();
        state.getValues().forEach(value -> properties.put(value.property().getName(), value.valueName()));
        out.put("state", properties);
        return out;
    }

    /** Builds the whole state before writing, so an unknown type, property or value changes nothing. */
    @Override
    public Map<String, Object> setBlock(String world, int x, int y, int z, Map<String, Object> write) {
        String type = Fields.text(write, "type");
        Map<String, Object> state = Fields.optionalObject(write, "state");
        Map<String, Object> wanted = state == null ? Collections.<String, Object>emptyMap() : new TreeMap<>(state);
        ServerLevel level = level(world);
        // The key an UNKNOWN_KEY names, as Bukkit's block data string: minecraft:anvil[facing=west].
        StringBuilder key = new StringBuilder(type);
        for (Map.Entry<String, Object> entry : wanted.entrySet()) {
            key.append(key.length() == type.length() ? '[' : ',').append(entry.getKey()).append('=')
                    .append(entry.getValue());
        }
        if (!wanted.isEmpty()) {
            key.append(']');
        }
        Identifier id = Identifier.tryParse(type);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw ItemJson.unknownKey(key.toString());
        }
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        BlockState next = block.defaultBlockState();
        for (Map.Entry<String, Object> entry : wanted.entrySet()) {
            if (!(entry.getValue() instanceof String)) {
                throw Fields.invalid("\"state\" must map property names to strings");
            }
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                throw ItemJson.unknownKey(key.toString());
            }
            next = with(next, property, (String) entry.getValue(), key.toString());
        }
        level.setBlock(new BlockPos(x, y, z), next, Block.UPDATE_ALL);
        return block(world, x, y, z);
    }

    /**
     * The mods loaded from the mods folder, told apart by their jar's path: not the game, Java or the loader (the
     * loader reports itself as an ordinary mod of type {@code fabric}, docs/platforms/fabric.md), and not a mod nested
     * in another jar (Fabric API's modules).
     */
    @Override
    public List<Map<String, Object>> mods() {
        Path folder = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
        List<ModContainer> top = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (mod.getOrigin().getKind() == ModOrigin.Kind.PATH && mod.getOrigin().getPaths().stream()
                    .anyMatch(path -> path.toAbsolutePath().normalize().startsWith(folder))) {
                top.add(mod);
            }
        }
        top.sort(Comparator.comparing(mod -> mod.getMetadata().getId()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ModContainer mod : top) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", mod.getMetadata().getId());
            entry.put("version", mod.getMetadata().getVersion().getFriendlyString());
            out.add(entry);
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value,
            String key) {
        T parsed = property.getValue(value).orElseThrow(() -> ItemJson.unknownKey(key));
        return state.setValue(property, parsed);
    }

    private ServerPlayer requirePlayer(String name) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
        if (player == null) {
            throw new AgentException(404, "PLAYER_NOT_FOUND", "no player named " + name,
                    Collections.singletonMap("name", name));
        }
        return player;
    }

    private static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw,
            float pitch) {
        if (!player.teleportTo(level, x, y, z, Collections.emptySet(), yaw, pitch, true)) {
            throw new AgentException(500, "INTERNAL", "the server refused to teleport " + player.getPlainTextName(),
                    null);
        }
    }

    private String worldName(ResourceKey<Level> dimension) {
        String base = server.getWorldData().getLevelName();
        if (dimension == Level.OVERWORLD) {
            return base;
        }
        if (dimension == Level.NETHER) {
            return base + "_nether";
        }
        if (dimension == Level.END) {
            return base + "_the_end";
        }
        return dimension.identifier().toString();
    }

    private ServerLevel level(String name) {
        for (ServerLevel level : server.getAllLevels()) {
            if (worldName(level.dimension()).equals(name)) {
                return level;
            }
        }
        throw Fields.invalid("no world named " + name);
    }

    private Map<String, Object> location(ServerPlayer player) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("world", worldName(player.level().dimension()));
        out.put("x", player.getX());
        out.put("y", player.getY());
        out.put("z", player.getZ());
        out.put("yaw", (double) player.getYRot());
        out.put("pitch", (double) player.getXRot());
        return out;
    }

    private static Map<String, Object> inventory(Inventory inventory) {
        Map<String, Object> slots = new LinkedHashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            Map<String, Object> item = ItemJson.readOrNull(inventory.getItem(i));
            if (item != null) {
                slots.put(String.valueOf(i), item);
            }
        }
        return slots;
    }

    /**
     * Null when only the player's own inventory is open. A vanilla server does not keep an open window's title (it is
     * sent to the client once, at open), so a window cannot be reported as player-state.json requires: that is a loud
     * error, never a made-up title (AGENTS.md section 3, docs/platforms/fabric.md Agent notes).
     */
    private static Map<String, Object> openWindow(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu) {
            return null;
        }
        throw new AgentException(501, "NOT_SUPPORTED", "player " + player.getPlainTextName() + " has a window open "
                + "and the Fabric agent cannot read its title; close it first",
                Collections.singletonMap("name", player.getPlainTextName()));
    }

    /** Null when the body has no game mode. An unknown name is INVALID_REQUEST, never a guess. */
    private static GameType gameMode(String name) {
        if (name == null) {
            return null;
        }
        for (GameType mode : GameType.values()) {
            if (mode.getName().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        throw Fields.invalid("\"gamemode\" must be survival, creative, adventure or spectator");
    }

    /** A command source that keeps what the command says: the reply of {@code POST /v1/command} (best-effort). */
    private static final class Capture implements CommandSource {

        final List<String> lines = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            lines.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
