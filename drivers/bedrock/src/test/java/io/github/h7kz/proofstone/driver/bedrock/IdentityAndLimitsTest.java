package io.github.h7kz.proofstone.driver.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.jose4j.jws.JsonWebSignature;
import org.junit.jupiter.api.Test;

/** The offline login and the raised read limit, on the real codec and no network. */
class IdentityAndLimitsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);

    private static JsonNode verified(String jwt) throws Exception {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setCompactSerialization(jwt);
        PublicKey key = java.security.KeyFactory.getInstance("EC")
                .generatePublic(new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(jws.getHeader("x5u"))));
        jws.setKey(key);
        assertThat(jws.verifySignature()).as("self-signed by the key in x5u").isTrue();
        return BedrockRpc.JSON.readTree(jws.getPayload());
    }

    @Test
    void theLoginChainIsSelfSignedWithTheClaimedNameAndNumericXuid() throws Exception {
        Identity identity = new Identity("Alex", "2535400000000007", CLOCK);
        JsonNode payload = verified(identity.identityJwt());
        assertThat(payload.at("/extraData/displayName").asText()).isEqualTo("Alex");
        assertThat(payload.at("/extraData/XUID").asText()).isEqualTo("2535400000000007");
        assertThat(payload.at("/extraData/identity").asText()).isEqualTo(identity.uuid().toString());
        assertThat(payload.get("identityPublicKey").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(identity.keys().getPublic().getEncoded()));
        assertThat(payload.get("iat").asLong()).isEqualTo(CLOCK.millis() / 1000);
        assertThat(payload.get("exp").asLong()).isGreaterThan(payload.get("nbf").asLong());
    }

    @Test
    void theClientDataNamesTheServerAndTheGameVersionAndHoldsNoAccount() throws Exception {
        JsonNode payload = verified(new Identity("Alex", "2535400000000007", CLOCK).clientJwt("server:19132", "1.26.60"));
        assertThat(payload.get("ServerAddress").asText()).isEqualTo("server:19132");
        assertThat(payload.get("GameVersion").asText()).isEqualTo("1.26.60");
        assertThat(payload.get("ThirdPartyName").asText()).isEqualTo("Alex");
        assertThat(payload.get("PlatformOnlineId").asText()).isEmpty();
        assertThat(payload.get("PlayFabId").asText()).isEmpty();
        assertThat(payload.get("PersonaPieces").isArray()).isTrue();
    }

    @Test
    void everyJoinMakesItsOwnKeyPair() {
        Identity a = new Identity("Alex", "2535400000000007", CLOCK);
        Identity b = new Identity("Alex", "2535400000000008", CLOCK);
        assertThat(a.keys().getPublic()).isNotEqualTo(b.keys().getPublic());
    }

    @Test
    void anIdentityNeedsAValidNameAndNumericXuid() {
        assertThatThrownBy(() -> new Identity("Alex", "", CLOCK)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Identity("Alex", "abc", CLOCK)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Identity("a b", "123", CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }

    /** The spike's first decode error: the server's item registry has more entries than the default list limit. */
    private static ByteBuf registryPacket(BedrockCodec codec, BedrockCodecHelper writer, int items) {
        ItemComponentPacket packet = new ItemComponentPacket();
        for (int i = 0; i < items; i++) {
            packet.getItems().add(new SimpleItemDefinition("proofstone:item_" + i, 1000 + i, ItemVersion.DATA_DRIVEN, false,
                    NbtMap.EMPTY));
        }
        ByteBuf buffer = Unpooled.buffer();
        codec.tryEncode(writer, buffer, packet);
        return buffer;
    }

    @Test
    void anItemRegistryLargerThanTheDefaultListLimitDecodesOnlyWithTheClientLimits() {
        BedrockCodec codec = Bedrock_v2225.CODEC;
        int id = codec.getPacketDefinition(ItemComponentPacket.class).getId();

        BedrockCodecHelper defaults = codec.createHelper();
        defaults.setEncodingSettings(EncodingSettings.DEFAULT);
        ByteBuf big = registryPacket(codec, defaults, 2088);
        assertThatThrownBy(() -> codec.tryDecode(defaults, big.copy(), id)).hasStackTraceContaining("maximum is 1536");

        BedrockCodecHelper raised = codec.createHelper();
        raised.setEncodingSettings(HostSession.LIMITS);
        ItemComponentPacket decoded = (ItemComponentPacket) codec.tryDecode(raised, big.copy(), id);
        assertThat(decoded.getItems()).hasSize(2088);
        assertThat(decoded.getItems().get(2087).getIdentifier()).isEqualTo("proofstone:item_2087");
    }

    @Test
    void aBlockDefinitionCarriesTheRuntimeIdTheServerSent() {
        RuntimeIdBlocks blocks = new RuntimeIdBlocks();
        assertThat(blocks.getDefinition(1234).getRuntimeId()).isEqualTo(1234);
        assertThat(blocks.isRegistered(blocks.getDefinition(-5))).isTrue();
    }

    @Test
    void theHostsLimitsAreTheClientProfileAndTheCodecIsTheDocumentedOne() {
        assertThat(HostSession.LIMITS.maxListSize()).isGreaterThan(2088).isGreaterThan(EncodingSettings.DEFAULT.maxListSize());
        assertThat(Bedrock_v2225.CODEC.getProtocolVersion()).isEqualTo(BedrockRpc.PROTOCOL);
        assertThat(Bedrock_v2225.CODEC.getMinecraftVersion()).isEqualTo(BedrockRpc.GAME_VERSION);
    }
}
