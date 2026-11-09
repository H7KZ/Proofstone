package io.github.h7kz.proofstone.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SchemaValidationTest {

    static Stream<Arguments> validDocuments() {
        return Stream.of(
                Arguments.of("health.json", ProtocolExamples.HEALTH),
                Arguments.of("proxy.json", ProtocolExamples.PROXY_SERVER),
                Arguments.of("proxy.json#/$defs/servers", ProtocolExamples.PROXY_SERVERS),
                Arguments.of("proxy.json#/$defs/servers", "{\"servers\":[]}"),
                Arguments.of("proxy.json#/$defs/player", ProtocolExamples.PROXY_PLAYER),
                Arguments.of("proxy.json#/$defs/player", ProtocolExamples.PROXY_PLAYER.replace("\"lobby\"", "null")),
                Arguments.of("error.json", ProtocolExamples.ERROR),
                Arguments.of("item.json", ProtocolExamples.ITEM),
                Arguments.of("player-state.json", ProtocolExamples.PLAYER_STATE),
                Arguments.of("command.json", ProtocolExamples.COMMAND),
                Arguments.of("command.json#/$defs/reply", ProtocolExamples.COMMAND_REPLY),
                Arguments.of("envelope.json", ProtocolExamples.ENVELOPE_OK),
                Arguments.of("envelope.json", ProtocolExamples.ENVELOPE_ERROR),
                Arguments.of("item.json", "{\"type\":\"minecraft:stone\",\"amount\":64}"),
                Arguments.of("player-state.json", ProtocolExamples.PLAYER_STATE.replace("\"cursor\":{\"type\":\"minecraft:stone\",\"amount\":64},", "\"cursor\":null,")),
                Arguments.of("block.json", ProtocolExamples.BLOCK),
                Arguments.of("block.json#/$defs/write", ProtocolExamples.BLOCK_WRITE),
                Arguments.of("config.json#/$defs/patch", ProtocolExamples.CONFIG_ENTRY),
                Arguments.of("config.json#/$defs/read", ProtocolExamples.CONFIG_ENTRY),
                Arguments.of("reload.json", "{\"proofstone-reload-command\":\"fixture-reload\"}"),
                Arguments.of("reload.json", "{}"),
                Arguments.of("ticks.json", ProtocolExamples.TICKS),
                Arguments.of("event.json", ProtocolExamples.EVENT),
                Arguments.of("log-record.json#/$defs/page", ProtocolExamples.LOG_PAGE)
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
                Arguments.of("health.json", ProtocolExamples.HEALTH.replace("\"1.2\"", "\"2.0\"")),
                Arguments.of("health.json", ProtocolExamples.HEALTH.replace("}", ",\"extra\":1}")),
                Arguments.of("proxy.json", ProtocolExamples.PROXY_SERVER.replace("\"online\":true", "\"online\":\"yes\"")),
                Arguments.of("proxy.json", ProtocolExamples.PROXY_SERVER.replace("\"players\":1", "\"players\":-1")),
                Arguments.of("proxy.json", ProtocolExamples.PROXY_SERVER.replace("\"address\":\"server-lobby:25565\",", "")),
                Arguments.of("proxy.json", ProtocolExamples.PROXY_SERVER.replace("}", ",\"extra\":1}")),
                Arguments.of("proxy.json#/$defs/servers", "{}"),
                Arguments.of("proxy.json#/$defs/player", ProtocolExamples.PROXY_PLAYER.replace(",\"previousServer\":\"lobby\"", "")),
                Arguments.of("proxy.json#/$defs/player", ProtocolExamples.PROXY_PLAYER.replace("\"proxy\":", "\"position\":")),
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
                Arguments.of("envelope.json", "{\"ok\":false}"),
                Arguments.of("block.json", ProtocolExamples.BLOCK.replace("minecraft:stone", "stone")),
                Arguments.of("block.json", ProtocolExamples.BLOCK.replace("\"x\":0", "\"x\":0.5")),
                Arguments.of("block.json#/$defs/write", ProtocolExamples.BLOCK_WRITE.replace("\"facing\":\"west\"", "\"Facing\":\"west\"")),
                Arguments.of("config.json#/$defs/patch", "{\"path\":\"greeting\"}"),
                Arguments.of("config.json#/$defs/patch", "{\"path\":\"bad path\",\"value\":1}"),
                Arguments.of("reload.json", "{\"proofstone-reload-command\":\"\"}"),
                Arguments.of("reload.json", "{\"command\":\"fixture-reload\"}"),
                Arguments.of("ticks.json", "{\"global\":-1}"),
                Arguments.of("event.json", ProtocolExamples.EVENT.replace("\"Alex\"", "[\"Alex\"]")),
                Arguments.of("event.json", ProtocolExamples.EVENT.replace("\"tick\":1200", "\"tick\":\"1200\"")),
                Arguments.of("log-record.json#/$defs/page", ProtocolExamples.LOG_PAGE.replace("\"WARN\"", "\"SEVERE\""))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidDocuments")
    void invalidDocumentFails(String schema, String json) {
        assertThat(ProtocolSchemas.errors(schema, json)).isNotEmpty();
    }
}
