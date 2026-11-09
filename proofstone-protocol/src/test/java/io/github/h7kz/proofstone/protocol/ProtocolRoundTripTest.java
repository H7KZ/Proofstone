package io.github.h7kz.proofstone.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProtocolRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    static Stream<Arguments> examples() {
        return Stream.of(
                Arguments.of(ProtocolExamples.HEALTH, new TypeReference<Health>() {}),
                Arguments.of(ProtocolExamples.PROXY_SERVER, new TypeReference<ProxyServer>() {}),
                Arguments.of(ProtocolExamples.PROXY_SERVERS, new TypeReference<ProxyServers>() {}),
                Arguments.of(ProtocolExamples.PROXY_PLAYER, new TypeReference<ProxyPlayer>() {}),
                Arguments.of(ProtocolExamples.ERROR, new TypeReference<ProtocolError>() {}),
                Arguments.of(ProtocolExamples.ITEM, new TypeReference<Item>() {}),
                Arguments.of(ProtocolExamples.PLAYER_STATE, new TypeReference<PlayerState>() {}),
                Arguments.of(ProtocolExamples.COMMAND, new TypeReference<Command>() {}),
                Arguments.of(ProtocolExamples.COMMAND_REPLY, new TypeReference<CommandReply>() {}),
                Arguments.of(ProtocolExamples.ENVELOPE_OK, new TypeReference<Envelope<Health>>() {}),
                Arguments.of(ProtocolExamples.ENVELOPE_ERROR, new TypeReference<Envelope<Health>>() {}),
                Arguments.of(ProtocolExamples.BLOCK, new TypeReference<Block>() {}),
                Arguments.of(ProtocolExamples.BLOCK_WRITE, new TypeReference<BlockWrite>() {}),
                Arguments.of(ProtocolExamples.CONFIG_ENTRY, new TypeReference<ConfigEntry>() {}),
                Arguments.of(ProtocolExamples.TICKS, new TypeReference<Ticks>() {}),
                Arguments.of(ProtocolExamples.LOG_PAGE, new TypeReference<LogPage>() {}));
    }

    @ParameterizedTest
    @MethodSource("examples")
    void parseThenSerialiseGivesEqualJson(String json, TypeReference<?> type) throws Exception {
        Object parsed = MAPPER.readValue(json, type);

        // Compare the wire text, re-parsed: a long field serialises as LongNode, the parsed input as IntNode.
        JsonNode reserialised = MAPPER.readTree(MAPPER.writeValueAsString(parsed));

        assertThat(reserialised).isEqualTo(MAPPER.readTree(json));
    }

    @Test
    void levelsAreNeverClamped() throws Exception {
        Item sword = MAPPER.readValue(ProtocolExamples.ITEM, Item.class);

        assertThat(sword.enchants()).containsEntry("minecraft:sharpness", 32767);
    }
}
