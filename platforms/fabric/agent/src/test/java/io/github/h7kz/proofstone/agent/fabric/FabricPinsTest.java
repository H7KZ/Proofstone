package io.github.h7kz.proofstone.agent.fabric;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The agent compiles against the catalog's Fabric pins and runs on the versions.yml row's (AGENTS.md section 3: one
 * source per pin). They must be the same artefacts, or the agent is tested against one Fabric API and built on another.
 */
class FabricPinsTest {

    private static final Path ROOT = Path.of(System.getProperty("proofstone.repoRoot"));

    @Test
    void theCatalogBuildsAgainstTheFabricRowOfVersionsYml() throws IOException {
        String catalog = Files.readString(ROOT.resolve("gradle/libs.versions.toml"), StandardCharsets.UTF_8);
        String versions = Files.readString(ROOT.resolve("versions.yml"), StandardCharsets.UTF_8);
        String fabric = versions.substring(versions.indexOf("\n  fabric:"), versions.indexOf("\n  neoforge:"));
        assertThat(value(catalog, "^fabric-loader = \"([^\"]+)\"")).isEqualTo(value(fabric, "loader: \"([^\"]+)\""));
        assertThat(value(catalog, "^fabric-api = \"([^\"]+)\""))
                .isEqualTo(value(fabric, "fabric-api: \\{ version: \"([^\"]+)\""));
        assertThat(value(catalog, "^fabric-api-sha256 = \"([^\"]+)\""))
                .isEqualTo(value(fabric, "fabric-api: \\{[^}]*sha256: \"([^\"]+)\""));
        assertThat(value(fabric, "- mc: \"([^\"]+)\"")).isEqualTo("26.3");
    }

    private static String value(String text, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        assertThat(matcher.find()).as(regex).isTrue();
        return matcher.group(1);
    }
}
