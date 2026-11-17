package io.github.h7kz.proofstone.examples.geyserbedrock;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Agent;
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
 * A plugin's Bedrock-only branch with a real Bedrock client (T-M9-12). The plugin is the Bedrock-prefix example: it calls an online
 * player Bedrock when the name starts with {@code .}, the prefix Floodgate gives. {@code /bedrock-check <name>} reports it.
 *
 * <p>The row is experimental, so the class plans a cell only when {@code -Dproofstone.cells} (or {@code PROOFSTONE_CELLS}) names it,
 * here {@code geyser@2.12.1+paper@26.3}. The cell is offline-stub: Xbox login validation is off, so a plugin that needs a verified
 * identity cannot be tested on it (docs/user/bedrock.md).
 */
@Proofstone(plugins = "build/plugin/proofstone-bedrock-prefix.jar")
@Matrix(platform = Platform.GEYSER, versions = {"latest"})
@Requires(Capability.BEDROCK_CLIENT)
class BedrockBranchE2E {

    /** Geyser starts every system message a Bedrock client gets with a reset code; the Java client gets plain text. */
    private static final String RESET = "§r";

    @CellTest
    void theBedrockBranchRunsForARealBedrockClient(Agent agent, Clients clients) {
        Client bedrock = clients.bedrock("PsEx1");

        // The server sees the Floodgate name, so the plugin's prefix check has something to find.
        assertThat(agent.player(bedrock).name()).isEqualTo(".PsEx1");
        bedrock.command("bedrock-check .PsEx1");
        bedrock.awaitMessage(Pattern.compile("^" + RESET + "bedrock-check \\.PsEx1 online=true bedrock=true$"));
    }

    @CellTest
    void theBedrockBranchDoesNotRunForAJavaClient(Client java, Clients clients) {
        Client bedrock = clients.bedrock("PsEx2");

        // The negative side: the same plugin must not call a Java player Bedrock.
        bedrock.command("bedrock-check " + java.name());
        bedrock.awaitMessage(Pattern.compile("^" + RESET + "bedrock-check " + Pattern.quote(java.name())
                + " online=true bedrock=false$"));
    }
}
