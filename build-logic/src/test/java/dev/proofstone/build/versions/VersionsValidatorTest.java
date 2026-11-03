package dev.proofstone.build.versions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

// versions.yml is validated by JSON Schema and by semantic rules (ADR-0008, specs/versions-yml.md).
// Each negative fixture trips exactly one rule.
class VersionsValidatorTest {

    private static final Path SCHEMA = Path.of("../proofstone-protocol/schemas/versions.schema.json");

    @Test
    void validFixturePasses() throws Exception {
        assertThat(errors("valid.yml")).isEmpty();
    }

    @Test
    void seedVersionsYmlPasses() throws Exception {
        // The seed is all experimental rows, so TODO pins are allowed.
        assertThat(VersionsValidator.validate(Path.of("../versions.yml"), SCHEMA)).isEmpty();
    }

    @Test
    void duplicateMcFails() throws Exception {
        assertThat(errors("duplicate-mc.yml")).singleElement().asString().contains("1.21.11").contains("twice");
    }

    @Test
    void requiredRowWithTodoFails() throws Exception {
        assertThat(errors("required-todo.yml")).singleElement().asString().contains("TODO");
    }

    @Test
    void unknownStatusFails() throws Exception {
        assertThat(errors("unknown-status.yml")).anySatisfy(e -> assertThat(e).contains("status"));
    }

    private static List<String> errors(String fixture) throws Exception {
        Path file = Path.of(VersionsValidatorTest.class.getResource("/versions/" + fixture).toURI());
        return VersionsValidator.validate(file, SCHEMA);
    }
}
