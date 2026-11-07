package dev.proofstone.agent.bukkit;

import dev.proofstone.agent.protocol.AgentException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

/**
 * Minecraft keys (minecraft:sharpness, test API section 6) to Bukkit objects. Enchantments resolve by key on
 * 1.13+ and by legacy name before that. The keyed API is reached by reflection because the 1.8.8 API this
 * agent compiles against does not have it (ADR-0002, Java 8 bytecode). An unknown key is UNKNOWN_KEY (spec
 * section 2).
 */
final class Keys {

    private static final String NAMESPACE = "minecraft:";
    private static final Pattern KEY_PATH = Pattern.compile("[a-z0-9_./-]+");
    private static final Pattern ENUM_NAME = Pattern.compile("[A-Z][A-Z0-9_]*");

    /** org.bukkit.block.data.BlockData, or null before the 1.13 flattening. */
    private static final Class<?> FLATTENED = findClass("org.bukkit.block.data.BlockData");
    /** org.bukkit.NamespacedKey: null before 1.12, and 1.12 has it without any keyed enchantment API. */
    private static final Class<?> NAMESPACED_KEY = findClass("org.bukkit.NamespacedKey");
    /** NamespacedKey.minecraft(String), or null before 1.13. */
    private static final Method MINECRAFT_KEY = method(NAMESPACED_KEY, "minecraft", String.class);
    /** Enchantment.getByKey(NamespacedKey), or null before 1.13. */
    private static final Method GET_BY_KEY = method(Enchantment.class, "getByKey", NAMESPACED_KEY);

    private Keys() {
    }

    /** Material.getKey() (Keyed, 1.13+), or null before. */
    private static final Method MATERIAL_GET_KEY = method(Material.class, "getKey");

    /** Enchantment.getKey(), or null before 1.13. */
    private static final Method GET_KEY = method(Enchantment.class, "getKey");

    /**
     * Bare key path of an enchantment, or null when it is not a vanilla one the agent can name. From 1.13 the key
     * comes from the server (getName() there is a legacy name that is missing for newer enchantments such as
     * mending, found by the dual oracle in T-M2-07); before that the legacy table is the only source.
     */
    static String enchantmentKey(Enchantment enchantment) {
        if (GET_KEY == null) {
            return LegacyNames.enchantmentKey(enchantment.getName());
        }
        try {
            String key = String.valueOf(GET_KEY.invoke(enchantment));
            return key.startsWith(NAMESPACE) ? key.substring(NAMESPACE.length()) : null;
        } catch (ReflectiveOperationException e) {
            throw new AgentException(500, "INTERNAL", "enchantment key lookup failed: " + e, null);
        }
    }

    /** Resolves minecraft:path to an enchantment. Levels are not checked here. */
    static Enchantment enchantment(String key) {
        String path = path(key);
        Enchantment enchantment = GET_BY_KEY != null ? keyed(path) : legacy(path);
        if (enchantment == null) {
            throw unknownKey(key);
        }
        return enchantment;
    }

    /**
     * Resolves minecraft:path to a material. From 1.13 the enum name is the path in upper case. Before that the
     * name is the path in upper case only when the legacy table has no row for it (DIAMOND_SWORD, but GOLD_SWORD
     * for golden_sword), and a legacy name that has a row is not a key (T-M3-06).
     */
    static Material material(String key) {
        String path = path(key);
        String name = path.toUpperCase(Locale.ROOT);
        if (legacy()) {
            String renamed = LegacyNames.materialName(path);
            if (renamed != null) {
                name = renamed;
            } else if (LegacyNames.isRenamedMaterial(name)) {
                throw unknownKey(key);
            }
        }
        if (!ENUM_NAME.matcher(name).matches()) {
            throw unknownKey(key);
        }
        Material material = Material.getMaterial(name);
        if (material == null || material == Material.AIR) {
            throw unknownKey(key);
        }
        // A newer server still resolves a legacy enum name (26.3 resolves GOLD_SWORD, whose real key is
        // golden_sword), so what resolved must read back under the key that was asked for: a name is never a key
        // on one version and not on another.
        String actual;
        try {
            actual = materialKey(material);
        } catch (IllegalArgumentException e) {
            throw unknownKey(key); // the server has no key for a legacy material
        }
        if (!path.equals(actual)) {
            throw unknownKey(key);
        }
        return material;
    }

    /**
     * The key path of a material: the server's own key from 1.13 ({@code Material.getKey()}, by reflection), the
     * legacy table before.
     *
     * @throws IllegalArgumentException when the server has no key for it (a legacy material on a newer server)
     */
    static String materialKey(Material material) {
        if (legacy()) {
            return LegacyNames.materialKey(material.name());
        }
        if (MATERIAL_GET_KEY == null) {
            return material.name().toLowerCase(Locale.ROOT);
        }
        try {
            return String.valueOf(MATERIAL_GET_KEY.invoke(material)).substring(NAMESPACE.length());
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e.getCause();
            }
            throw new AgentException(500, "INTERNAL", "material key lookup failed: " + e.getCause(), null);
        } catch (IllegalAccessException e) {
            throw new AgentException(500, "INTERNAL", "material key lookup failed: " + e, null);
        }
    }

    /** True before the 1.13 flattening. Not NamespacedKey: it exists since 1.12. BlockData is the 1.13 marker. */
    static boolean legacy() {
        return FLATTENED == null;
    }

    static AgentException unknownKey(String key) {
        return new AgentException(400, "UNKNOWN_KEY", "unknown key " + key,
                Collections.singletonMap("key", key));
    }

    private static String path(String key) {
        if (!key.startsWith(NAMESPACE)) {
            throw unknownKey(key);
        }
        String path = key.substring(NAMESPACE.length());
        if (!KEY_PATH.matcher(path).matches()) {
            throw unknownKey(key);
        }
        return path;
    }

    private static Enchantment legacy(String path) {
        String legacy = LegacyNames.enchantmentName(path);
        return legacy == null ? null : Enchantment.getByName(legacy);
    }

    private static Enchantment keyed(String path) {
        try {
            return (Enchantment) GET_BY_KEY.invoke(null, MINECRAFT_KEY.invoke(null, path));
        } catch (ReflectiveOperationException e) {
            throw new AgentException(500, "INTERNAL", "keyed enchantment lookup failed: " + e, null);
        }
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
