package dev.proofstone.build.versions;

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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates {@code versions.yml} against its JSON Schema, then against the semantic rules that
 * JSON Schema cannot express. Decision: ADR-0008. Spec: docs/specs/versions-yml.md.
 */
public final class VersionsValidator {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Set<String> STATUSES = Set.of("required", "experimental", "retired");

    private VersionsValidator() {
    }

    /** @return one readable message per problem; empty when the file is valid */
    public static List<String> validate(Path versionsFile, Path schemaFile) throws IOException {
        JsonNode document = YAML.readTree(versionsFile.toFile());
        List<String> errors = new ArrayList<>();
        errors.addAll(schemaErrors(document, schemaFile));
        errors.addAll(semanticErrors(document));
        return errors;
    }

    private static List<String> schemaErrors(JsonNode document, Path schemaFile) throws IOException {
        List<String> errors = new ArrayList<>();
        try (InputStream schemaStream = Files.newInputStream(schemaFile)) {
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaStream);
            for (ValidationMessage message : schema.validate(document)) {
                errors.add("schema: " + message.getMessage());
            }
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
                String where = "platforms." + platform.getKey() + ".rows[" + i + "] (mc " + row.path("mc").asText() + ")";
                String mc = row.path("mc").asText();
                if (!seenMc.add(mc)) {
                    errors.add("platforms." + platform.getKey() + ": mc \"" + mc + "\" appears twice");
                }
                String status = row.path("status").asText();
                if (!STATUSES.contains(status)) {
                    errors.add(where + ": unknown status \"" + status + "\" (allowed: required, experimental, retired)");
                }
                if ("required".equals(status) && containsTodo(row)) {
                    errors.add(where + ": required row must not contain TODO");
                }
            }
        }
        return errors;
    }

    private static boolean containsTodo(JsonNode node) {
        if (node.isTextual()) {
            return "TODO".equals(node.asText());
        }
        for (JsonNode child : node) {
            if (containsTodo(child)) {
                return true;
            }
        }
        return false;
    }
}
