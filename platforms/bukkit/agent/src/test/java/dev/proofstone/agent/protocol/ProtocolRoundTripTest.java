package dev.proofstone.agent.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProtocolRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    static Stream<Arguments> examples() {
        return Stream.of(
                Arguments.of(ProtocolExamples.HEALTH, new TypeReference<Health>() {}),
                Arguments.of(ProtocolExamples.ERROR, new TypeReference<ProtocolError>() {}),
                Arguments.of(ProtocolExamples.ENVELOPE_OK, new TypeReference<Envelope<Health>>() {}),
                Arguments.of(ProtocolExamples.ENVELOPE_ERROR, new TypeReference<Envelope<Health>>() {}));
    }

    @ParameterizedTest
    @MethodSource("examples")
    void parseThenSerialiseGivesEqualJson(String json, TypeReference<?> type) throws Exception {
        Object parsed = MAPPER.readValue(json, type);

        JsonNode reserialised = MAPPER.valueToTree(parsed);

        assertThat(reserialised).isEqualTo(MAPPER.readTree(json));
    }
}
