package io.github.h7kz.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetExperiencePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.junit.jupiter.api.Test;

/**
 * T-M7-09: the session state machine on a live connection, driven by scripted packet sequences (no server). The
 * sequences are the ones the spike saw on Velocity and BungeeCord (docs/platforms/velocity.md, bungeecord.md): start
 * configuration, the client's acknowledgement, finish configuration answered, then a new login.
 */
class McplSwitchTest {

    private static final Duration SHORT = Duration.ofMillis(300);
    private static final Duration LONG = Duration.ofSeconds(5);
    private static final int APPLE = 7;
    private static final int DIAMOND = 9;

    private final List<Packet> sent = new ArrayList<>();
    private final Session network = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(),
            new Class<?>[] {Session.class}, (proxy, method, args) -> {
                if (method.getName().equals("send")) {
                    sent.add((Packet) args[0]);
                }
                return null;
            });
    private final McplSession session = new McplSession(network, "Bot");

    private void received(Packet packet) {
        session.listener().packetReceived(network, packet);
    }

    private void sent(Packet packet) {
        session.listener().packetSent(network, packet);
    }

    private static ClientboundLoginPacket login() {
        return new ClientboundLoginPacket(1, false, new Key[0], 20, 8, 8, false, true, false, null, false, false);
    }

    private static ClientboundContainerSetContentPacket inventoryOf(int item) {
        ItemStack[] slots = new ItemStack[46];
        slots[36] = new ItemStack(item, 3);
        return new ClientboundContainerSetContentPacket(0, 1, slots, null);
    }

    private static ClientboundPlayerPositionPacket position(double x) {
        return new ClientboundPlayerPositionPacket(1, Vector3d.from(x, 64, 0), Vector3d.ZERO, 0, 0, List.of());
    }

    private void joinFirstServer() {
        received(login());
        received(position(10));
        received(new ClientboundSetExperiencePacket(0.5f, 7, 100));
        received(inventoryOf(APPLE));
        received(new ClientboundOpenScreenPacket(3, ContainerType.GENERIC_9X3, Component.text("lobby chest")));
        received(new ClientboundContainerSetContentPacket(3, 1, new ItemStack[63], null));
        assertThat(session.state()).isEqualTo(McplSession.State.PLAYING);
    }

    /** The proxy's configuration phase as the client saw it, up to but not including the new login. */
    private void configurationPhase() {
        received(new ClientboundStartConfigurationPacket());
        sent(new ServerboundConfigurationAcknowledgedPacket());
        received(new ClientboundFinishConfigurationPacket());
        sent(ServerboundFinishConfigurationPacket.INSTANCE);
    }

    @Test
    void aSwitchGoesPlayConfigurationPlayAndTheViewsAreTheNewServers() {
        joinFirstServer();
        assertThat(session.inventory().slots()).hasSize(46);

        received(new ClientboundStartConfigurationPacket());
        assertThat(session.state()).isEqualTo(McplSession.State.SWITCHING);
        assertThatThrownBy(() -> session.chat("hello"))
                .isInstanceOf(ClientDriverException.class).hasMessageContaining("SWITCHING");
        sent(new ServerboundConfigurationAcknowledgedPacket());
        received(new ClientboundFinishConfigurationPacket());
        sent(ServerboundFinishConfigurationPacket.INSTANCE);
        assertThat(session.state()).as("the switch is not done until the new login").isEqualTo(McplSession.State.SWITCHING);
        received(login());

        assertThat(session.state()).isEqualTo(McplSession.State.PLAYING);
        session.awaitServerSwitch(LONG);
        // Nothing of the first server survives: the new server is authoritative and has not synced yet.
        assertThat(session.inventory().slots()).as("no stale slots").isEmpty();
        assertThat(session.inventory().cursor().isEmpty()).isTrue();
        assertThat(session.position()).as("position waits for the new server's sync").isNull();
        assertThatThrownBy(session::experienceLevel).isInstanceOf(ClientDriverException.class);
        assertThatThrownBy(() -> session.awaitWindow(SHORT)).as("the lobby's unclaimed chest is gone")
                .isInstanceOf(ClientDriverException.class).hasMessageContaining("no window opened");

        received(position(500));
        received(inventoryOf(DIAMOND));
        assertThat(session.position()[0]).isEqualTo(500);
        assertThat(session.inventory().slots().get(36).registryId()).isEqualTo(DIAMOND);
        assertThat(sent.stream().filter(p -> p instanceof ServerboundPlayerLoadedPacket))
                .as("the join burst is repeated for the new server: loaded again").hasSize(2);
    }

    @Test
    void aWindowOpenAcrossTheSwitchIsClosedNotKept() {
        joinFirstServer();
        var window = session.awaitWindow(LONG);

        configurationPhase();
        received(login());

        assertThatThrownBy(() -> window.click(0, io.github.h7kz.proofstone.api.Click.LEFT))
                .isInstanceOf(ClientDriverException.class).hasMessageContaining("closed");
    }

    @Test
    void aKickDuringTheSwitchIsAFailureWithItsReason() {
        joinFirstServer();
        received(new ClientboundStartConfigurationPacket());
        sent(new ServerboundConfigurationAcknowledgedPacket());
        received(new ClientboundDisconnectPacket(Component.text("Kicked: server restarting")));
        session.listener().disconnected(new DisconnectedEvent(network, Component.text("Kicked: server restarting"), null));

        assertThat(session.state()).isEqualTo(McplSession.State.DISCONNECTED);
        assertThatThrownBy(() -> session.awaitServerSwitch(LONG))
                .isNotInstanceOf(JoinTimeoutException.class)
                .isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("disconnected during a server switch")
                .hasMessageContaining("SWITCH_CONFIGURATION_STALLED")
                .hasMessageContaining("Kicked: server restarting");
    }

    @Test
    void aKickBeforeAnySwitchStartedIsAlsoAFailureNotASwitch() {
        joinFirstServer();
        received(new ClientboundDisconnectPacket(Component.text("Kicked: flying")));
        session.listener().disconnected(new DisconnectedEvent(network, Component.text("Kicked: flying"), null));

        assertThatThrownBy(() -> session.awaitServerSwitch(LONG)).isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("disconnected while waiting for a server switch")
                .hasMessageContaining("Kicked: flying");
    }

    @Test
    void aSwitchToAnUnreachableServerLeavesTheClientOnTheFirstAndNamesTheProxysReply() {
        joinFirstServer();
        session.command("server survival");
        received(new ClientboundSystemChatPacket(Component.text("Unable to connect you to survival. Try again later."), false));

        assertThatThrownBy(() -> session.awaitServerSwitch(SHORT))
                .isInstanceOfSatisfying(JoinTimeoutException.class, e -> {
                    assertThat(e.stage()).isEqualTo("SWITCH_NOT_STARTED");
                    assertThat(e.player()).isEqualTo("Bot");
                    assertThat(e.getMessage()).startsWith("timed out joining Bot")
                            .contains("Unable to connect you to survival")
                            .contains("SWITCH_NOT_STARTED");
                });
        // Still the first server's session, intact and usable.
        assertThat(session.state()).isEqualTo(McplSession.State.PLAYING);
        assertThat(session.inventory().slots().get(36).registryId()).isEqualTo(APPLE);
        assertThat(session.position()[0]).isEqualTo(10);
        session.chat("still here");
    }

    @Test
    void aBackendRepliesInsteadOfSwitchingIsQuotedLoudly() {
        // Velocity's rate limit forwards the command to the backend (docs/platforms/velocity.md, Gotchas).
        joinFirstServer();
        session.command("server survival");
        received(new ClientboundSystemChatPacket(Component.text("command.unknown.command"), false));

        assertThatThrownBy(() -> session.awaitServerSwitch(SHORT)).isInstanceOf(JoinTimeoutException.class)
                .hasMessageContaining("command.unknown.command");
    }

    @Test
    void aSwitchThatStallsNamesItsStage() {
        joinFirstServer();
        received(new ClientboundStartConfigurationPacket());
        assertThatThrownBy(() -> session.awaitServerSwitch(SHORT)).isInstanceOfSatisfying(JoinTimeoutException.class,
                e -> assertThat(e.stage()).isEqualTo("SWITCH_START_NOT_ACKNOWLEDGED"));

        sent(new ServerboundConfigurationAcknowledgedPacket());
        assertThatThrownBy(() -> session.awaitServerSwitch(SHORT)).isInstanceOfSatisfying(JoinTimeoutException.class,
                e -> assertThat(e.stage()).isEqualTo("SWITCH_CONFIGURATION_STALLED"));

        received(new ClientboundFinishConfigurationPacket());
        assertThatThrownBy(() -> session.awaitServerSwitch(SHORT)).isInstanceOfSatisfying(JoinTimeoutException.class,
                e -> assertThat(e.stage()).isEqualTo("SWITCH_FINISH_NOT_ANSWERED"));

        sent(ServerboundFinishConfigurationPacket.INSTANCE);
        assertThatThrownBy(() -> session.awaitServerSwitch(SHORT)).isInstanceOfSatisfying(JoinTimeoutException.class,
                e -> assertThat(e.getMessage()).startsWith("timed out joining Bot")
                        .contains("SWITCH_NO_JOIN_PACKET").contains("last packet received: ClientboundFinishConfigurationPacket"));
    }

    @Test
    void twoSwitchesAreTwoAwaitsAndNoMore() {
        joinFirstServer();
        configurationPhase();
        received(login());
        session.awaitServerSwitch(LONG);
        received(inventoryOf(DIAMOND));

        configurationPhase();
        received(login());
        session.awaitServerSwitch(LONG);
        assertThat(session.inventory().slots()).as("back on a new join: nothing stale").isEmpty();

        // A third await has nothing to claim: a switch is counted once.
        assertThatThrownBy(() -> session.awaitServerSwitch(SHORT)).isInstanceOf(JoinTimeoutException.class);
    }

    @Test
    void aSwitchThatFinishedBeforeTheAwaitIsNotLost() {
        joinFirstServer();
        configurationPhase();
        received(login());
        configurationPhase();
        received(login());

        session.awaitServerSwitch(SHORT);
        session.awaitServerSwitch(SHORT);
    }

    @Test
    void theSinglePlainJoinIsNotASwitch() {
        joinFirstServer();

        assertThatThrownBy(() -> session.awaitServerSwitch(SHORT)).isInstanceOf(JoinTimeoutException.class);
        assertThat(session.inventory().slots()).hasSize(46);
    }
}
