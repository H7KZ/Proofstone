package dev.proofstone.agent.bukkit;

import dev.proofstone.agent.protocol.AgentException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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

    /** Legacy (1.8) enchantment name to Minecraft key path. Newer enchantments fail loudly until added. */
    private static final Map<String, String> LEGACY_ENCHANTMENTS = legacyEnchantments();

    /** org.bukkit.NamespacedKey, or null before 1.13. */
    private static final Class<?> NAMESPACED_KEY = findClass("org.bukkit.NamespacedKey");
    /** NamespacedKey.minecraft(String), or null before 1.13. */
    private static final Method MINECRAFT_KEY = method(NAMESPACED_KEY, "minecraft", String.class);
    /** Enchantment.getByKey(NamespacedKey), or null before 1.13. */
    private static final Method GET_BY_KEY = method(Enchantment.class, "getByKey", NAMESPACED_KEY);

    private Keys() {
    }

    /** Bare key path for a legacy enchantment name, or null when the name is not in the table. */
    static String enchantmentKey(String legacyName) {
        return LEGACY_ENCHANTMENTS.get(legacyName);
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
     * Resolves minecraft:path to a material. The 1.8 enum name of an item is its path in upper case
     * (DIAMOND_SWORD), so one lookup serves legacy and 1.13+ servers alike.
     */
    static Material material(String key) {
        String name = path(key).toUpperCase(Locale.ROOT);
        if (!ENUM_NAME.matcher(name).matches()) {
            throw unknownKey(key);
        }
        Material material = Material.matchMaterial(name);
        if (material == null || material == Material.AIR) {
            throw unknownKey(key);
        }
        return material;
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
        String legacy = legacyName(path);
        return legacy == null ? null : Enchantment.getByName(legacy);
    }

    private static Enchantment keyed(String path) {
        try {
            return (Enchantment) GET_BY_KEY.invoke(null, MINECRAFT_KEY.invoke(null, path));
        } catch (ReflectiveOperationException e) {
            throw new AgentException(500, "INTERNAL", "keyed enchantment lookup failed: " + e, null);
        }
    }

    private static String legacyName(String path) {
        for (Map.Entry<String, String> entry : LEGACY_ENCHANTMENTS.entrySet()) {
            if (entry.getValue().equals(path)) {
                return entry.getKey();
            }
        }
        return null;
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

    private static Map<String, String> legacyEnchantments() {
        Map<String, String> table = new LinkedHashMap<>();
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
