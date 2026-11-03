package dev.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.proofstone.core.await.Await;
import dev.proofstone.core.cell.Cell;
import dev.proofstone.core.cell.CellPlanner;
import dev.proofstone.core.driver.BlockPos;
import dev.proofstone.core.driver.ClientSession;
import dev.proofstone.core.driver.ConnectSpec;
import dev.proofstone.core.driver.Face;
import dev.proofstone.core.driver.SlotStack;
import dev.proofstone.core.driver.WindowSession;
import dev.proofstone.core.host.Eula;
import dev.proofstone.core.host.PaperHost;
import dev.proofstone.core.host.PaperHostOptions;
import dev.proofstone.core.versions.VersionsCatalog;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-M2-04 on a real Paper server: the driver right-clicks a chest, an anvil and a grindstone and mirrors what the
 * server syncs. The blocks come from vanilla console commands through the agent, because the agent's world and
 * window endpoints are T-M2-02; the "matches the agent openWindow view" check waits for that card. Needs Docker and
 * an accepted EULA ({@value Eula#VARIABLE}=true); without it the class is skipped with that reason (ADR-0005).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McplWindowsIT {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final String PLAYER = "WinBot";
    private static final int PLAYER_SLOTS = 36;

    private PaperHost host;
    private ClientSession bot;

    @BeforeAll
    void bootAndJoin() {
        assumeTrue(Eula.accepted(), "Minecraft EULA not accepted: set " + Eula.VARIABLE + "=true");
        Path root = Path.of(System.getProperty("proofstone.repoRoot"));
        VersionsCatalog catalog = VersionsCatalog.load(root.resolve("versions.yml"),
                root.resolve("proofstone-protocol/schemas/versions.schema.json"));
        Cell cell = new CellPlanner(catalog).plan("paper", List.of("latest")).get(0);
        host = PaperHost.start(cell, PaperHostOptions.of(Path.of(System.getProperty("proofstone.agentJar")), true));
        bot = new McplDriver().connect(new ConnectSpec(host.gameHost(), host.gamePort(), PLAYER,
                Duration.ofSeconds(60)));
        // Only chunks around a player are loaded, so the world is built after the join ("That position is not
        // loaded" otherwise). Flat worlds spawn underground (docs/platforms/paper.md): stone at (0, 63, 0) and a
        // teleport onto it give ground at y 64.
        bot.awaitMessage(Pattern.compile("joined"), WAIT);
        host.command("setblock 0 63 0 minecraft:stone");
        // Three blocks within reach of (0.5, 64, 0.5): a chest holding two stacks, an anvil and a grindstone.
        host.command("setblock 2 64 0 minecraft:chest");
        host.command("item replace block 2 64 0 container.0 with minecraft:diamond 5");
        host.command("item replace block 2 64 0 container.1 with minecraft:stick 3");
        // An anvil falls and a floor grindstone breaks without a block below.
        host.command("setblock 0 63 2 minecraft:stone");
        host.command("setblock -2 63 0 minecraft:stone");
        host.command("setblock 0 64 2 minecraft:anvil");
        host.command("setblock -2 64 0 minecraft:grindstone");
        host.command("tp " + PLAYER + " 0.5 64 0.5");
        Await.until("teleport of " + PLAYER + " seen by the client", ((McplSession) bot)::position,
                p -> p != null && p[1] == 64.0, WAIT);
    }

    @AfterAll
    void stop() {
        if (bot != null) {
            bot.close();
        }
        if (host != null) {
            host.close();
        }
    }

    @Test
    void chestAnvilAndGrindstoneOpenWithTheServersTypeAndSlotCount() {
        host.command("give " + PLAYER + " minecraft:apple 7");
        // Window 0 is synced on its own, apart from any open window.
        Await.until("apples in the player inventory of " + PLAYER, () -> bot.inventory().slots(),
                slots -> slots.stream().anyMatch(s -> s.amount() == 7), WAIT);
        assertThat(bot.inventory().id()).isZero();
        assertThat(bot.inventory().slots()).hasSize(46);

        bot.useBlock(new BlockPos(2, 64, 0), Face.UP);
        WindowSession chest = bot.awaitWindow(WAIT);
        assertThat(chest.type()).isEqualTo("minecraft:generic_9x3");
        assertThat(chest.id()).isPositive();
        assertThat(chest.slots()).hasSize(27 + PLAYER_SLOTS);
        SlotStack diamonds = chest.slots().get(0);
        SlotStack sticks = chest.slots().get(1);
        assertThat(diamonds.amount()).isEqualTo(5);
        assertThat(sticks.amount()).isEqualTo(3);
        assertThat(diamonds.registryId()).isNotEqualTo(sticks.registryId());
        assertThat(chest.slots().subList(2, 27)).allMatch(SlotStack::isEmpty);
        // The tail of a container window is the player's 36 inventory slots, the same items window 0 holds at 9..44.
        assertThat(chest.slots().subList(27, 27 + PLAYER_SLOTS))
                .isEqualTo(bot.inventory().slots().subList(9, 9 + PLAYER_SLOTS));
        assertThat(chest.cursor().isEmpty()).isTrue();
        chest.close();

        bot.useBlock(new BlockPos(0, 64, 2), Face.UP);
        WindowSession anvil = bot.awaitWindow(WAIT);
        assertThat(anvil.type()).isEqualTo("minecraft:anvil");
        assertThat(anvil.slots()).hasSize(3 + PLAYER_SLOTS);
        assertThat(anvil.slots().subList(0, 3)).allMatch(SlotStack::isEmpty);
        anvil.close();

        bot.useBlock(new BlockPos(-2, 64, 0), Face.UP);
        WindowSession grindstone = bot.awaitWindow(WAIT);
        assertThat(grindstone.type()).isEqualTo("minecraft:grindstone");
        assertThat(grindstone.slots()).hasSize(3 + PLAYER_SLOTS);
        assertThat(grindstone.slots().subList(0, 3)).allMatch(SlotStack::isEmpty);
        grindstone.close();

        // The player inventory model kept its own id and size through all three windows.
        assertThat(bot.inventory().id()).isZero();
        assertThat(bot.inventory().slots()).hasSize(46);
        System.out.println("transcript tail: " + bot.transcript().stream().filter(l -> l.contains("Container")
                || l.contains("OpenScreen") || l.contains("UseItemOn")).toList());
    }
}
