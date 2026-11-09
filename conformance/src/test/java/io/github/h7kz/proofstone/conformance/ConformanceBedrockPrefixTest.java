package io.github.h7kz.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Capability;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Clients;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Requires;
import java.util.regex.Pattern;

/**
 * C-B01 (T-M5-06, simulated Bedrock tier): a plugin's name-prefix detection sees a Java client that logs in under a
 * {@code .} name as a Bedrock player, and sees a normal client as a Java player. The plugin is the example fixture in
 * examples/bedrock-prefix; {@code /bedrock-check <name>} reports what it detects, so the test reads that from the
 * clients' own chat.
 *
 * <p>This proves the prefixed login and the plugin's detection on the row. It does not prove anything about the Bedrock
 * protocol: no Bedrock client joins (docs/platforms.md, Bedrock Edition). Only a cell that holds SIMULATED_BEDROCK runs it.
 */
@Proofstone(plugins = "build/bedrock-prefix-plugins/*.jar")
// Every platform is declared so a selection plans a cell (zero cells is a loud error); @Requires then makes the class a
// reasoned skip wherever the prefixed login is not held.
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Matrix(platform = Platform.FOLIA, versions = {"latest"})
@Matrix(platform = Platform.PURPUR, versions = {"latest"})
@Matrix(platform = Platform.SPIGOT, versions = {"latest"})
@Requires(Capability.SIMULATED_BEDROCK)
class ConformanceBedrockPrefixTest {

    private static final String BEDROCK_NAME = ".BedrockBot";

    @CellTest
    void prefixedClientIsBedrockAndAJavaClientIsNot(Client client, Clients clients) {
        Client bedrock = clients.create(BEDROCK_NAME);
        assertThat(bedrock.name()).isEqualTo(BEDROCK_NAME);

        client.command("bedrock-check " + BEDROCK_NAME);
        client.awaitMessage(Pattern.compile("^bedrock-check " + Pattern.quote(BEDROCK_NAME)
                + " online=true bedrock=true$"));

        // The negative: the test's own client has no prefix, so the same plugin must not call it Bedrock.
        client.command("bedrock-check " + client.name());
        client.awaitMessage(Pattern.compile("^bedrock-check " + Pattern.quote(client.name())
                + " online=true bedrock=false$"));
    }
}
