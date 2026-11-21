package io.github.h7kz.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.core.driver.SlotStack;
import io.github.h7kz.proofstone.core.driver.WindowSession;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.junit.jupiter.api.Test;

/**
 * T-M11-07: a window packet the server sent before it read the client's close is still the server's word on the player's
 * slots. The sequence is the one three Folia runs of the Disenchantment suite recorded (RestrictionsE2E, DisenchantE2E,
 * PreEventCancelE2E): the click that places the cursor book into a player slot is sent as the late resync of the click
 * before arrives (a plugin task two ticks later), the click returns on that resync, the test closes, and the answer that
 * holds the book in slot 9 arrives for a window the driver already closed. The server moved the anvil's remote slots to
 * window 0 on close (vanilla transferState), so it never resends slot 9. Scripted packets, no server.
 */
class McplLateWindowPacketTest {

    private static final Duration LONG = Duration.ofSeconds(5);
    private static final int BOOK = 11;
    private static final int SWORD = 12;
    private static final int ANVIL_SLOTS = 3 + 36;
    /** Container slot of the first main-inventory slot in an anvil: window 0 slot 9. */
    private static final int FIRST_MAIN = 3;

    private final LinkedBlockingQueue<Object> sent = new LinkedBlockingQueue<>();
    private final Session network = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(),
            new Class<?>[] {Session.class}, (proxy, method, args) -> {
                if (method.getName().equals("send") && args != null && args.length > 0) {
                    sent.add(args[0]);
                }
                return null;
            });
    private final McplSession session = new McplSession(network, "Bot");

    private void received(Packet packet) {
        session.listener().packetReceived(network, packet);
    }

    private void join() {
        received(new ClientboundLoginPacket(1, false, new Key[0], 20, 8, 8, false, true, false, null, false, false));
        received(new ClientboundPlayerPositionPacket(1, Vector3d.from(0, 64, 0), Vector3d.ZERO, 0, 0, List.of()));
        received(new ClientboundSetHealthPacket(20f, 20, 5f));
        ItemStack[] windowZero = new ItemStack[46];
        windowZero[36] = new ItemStack(SWORD, 1);
        received(new ClientboundContainerSetContentPacket(0, 1, windowZero, null));
    }

    /** The anvil after the result click: the stripped sword in input 0, the book on the cursor, slot 9 empty. */
    private static ItemStack[] anvilBeforeThePlace() {
        ItemStack[] anvil = new ItemStack[ANVIL_SLOTS];
        anvil[0] = new ItemStack(SWORD, 1);
        return anvil;
    }

    private WindowSession openAnvil() {
        received(new ClientboundOpenScreenPacket(1, ContainerType.ANVIL, Component.text("Repair & Name")));
        received(new ClientboundContainerSetSlotPacket(1, 13, 2, null));
        received(new ClientboundContainerSetContentPacket(1, 13, anvilBeforeThePlace(), new ItemStack(BOOK, 1)));
        return session.awaitWindow(LONG);
    }

    private SlotStack windowZero(int slot) {
        return session.inventory().slots().get(slot);
    }

    @Test
    void theAnswerToTheLastClickArrivingAfterTheCloseLandsInThePlayerInventory() throws Exception {
        join();
        WindowSession anvil = openAnvil();

        // The place click goes out; before the server reads it, the plugin's late resync of the click before arrives.
        sent.clear(); // the join's teleport acknowledgement and loaded packets
        CompletableFuture<Void> click = CompletableFuture.runAsync(() -> anvil.click(FIRST_MAIN, Click.LEFT));
        assertThat(sent.poll(5, TimeUnit.SECONDS)).isInstanceOf(ServerboundContainerClickPacket.class);
        received(new ClientboundContainerSetSlotPacket(1, 14, 1, null));
        received(new ClientboundContainerSetContentPacket(1, 15, anvilBeforeThePlace(), new ItemStack(BOOK, 1)));
        click.get(5, TimeUnit.SECONDS);
        anvil.close();

        // The click's own answer: the book placed in the first main slot. It carries a stack (the recorded one carried
        // none) so the cursor assertion below can fail: the server returns or drops a carried stack when it reads the
        // close, so a closed window's carried item must not reach the client cursor.
        ItemStack[] answered = anvilBeforeThePlace();
        answered[FIRST_MAIN] = new ItemStack(BOOK, 1);
        received(new ClientboundContainerSetContentPacket(1, 16, answered, new ItemStack(SWORD, 1)));
        // Then window 0 after the server's close: the offhand, and the input sword handed back to hotbar 0.
        received(new ClientboundContainerSetSlotPacket(0, 107, 45, null));
        received(new ClientboundSetPlayerInventoryPacket(0, new ItemStack(SWORD, 1)));
        received(new ClientboundContainerSetSlotPacket(0, 108, 36, new ItemStack(SWORD, 1)));

        assertThat(windowZero(9)).as("the book the server placed in slot 9 before it read the close")
                .isEqualTo(new SlotStack(BOOK, 1));
        assertThat(windowZero(36)).isEqualTo(new SlotStack(SWORD, 1));
        assertThat(session.inventory().cursor().isEmpty()).as("a closed window does not hand its cursor back").isTrue();
    }

    @Test
    void aLateSlotUpdateOfThePlayerSectionLandsInWindowZero() {
        join();
        WindowSession anvil = openAnvil();
        anvil.close();

        received(new ClientboundContainerSetSlotPacket(1, 16, FIRST_MAIN, new ItemStack(BOOK, 1)));

        assertThat(windowZero(9)).isEqualTo(new SlotStack(BOOK, 1));
    }

    @Test
    void aLateSlotUpdateOfTheAnvilItselfLeavesWindowZeroAlone() {
        join();
        WindowSession anvil = openAnvil();
        anvil.close();
        List<SlotStack> before = session.inventory().slots();

        received(new ClientboundContainerSetSlotPacket(1, 16, 1, new ItemStack(BOOK, 1)));

        assertThat(session.inventory().slots()).isEqualTo(before);
    }

    @Test
    void aLateSingleSlotDoesNotUndoAHandBackThatCameBeforeIt() {
        join();
        WindowSession anvil = openAnvil();
        anvil.close();

        received(new ClientboundSetPlayerInventoryPacket(10, new ItemStack(SWORD, 1)));
        received(new ClientboundContainerSetSlotPacket(1, 16, FIRST_MAIN, new ItemStack(BOOK, 1)));

        assertThat(windowZero(10)).as("only the slot the late packet names is taken").isEqualTo(new SlotStack(SWORD, 1));
        assertThat(windowZero(9)).isEqualTo(new SlotStack(BOOK, 1));
    }

    @Test
    void aNewWindowWithTheSameIdTakesItsOwnPackets() {
        join();
        WindowSession first = openAnvil();
        first.close();

        received(new ClientboundOpenScreenPacket(1, ContainerType.ANVIL, Component.text("Repair & Name")));
        received(new ClientboundContainerSetContentPacket(1, 1, new ItemStack[ANVIL_SLOTS], null));
        WindowSession second = session.awaitWindow(LONG);
        received(new ClientboundContainerSetSlotPacket(1, 2, FIRST_MAIN, new ItemStack(BOOK, 1)));

        assertThat(second.slots().get(FIRST_MAIN)).isEqualTo(new SlotStack(BOOK, 1));
        assertThat(first.slots().get(FIRST_MAIN).isEmpty()).as("the closed window is no longer written").isTrue();
    }

    @Test
    void aWindowTheServerClosedTakesNoLatePackets() {
        join();
        openAnvil();
        received(new ClientboundContainerClosePacket(1));

        received(new ClientboundContainerSetSlotPacket(1, 16, FIRST_MAIN, new ItemStack(BOOK, 1)));

        assertThat(windowZero(9).isEmpty()).as("the server sends nothing for a window it closed itself").isTrue();
    }

    @Test
    void closingAWindowTheServerAlreadyClosedDoesNotMakeItALateTarget() {
        join();
        WindowSession anvil = openAnvil();
        received(new ClientboundContainerClosePacket(1));
        anvil.close();

        received(new ClientboundContainerSetSlotPacket(1, 16, FIRST_MAIN, new ItemStack(BOOK, 1)));

        assertThat(windowZero(9).isEmpty()).isTrue();
    }

    @Test
    void openingAnotherWindowEndsTheLateTargetOfTheClosedOne() {
        join();
        WindowSession first = openAnvil();
        first.close();

        // A different id, so only the clear on OpenScreen keeps window 1 from taking the packet below.
        received(new ClientboundOpenScreenPacket(2, ContainerType.ANVIL, Component.text("Repair & Name")));
        received(new ClientboundContainerSetContentPacket(2, 1, new ItemStack[ANVIL_SLOTS], null));
        session.awaitWindow(LONG);
        received(new ClientboundContainerSetSlotPacket(1, 16, FIRST_MAIN, new ItemStack(BOOK, 1)));

        assertThat(windowZero(9).isEmpty()).as("window 1 stopped being a late target when window 2 opened").isTrue();
        assertThat(first.slots().get(FIRST_MAIN).isEmpty()).isTrue();
    }

    @Test
    void aNewServerJoinEndsTheLateTarget() {
        join();
        WindowSession anvil = openAnvil();
        anvil.close();

        // A proxy switch: configuration again, then the new server's login and window 0 (resetViews).
        received(new ClientboundStartConfigurationPacket());
        received(new ClientboundFinishConfigurationPacket());
        join();
        received(new ClientboundContainerSetSlotPacket(1, 16, FIRST_MAIN, new ItemStack(BOOK, 1)));

        assertThat(windowZero(9).isEmpty()).as("the previous server's closed window takes nothing on the new one").isTrue();
    }

    @Test
    void aLateContentAfterAHandBackTakesAllPlayerSlotsBecauseTheServerSentItLater() {
        join();
        WindowSession anvil = openAnvil();
        anvil.close();

        // Pinned on purpose: packets arrive in the server's send order, and the server sends nothing for the window once
        // it read the close, so a late content was sent after any hand-back that precedes it and already reflects it.
        // A content that disagrees with an earlier hand-back is the server's newer word, and it wins.
        received(new ClientboundSetPlayerInventoryPacket(10, new ItemStack(SWORD, 1)));
        ItemStack[] content = anvilBeforeThePlace();
        content[FIRST_MAIN] = new ItemStack(BOOK, 1);
        received(new ClientboundContainerSetContentPacket(1, 16, content, null));

        assertThat(windowZero(9)).isEqualTo(new SlotStack(BOOK, 1));
        assertThat(windowZero(10).isEmpty()).as("the later content's slot 10 replaces the earlier hand-back").isTrue();
    }
}
