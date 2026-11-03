package dev.proofstone.driver.mcpl;

import dev.proofstone.core.await.Await;
import dev.proofstone.core.await.AwaitTimeoutException;
import dev.proofstone.core.driver.BlockPos;
import dev.proofstone.core.driver.ChatMessage;
import dev.proofstone.core.driver.ClientDriverException;
import dev.proofstone.core.driver.ClientSession;
import dev.proofstone.core.driver.Face;
import dev.proofstone.core.driver.SlotStack;
import dev.proofstone.core.driver.WindowSession;
import dev.proofstone.core.driver.WindowView;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

/**
 * One joined player. It keeps a buffer of received chat so a reply that beats {@link #awaitMessage} is not lost,
 * and a transcript of every packet name for repro bundles. A kick or disconnect ends the session with its reason;
 * waiting on a closed session fails with that reason and never hangs.
 */
final class McplSession implements ClientSession {

    enum State { JOINING, PLAYING, DISCONNECTED }

    private final Session network;
    private final String username;
    private final List<ChatMessage> chat = Collections.synchronizedList(new ArrayList<>());
    private final List<String> transcript = Collections.synchronizedList(new ArrayList<>());
    private static final double EYE_HEIGHT = 1.62;
    private static final Duration POSITION_WAIT = Duration.ofSeconds(10);

    /** The player inventory, window id 0. It lives apart from the open windows (client-driver spec §2). */
    private final ItemRegistries registries = ItemRegistries.forProtocol(McplDriver.PROTOCOL_VERSION);
    private final WindowModel playerInventory = new WindowModel(0, "minecraft:inventory", "Inventory", registries);
    private final List<WindowModel> opened = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile double[] position;
    private volatile State state = State.JOINING;
    private volatile String disconnectReason;

    McplSession(Session network, String username) {
        this.network = network;
        this.username = username;
    }

    SessionListener listener() {
        return new SessionAdapter() {
            @Override
            public void packetReceived(Session session, Packet packet) {
                onReceived(packet);
            }

            @Override
            public void packetSent(Session session, Packet packet) {
                record("-> " + packet.getClass().getSimpleName());
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                String reason = event.getReason() == null ? "" : flatten(event.getReason());
                if (event.getCause() != null) {
                    reason += (reason.isEmpty() ? "" : " ") + "(" + event.getCause() + ")";
                }
                // The disconnect packet already named the reason; the event only fills in when none did.
                if (disconnectReason == null) {
                    disconnectReason = reason.isEmpty() ? "connection closed with no reason" : reason;
                }
                record("disconnected: " + disconnectReason);
                state = State.DISCONNECTED;
            }
        };
    }

