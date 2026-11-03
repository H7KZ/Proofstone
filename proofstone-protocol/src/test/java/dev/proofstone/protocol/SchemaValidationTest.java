package dev.proofstone.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SchemaValidationTest {

    static Stream<Arguments> validDocuments() {
        return Stream.of(
                Arguments.of("health.json", ProtocolExamples.HEALTH),
                Arguments.of("error.json", ProtocolExamples.ERROR),
                Arguments.of("item.json", ProtocolExamples.ITEM),
                Arguments.of("player-state.json", ProtocolExamples.PLAYER_STATE),
                Arguments.of("command.json", ProtocolExamples.COMMAND),
                Arguments.of("command.json#/$defs/reply", ProtocolExamples.COMMAND_REPLY),
                Arguments.of("envelope.json", ProtocolExamples.ENVELOPE_OK),
                Arguments.of("envelope.json", ProtocolExamples.ENVELOPE_ERROR),
                Arguments.of("item.json", "{\"type\":\"minecraft:stone\",\"amount\":64}"),
                Arguments.of("player-state.json", ProtocolExamples.PLAYER_STATE.replace("\"cursor\":{\"type\":\"minecraft:stone\",\"amount\":64},", "\"cursor\":null,"))
        );
    }

    @ParameterizedTest
    @MethodSource("validDocuments")
    void validDocumentPasses(String schema, String json) {
        assertThat(ProtocolSchemas.errors(schema, json)).isEmpty();
    }

    static Stream<Arguments> invalidDocuments() {
        return Stream.of(
                Arguments.of("health.json", "{\"agentVersion\":\"0.1.0\",\"platform\":\"paper\",\"mcVersion\":\"1.21.11\",\"capabilities\":[]}"),
                Arguments.of("health.json", ProtocolExamples.HEALTH.replace("\"1.0\"", "\"2.0\"")),
                Arguments.of("health.json", ProtocolExamples.HEALTH.replace("}", ",\"extra\":1}")),
                Arguments.of("error.json", ProtocolExamples.ERROR.replace("UNKNOWN_KEY", "unknown_key")),
                Arguments.of("item.json", ProtocolExamples.ITEM.replace("\"amount\":1", "\"amount\":0")),
                Arguments.of("item.json", ProtocolExamples.ITEM.replace("minecraft:diamond_sword", "diamond_sword")),
                Arguments.of("item.json", ProtocolExamples.ITEM.replace("minecraft:sharpness", "sharpness")),
                Arguments.of("item.json", ProtocolExamples.ITEM.replace("\"raw\":\"{}\"", "\"raw\":\"{}\",\"color\":1")),
                Arguments.of("player-state.json", ProtocolExamples.PLAYER_STATE.replace("survival", "SURVIVAL")),
                Arguments.of("player-state.json", ProtocolExamples.PLAYER_STATE.replace("\"0\":", "\"slot0\":")),
                Arguments.of("command.json", "{\"command\":\"\"}"),
                Arguments.of("command.json", "{\"command\":\"version\",\"timeoutMs\":0}"),
                Arguments.of("command.json#/$defs/reply", "{}"),
                Arguments.of("envelope.json", "{\"ok\":true,\"error\":{\"code\":\"TIMEOUT\",\"message\":\"x\"}}"),
                Arguments.of("envelope.json", "{\"ok\":false,\"data\":{},\"error\":{\"code\":\"TIMEOUT\",\"message\":\"x\"}}"),
                Arguments.of("envelope.json", "{\"ok\":false}")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidDocuments")
    void invalidDocumentFails(String schema, String json) {
        assertThat(ProtocolSchemas.errors(schema, json)).isNotEmpty();
    }
}
