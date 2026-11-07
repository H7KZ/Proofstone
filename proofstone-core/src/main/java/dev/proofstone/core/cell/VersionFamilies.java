package dev.proofstone.core.cell;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.proofstone.core.versions.Row;
import dev.proofstone.core.versions.VersionNumber;
import dev.proofstone.core.versions.VersionsCatalog;
import dev.proofstone.core.versions.VersionsException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The version families a project declares in its {@code proofstone.yml} (versioning spec §4). A family is a named
 * group of exact versions on one platform. For {@code all} in PR scope the newest version of each family runs; in
 * nightly scope every version runs. v0 reads the file from the test working directory, and a missing file means no
 * families. Spec: docs/specs/test-api.md §1.
 *
 * <pre>
 * version-families:
 *   paper:
 *     netty: ["1.8.8", "1.12.2"]
 *     modern: ["1.20.4", "26.3"]
 * </pre>
 */
public final class VersionFamilies {

    static final String KEY = "version-families";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final VersionFamilies NONE = new VersionFamilies(Map.of());

    /** platform id to family name to versions */
    private final Map<String, Map<String, List<String>>> families;

    private VersionFamilies(Map<String, Map<String, List<String>>> families) {
        this.families = families;
    }

    /** @return no families: {@code all} is exactly the rows in scope */
    public static VersionFamilies none() {
        return NONE;
    }

    /**
     * Reads {@code file}; a file that does not exist means no families.
     *
     * @throws VersionsException when the file exists but is invalid
     */
    public static VersionFamilies load(Path file, VersionsCatalog catalog) {
        Objects.requireNonNull(file, "file");
        if (!Files.exists(file)) {
            return NONE;
        }
        try {
            return parse(Files.readString(file), catalog);
        } catch (IOException e) {
            throw new VersionsException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * @throws VersionsException on an unknown top-level key, an unknown platform, a family that is not a list of
     *     exact versions, or a version that is not a row of its platform
     */
    public static VersionFamilies parse(String yaml, VersionsCatalog catalog) {
        JsonNode document = read(yaml);
        Map<String, Map<String, List<String>>> byPlatform = new LinkedHashMap<>();
        Iterator<String> keys = document.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!KEY.equals(key)) {
                throw new VersionsException("proofstone.yml: unknown key \"" + key + "\"; the only key is " + KEY);
            }
        }
        JsonNode platforms = document.path(KEY);
        if (platforms.isMissingNode() || platforms.isNull()) {
            return NONE;
        }
        if (!platforms.isObject()) {
            throw new VersionsException("proofstone.yml: " + KEY + " must map platform ids to families");
        }
        Iterator<Map.Entry<String, JsonNode>> platformFields = platforms.fields();
        while (platformFields.hasNext()) {
            Map.Entry<String, JsonNode> platform = platformFields.next();
            catalog.platform(platform.getKey());
            Map<String, List<String>> byFamily = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> familyFields = platform.getValue().fields();
            while (familyFields.hasNext()) {
                Map.Entry<String, JsonNode> family = familyFields.next();
                byFamily.put(family.getKey(), versionsOf(platform.getKey(), family.getKey(), family.getValue(),
                        catalog));
            }
            // Declaration order matters (the newest of each family is listed in file order), and Map.copyOf
            // iterates in an order that varies per JVM.
            byPlatform.put(platform.getKey(), Collections.unmodifiableMap(byFamily));
        }
        return new VersionFamilies(Collections.unmodifiableMap(byPlatform));
    }

    /**
     * @param platform platform id
     * @param scope {@code PR}: the newest version of each family; {@code NIGHTLY}: every version
     * @return the family versions to add to {@code all}, possibly empty
     */
    public List<String> versions(String platform, Scope scope) {
        Map<String, List<String>> byFamily = families.getOrDefault(platform, Map.of());
        List<String> result = new ArrayList<>();
        for (List<String> versions : byFamily.values()) {
            if (scope == Scope.NIGHTLY) {
                result.addAll(versions);
            } else {
                versions.stream().max(VersionNumber::compare).ifPresent(result::add);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> versionsOf(String platform, String family, JsonNode node, VersionsCatalog catalog) {
        if (!node.isArray() || node.isEmpty()) {
            throw new VersionsException("proofstone.yml: " + platform + "." + family + " must be a non-empty list of "
                    + "exact versions");
        }
        List<String> versions = new ArrayList<>();
        for (JsonNode item : node) {
            String version = item.asText("");
            if (!version.matches("\\d+(?:\\.\\d+)*")) {
                throw new VersionsException("proofstone.yml: " + platform + "." + family + " lists \"" + version
                        + "\", which is not an exact version (latest and all are not allowed here)");
            }
            if (!versions.contains(version)) {
                versions.add(version);
            }
        }
        // Checked against the matrix here, so a typo fails at load, not when the class is planned.
        List<String> rows = catalog.platform(platform).rows().stream().map(Row::mc).toList();
        for (String version : versions) {
            if (!rows.contains(version)) {
                throw new VersionsException("proofstone.yml: " + platform + "." + family + " lists \"" + version
                        + "\", which is not a row of " + platform + " in versions.yml");
            }
        }
        return List.copyOf(versions);
    }

    private static JsonNode read(String yaml) {
        try {
            JsonNode node = YAML.readTree(yaml == null ? "" : yaml);
            return node == null || node.isNull() || node.isMissingNode() ? YAML.createObjectNode() : node;
        } catch (IOException e) {
            throw new VersionsException("proofstone.yml is not valid YAML: " + e.getMessage(), e);
        }
    }
}
