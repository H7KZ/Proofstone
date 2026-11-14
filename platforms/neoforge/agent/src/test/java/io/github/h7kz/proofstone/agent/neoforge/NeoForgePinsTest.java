package io.github.h7kz.proofstone.agent.neoforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The agent compiles against the catalog's NeoForge pin and runs on the versions.yml row's (AGENTS.md section 3: one
 * source per pin). They must be the same NeoForge, or the agent is tested on one NeoForge and built against another.
 */
class NeoForgePinsTest {

    private static final Path ROOT = Path.of(System.getProperty("proofstone.repoRoot"));

    @Test
    void theCatalogBuildsAgainstTheNeoForgeRowOfVersionsYml() throws IOException {
        String catalog = Files.readString(ROOT.resolve("gradle/libs.versions.toml"), StandardCharsets.UTF_8);
        String versions = Files.readString(ROOT.resolve("versions.yml"), StandardCharsets.UTF_8);
        String neoforge = versions.substring(versions.indexOf("\n  neoforge:"));
        assertThat(value(catalog, "^neoforge = \"([^\"]+)\"")).isEqualTo(value(neoforge, "loader: \"([^\"]+)\""));
        assertThat(value(neoforge, "- mc: \"([^\"]+)\"")).isEqualTo("26.3");
    }

    private static String value(String text, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        assertThat(matcher.find()).as(regex).isTrue();
        return matcher.group(1);
    }
}
