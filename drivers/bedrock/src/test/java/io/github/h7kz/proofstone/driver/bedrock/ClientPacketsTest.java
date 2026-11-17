package io.github.h7kz.proofstone.driver.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.JoinStage;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Slot;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientToServerHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
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
import org.junit.jupiter.api.Test;

/** The join and the v1 reads on scripted packets: no network, no library class that contacts Microsoft. */
class ClientPacketsTest {

    static final class Recorder implements Wire {
        final List<BedrockPacket> sent = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        List<ItemDefinition> definitions;

        @Override
        public void send(BedrockPacket packet) {
            sent.add(packet);
        }

        @Override
        public void setCompression(PacketCompressionAlgorithm algorithm) {
            events.add("compression " + algorithm);
        }

        @Override
        public void enableEncryption(SecretKey key) {
            events.add("encryption on");
        }

        @Override
        public void useItemDefinitions(List<ItemDefinition> items) {
            definitions = items;
        }

        @Override
        public SecretKey secret(PrivateKey key, String serverJwt) {
            events.add("secret from " + serverJwt);
            return new SecretKeySpec(new byte[32], "AES");
        }

        <T extends BedrockPacket> T last(Class<T> type) {
            return sent.stream().filter(type::isInstance).map(type::cast).reduce((a, b) -> b).orElseThrow();
        }
    }

    private final SessionState state = new SessionState();
    private final Recorder wire = new Recorder();
    private final Identity identity = new Identity("Alex", "2535400000000007", Clock.systemUTC());
    private final ClientPackets client = new ClientPackets(state, wire, identity, "server:19132", 2225, "1.26.60");
    private final ItemDefinition diamond = new SimpleItemDefinition("minecraft:diamond", 318, false);

    private void receive(BedrockPacket packet) {
        client.handlePacket(packet);
    }

    private static PlayStatusPacket status(PlayStatusPacket.Status status) {
        PlayStatusPacket packet = new PlayStatusPacket();
        packet.setStatus(status);
        return packet;
    }

    private StartGamePacket startGame(float y) {
        StartGamePacket start = new StartGamePacket();
        start.setRuntimeEntityId(77);
        start.setPlayerPosition(Vector3f.from(10, y, -4));
        start.setItemDefinitions(List.of(diamond));
        return start;
    }

    private void joinThrough(JoinStage last) {
        walk(client, last);
    }

    @Test
    void theJoinWalksEveryStageInOrderAndAnswersEachPacket() {
        List<JoinStage> seen = new ArrayList<>();
        seen.add(state.stage());
        NetworkSettingsPacket settings = new NetworkSettingsPacket();
        settings.setCompressionAlgorithm(PacketCompressionAlgorithm.SNAPPY);
        receive(settings);
        seen.add(state.stage());
        assertThat(wire.events).containsExactly("compression SNAPPY");
        LoginPacket login = wire.last(LoginPacket.class);
        assertThat(login.getProtocolVersion()).isEqualTo(2225);
        assertThat(login.getClientJwt()).isNotBlank();

        ServerToClientHandshakePacket handshake = new ServerToClientHandshakePacket();
        handshake.setJwt("server-jwt");
        receive(handshake);
        seen.add(state.stage());
        assertThat(wire.events).containsExactly("compression SNAPPY", "secret from server-jwt", "encryption on");
        assertThat(wire.sent.get(wire.sent.size() - 1)).isInstanceOf(ClientToServerHandshakePacket.class);

        receive(status(PlayStatusPacket.Status.LOGIN_SUCCESS));
        seen.add(state.stage());
        receive(new ResourcePacksInfoPacket());
        assertThat(wire.last(ResourcePackClientResponsePacket.class).getStatus())
                .isEqualTo(ResourcePackClientResponsePacket.Status.HAVE_ALL_PACKS);
        receive(new ResourcePackStackPacket());
        assertThat(wire.last(ResourcePackClientResponsePacket.class).getStatus())
                .isEqualTo(ResourcePackClientResponsePacket.Status.COMPLETED);
        seen.add(state.stage());

        receive(startGame(65.62f));
        seen.add(state.stage());
        RequestChunkRadiusPacket radius = wire.last(RequestChunkRadiusPacket.class);
        assertThat(radius.getRadius()).isEqualTo(4);
        assertThat(state.awaitJoin(Duration.ofMillis(1)).spawned()).isFalse();

        receive(status(PlayStatusPacket.Status.PLAYER_SPAWN));
        assertThat(wire.last(SetLocalPlayerAsInitializedPacket.class).getRuntimeEntityId()).isEqualTo(77);
        assertThat(state.awaitJoin(Duration.ofMillis(1)).spawned()).isTrue();
        assertThat(seen).containsExactly(JoinStage.RAKNET_CONNECT, JoinStage.LOGIN_SENT, JoinStage.ENCRYPTION_HANDSHAKE,
                JoinStage.RESOURCE_PACKS, JoinStage.START_GAME, JoinStage.SPAWN_STATUS);
        assertThat(wire.definitions).containsExactly(diamond);
    }

