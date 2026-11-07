package dev.proofstone.agent.bukkit;

import dev.proofstone.agent.game.Fields;
import dev.proofstone.agent.protocol.AgentException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Block reads and writes (spec §2, block.json). Block data (namespaced type plus state) uses the 1.13+ BlockData API
 * through reflection, since the agent compiles against 1.8.8. Before 1.13 the state is the data value, as far as the
 * legacy table goes (LegacyBlocks); a state outside it is UNKNOWN_KEY, never silently dropped. Runs on the main thread (BukkitGame wraps the calls).
 */
final class WorldAccess {

    private static final Pattern NAMESPACED = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private static final Pattern STATE_KEY = Pattern.compile("^[a-z_]+$");

    /** Null before 1.13, where there is no BlockData. */
    private static final Method CREATE_BLOCK_DATA = find(Bukkit.class, "createBlockData", String.class);
    private static final Method GET_BLOCK_DATA = find(Block.class, "getBlockData");
    private static final Method GET_AS_STRING = blockDataMethod("getAsString");
    private static final Method SET_BLOCK_DATA = blockDataSetter();

    private WorldAccess() {
    }

    static Map<String, Object> block(String world, int x, int y, int z) {
        Block block = blockAt(world, x, y, z);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("world", world);
        out.put("x", x);
        out.put("y", y);
        out.put("z", z);
        if (GET_BLOCK_DATA == null) {
            // Before 1.13 a state is the data value; the legacy table says which properties it carries.
            out.put("type", "minecraft:" + Keys.materialKey(block.getType()));
            out.put("state", new TreeMap<String, Object>(
                    LegacyBlocks.state(block.getType().name(), block.getData())));
            return out;
        }
        String data = blockDataString(block);
        int bracket = data.indexOf('[');
        out.put("type", bracket < 0 ? data : data.substring(0, bracket));
        out.put("state", bracket < 0 ? Collections.emptyMap() : state(data.substring(bracket + 1, data.length() - 1)));
        return out;
    }

    /** Validates the whole write, then sets the block. An unknown type or state changes nothing. */
    static Map<String, Object> setBlock(String world, int x, int y, int z, Map<String, Object> write) {
        String type = Fields.text(write, "type");
        if (!NAMESPACED.matcher(type).matches()) {
            throw Fields.invalid("\"type\" must be a namespaced key such as minecraft:stone");
        }
        Map<String, String> state = state(write);
        Block block = blockAt(world, x, y, z);
        if (GET_BLOCK_DATA == null) {
            setLegacy(block, type, state);
        } else {
            setData(block, type, state);
        }
        return block(world, x, y, z);
    }

    /** Sets the material, then the data value the state stands for. Nothing changes when the state is unknown. */
    private static void setLegacy(Block block, String type, Map<String, String> state) {
        Material material = Keys.material(type);
        if (!material.isBlock()) {
            throw new AgentException(400, "UNKNOWN_KEY", "unknown block " + type,
                    Collections.singletonMap("key", type));
        }
        // A new block of another material starts from data 0; the same material keeps its other bits.
        int current = block.getType() == material ? block.getData() : 0;
        int data = LegacyBlocks.data(material.name(), state, current);
        block.setType(material);
        block.setData((byte) data);
    }

    private static void setData(Block block, String type, Map<String, String> state) {
        StringBuilder data = new StringBuilder(type);
        if (!state.isEmpty()) {
            data.append('[');
            boolean first = true;
            for (Map.Entry<String, String> entry : state.entrySet()) {
                data.append(first ? "" : ",").append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
            data.append(']');
        }
        Object blockData;
        try {
            blockData = CREATE_BLOCK_DATA.invoke(null, data.toString());
            SET_BLOCK_DATA.invoke(block, blockData);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Bukkit rejects an unknown block or property with IllegalArgumentException, before anything changes.
            throw new AgentException(400, "UNKNOWN_KEY", "unknown block data " + data,
                    Collections.singletonMap("key", data.toString()));
        } catch (ReflectiveOperationException e) {
            throw new AgentException(500, "INTERNAL", "cannot set block data: " + e, null);
        }
    }

    private static String blockDataString(Block block) {
        try {
            Object blockData = GET_BLOCK_DATA.invoke(block);
            return String.valueOf(GET_AS_STRING.invoke(blockData));
        } catch (ReflectiveOperationException e) {
            throw new AgentException(500, "INTERNAL", "cannot read block data: " + e, null);
        }
    }

    /** {@code state} of the write: an object of strings, with keys as BlockData property names. */
    private static Map<String, String> state(Map<String, Object> write) {
        Map<String, String> out = new TreeMap<>();
        Map<String, Object> state = Fields.optionalObject(write, "state");
        if (state == null) {
            return out;
        }
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            if (!STATE_KEY.matcher(entry.getKey()).matches() || !(entry.getValue() instanceof String)) {
                throw Fields.invalid("\"state\" must map property names to strings");
            }
            out.put(entry.getKey(), (String) entry.getValue());
        }
        return out;
    }

    /** Parses {@code key=value,key=value} from a BlockData string, in key order. */
    private static Map<String, Object> state(String properties) {
        Map<String, Object> out = new TreeMap<>();
        for (String pair : properties.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return out;
    }

    private static Block blockAt(String world, int x, int y, int z) {
        World found = Bukkit.getWorld(world);
        if (found == null) {
            throw Fields.invalid("no world named " + world);
        }
        return found.getBlockAt(x, y, z);
    }

    private static Method find(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Method blockDataMethod(String name) {
        Class<?> data = blockDataClass();
        return data == null ? null : find(data, name);
    }

    private static Method blockDataSetter() {
        Class<?> data = blockDataClass();
        return data == null ? null : find(Block.class, "setBlockData", data);
    }

    private static Class<?> blockDataClass() {
        try {
            return Class.forName("org.bukkit.block.data.BlockData");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
