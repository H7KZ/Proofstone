package io.github.h7kz.proofstone.examples.smoke;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.DualAssert;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
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
