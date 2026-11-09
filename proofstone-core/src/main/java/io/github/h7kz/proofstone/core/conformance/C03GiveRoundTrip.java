package io.github.h7kz.proofstone.core.conformance;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.DualAssert;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.PlayerHandle;
import java.util.Map;

/**
 * C03 (specs/self-tests.md §1): an item the agent gives reads back the same on both oracles. The sharpness level is
 * the server's cap, 255, so a pass also shows that the harness does not clamp levels (docs/platforms/paper.md).
 */
public final class C03GiveRoundTrip {

    private C03GiveRoundTrip() {
    }

    /** Runs the scenario on a joined client. Throws an {@link AssertionError} when an oracle disagrees. */
    public static void run(Cell cell, Agent agent, Client client) {
        PlayerHandle player = ConformanceFixture.stand(agent, client);

        player.give(Item.of("diamond_sword").name("Blade").enchant("sharpness", 255).enchant("unbreaking", 3));
        DualAssert.item(player, client).inInventory(ConformanceFixture.sword()).amount(1).name("Blade")
                .enchants(Map.of("minecraft:sharpness", 255, "minecraft:unbreaking", 3));

        player.give(Item.of("enchanted_book").storedEnchant("sharpness", 5).storedEnchant("knockback", 2));
        DualAssert.item(player, client).inInventory(item -> item.is("enchanted_book")).amount(1)
                .storedEnchants(Map.of("minecraft:sharpness", 5, "minecraft:knockback", 2)).enchants(Map.of());

        // Mending arrived in 1.9, so 1.8.8 has no such enchantment; every later row keeps the original check.
        if (!cell.version().startsWith("1.8.")) {
            player.give(Item.of("enchanted_book").storedEnchant("mending", 1));
            DualAssert.item(player, client).inInventory(item -> item.is("enchanted_book")
                    && item.storedEnchants().containsKey("minecraft:mending")).amount(1)
                    .storedEnchants(Map.of("minecraft:mending", 1)).enchants(Map.of());
        }
    }
}
