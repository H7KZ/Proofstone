package dev.proofstone.examples.smoke;

import dev.proofstone.api.Agent;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Client;
import dev.proofstone.api.DualAssert;
import dev.proofstone.api.Item;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Proofstone;
import java.util.Map;

/**
 * T-M4-06: a test both drivers agree on. Without {@code -Dproofstone.differential=true} it is one ordinary run on
 * 26.1.2 with mcpl. With it, the test runs again with the reference driver (native on 26.1.2), and the two runs'
 * client observations must match, so it passes with no DISAGREE.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"26.1.2"})
class DifferentialAgreeE2E {

    @CellTest
    void bothDriversSeeTheSameGivenSword(Agent agent, Client client) {
        agent.player(client).give(Item.of("diamond_sword").name("Blade").enchant("sharpness", 5));
        DualAssert.item(agent.player(client), client).inInventory(item -> item.is("diamond_sword"))
                .amount(1).name("Blade").enchants(Map.of("minecraft:sharpness", 5));
    }
}
