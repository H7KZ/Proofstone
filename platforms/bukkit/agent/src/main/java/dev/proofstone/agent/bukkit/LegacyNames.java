package dev.proofstone.agent.bukkit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-1.13 enum names to Minecraft keys, for materials and enchantments (T-M3-06). The data is in resources
 * (legacy-materials.csv, legacy-enchantments.csv), so a new row is a data change. A table that cannot be read is an
 * error at class load, never an empty table that would answer UNKNOWN_KEY for everything.
 */
final class LegacyNames {

    private static final Map<String, String> ENCHANTMENTS = load("legacy-enchantments.csv");
    private static final Map<String, String> MATERIALS = load("legacy-materials.csv");
    private static final Map<String, String> ENCHANTMENT_NAMES = inverse(ENCHANTMENTS);
    private static final Map<String, String> MATERIAL_NAMES = inverse(MATERIALS);

    private LegacyNames() {
    }

    /** Enum name to key path, in file order. */
    static Map<String, String> enchantments() {
        return ENCHANTMENTS;
    }

    /** Enum name to key path, only for materials whose key is not the lower case name. */
    static Map<String, String> materials() {
        return MATERIALS;
    }

    /** Key path of a legacy enchantment name, or null when the table has no row. */
    static String enchantmentKey(String enumName) {
        return ENCHANTMENTS.get(enumName);
    }

    /** Legacy enchantment name of a key path, or null when the table has no row. */
    static String enchantmentName(String path) {
        return ENCHANTMENT_NAMES.get(path);
    }

    /** Key path of a legacy material name: the table row, or else the lower case name. */
    static String materialKey(String enumName) {
        String row = MATERIALS.get(enumName);
        return row != null ? row : enumName.toLowerCase(java.util.Locale.ROOT);
    }

    /** Legacy material name of a key path, or null when the key has no row. */
    static String materialName(String path) {
        return MATERIAL_NAMES.get(path);
    }

    /** True for an enum name that has a row, so its lower case form is not its key. */
    static boolean isRenamedMaterial(String enumName) {
        return MATERIALS.containsKey(enumName);
    }

    /** Non-comment lines of a resource next to this class, split on {@code separator}. */
    static List<String[]> rows(String resource, String separator) {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = LegacyNames.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + resource);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                rows.add(line.trim().split(separator, -1));
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read resource " + resource, e);
        }
        return rows;
    }

    private static Map<String, String> load(String resource) {
        Map<String, String> table = new LinkedHashMap<>();
        for (String[] row : rows(resource, ",")) {
            if (row.length != 2 || row[0].isEmpty() || row[1].isEmpty() || table.put(row[0], row[1]) != null) {
                throw new IllegalStateException("bad or duplicate row in " + resource + ": " + String.join(",", row));
            }
        }
        return Collections.unmodifiableMap(table);
    }

    private static Map<String, String> inverse(Map<String, String> table) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> row : table.entrySet()) {
            if (out.put(row.getValue(), row.getKey()) != null) {
                throw new IllegalStateException("two rows share the key " + row.getValue());
            }
        }
        return Collections.unmodifiableMap(out);
    }
}
