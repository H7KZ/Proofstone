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

    /** The forwarding modes each proxy can run (ADR-0016 section 8); an id not here is not a proxy Proofstone can start. */
    private static final Map<String, List<String>> PROXY_FORWARDING = Map.of(
            "velocity", List.of("modern", "legacy"),
            "bungeecord", List.of("legacy"));

    private final Map<String, Platform> platforms;
    private final Map<String, ProxyPlatform> proxies;
    private final List<JavaRule> javaRules;
    private final Map<String, ImagePin> driverImages;

    private VersionsCatalog(Map<String, Platform> platforms, Map<String, ProxyPlatform> proxies,
            List<JavaRule> javaRules, Map<String, ImagePin> driverImages) {
        this.platforms = platforms;
        this.proxies = proxies;
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

    /** @return proxy ids in file order; empty for a schema 2 file (schema 3 {@code proxies}, ADR-0016) */
    public List<String> proxyIds() {
        return List.copyOf(proxies.keySet());
    }

    /**
     * @throws VersionsException when the proxy is not in the matrix, naming the known ones
     */
    public ProxyPlatform proxy(String id) {
        ProxyPlatform proxy = proxies.get(id);
        if (proxy == null) {
            throw new VersionsException("unknown proxy \"" + id + "\"; known: "
                    + (proxies.isEmpty() ? "none (versions.yml has no proxies)" : String.join(", ", proxies.keySet())));
        }
        return proxy;
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
        errors.addAll(proxyErrors(document));
        return errors;
    }

    /** The schema 3 rules of docs/specs/versions-yml.md; a file without {@code proxies} has none to break. */
    private static List<String> proxyErrors(JsonNode document) {
        List<String> errors = new ArrayList<>();
        JsonNode proxies = document.path("proxies");
        if (proxies.isMissingNode()) {
            return errors;
        }
        if (document.path("schema").asInt() != 3) {
            errors.add("proxies: needs \"schema: 3\" (ADR-0016); this file says schema "
                    + document.path("schema").asText());
        }
        String driverMc = document.path("drivers").path("mcpl").path("protocol").asText("");
        if (driverMc.isEmpty()) {
            errors.add("proxies: drivers.mcpl.protocol is missing, so pairings cannot be checked for being native");
        }
        Iterator<Map.Entry<String, JsonNode>> ids = proxies.fields();
        while (ids.hasNext()) {
            Map.Entry<String, JsonNode> proxy = ids.next();
            String id = proxy.getKey();
            JsonNode node = proxy.getValue();
            String at = "proxies." + id;
            List<String> supported = PROXY_FORWARDING.get(id);
            if (supported == null) {
                errors.add(at + ": unknown proxy; Proofstone can run velocity, bungeecord"
                        + " (Waterfall is out, ADR-0016 section 4)");
            } else if (!supported.contains(node.path("forwarding").asText())) {
                errors.add(at + ": forwarding \"" + node.path("forwarding").asText() + "\" is not supported by " + id
                        + "; it supports " + String.join(", ", supported));
            }
            if (document.path("platforms").has(id)) {
                errors.add(at + ": " + id + " is also a platform id; a proxy is never a platform");
            }
            Set<String> seenVersions = new HashSet<>();
            JsonNode rows = node.path("rows");
            for (int i = 0; i < rows.size(); i++) {
                JsonNode row = rows.get(i);
                String version = row.path("version").asText();
                String where = at + ".rows[" + i + "] (version " + version + ")";
                if (!seenVersions.add(version)) {
                    errors.add(at + ": version \"" + version + "\" appears twice");
                }
                if (!node.path("host").path("digests").has(row.path("java").asText())) {
                    errors.add(where + ": host.digests has no entry for Java tag " + row.path("java").asText());
                }
                if ("required".equals(row.path("status").asText()) && containsTodo(row)) {
                    errors.add(where + ": required row must not contain TODO");
                }
                errors.addAll(pairingErrors(document, id, row, where, driverMc));
            }
        }
        return errors;
    }

    private static List<String> pairingErrors(JsonNode document, String proxy, JsonNode row, String where,
            String driverMc) {
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode pairing : row.path("backends")) {
            String platform = pairing.path("platform").asText();
            String mc = pairing.path("mc").asText();
            String topology = proxy + "@" + row.path("version").asText() + "+" + platform + "@" + mc;
            if (!seen.add(platform + "@" + mc)) {
                errors.add(where + ": pairing " + platform + "@" + mc + " is listed twice");
                continue;
            }
            JsonNode backend = backendRow(document, platform, mc);
            if (backend == null) {
                errors.add(where + ": backend " + platform + "@" + mc + " is not a row of platforms." + platform);
                continue;
            }
            String proxyStatus = row.path("status").asText();
            String backendStatus = backend.path("status").asText();
            if ("required".equals(pairing.path("status").asText())) {
                if (!"required".equals(proxyStatus)) {
                    errors.add(topology + ": a required pairing needs a required proxy row (the proxy row is "
                            + proxyStatus + ")");
                }
                if (!"required".equals(backendStatus)) {
                    errors.add(topology + ": a required pairing needs a required backend row (the backend row is "
                            + backendStatus + ")");
                }
            }
            if (!driverMc.isEmpty() && !nativeFor(document, backend, driverMc)) {
                errors.add(topology + ": a bridged topology is not planned (the driver speaks " + driverMc
                        + " natively and the backend row's protocol differs); native pairings only, ADR-0016 section 3");
            }
        }
        return errors;
    }

    private static JsonNode backendRow(JsonNode document, String platform, String mc) {
        for (JsonNode row : document.path("platforms").path(platform).path("rows")) {
            if (mc.equals(row.path("mc").asText())) {
                return row;
            }
        }
        return null;
    }

    /** A backend row is native when it is the driver's own version, or has that version's protocol number. */
    private static boolean nativeFor(JsonNode document, JsonNode backend, String driverMc) {
        if (driverMc.equals(backend.path("mc").asText())) {
            return true;
        }
        if (!backend.has("protocol")) {
            return false;
        }
        Iterator<JsonNode> platforms = document.path("platforms").elements();
        while (platforms.hasNext()) {
            for (JsonNode row : platforms.next().path("rows")) {
                if (driverMc.equals(row.path("mc").asText()) && row.has("protocol")
                        && row.path("protocol").asInt() == backend.path("protocol").asInt()) {
                    return true;
                }
            }
        }
        return false;
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
        return new VersionsCatalog(platforms, proxies(document), List.copyOf(rules), Map.copyOf(driverImages));
    }

    private static Map<String, ProxyPlatform> proxies(JsonNode document) {
        Map<String, ProxyPlatform> proxies = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = document.path("proxies").fields();
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
            List<ProxyRow> rows = new ArrayList<>();
            for (JsonNode row : node.path("rows")) {
                List<ProxyRow.Pairing> pairings = new ArrayList<>();
                for (JsonNode pairing : row.path("backends")) {
                    pairings.add(new ProxyRow.Pairing(pairing.path("platform").asText(), pairing.path("mc").asText(),
                            status(pairing.path("status"))));
                }
                rows.add(new ProxyRow(row.path("version").asText(),
                        row.path("build").isInt() ? row.path("build").asInt() : null,
                        pin(row.path("sha256")), row.path("java").asText(), status(row.path("status")),
                        List.copyOf(pairings), row.path("observed").asText(""), row.path("notes").asText("")));
            }
            proxies.put(entry.getKey(), new ProxyPlatform(entry.getKey(),
                    new Host(node.path("host").path("image").asText(), digests), node.path("forwarding").asText(),
                    List.copyOf(rows)));
        }
        return proxies;
    }

    private static Status status(JsonNode node) {
        return Status.valueOf(node.asText().toUpperCase(Locale.ROOT));
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
