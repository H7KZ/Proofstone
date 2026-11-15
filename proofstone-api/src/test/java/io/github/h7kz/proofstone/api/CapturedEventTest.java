package io.github.h7kz.proofstone.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A getter that returned null is a captured value, not a missing getter. PlayerResourcePackStatusEvent's {@code getHash} is
 * null on Paper 26.3, and Map.copyOf refused the whole event with a NullPointerException (T-M8-10).
 */
class CapturedEventTest {

    @Test
    void aNullGetterValueIsKeptAndReadAsNullWhileAMissingGetterStillFailsLoudly() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("getHash", null);
        fields.put("getStatus", Map.of("value", "ACCEPTED"));
        CapturedEvent event = new CapturedEvent("x.Event", 1, "Server thread", fields);

        assertThat(event.field("getHash")).isNull();
        assertThat(event.field("hash")).isNull();
        assertThat(event.fields()).containsKey("getHash");
        assertThatThrownBy(() -> event.field("getNothing")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no getter");
        assertThatThrownBy(() -> event.fields().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }
}
