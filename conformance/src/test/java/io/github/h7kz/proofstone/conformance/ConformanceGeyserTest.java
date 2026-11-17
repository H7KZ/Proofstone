package io.github.h7kz.proofstone.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.api.Capability;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Clients;
import io.github.h7kz.proofstone.api.DualAssert;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Requires;
import java.util.regex.Pattern;

/**
 * The Geyser row's conformance (specs/self-tests.md section 1d, ADR-0018 section 9, T-M9-08). Before the first test the cell's
 * own cached check runs (the stand-in was asked exactly the two expected Microsoft names, the name guard is clean, one Bedrock
 * client joined and left); a red makes every test here INVALID. C-G01 to C-G04 are the Bedrock round trips a client can do in
 * driver v1. What it cannot do is listed as skipped tests with the reason, never passed (the last four methods).
 *
 * <p>Run one Geyser cell per invocation: {@code -Dproofstone.cells=geyser@2.12.1+paper@26.3}. {@code :conformance:run} runs
 * this class only when the selector names a Geyser cell (build.gradle.kts). The cell is offline-stub: Xbox login validation is
 * off (ADR-0018 section 4). The plugin of C-G04 is the example in examples/bedrock-prefix, the one C-B01 uses.
 */
@Proofstone(plugins = "build/bedrock-prefix-plugins/*.jar")
@Matrix(platform = Platform.GEYSER, versions = {"latest"})
@Requires(Capability.BEDROCK_CLIENT)
class ConformanceGeyserTest {

    /**
     * Geyser prefixes every system message it sends a Bedrock client with a reset code (section sign, {@code r}); the Java
     * client gets the plain text. Asserted as it is (docs/platforms/geyser.md, T-M9-08).
     */
    private static final String RESET = "§r";

    /** C-G01: a Bedrock client joins, and the agent sees the player under the Floodgate name. */
    @CellTest
    void cG01TheAgentSeesTheBedrockPlayerUnderTheFloodgateName(Cell cell, Agent agent, Clients clients) {
        assertThat(cell.capabilities()).contains(Capability.BEDROCK_CLIENT).doesNotContain(Capability.PROXY);
        Client bedrock = clients.bedrock("PsG01");

        assertThat(bedrock.name()).isEqualTo(".PsG01");
        assertThat(agent.player(bedrock).name()).isEqualTo(".PsG01");
        assertThat(agent.player(bedrock).state().name()).isEqualTo(".PsG01");
        assertThat(bedrock.position()).isNotNull();
    }

    /** C-G02: a command the Bedrock client types is run by the server and its reply comes back to that client. */
    @CellTest
    void cG02ACommandTypedByTheBedrockClientRepliesToIt(Clients clients) {
        Client bedrock = clients.bedrock("PsG02");

        // Paper's /plugins is open to every player. Vanilla /list is not used: the server answered the Bedrock client
        // "Unknown or incomplete command" (docs/platforms/geyser.md, T-M9-08).
        bedrock.command("plugins");
        // Paper sends the header and the plugin list as separate messages; both reach the Bedrock client.
        bedrock.awaitMessage(Pattern.compile("Server Plugins \\(\\d+\\)"));
        assertThat(bedrock.awaitMessage(Pattern.compile("ProofstoneBedrockPrefix"))).contains("ProofstoneBedrockPrefix");
    }

    /** C-G03: an item the agent gives is in the Bedrock client's inventory, on both oracles (id and count only). */
    @CellTest
    void cG03AGivenItemReadsBackOnBothOracles(Agent agent, Clients clients) {
        Client bedrock = clients.bedrock("PsG03");

        agent.player(bedrock).give(Item.of("minecraft:stick").amount(3));
        DualAssert.item(agent.player(bedrock), bedrock).inInventory(i -> i.is("stick")).amount(3);
        agent.player(bedrock).give(Item.of("minecraft:diamond_sword"));
        DualAssert.item(agent.player(bedrock), bedrock).inInventory(i -> i.is("diamond_sword")).amount(1);
    }

    /**
     * C-G04, the counterpart of C-B01: a plugin's Bedrock-only branch with a real Bedrock client. The plugin detects the
     * {@code .} prefix Floodgate gives; the Bedrock client is seen as Bedrock, and the Java client is not.
     */
    @CellTest
    void cG04ThePluginsBedrockBranchRunsForARealBedrockClientAndNotForAJavaOne(Client java, Clients clients) {
        Client bedrock = clients.bedrock("PsG04");

        bedrock.command("bedrock-check .PsG04");
        bedrock.awaitMessage(Pattern.compile("^" + RESET + "bedrock-check \\.PsG04 online=true bedrock=true$"));
        java.command("bedrock-check .PsG04");
        java.awaitMessage(Pattern.compile("^bedrock-check \\.PsG04 online=true bedrock=true$"));
        // The negative: the Java client has no prefix, so the same plugin must not call it Bedrock.
        bedrock.command("bedrock-check " + java.name());
        bedrock.awaitMessage(Pattern.compile("^" + RESET + "bedrock-check " + Pattern.quote(java.name())
                + " online=true bedrock=false$"));
    }

    // Skipped, with the reason in the XML (never passed): the Bedrock driver of v1 has no window, form or resource-pack support
    // (ADR-0018 section 2). Each first shows that the driver refuses the call, so the skip is true, then aborts with the reason.

    @CellTest
    void skippedWindowClicksAndUseBlock(Clients clients) {
        Client bedrock = clients.bedrock("PsG05");

        assertThatThrownBy(() -> bedrock.openBlock(new BlockPos(0, 64, 2))).isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ADR-0018");
        abort("C04 to C08 equivalents (useBlock, window clicks): a Bedrock window needs ContainerOpen, InventoryTransaction and "
                + "ItemStackRequest packets, not in driver v1 (ADR-0018 section 2)");
    }

    @CellTest
    void skippedForms() {
        abort("forms (Floodgate and Bedrock forms) are not in Bedrock driver v1: no form packet is read or answered "
                + "(ADR-0018 section 2)");
    }

    @CellTest
    void skippedResourcePacks(Clients clients) {
        Client bedrock = clients.bedrock("PsG06");

        assertThatThrownBy(bedrock::resourcePacks).isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ADR-0018");
        abort("resource packs: the Bedrock driver does not answer a pack push in v1 and RESOURCE_PACKS is not held on a Geyser "
                + "cell (ADR-0018 section 2)");
    }
}
