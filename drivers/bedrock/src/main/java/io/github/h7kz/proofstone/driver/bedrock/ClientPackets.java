package io.github.h7kz.proofstone.driver.bedrock;

import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.JoinStage;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Position;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Slot;
import java.util.ArrayList;
import java.util.List;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.ClientToServerHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.common.PacketSignal;

/**
 * The client side of the Bedrock join and the v1 reads, on one connection (client-driver spec section 6). Each join
 * stage is entered at its event ({@link JoinStage}). It reads only what v1 needs; a packet it reads with a value it
 * does not know (a text type, a play status, an item without a definition) fails the session with the field named,
 * never a default (AGENTS.md section 3). It never predicts: every view is what the server sent.
 */
final class ClientPackets implements BedrockPacketHandler {

    /** Bedrock sends the eye position; the Java side reports the feet. */
    static final double EYE_HEIGHT = 1.62;
    private static final int CHUNK_RADIUS = 4;

    private final SessionState state;
    private final Wire wire;
    private final Identity identity;
    private final String serverAddress;
    private final int protocol;
    private final String gameVersion;
    private long runtimeId = -1;

    ClientPackets(SessionState state, Wire wire, Identity identity, String serverAddress, int protocol,
            String gameVersion) {
        this.state = state;
        this.wire = wire;
        this.identity = identity;
        this.serverAddress = serverAddress;
        this.protocol = protocol;
        this.gameVersion = gameVersion;
    }

    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        state.packet(packet.getClass().getSimpleName());
        try {
            return packet.handle(this);
        } catch (RuntimeException e) {
            state.fail(BedrockRpc.PROTOCOL_ERROR, packet.getClass().getSimpleName() + ": " + e.getMessage());
            return PacketSignal.HANDLED;
        }
    }

    @Override
    public void onDisconnect(CharSequence reason) {
        state.fail(BedrockRpc.DISCONNECTED, "the connection closed: " + reason);
    }

    @Override
    public PacketSignal handle(NetworkSettingsPacket packet) {
        wire.setCompression(packet.getCompressionAlgorithm());
        LoginPacket login = new LoginPacket();
        login.setProtocolVersion(protocol);
        login.setAuthPayload(new CertificateChainPayload(List.of(identity.identityJwt()), AuthType.SELF_SIGNED));
        login.setClientJwt(identity.clientJwt(serverAddress, gameVersion));
        wire.send(login);
        state.stage(JoinStage.LOGIN_SENT);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ServerToClientHandshakePacket packet) {
        wire.enableEncryption(wire.secret(identity.keys().getPrivate(), packet.getJwt()));
        wire.send(new ClientToServerHandshakePacket());
        state.stage(JoinStage.ENCRYPTION_HANDSHAKE);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(PlayStatusPacket packet) {
        switch (packet.getStatus()) {
            case LOGIN_SUCCESS -> state.stage(JoinStage.RESOURCE_PACKS);
            case PLAYER_SPAWN -> {
                if (runtimeId < 0) {
                    throw new IllegalStateException("the spawn status came before the start game packet");
                }
                SetLocalPlayerAsInitializedPacket initialized = new SetLocalPlayerAsInitializedPacket();
                initialized.setRuntimeEntityId(runtimeId);
                wire.send(initialized);
                state.spawned();
            }
            // Every other status is a refusal; its name is the reason (a version mismatch, a full server).
            default -> state.fail(BedrockRpc.DISCONNECTED, "the server answered the login with " + packet.getStatus());
        }
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePacksInfoPacket packet) {
        // v1 answers "have all" and then "completed" without downloading: packs are out of scope (ADR-0018 section 2).
        state.note("resource packs offered: " + packet.getResourcePackInfos().size() + " resource, "
                + packet.getBehaviorPackInfos().size() + " behavior");
        ResourcePackClientResponsePacket response = new ResourcePackClientResponsePacket();
        response.setStatus(ResourcePackClientResponsePacket.Status.HAVE_ALL_PACKS);
        wire.send(response);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePackStackPacket packet) {
        ResourcePackClientResponsePacket response = new ResourcePackClientResponsePacket();
        response.setStatus(ResourcePackClientResponsePacket.Status.COMPLETED);
        wire.send(response);
        state.stage(JoinStage.START_GAME);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(StartGamePacket packet) {
        runtimeId = packet.getRuntimeEntityId();
        // Without the definitions the creative content packet cannot be read (the spike's decode error).
        wire.useItemDefinitions(packet.getItemDefinitions());
        state.position(feet(packet.getPlayerPosition().getX(), packet.getPlayerPosition().getY(),
                packet.getPlayerPosition().getZ()));
        state.stage(JoinStage.SPAWN_STATUS);
        RequestChunkRadiusPacket radius = new RequestChunkRadiusPacket();
        radius.setRadius(CHUNK_RADIUS);
        radius.setMaxRadius(CHUNK_RADIUS);
        wire.send(radius);
        return PacketSignal.HANDLED;
    }

    /**
     * From protocol 2225 the server's item definitions come in this packet, after start game (whose list is empty), and the
     * creative content that follows cannot be read without them. Found by the first real join (T-M9-06): the library's
     * own decode of CreativeContentPacket failed.
     */
    @Override
    public PacketSignal handle(ItemComponentPacket packet) {
        wire.useItemDefinitions(packet.getItems());
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(TextPacket packet) {
        TextPacket.Type type = packet.getType();
        boolean system;
        switch (type) {
            case CHAT, WHISPER -> system = false;
            case RAW, TRANSLATION, POPUP, JUKEBOX_POPUP, TIP, SYSTEM, ANNOUNCEMENT -> system = true;
            // The JSON types carry a component this driver does not read: unknown, so an error.
            default -> throw new IllegalStateException("unknown text type " + type);
        }
        StringBuilder text = new StringBuilder();
        if (!system && !packet.getSourceName().isEmpty()) {
            text.append('<').append(packet.getSourceName()).append("> ");
        }
        text.append(packet.getMessage());
        if (type == TextPacket.Type.TRANSLATION && !packet.getParameters().isEmpty()) {
            text.append(' ').append(String.join(" ", packet.getParameters()));
        }
        state.message(text.toString(), system);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(InventoryContentPacket packet) {
        // Other containers are windows: out of v1. Only the player inventory (id 0) is mirrored.
        if (packet.getContainerId() == ContainerId.INVENTORY) {
            List<Slot> slots = new ArrayList<>();
            for (ItemData item : packet.getContents()) {
                slots.add(slot(slots.size(), item));
            }
            state.inventory(slots);
        }
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(InventorySlotPacket packet) {
        if (packet.getContainerId() == ContainerId.INVENTORY) {
            state.inventorySlot(slot(packet.getSlot(), packet.getItem()));
        }
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(MovePlayerPacket packet) {
        if (packet.getRuntimeEntityId() == runtimeId) {
            state.position(feet(packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ()));
        }
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(DisconnectPacket packet) {
        String message = packet.getKickMessage();
        state.fail(BedrockRpc.DISCONNECTED, message == null || message.isEmpty() ? String.valueOf(packet.getReason())
                : message);
        return PacketSignal.HANDLED;
    }

    private static Slot slot(int index, ItemData item) {
        if (item == null) {
            throw new IllegalStateException("slot " + index + " has no item data");
        }
        ItemDefinition definition = item.getDefinition();
        if (definition == null) {
            throw new IllegalStateException("slot " + index + " has an item without a definition");
        }
        if (item.getCount() < 0) {
            throw new IllegalStateException("slot " + index + " has a negative count " + item.getCount());
        }
        return new Slot(index, definition.getIdentifier(), definition.getRuntimeId(), item.getCount());
    }

    private static Position feet(float x, float y, float z) {
        return new Position(x, y - EYE_HEIGHT, z);
    }
}
