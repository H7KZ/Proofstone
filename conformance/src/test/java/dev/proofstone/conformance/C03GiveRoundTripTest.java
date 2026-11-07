package dev.proofstone.conformance;

import dev.proofstone.api.Agent;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Client;
import dev.proofstone.api.DualAssert;
import dev.proofstone.api.Item;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.PlayerHandle;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Proofstone;
import java.util.Map;

/**
 * C03 (specs/self-tests.md §1): an item the agent gives reads back the same on both oracles. The sharpness level is
 * the server's cap, 255, so a pass also shows that the harness does not clamp levels (docs/platforms/paper.md).
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class C03GiveRoundTripTest {

    @CellTest
    void givenItemsReadTheSameOnBothOracles(Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);

        player.give(Item.of("diamond_sword").name("Blade").enchant("sharpness", 255).enchant("unbreaking", 3));
        DualAssert.item(player, client).inInventory(ConformanceFixture.sword()).amount(1).name("Blade")
                .enchants(Map.of("minecraft:sharpness", 255, "minecraft:unbreaking", 3));

        player.give(Item.of("enchanted_book").storedEnchant("sharpness", 5).storedEnchant("knockback", 2));
        DualAssert.item(player, client).inInventory(item -> item.is("enchanted_book")).amount(1)
                .storedEnchants(Map.of("minecraft:sharpness", 5, "minecraft:knockback", 2)).enchants(Map.of());
    }
}
