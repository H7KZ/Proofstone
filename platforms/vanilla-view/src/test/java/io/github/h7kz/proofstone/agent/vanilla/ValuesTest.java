package io.github.h7kz.proofstone.agent.vanilla;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** event.json {@code fieldValue} for the values that need no running game. */
class ValuesTest {

    @Test
    void primitivesAndNullPassThrough() {
        assertThat(Values.json(null)).isNull();
        assertThat(Values.json("Alex")).isEqualTo("Alex");
        assertThat(Values.json(3)).isEqualTo(3);
        assertThat(Values.json(true)).isEqualTo(true);
    }

    @Test
    void anythingElseIsTypedWithItsToString() {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("$type", "java.lang.StringBuilder");
        expected.put("value", "abc");
        assertThat(Values.json(new StringBuilder("abc"))).isEqualTo(expected);
        assertThat(Values.typed("error", "boom")).containsEntry("$type", "error").containsEntry("value", "boom");
    }
}
