package io.github.h7kz.proofstone.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Validates JSON against the v1 schemas in {@code proofstone-protocol/schemas/}. Each schema is registered
 * under {@code https://proofstone.dev/schemas/<file>} so cross-file {@code $ref}s resolve offline.
 */
final class ProtocolSchemas {

    static final String BASE = "https://proofstone.dev/schemas/";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(
            SpecVersion.VersionFlag.V202012,
            builder -> builder.schemaLoaders(loaders -> loaders.schemas(loadAll())));

    private ProtocolSchemas() {
    }

    /** @return one message per violation; empty when the document is valid. {@code ref} may carry a fragment. */
    static List<String> errors(String ref, String json) {
        JsonSchema schema = FACTORY.getSchema(SchemaLocation.of(BASE + ref));
        try {
            JsonNode document = JSON.readTree(json);
            return schema.validate(document).stream().map(message -> message.getMessage()).toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("not JSON: " + json, e);
        }
    }

    private static Map<String, String> loadAll() {
        Path dir = Path.of("schemas");
        Map<String, String> schemas = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                schemas.put(BASE + file.getFileName(), Files.readString(file));
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read schemas from " + dir.toAbsolutePath(), e);
        }
        return schemas;
    }
}