    @Test
    void aStallNamesTheStageItStoppedInAndTheLastPacket() {
        for (JoinStage stalled : new JoinStage[] {JoinStage.LOGIN_SENT, JoinStage.ENCRYPTION_HANDSHAKE,
            JoinStage.RESOURCE_PACKS, JoinStage.START_GAME, JoinStage.SPAWN_STATUS}) {
            SessionState fresh = new SessionState();
            walk(new ClientPackets(fresh, new Recorder(), identity, "server:19132", 2225, "1.26.60"), stalled);
            SessionState.JoinResult result = fresh.awaitJoin(Duration.ofMillis(5));
            assertThat(result.spawned()).isFalse();
            assertThat(result.failure()).isNull();
            assertThat(result.stage()).isEqualTo(stalled);
            assertThat(result.lastPacket()).isNotEqualTo("none");
        }
    }

    private void walk(ClientPackets target, JoinStage last) {
        NetworkSettingsPacket settings = new NetworkSettingsPacket();
        settings.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);
        target.handlePacket(settings);
        if (last == JoinStage.LOGIN_SENT) {
            return;
        }
        ServerToClientHandshakePacket handshake = new ServerToClientHandshakePacket();
        handshake.setJwt("jwt");
        target.handlePacket(handshake);
        if (last == JoinStage.ENCRYPTION_HANDSHAKE) {
            return;
        }
        target.handlePacket(status(PlayStatusPacket.Status.LOGIN_SUCCESS));
        if (last == JoinStage.RESOURCE_PACKS) {
            return;
        }
        target.handlePacket(new ResourcePacksInfoPacket());
        target.handlePacket(new ResourcePackStackPacket());
        if (last == JoinStage.START_GAME) {
            return;
        }
        target.handlePacket(startGame(65.62f));
    }

    @Test
    void aRefusalStatusEndsTheJoinWithItsNameNotATimeout() {
        joinThrough(JoinStage.ENCRYPTION_HANDSHAKE);
        receive(status(PlayStatusPacket.Status.LOGIN_FAILED_SERVER_OLD));
        SessionState.JoinResult result = state.awaitJoin(Duration.ofSeconds(1));
        assertThat(result.failure().code()).isEqualTo(BedrockRpc.DISCONNECTED);
        assertThat(result.failure().message()).contains("LOGIN_FAILED_SERVER_OLD");
    }

    @Test
    void aDisconnectPacketQuotesTheServersText() {
        joinThrough(JoinStage.LOGIN_SENT);
        DisconnectPacket kick = new DisconnectPacket();
        kick.setKickMessage("Please log into Xbox to join this server.");
        receive(kick);
        assertThat(state.failure().code()).isEqualTo(BedrockRpc.DISCONNECTED);
        assertThat(state.failure().message()).isEqualTo("Please log into Xbox to join this server.");
    }

    @Test
    void theSpawnStatusBeforeStartGameIsAnError() {
        joinThrough(JoinStage.RESOURCE_PACKS);
        receive(status(PlayStatusPacket.Status.PLAYER_SPAWN));
        assertThat(state.failure().code()).isEqualTo(BedrockRpc.PROTOCOL_ERROR);
        assertThat(state.failure().message()).contains("before the start game");
    }

    private static TextPacket text(TextPacket.Type type, String source, String message, String... parameters) {
        TextPacket packet = new TextPacket();
        packet.setType(type);
        packet.setSourceName(source);
        packet.setMessage(message);
        packet.setParameters(List.of(parameters));
        return packet;
    }

    @Test
    void textPacketsBecomeChatAndSystemLines() {
        receive(text(TextPacket.Type.CHAT, "Steve", "hi"));
        receive(text(TextPacket.Type.RAW, "", "Given 3 [Diamond] to Alex"));
        receive(text(TextPacket.Type.TRANSLATION, "", "chat.type.text", "Steve", "yo"));
        assertThat(state.messagesSince(0)).extracting(BedrockRpc.Message::text, BedrockRpc.Message::system)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("<Steve> hi", false),
                        org.assertj.core.groups.Tuple.tuple("Given 3 [Diamond] to Alex", true),
                        org.assertj.core.groups.Tuple.tuple("chat.type.text Steve yo", true));
        assertThat(state.awaitMessage(Pattern.compile("Given \\d"), Duration.ofMillis(1)).seq()).isEqualTo(1);
        // Consumed once: the same pattern no longer matches, the other lines stay.
        assertThat(state.awaitMessage(Pattern.compile("Given \\d"), Duration.ofMillis(1))).isNull();
        assertThat(state.awaitMessage(Pattern.compile("hi"), Duration.ofMillis(1)).text()).isEqualTo("<Steve> hi");
    }

    @Test
    void aTextTypeTheDriverDoesNotReadFailsTheSessionNamingIt() {
        receive(text(TextPacket.Type.JSON, "", "{\"rawtext\":[]}"));
        assertThat(state.failure().code()).isEqualTo(BedrockRpc.PROTOCOL_ERROR);
        assertThat(state.failure().message()).contains("TextPacket").contains("unknown text type JSON");
        assertThat(state.awaitMessage(Pattern.compile("."), Duration.ofSeconds(5))).isNull();
    }

    @Test
    void theInventoryIsTheServersContentAndSlotUpdates() {
        InventoryContentPacket content = new InventoryContentPacket();
        content.setContainerId(ContainerId.INVENTORY);
        content.setContents(List.of(ItemData.AIR, ItemData.builder().definition(diamond).count(3).build()));
        receive(content);
        assertThat(state.inventory()).containsExactly(new Slot(0, "minecraft:air", 0, 0), new Slot(1, "minecraft:diamond", 318, 3));

        InventorySlotPacket slot = new InventorySlotPacket();
        slot.setContainerId(ContainerId.INVENTORY);
        slot.setSlot(4);
        slot.setItem(ItemData.builder().definition(diamond).count(64).build());
        receive(slot);
        assertThat(state.inventory()).hasSize(5);
        assertThat(state.inventory().get(4)).isEqualTo(new Slot(4, "minecraft:diamond", 318, 64));
        assertThat(state.inventory().get(2).count()).isZero();
    }

    @Test
    void otherContainersAreWindowsAndOutOfScope() {
        InventoryContentPacket armor = new InventoryContentPacket();
        armor.setContainerId(ContainerId.ARMOR);
        armor.setContents(List.of(ItemData.builder().definition(diamond).count(1).build()));
        receive(armor);
        assertThat(state.inventory()).isEmpty();
        assertThat(state.failure()).isNull();
    }

    @Test
    void anItemWithoutADefinitionIsAnErrorNotAnEmptySlot() {
        InventorySlotPacket slot = new InventorySlotPacket();
        slot.setContainerId(ContainerId.INVENTORY);
        slot.setSlot(0);
        slot.setItem(ItemData.builder().definition(null).count(2).build());
        receive(slot);
        assertThat(state.failure().code()).isEqualTo(BedrockRpc.PROTOCOL_ERROR);
        assertThat(state.failure().message()).contains("without a definition");
    }

    @Test
    void anInventorySlotOutsideTheModelIsAnError() {
        InventorySlotPacket slot = new InventorySlotPacket();
        slot.setContainerId(ContainerId.INVENTORY);
        slot.setSlot(-1);
        slot.setItem(ItemData.AIR);
        receive(slot);
        assertThat(state.failure().message()).contains("outside");
    }

    @Test
    void positionIsTheFeetFromTheStartGameAndMovesOfThisPlayerOnly() {
        joinThrough(JoinStage.SPAWN_STATUS);
        assertThat(state.position().x()).isEqualTo(10.0);
        assertThat(state.position().y()).isCloseTo(64.0, org.assertj.core.data.Offset.offset(0.001));
        MovePlayerPacket other = new MovePlayerPacket();
        other.setRuntimeEntityId(5);
        other.setPosition(Vector3f.from(99, 99, 99));
        receive(other);
        assertThat(state.position().x()).isEqualTo(10.0);
        MovePlayerPacket mine = new MovePlayerPacket();
        mine.setRuntimeEntityId(77);
        mine.setPosition(Vector3f.from(1, 70, 2));
        receive(mine);
        assertThat(state.position().x()).isEqualTo(1.0);
        assertThat(state.position().y()).isCloseTo(70 - ClientPackets.EYE_HEIGHT, org.assertj.core.data.Offset.offset(0.001));
        assertThat(state.position().z()).isEqualTo(2.0);
    }

    @Test
    void theFirstFailureWinsAndTheTranscriptNamesPackets() {
        receive(text(TextPacket.Type.JSON, "", "x"));
        state.fail(BedrockRpc.DISCONNECTED, "later");
        assertThat(state.failure().code()).isEqualTo(BedrockRpc.PROTOCOL_ERROR);
        assertThat(state.transcript()).anyMatch(l -> l.endsWith("TextPacket")).anyMatch(l -> l.startsWith("FAILED PROTOCOL_ERROR"));
    }

    @Test
    void theCloseOfTheConnectionIsAFailureNamingWhy() {
        client.onDisconnect("Disconnected");
        assertThat(state.failure().code()).isEqualTo(BedrockRpc.DISCONNECTED);
        assertThat(state.failure().message()).contains("Disconnected");
    }
}
