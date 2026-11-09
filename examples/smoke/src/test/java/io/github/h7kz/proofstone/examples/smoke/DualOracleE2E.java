package io.github.h7kz.proofstone.examples.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.DisagreeException;
import io.github.h7kz.proofstone.api.DualAssert;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.PlayerHandle;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Position;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Window;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * T-M2-07 on a real server: an item is given through the agent, a window is opened with the client, and the dual
 * oracle compares what the agent reports with what the client received.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"latest"})
class DualOracleE2E {

    private static final BlockPos ANVIL = new BlockPos(0, 64, 2);
    private static final Map<String, Integer> SWORD_ENCHANTS = Map.of("minecraft:sharpness", 5,
            "minecraft:unbreaking", 3);

    @CellTest
    void givenItemIsSeenByBothOraclesBeforeDuringAndAfterAWindow(Agent agent, Client client) {
        PlayerHandle player = standNextToAnAnvil(agent, client);
        player.give(Item.of("diamond_sword").name("Blade").enchant("sharpness", 5).enchant("unbreaking", 3));

        DualAssert.item(player, client).inInventory(sword())
                .amount(1).name("Blade").enchants(SWORD_ENCHANTS);

        try (Window anvil = client.openBlock(ANVIL)) {
            assertThat(anvil.type()).isEqualTo("minecraft:anvil");
            anvil.moveFromInventory(sword(), 0);
            ItemView input = anvil.awaitSlot(0, item -> item.is("diamond_sword"));
            assertThat(input.enchants()).isEqualTo(SWORD_ENCHANTS);
            assertThat(anvil.cursor().isEmpty()).isTrue();
            // The server agrees: the sword left the inventory for the anvil.
            player.awaitState("sword moved into the anvil",
                    state -> state.inventory().values().stream().noneMatch(item -> item.is("diamond_sword")));
        }

        // Closing returns the input to the inventory, and both oracles must see it come back.
        DualAssert.item(player, client).inInventory(sword()).name("Blade").enchants(SWORD_ENCHANTS);
    }

    @CellTest
    void bookAndSwordKeepTheirOwnFieldsOnBothOracles(Agent agent, Client client) {
        PlayerHandle player = standNextToAnAnvil(agent, client);
        player.give(Item.of("enchanted_book").storedEnchant("sharpness", 5).storedEnchant("mending", 1));

        DualAssert.item(player, client).inInventory(item -> item.is("enchanted_book"))
                .storedEnchants(Map.of("minecraft:sharpness", 5, "minecraft:mending", 1))
                .enchants(Map.of());
    }

    @CellTest
    void oraclesThatDisagreeFailWithBothValuesInTheMessage(Agent agent, Client client) {
        PlayerHandle player = standNextToAnAnvil(agent, client);
        player.give(Item.of("diamond_sword").enchant("sharpness", 5));
        // The real server and the real client agree on this item (the first test proves it). The client below is
        // the same one with a ghost: it reports sharpness 4, as a desynced client would.
        Client desynced = new GhostClient(client, "minecraft:sharpness", 4);

        assertThatThrownBy(() -> DualAssert.item(player, desynced).inInventory(sword())
                .enchants(Map.of("minecraft:sharpness", 5)))
                .isInstanceOfSatisfying(DisagreeException.class, e -> {
                    assertThat(e.getMessage()).startsWith("DISAGREE").contains("expected {minecraft:sharpness=5}")
                            .contains("server oracle (agent):  {minecraft:sharpness=5}")
                            .contains("client oracle (driver): {minecraft:sharpness=4}").contains("ms");
                    assertThat(e.serverObserved()).contains("sharpness=5");
                    assertThat(e.clientObserved()).contains("sharpness=4");
                });
        // The same assertion on the honest client passes, so the failure above is the disagreement and nothing else.
        DualAssert.item(player, client).inInventory(sword()).enchants(Map.of("minecraft:sharpness", 5));
    }

    @CellTest
    void serverOnlyNeedsAReasonAndFailsWithItWhenTheServerValueDiffers(Agent agent, Client client) {
        PlayerHandle player = standNextToAnAnvil(agent, client);
        player.give(Item.of("diamond_sword"));
        DualAssert.ItemAssert selected = DualAssert.item(player, client).inInventory(sword());

        assertThatThrownBy(() -> selected.serverOnly("  ")).isInstanceOf(IllegalArgumentException.class);
        // A server-only check on a tag nobody set fails, and names why it was server only.
        assertThatThrownBy(() -> selected.serverOnly("PDC is not sent to clients").pdc("proofstone:none", "x"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("PDC is not sent to clients");
        assertThat(DualAssert.serverOnlyReasons()).contains("PDC is not sent to clients");
    }

    /** Joins the world at a spot with ground, an anvil in reach and an empty inventory. */
    private static PlayerHandle standNextToAnAnvil(Agent agent, Client client) {
        // Reset first: it also teleports the player to the fixture spot, which would undo the teleport below.
        PlayerHandle player = agent.player(client).reset();
        player.awaitState("empty inventory", state -> state.inventory().isEmpty());
        // Chunks load around players only, and flat worlds spawn underground (docs/platforms/paper.md).
        agent.command("setblock 0 63 0 minecraft:stone");
        agent.command("setblock 0 63 2 minecraft:stone");
        agent.command("setblock 0 64 2 minecraft:anvil");
        agent.command("tp " + client.name() + " 0.5 64 0.5");
        Position at = client.awaitPosition("teleport to the anvil", p -> p.y() == 64.0);
        assertThat(at.x()).isEqualTo(0.5);
        return player;
    }

    private static Predicate<ItemView> sword() {
        return item -> item.is("diamond_sword");
    }

    /** The real client, except that its inventory reports one enchantment at the wrong level. */
    private record GhostClient(Client real, String enchantment, int level) implements Client {

        @Override
        public String name() {
            return real.name();
        }

        @Override
        public void command(String commandWithoutSlash) {
            real.command(commandWithoutSlash);
        }

        @Override
        public String awaitMessage(Pattern pattern) {
            return real.awaitMessage(pattern);
        }

        @Override
        public Position position() {
            return real.position();
        }

        @Override
        public Position awaitPosition(String description, Predicate<Position> predicate) {
            return real.awaitPosition(description, predicate);
        }

        @Override
        public Window openBlock(BlockPos pos) {
            return real.openBlock(pos);
        }

        @Override
        public int experienceLevel() {
            return real.experienceLevel();
        }

        @Override
        public List<ItemView> inventory() {
            return real.inventory().stream().map(item -> item.enchants().containsKey(enchantment) ? ghost(item) : item)
                    .toList();
        }

        private ItemView ghost(ItemView item) {
            Map<String, Integer> enchants = new java.util.HashMap<>(item.enchants());
            enchants.put(enchantment, level);
            return new ItemView(item.type(), item.amount(), enchants, item.storedEnchants(), item.name(), item.lore(),
                    item.damage(), item.repairCost(), item.customData());
        }
    }
}
