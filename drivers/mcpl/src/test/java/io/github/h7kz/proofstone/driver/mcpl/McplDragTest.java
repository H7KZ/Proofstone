package io.github.h7kz.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.DragMode;
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
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.SpreadItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetCursorItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.junit.jupiter.api.Test;

/**
 * T-M11-09: {@code Window.drag} sends the quick-craft start, one add per slot and the end, with the button's header
 * codes, and returns when the server answers the end. Scripted packets, no server.
 */
class McplDragTest {

    private static final Duration LONG = Duration.ofSeconds(5);
    private static final int STONE = 11;
    private static final int CHEST_SLOTS = 27 + 36;
    private static final int OUTSIDE = -999;

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

    private WindowSession openChest() {
        received(new ClientboundLoginPacket(1, false, new Key[0], 20, 8, 8, false, true, false, null, false, false));
        received(new ClientboundPlayerPositionPacket(1, Vector3d.from(0, 64, 0), Vector3d.ZERO, 0, 0, List.of()));
        received(new ClientboundSetHealthPacket(20f, 20, 5f));
        received(new ClientboundContainerSetContentPacket(0, 1, new ItemStack[46], null));
        received(new ClientboundOpenScreenPacket(1, ContainerType.GENERIC_9X3, Component.text("Chest")));
        received(new ClientboundContainerSetContentPacket(1, 5, new ItemStack[CHEST_SLOTS], new ItemStack(STONE, 9)));
        return session.awaitWindow(LONG);
    }

    private static void assertStage(Object packet, int slot, SpreadItemAction header) {
        assertThat(packet).isInstanceOf(ServerboundContainerClickPacket.class);
        ServerboundContainerClickPacket click = (ServerboundContainerClickPacket) packet;
        assertThat(click.getContainerId()).isEqualTo(1);
        assertThat(click.getStateId()).as("every stage carries the state id the drag started from").isEqualTo(5);
        assertThat(click.getAction()).isEqualTo(ContainerActionType.SPREAD_ITEM);
        assertThat(click.getSlot()).isEqualTo(slot);
        assertThat(click.getParam()).isEqualTo(header);
    }

    private void verifyStages(DragMode mode, SpreadItemAction begin, SpreadItemAction add, SpreadItemAction end)
            throws Exception {
        WindowSession chest = openChest();
        sent.clear();

        CompletableFuture<Void> drag = CompletableFuture.runAsync(() -> chest.drag(List.of(3, 1, 20), mode));
        assertStage(sent.poll(5, TimeUnit.SECONDS), OUTSIDE, begin);
        assertStage(sent.poll(5, TimeUnit.SECONDS), 3, add);
        assertStage(sent.poll(5, TimeUnit.SECONDS), 1, add);
        assertStage(sent.poll(5, TimeUnit.SECONDS), 20, add);
        assertStage(sent.poll(5, TimeUnit.SECONDS), OUTSIDE, end);
        assertThat(sent).as("no sixth packet").isEmpty();

        // Vanilla 26.3 order: a cursor update for the start and each add, then the end's slots, then the end's cursor.
        for (int i = 0; i < 4; i++) {
            received(new ClientboundSetCursorItemPacket(new ItemStack(STONE, 10)));
        }
        assertThat(drag.isDone()).as("cursor updates alone are not the answer").isFalse();
        received(new ClientboundContainerSetSlotPacket(1, 6, 3, new ItemStack(STONE, 3)));
        received(new ClientboundContainerSetSlotPacket(1, 7, 1, new ItemStack(STONE, 3)));
        received(new ClientboundContainerSetSlotPacket(1, 8, 20, new ItemStack(STONE, 3)));
        assertThat(drag.isDone()).as("the first slot of the answer is not all of it: the cursor follows").isFalse();

        received(new ClientboundSetCursorItemPacket(new ItemStack(STONE, 1)));
        drag.get(5, TimeUnit.SECONDS);
        assertThat(chest.slots().get(20).isEmpty()).as("the slot is the server's word").isFalse();
        assertThat(chest.cursor().isEmpty()).as("the cursor is read after the answer").isFalse();
    }

    @Test
    void leftDragSendsStartAddsAndEndWithTheLeftButtonHeaders() throws Exception {
        verifyStages(DragMode.LEFT, SpreadItemAction.LEFT_MOUSE_BEGIN_DRAG, SpreadItemAction.LEFT_MOUSE_ADD_SLOT,
                SpreadItemAction.LEFT_MOUSE_END_DRAG);
    }

    @Test
    void rightDragSendsStartAddsAndEndWithTheRightButtonHeaders() throws Exception {
        verifyStages(DragMode.RIGHT, SpreadItemAction.RIGHT_MOUSE_BEGIN_DRAG, SpreadItemAction.RIGHT_MOUSE_ADD_SLOT,
                SpreadItemAction.RIGHT_MOUSE_END_DRAG);
    }

    @Test
    void middleDragSendsStartAddsAndEndWithTheMiddleButtonHeaders() throws Exception {
        verifyStages(DragMode.MIDDLE, SpreadItemAction.MIDDLE_MOUSE_BEGIN_DRAG, SpreadItemAction.MIDDLE_MOUSE_ADD_SLOT,
                SpreadItemAction.MIDDLE_MOUSE_END_DRAG);
    }

    @Test
    void aSlotOutsideTheWindowIsRefusedBeforeAnyPacketIsSent() {
        WindowSession chest = openChest();
        sent.clear();

        assertThatThrownBy(() -> chest.drag(List.of(1, CHEST_SLOTS), DragMode.LEFT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outside");
        assertThat(sent).isEmpty();
    }

    @Test
    void anEmptyOrRepeatingSlotListIsRefused() {
        WindowSession chest = openChest();
        sent.clear();

        assertThatThrownBy(() -> chest.drag(List.of(), DragMode.LEFT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chest.drag(List.of(2, 2), DragMode.RIGHT)).isInstanceOf(IllegalArgumentException.class);
        assertThat(sent).isEmpty();
    }

    @Test
    void aClosedWindowCannotDrag() {
        WindowSession chest = openChest();
        chest.close();
        sent.clear();

        assertThatThrownBy(() -> chest.drag(List.of(1), DragMode.LEFT)).hasMessageContaining("closed");
        assertThat(sent).isEmpty();
    }
}
