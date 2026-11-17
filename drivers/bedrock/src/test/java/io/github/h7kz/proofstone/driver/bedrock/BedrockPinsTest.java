package io.github.h7kz.proofstone.driver.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** One source per pin (AGENTS.md section 3): the catalog builds against what versions.yml says the driver runs. */
class BedrockPinsTest {

    private static final Path ROOT = Path.of(System.getProperty("proofstone.repoRoot"));

    @Test
    void theCatalogAndVersionsYmlAgreeOnTheLibraryTheProtocolAndTheGameVersion() throws IOException {
        String catalog = Files.readString(ROOT.resolve("gradle/libs.versions.toml"), StandardCharsets.UTF_8);
        String versions = Files.readString(ROOT.resolve("versions.yml"), StandardCharsets.UTF_8);
        String row = versions.substring(versions.indexOf("\n  bedrock:"));
        assertThat(value(catalog, "^cloudburst-protocol = \"([^\"]+)\"")).isEqualTo(value(row, "version: \"([^\"]+)\""));
        assertThat(value(row, "protocol: ([0-9]+)")).isEqualTo(String.valueOf(BedrockRpc.PROTOCOL));
        assertThat(value(row, "game-version: \"([^\"]+)\"")).isEqualTo(BedrockRpc.GAME_VERSION);
        assertThat(value(row, "library: \"([^\"]+)\"")).isEqualTo("org.cloudburstmc.protocol:bedrock-connection");
    }

    @Test
    void everyCloudburstPinIsATimestampedBuildNeverAMovingVersion() throws IOException {
        String catalog = Files.readString(ROOT.resolve("gradle/libs.versions.toml"), StandardCharsets.UTF_8);
        for (String name : new String[] {"cloudburst-protocol", "cloudburst-netty", "cloudburst-math"}) {
            assertThat(value(catalog, "^" + name + " = \"([^\"]+)\"")).matches(".*-[0-9]{8}\\.[0-9]{6}-[0-9]+");
        }
    }

    private static String value(String text, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        assertThat(matcher.find()).as(regex).isTrue();
        return matcher.group(1);
    }
}
