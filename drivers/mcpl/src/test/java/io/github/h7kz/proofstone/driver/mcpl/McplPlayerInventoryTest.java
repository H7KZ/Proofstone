package io.github.h7kz.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.core.driver.SlotStack;
import io.github.h7kz.proofstone.core.driver.WindowSession;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;
import org.junit.jupiter.api.Test;

/**
 * T-M11-06: ClientboundSetPlayerInventoryPacket (1.21.2+) is how the server hands a stack back to the player inventory
 * when a menu closes (an anvil's input returned, for example). The sequence is the one a Folia run of
 * PreEventCancelE2E recorded: a resync of the open anvil, the window close, then the packet that puts the book in slot 9.
 * Scripted packets, no server.
 */
class McplPlayerInventoryTest {

    private static final Duration LONG = Duration.ofSeconds(5);
    private static final int BOOK = 11;
    private static final int SWORD = 12;
    private static final int ANVIL_SLOTS = 3 + 36;

    private final Session network = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(),
            new Class<?>[] {Session.class}, (proxy, method, args) -> null);
    private final McplSession session = new McplSession(network, "Bot");

    private void received(Packet packet) {
        session.listener().packetReceived(network, packet);
    }

    private static ItemStack[] windowZero() {
        ItemStack[] slots = new ItemStack[46];
        slots[36] = new ItemStack(SWORD, 1);
        return slots;
    }

    private void join() {
        received(new ClientboundLoginPacket(1, false, new Key[0], 20, 8, 8, false, true, false, null, false, false));
        received(new ClientboundPlayerPositionPacket(1, Vector3d.from(0, 64, 0), Vector3d.ZERO, 0, 0, List.of()));
        received(new ClientboundSetHealthPacket(20f, 20, 5f));
        received(new ClientboundContainerSetContentPacket(0, 1, windowZero(), null));
    }

    private WindowSession openAnvil() {
        received(new ClientboundOpenScreenPacket(1, ContainerType.ANVIL, Component.text("Repair & Name")));
        // The anvil's resync after the click: the book is in the input, the player's slots are the container's tail.
        ItemStack[] anvil = new ItemStack[ANVIL_SLOTS];
        anvil[0] = new ItemStack(BOOK, 1);
        anvil[3 + 27] = new ItemStack(SWORD, 1);
        received(new ClientboundContainerSetContentPacket(1, 15, anvil, null));
        return session.awaitWindow(LONG);
    }

    private int registryIdAt(int windowZeroSlot) {
        return session.inventory().slots().get(windowZeroSlot).registryId();
    }

    @Test
    void aStackHandedBackAfterTheWindowClosedLandsInThePlayerInventory() {
        join();
        var anvil = openAnvil();
        anvil.close();
        assertThat(session.inventory().slots().get(9).isEmpty()).as("nothing was in slot 9 yet").isTrue();

        // Inventory slot 9 is the first main-inventory slot, and window 0 numbers it 9 as well.
        received(new ClientboundSetPlayerInventoryPacket(9, new ItemStack(BOOK, 1)));

        assertThat(registryIdAt(9)).isEqualTo(BOOK);
        assertThat(session.inventory().itemViews()).hasSize(46);
    }

    @Test
    void aStackHandedBackWhileAWindowIsOpenSurvivesTheClose() {
        join();
        var anvil = openAnvil();

        received(new ClientboundSetPlayerInventoryPacket(9, new ItemStack(BOOK, 1)));
        assertThat(anvil.slots().get(3).registryId()).as("the open window's player section tracks it").isEqualTo(BOOK);
        anvil.close();

        assertThat(registryIdAt(9)).as("closing adopts the window's player slots: they must hold the stack").isEqualTo(BOOK);
    }

    @Test
    void hotbarArmorAndOffhandSlotsMapToWindowZeroNumbering() {
        join();
        received(new ClientboundSetPlayerInventoryPacket(0, new ItemStack(BOOK, 1)));   // hotbar 0 -> 36
        received(new ClientboundSetPlayerInventoryPacket(8, new ItemStack(BOOK, 2)));   // hotbar 8 -> 44
        received(new ClientboundSetPlayerInventoryPacket(39, new ItemStack(BOOK, 3)));  // head -> 5
        received(new ClientboundSetPlayerInventoryPacket(36, new ItemStack(BOOK, 4)));  // feet -> 8
        received(new ClientboundSetPlayerInventoryPacket(40, new ItemStack(BOOK, 5)));  // offhand -> 45

        assertThat(session.inventory().slots().get(36)).isEqualTo(new SlotStack(BOOK, 1));
        assertThat(session.inventory().slots().get(44)).isEqualTo(new SlotStack(BOOK, 2));
        assertThat(session.inventory().slots().get(5)).isEqualTo(new SlotStack(BOOK, 3));
        assertThat(session.inventory().slots().get(8)).isEqualTo(new SlotStack(BOOK, 4));
        assertThat(session.inventory().slots().get(45)).isEqualTo(new SlotStack(BOOK, 5));
    }

    @Test
    void anEmptyStackClearsTheSlot() {
        join();
        received(new ClientboundSetPlayerInventoryPacket(0, null));

        assertThat(session.inventory().slots().get(36).isEmpty()).isTrue();
    }

    @Test
    void aSlotOutsideTheInventoryIsNotAppliedAndDoesNotThrow() {
        join();
        received(new ClientboundSetPlayerInventoryPacket(99, new ItemStack(BOOK, 1)));

        assertThat(session.inventory().slots()).hasSize(46);
        assertThat(registryIdAt(36)).isEqualTo(SWORD);
    }

    @Test
    void aClosePacketFromTheServerThenTheHandBackBehavesTheSame() {
        join();
        openAnvil();
        received(new ClientboundContainerClosePacket(1));

        received(new ClientboundSetPlayerInventoryPacket(9, new ItemStack(BOOK, 1)));

        assertThat(registryIdAt(9)).isEqualTo(BOOK);
    }

    @Test
    void aHotbarIndexLandsAtTheTailOfAnOpenWindowAfterTheMainSlots() {
        join();
        var anvil = openAnvil();

        received(new ClientboundSetPlayerInventoryPacket(0, new ItemStack(BOOK, 1)));

        assertThat(anvil.slots().get(3 + 27)).as("hotbar 0 follows the 27 main slots").isEqualTo(new SlotStack(BOOK, 1));
        assertThat(session.inventory().slots().get(36)).as("window 0 takes it as hotbar 0").isEqualTo(new SlotStack(BOOK, 1));
    }

    @Test
    void anArmorIndexWhileAWindowIsOpenIsLeftToWindowZero() {
        join();
        var anvil = openAnvil();
        var before = anvil.slots();

        received(new ClientboundSetPlayerInventoryPacket(39, new ItemStack(BOOK, 1)));
        received(new ClientboundSetPlayerInventoryPacket(99, new ItemStack(BOOK, 1)));

        assertThat(anvil.slots()).as("a container has no armor section and an unknown index has no place").isEqualTo(before);
        assertThat(session.inventory().slots().get(5)).as("window 0 takes the head slot").isEqualTo(new SlotStack(BOOK, 1));
    }
}
