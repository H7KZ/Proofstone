package io.github.h7kz.proofstone.core.versions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The parsed, validated {@code versions.yml} (ADR-0008). Load it with {@link #load}; the file is never
 * found by a relative path, so the caller passes both the matrix and the schema explicitly.
 * Spec: docs/specs/versions-yml.md.
 */
public final class VersionsCatalog {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final String TODO = "TODO";

    private final Map<String, Platform> platforms;
    private final List<JavaRule> javaRules;
    private final Map<String, ImagePin> driverImages;

    private VersionsCatalog(Map<String, Platform> platforms, List<JavaRule> javaRules,
            Map<String, ImagePin> driverImages) {
        this.platforms = platforms;
        this.javaRules = javaRules;
        this.driverImages = driverImages;
    }

    /**
     * Reads and validates the matrix: JSON Schema first, then the semantic rules the schema cannot express.
     *
     * @throws VersionsException when the file is unreadable, invalid or has an unparsable Java rule
     */
    public static VersionsCatalog load(Path versionsFile, Path schemaFile) {
        Objects.requireNonNull(versionsFile, "versionsFile");
        Objects.requireNonNull(schemaFile, "schemaFile");
        JsonNode document = readYaml(versionsFile);
        List<String> errors = schemaErrors(document, schemaFile);
        if (errors.isEmpty()) {
            errors.addAll(semanticErrors(document));
        }
        if (!errors.isEmpty()) {
            throw new VersionsException(versionsFile + " is invalid:\n  " + String.join("\n  ", errors));
        }
        VersionsCatalog catalog = fromDocument(document);
        catalog.requireDigestKeys(document);
        return catalog;
    }

    // ADR-0013: a row's Java tag must have a host.digests key (the value may still be TODO; the planner refuses that).
    private void requireDigestKeys(JsonNode document) {
        List<String> errors = new ArrayList<>();
        for (Platform platform : platforms.values()) {
            JsonNode digests = document.path("platforms").path(platform.id()).path("host").path("digests");
            for (Row row : platform.rows()) {
                String tag = javaTag(row);
                if (!digests.has(tag)) {
                    errors.add("platforms." + platform.id() + " (mc " + row.mc()
                            + "): host.digests has no entry for Java tag " + tag);
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new VersionsException("versions.yml is invalid:\n  " + String.join("\n  ", errors));
        }
    }

    /**
     * The pinned image of a driver-side container (for example {@code viaproxy}).
     *
     * @throws VersionsException when the driver has no image, or its digest is still {@code TODO}, so an unpinned
     *     image never runs (AGENTS.md section 3)
     */
    public ImagePin driverImage(String driverId) {
        ImagePin pin = driverImages.get(driverId);
        if (pin == null) {
            throw new VersionsException("drivers." + driverId + " has no image with a pinned digest in versions.yml");
        }
        return pin;
    }

    /** @return platform ids in file order */
    public List<String> platformIds() {
        return List.copyOf(platforms.keySet());
    }

    /**
     * @throws VersionsException when the platform is not in the matrix
     */
    public Platform platform(String id) {
        Platform platform = platforms.get(id);
        if (platform == null) {
            throw new VersionsException("unknown platform \"" + id + "\"; known: " + String.join(", ", platforms.keySet()));
        }
        return platform;
    }

    /**
     * The Java image for a row: its own {@code java} override if set, else the first matching rule.
     *
     * @throws VersionsException when no rule matches, so an unknown version never gets a guessed Java
     */
    public String javaTag(Row row) {
        if (row.java() != null) {
            return row.java();
        }
        for (JavaRule rule : javaRules) {
            if (rule.matches(row.mc())) {
                return rule.image();
            }
        }
        throw new VersionsException("no java-rules entry matches " + row.mc());
    }

    private static JsonNode readYaml(Path versionsFile) {
        try {
            return YAML.readTree(versionsFile.toFile());
        } catch (IOException e) {
            throw new VersionsException("cannot read " + versionsFile + ": " + e.getMessage(), e);
        }
    }

    private static List<String> schemaErrors(JsonNode document, Path schemaFile) {
        List<String> errors = new ArrayList<>();
        try (InputStream schemaStream = Files.newInputStream(schemaFile)) {
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaStream);
            for (ValidationMessage message : schema.validate(document)) {
                errors.add("schema: " + message.getMessage());
            }
        } catch (IOException e) {
            throw new VersionsException("cannot read schema " + schemaFile + ": " + e.getMessage(), e);
        }
        return errors;
    }

    private static List<String> semanticErrors(JsonNode document) {
        List<String> errors = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> platforms = document.path("platforms").fields();
        while (platforms.hasNext()) {
            Map.Entry<String, JsonNode> platform = platforms.next();
            Set<String> seenMc = new HashSet<>();
            JsonNode rows = platform.getValue().path("rows");
            for (int i = 0; i < rows.size(); i++) {
                JsonNode row = rows.get(i);
                String mc = row.path("mc").asText();
                if (!seenMc.add(mc)) {
                    errors.add("platforms." + platform.getKey() + ": mc \"" + mc + "\" appears twice");
                }
                if (!row.has("build") && !platform.getValue().has("builder")) {
                    errors.add("platforms." + platform.getKey() + ".rows[" + i + "] (mc " + mc
                            + "): build is required unless the platform has a builder (Spigot)");
                }
                if ("required".equals(row.path("status").asText()) && containsTodo(row)) {
                    errors.add("platforms." + platform.getKey() + ".rows[" + i + "] (mc " + mc
                            + "): required row must not contain TODO");
                }
            }
        }
        return errors;
    }

    private static boolean containsTodo(JsonNode node) {
        if (node.isTextual()) {
            return TODO.equals(node.asText());
        }
        for (JsonNode child : node) {
            if (containsTodo(child)) {
                return true;
            }
        }
        return false;
    }

    private static VersionsCatalog fromDocument(JsonNode document) {
        List<JavaRule> rules = new ArrayList<>();
        for (JsonNode rule : document.path("java-rules")) {
            rules.add(JavaRule.parse(rule.path("range").asText(), rule.path("image").asText()));
        }
        Map<String, Platform> platforms = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = document.path("platforms").fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode node = entry.getValue();
            Map<String, String> digests = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> tags = node.path("host").path("digests").fields();
            while (tags.hasNext()) {
                Map.Entry<String, JsonNode> tag = tags.next();
                String digest = pin(tag.getValue());
                if (digest != null) {
                    digests.put(tag.getKey(), digest);
                }
            }
            Host host = new Host(node.path("host").path("image").asText(), digests);
            List<Row> rows = new ArrayList<>();
            for (JsonNode row : node.path("rows")) {
                rows.add(toRow(row));
            }
            Builder builder = node.has("builder") ? parseBuilder(node.path("builder")) : null;
            platforms.put(entry.getKey(), new Platform(entry.getKey(), host, List.copyOf(rows), builder));
        }
        Map<String, ImagePin> driverImages = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> drivers = document.path("drivers").fields();
        while (drivers.hasNext()) {
            Map.Entry<String, JsonNode> driver = drivers.next();
            String digest = pin(driver.getValue().path("digest"));
            if (driver.getValue().has("image") && digest != null) {
                driverImages.put(driver.getKey(), new ImagePin(driver.getValue().path("image").asText(), digest));
            }
        }
        return new VersionsCatalog(platforms, List.copyOf(rules), Map.copyOf(driverImages));
    }

    private static Row toRow(JsonNode row) {
        List<String> drivers = new ArrayList<>();
        for (JsonNode driver : row.path("native-drivers")) {
            drivers.add(driver.asText());
        }
        return new Row(
                row.path("mc").asText(),
                row.path("build").isInt() ? row.path("build").asInt() : null,
                pin(row.path("sha256")),
                Status.valueOf(row.path("status").asText().toUpperCase(Locale.ROOT)),
                row.has("java") ? row.path("java").asText() : null,
                row.has("protocol") ? row.path("protocol").asInt() : null,
                List.copyOf(drivers),
                row.path("observed").asText(""),
                row.path("notes").asText(""));
    }

    private static Builder parseBuilder(JsonNode node) {
        JsonNode buildTools = node.path("buildtools");
        Map<String, String> jdkImages = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> jdks = node.path("jdk").fields();
        while (jdks.hasNext()) {
            Map.Entry<String, JsonNode> jdk = jdks.next();
            String image = pin(jdk.getValue());
            if (image != null) {
                jdkImages.put(jdk.getKey(), image);
            }
        }
        return new Builder(buildTools.path("build").asInt(), pin(buildTools.path("sha256")), jdkImages);
    }

    /** A {@code TODO} pin is stored as {@code null}, so a planner can refuse to use it. */
    private static String pin(JsonNode node) {
        String text = node.asText("");
        return TODO.equals(text) || text.isEmpty() ? null : text;
    }
}