    private void onReceived(Packet packet) {
        String summary = packet.getClass().getSimpleName();
        if (packet instanceof ClientboundLoginPacket) {
            state = State.PLAYING;
        } else if (packet instanceof ClientboundDisconnectPacket kick) {
            disconnectReason = flatten(kick.getReason());
            summary += " " + disconnectReason;
        } else if (packet instanceof ClientboundPlayerPositionPacket move) {
            applyPosition(move);
        } else if (packet instanceof ClientboundRegistryDataPacket registry) {
            registries.record(registry);
        } else if (packet instanceof ClientboundOpenScreenPacket open) {
            WindowModel window = new WindowModel(open.getContainerId(), namespaced(open.getType()),
                    flatten(open.getTitle()), registries);
            opened.add(window);
            summary += " id=" + window.id() + " " + window.type();
        } else if (packet instanceof ClientboundContainerSetContentPacket content) {
            summary += applyContent(content);
        } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            summary += applySlot(slot);
        } else if (packet instanceof ClientboundContainerClosePacket close) {
            summary += " id=" + close.getContainerId();
            synchronized (opened) {
                for (WindowModel window : opened) {
                    if (window.id() == close.getContainerId()) {
                        window.markClosed();
                    }
                }
            }
        } else if (packet instanceof ClientboundSystemChatPacket system) {
            // Overlay is the action bar, which is not chat.
            if (!system.isOverlay()) {
                summary += " " + buffer(flatten(system.getContent()), true);
            }
        } else if (packet instanceof ClientboundDisguisedChatPacket disguised) {
            summary += " " + buffer(flatten(disguised.getMessage()), false);
        } else if (packet instanceof ClientboundPlayerChatPacket player) {
            Component unsigned = player.getUnsignedContent();
            String body = unsigned != null ? flatten(unsigned) : player.getContent();
            summary += " " + buffer("<" + flatten(player.getName()) + "> " + body, false);
        }
        record("<- " + summary);
    }

    /** The last server-synced position {x, y, z}, or null before the first sync. */
    double[] position() {
        return position;
    }

    private void applyPosition(ClientboundPlayerPositionPacket move) {
        double[] now = position == null ? new double[3] : position.clone();
        var relatives = move.getRelatives();
        now[0] = (relatives.contains(PositionElement.X) ? now[0] : 0) + move.getPosition().getX();
        now[1] = (relatives.contains(PositionElement.Y) ? now[1] : 0) + move.getPosition().getY();
        now[2] = (relatives.contains(PositionElement.Z) ? now[2] : 0) + move.getPosition().getZ();
        boolean first = position == null;
        position = now;
        // MCProtocolLib does not answer a teleport. Until the server gets this acknowledgement it ignores the
        // player's packets, including block use. Rotation is taken as absolute because the join and tp packets are.
        network.send(new ServerboundAcceptTeleportationPacket(move.getId(), now[0], now[1], now[2],
                move.getYRot(), move.getXRot()));
        if (first) {
            // The vanilla client says so once its first position arrived and the terrain is up.
            network.send(ServerboundPlayerLoadedPacket.INSTANCE);
        }
    }

    private String applyContent(ClientboundContainerSetContentPacket content) {
        WindowModel target = target(content.getContainerId());
        if (target == null) {
            return " id=" + content.getContainerId() + " (no such window, ignored)";
        }
        target.setContent(content.getStateId(), content.getItems(), content.getCarriedItem());
        return " id=" + target.id() + " stateId=" + content.getStateId() + " slots=" + content.getItems().length;
    }

    private String applySlot(ClientboundContainerSetSlotPacket slot) {
        // Container -1 is the cursor, outside any window.
        if (slot.getContainerId() == -1) {
            playerInventory.setCursor(slot.getItem());
            return " cursor";
        }
        WindowModel target = target(slot.getContainerId());
        if (target == null || !target.setSlot(slot.getStateId(), slot.getSlot(), slot.getItem())) {
            return " id=" + slot.getContainerId() + " slot=" + slot.getSlot() + " (not applied: unknown window or slot)";
        }
        return " id=" + slot.getContainerId() + " slot=" + slot.getSlot() + " stateId=" + slot.getStateId();
    }

    /** The model a container packet addresses: the player inventory for id 0, else the newest open window. */
    private WindowModel target(int containerId) {
        if (containerId == 0) {
            return playerInventory;
        }
        synchronized (opened) {
            for (int i = opened.size() - 1; i >= 0; i--) {
                WindowModel window = opened.get(i);
                if (window.id() == containerId && !window.isClosed()) {
                    return window;
                }
            }
        }
        return null;
    }

    /** Vanilla menu type keys. The switch has no default, so a new constant fails the build instead of a guess. */
    static String namespaced(ContainerType type) {
        return "minecraft:" + switch (type) {
            case GENERIC_9X1 -> "generic_9x1";
            case GENERIC_9X2 -> "generic_9x2";
            case GENERIC_9X3 -> "generic_9x3";
            case GENERIC_9X4 -> "generic_9x4";
            case GENERIC_9X5 -> "generic_9x5";
            case GENERIC_9X6 -> "generic_9x6";
            case GENERIC_3X3 -> "generic_3x3";
            case CRAFTER_3x3 -> "crafter_3x3";
            case ANVIL -> "anvil";
            case BEACON -> "beacon";
            case BLAST_FURNACE -> "blast_furnace";
            case BREWING_STAND -> "brewing_stand";
            case CRAFTING -> "crafting";
            case ENCHANTMENT -> "enchantment";
            case FURNACE -> "furnace";
            case GRINDSTONE -> "grindstone";
            case HOPPER -> "hopper";
            case LECTERN -> "lectern";
            case LOOM -> "loom";
            case MERCHANT -> "merchant";
            case SHULKER_BOX -> "shulker_box";
            case SMITHING -> "smithing";
            case SMOKER -> "smoker";
            case CARTOGRAPHY -> "cartography_table";
            case STONECUTTER -> "stonecutter";
        };
    }

    @Override
    public void useBlock(BlockPos pos, Face face) {
        requireOpen("cannot use a block");
        double[] at = Await.until("synced position of " + username, () -> position, p -> p != null, POSITION_WAIT);
        double[] hit = faceCentre(face);
        double dx = pos.x() + hit[0] - at[0];
        double dy = pos.y() + hit[1] - (at[1] + EYE_HEIGHT);
        double dz = pos.z() + hit[2] - at[2];
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        network.send(new ServerboundMovePlayerRotPacket(true, false, yaw, pitch));
        network.send(new ServerboundUseItemOnPacket(Vector3i.from(pos.x(), pos.y(), pos.z()),
                Direction.valueOf(face.name()), Hand.MAIN_HAND, (float) hit[0], (float) hit[1], (float) hit[2],
                false, false, sequence.incrementAndGet()));
    }

    /** The click point as an offset inside the block: the centre of the face. */
    private static double[] faceCentre(Face face) {
        return switch (face) {
            case DOWN -> new double[] {0.5, 0.0, 0.5};
            case UP -> new double[] {0.5, 1.0, 0.5};
            case NORTH -> new double[] {0.5, 0.5, 0.0};
            case SOUTH -> new double[] {0.5, 0.5, 1.0};
            case WEST -> new double[] {0.0, 0.5, 0.5};
            case EAST -> new double[] {1.0, 0.5, 0.5};
        };
    }

    @Override
    public WindowSession awaitWindow(Duration timeout) {
        WindowModel found;
        try {
            // A disconnect is a value, not an exception, because Await treats a throwing probe as "not yet".
            found = Await.until("a window opened for " + username, this::claimWindow,
                    w -> w != null || state == State.DISCONNECTED, timeout);
        } catch (AwaitTimeoutException e) {
            throw new ClientDriverException("no window opened within " + timeout + "; transcript tail: " + tail(), e);
        }
        if (found == null) {
            throw new ClientDriverException("disconnected while waiting for a window: " + disconnectReason);
        }
        return new Window(found);
    }

    private WindowModel claimWindow() {
        synchronized (opened) {
            for (WindowModel window : opened) {
                if (window.claim()) {
                    return window;
                }
            }
        }
        return null;
    }

    @Override
    public WindowView inventory() {
        return playerInventory;
    }

    /** A live view of one window model. */
    private final class Window implements WindowSession, ItemViews {
        private final WindowModel model;

        Window(WindowModel model) {
            this.model = model;
        }

        @Override
        public String title() {
            return model.title();
        }

        @Override
        public void close() {
            requireOpen("cannot close a window");
            network.send(new ServerboundContainerClosePacket(model.id()));
            model.markClosed();
        }

        @Override
        public String type() {
            return model.type();
        }

        @Override
        public int id() {
            return model.id();
        }

        @Override
        public int stateId() {
            return model.stateId();
        }

        @Override
        public List<SlotStack> slots() {
            return model.slots();
        }

        @Override
        public SlotStack cursor() {
            return model.cursor();
        }

        @Override
        public List<ItemView> itemViews() {
            return model.itemViews();
        }

        @Override
        public ItemView cursorView() {
            return model.cursorView();
        }
    }

    private String buffer(String text, boolean system) {
        chat.add(new ChatMessage(text, system));
        return text;
    }

    private void record(String line) {
        transcript.add(Instant.now() + " " + line);
    }

    State state() {
        return state;
    }

    String disconnectReason() {
        return disconnectReason;
    }

    private void requireOpen(String what) {
        if (state != State.PLAYING) {
            throw new ClientDriverException(what + " but " + username + " is " + state
                    + (disconnectReason == null ? "" : ": " + disconnectReason));
        }
    }

    @Override
    public void chat(String message) {
        requireOpen("cannot chat");
        // Offline mode has no signing keys, so the signature is absent and the acknowledgement list is empty.
        network.send(new ServerboundChatPacket(message, System.currentTimeMillis(), 0L, null, 0, new BitSet(), 0));
    }

    @Override
    public void command(String commandWithoutSlash) {
        requireOpen("cannot run a command");
        network.send(new ServerboundChatCommandPacket(commandWithoutSlash));
    }

    @Override
    public ChatMessage awaitMessage(Pattern pattern, Duration timeout) {
        Probe probe;
        try {
            // Await treats a throwing probe as "not yet", so a disconnect is reported as a value instead.
            probe = Await.until("chat message matching " + pattern + " for " + username, () -> take(pattern),
                    p -> p.message() != null || p.closed(), timeout);
        } catch (AwaitTimeoutException e) {
            throw new ClientDriverException("no message matching " + pattern + " within " + timeout
                    + "; unconsumed: " + chat + "; transcript tail: " + tail(), e);
        }
        if (probe.message() == null) {
            throw new ClientDriverException("disconnected while waiting for " + pattern + ": " + disconnectReason
                    + "; unconsumed: " + chat);
        }
        return probe.message();
    }

    private record Probe(ChatMessage message, boolean closed) {
    }

    private Probe take(Pattern pattern) {
        synchronized (chat) {
            for (Iterator<ChatMessage> it = chat.iterator(); it.hasNext();) {
                ChatMessage candidate = it.next();
                if (pattern.matcher(candidate.text()).find()) {
                    it.remove();
                    return new Probe(candidate, false);
                }
            }
        }
        return new Probe(null, state == State.DISCONNECTED);
    }

    private List<String> tail() {
        synchronized (transcript) {
            return new ArrayList<>(transcript.subList(Math.max(0, transcript.size() - 10), transcript.size()));
        }
    }

    @Override
    public List<String> transcript() {
        synchronized (transcript) {
            return List.copyOf(transcript);
        }
    }

    @Override
    public void close() {
        network.disconnect("proofstone closing");
    }

    /** Plain text of a component: text, translation keys with their arguments, then children. No formatting. */
    static String flatten(Component component) {
        StringBuilder out = new StringBuilder();
        append(component, out);
        return out.toString();
    }

    private static void append(Component component, StringBuilder out) {
        if (component instanceof TextComponent text) {
            out.append(text.content());
        } else if (component instanceof TranslatableComponent translatable) {
            // Language files are not loaded, so the key stands in for the sentence and its arguments follow.
            out.append(translatable.fallback() != null ? translatable.fallback() : translatable.key());
            translatable.arguments().forEach(arg -> {
                out.append(' ');
                append(arg.asComponent(), out);
            });
        }
        component.children().forEach(child -> append(child, out));
    }
}
