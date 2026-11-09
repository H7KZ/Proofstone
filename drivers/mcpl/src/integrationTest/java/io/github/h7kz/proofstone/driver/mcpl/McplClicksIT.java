package io.github.h7kz.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.CellPlanner;
import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.Face;
import io.github.h7kz.proofstone.core.driver.SlotStack;
import io.github.h7kz.proofstone.core.driver.WindowSession;
import io.github.h7kz.proofstone.core.host.Eula;
import io.github.h7kz.proofstone.core.host.PaperHost;
import io.github.h7kz.proofstone.core.host.PaperHostOptions;
import io.github.h7kz.proofstone.core.versions.VersionsCatalog;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-M2-05 on a real Paper server: server-authoritative clicks in a chest and an anvil, the cursor, and the player
 * inventory after a close. Every assertion reads state the server synced, and every wait goes through {@link Await}.
 * Needs Docker and an accepted EULA ({@value Eula#VARIABLE}=true); without it the class is skipped with that reason.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McplClicksIT {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final String PLAYER = "ClickBot";

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
        bot.awaitMessage(Pattern.compile("joined"), WAIT);
        host.command("setblock 0 63 0 minecraft:stone");
        host.command("setblock 2 64 0 minecraft:chest");
        host.command("item replace block 2 64 0 container.0 with minecraft:diamond 5");
        host.command("setblock 0 63 2 minecraft:stone");
        host.command("setblock 0 64 2 minecraft:anvil");
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
    void chestMoveIsConfirmedByTheServer20TimesThenCloseResyncsTheInventory() {
        bot.useBlock(new BlockPos(2, 64, 0), Face.UP);
        WindowSession chest = bot.awaitWindow(WAIT);
        assertThat(chest.type()).isEqualTo("minecraft:generic_9x3");
        SlotStack diamonds = chest.slots().get(0);
        assertThat(diamonds.amount()).isEqualTo(5);

        // C04: pick the stack up, put it down in another slot, and back, 20 times. The cursor and the slots come
        // from the server's answer to each click, never from the driver's own guess.
        for (int i = 0; i < 20; i++) {
            int from = i % 2 == 0 ? 0 : 4;
            int to = i % 2 == 0 ? 4 : 0;
            chest.click(from, Click.LEFT);
            assertThat(chest.cursor()).as("cursor after picking up (round %d)", i).isEqualTo(diamonds);
            assertThat(chest.slots().get(from).isEmpty()).isTrue();
            chest.click(to, Click.LEFT);
            assertThat(chest.cursor().isEmpty()).as("cursor after placing (round %d)", i).isTrue();
            assertThat(chest.slots().get(to)).isEqualTo(diamonds);
            assertThat(chest.slots().get(from).isEmpty()).isTrue();
        }

        // Right click splits off half: ceil(5 / 2) = 3 on the cursor, 2 left.
        chest.click(0, Click.RIGHT);
        assertThat(chest.cursor()).isEqualTo(new SlotStack(diamonds.registryId(), 3));
        assertThat(chest.slots().get(0)).isEqualTo(new SlotStack(diamonds.registryId(), 2));
        chest.click(0, Click.LEFT);

        // Close with the stack on the cursor: the server hands it back to the player. Window 0 is stale until the
        // server says so, so the driver waits for the state instead of reading it straight away.
        chest.click(0, Click.LEFT);
        assertThat(chest.cursor()).isEqualTo(diamonds);
        chest.close();
        Await.until("diamonds back in the inventory of " + PLAYER, () -> bot.inventory().slots(),
                slots -> slots.stream().anyMatch(s -> s.equals(diamonds)), WAIT);
        Await.until("empty cursor after close", bot.inventory()::cursor, SlotStack::isEmpty, WAIT);
    }

    @Test
    void anvilInputsAndResultAreServerState() {
        // The other test leaves diamonds behind; start from nothing so slot order is known.
        host.command("clear " + PLAYER);
        Await.until("empty inventory of " + PLAYER, () -> bot.inventory().slots(),
                slots -> slots.stream().allMatch(SlotStack::isEmpty), WAIT);
        host.command("give " + PLAYER + " minecraft:diamond_sword[damage=500]");
        host.command("give " + PLAYER + " minecraft:diamond 1");
        Await.until("sword in the inventory of " + PLAYER, () -> bot.inventory().slots(),
                slots -> slots.stream().filter(s -> !s.isEmpty()).count() >= 2, WAIT);

        bot.useBlock(new BlockPos(0, 64, 2), Face.UP);
        WindowSession anvil = bot.awaitWindow(WAIT);
        assertThat(anvil.type()).isEqualTo("minecraft:anvil");
        // Anvil slots are 0 left input, 1 right input, 2 result, then the player's 36.
        int sword = firstNonEmptyFrom(anvil, 3);
        int gem = lastNonEmptyFrom(anvil, 3);
        assertThat(sword).isNotEqualTo(gem);
        SlotStack swordStack = anvil.slots().get(sword);
        SlotStack gemStack = anvil.slots().get(gem);

        anvil.click(sword, Click.LEFT);
        assertThat(anvil.cursor()).isEqualTo(swordStack);
        anvil.click(0, Click.LEFT);
        assertThat(anvil.slots().get(0)).isEqualTo(swordStack);
        assertThat(anvil.cursor().isEmpty()).isTrue();
        anvil.click(gem, Click.LEFT);
        anvil.click(1, Click.LEFT);
        assertThat(anvil.slots().get(1)).isEqualTo(gemStack);

        // The result slot fills about a tick after both inputs are in (docs/platforms/paper.md): await it.
        SlotStack result = Await.until("anvil result slot", () -> anvil.slots().get(2), s -> !s.isEmpty(), WAIT);
        assertThat(result.registryId()).isEqualTo(swordStack.registryId());

        // Taking the result needs experience the bot does not have, so the click is a server-side refusal; the
        // inputs go back to the player on close and the cursor stays empty.
        anvil.close();
        Await.until("sword back in the inventory of " + PLAYER, () -> bot.inventory().slots(),
                slots -> slots.stream().anyMatch(s -> s.equals(swordStack)), WAIT);
        assertThat(bot.inventory().slots().stream().anyMatch(s -> s.equals(gemStack))).isTrue();
    }

    private static int firstNonEmptyFrom(WindowSession w, int from) {
        for (int i = from; i < w.slots().size(); i++) {
            if (!w.slots().get(i).isEmpty()) {
                return i;
            }
        }
        throw new AssertionError("no item in the player part of " + w.type());
    }

    private static int lastNonEmptyFrom(WindowSession w, int from) {
        for (int i = w.slots().size() - 1; i >= from; i--) {
            if (!w.slots().get(i).isEmpty()) {
                return i;
            }
        }
        throw new AssertionError("no item in the player part of " + w.type());
    }
}
