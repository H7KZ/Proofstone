package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.protocol.Item;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The wire {@code item.pdc} to the typed values of {@code ItemView.pdc()} (T-M11-05). */
class PdcWireTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static ItemView view(String pdc) throws Exception {
        Item item = JSON.readValue("{\"type\":\"minecraft:stick\",\"amount\":1" + pdc + "}", Item.class);
        return HostPlayer.view(item);
    }

    @Test
    void everyPrimitiveComesBackAsItsJavaType() throws Exception {
        Map<String, Object> pdc = view(",\"pdc\":{"
                + "\"p:b\":{\"type\":\"byte\",\"value\":1},\"p:s\":{\"type\":\"short\",\"value\":2},"
                + "\"p:i\":{\"type\":\"int\",\"value\":3},\"p:l\":{\"type\":\"long\",\"value\":4},"
                + "\"p:f\":{\"type\":\"float\",\"value\":1.5},\"p:d\":{\"type\":\"double\",\"value\":2.5},"
                + "\"p:t\":{\"type\":\"string\",\"value\":\"v\"}}").pdc();

        assertThat(pdc).containsEntry("p:b", (byte) 1).containsEntry("p:s", (short) 2).containsEntry("p:i", 3)
                .containsEntry("p:l", 4L).containsEntry("p:f", 1.5f).containsEntry("p:d", 2.5d)
                .containsEntry("p:t", "v");
    }

    @Test
    void arraysAndNestedContainersAreTyped() throws Exception {
        Map<String, Object> pdc = view(",\"pdc\":{"
                + "\"p:b\":{\"type\":\"byte[]\",\"value\":[1,-2]},\"p:i\":{\"type\":\"int[]\",\"value\":[3,4]},"
                + "\"p:l\":{\"type\":\"long[]\",\"value\":[5,9000000000]},"
                + "\"p:c\":{\"type\":\"container\",\"value\":{\"q:n\":{\"type\":\"byte\",\"value\":7}}}}").pdc();

        assertThat((byte[]) pdc.get("p:b")).containsExactly(1, -2);
        assertThat((int[]) pdc.get("p:i")).containsExactly(3, 4);
        assertThat((long[]) pdc.get("p:l")).containsExactly(5L, 9000000000L);
        assertThat(pdc.get("p:c")).isEqualTo(Map.of("q:n", (byte) 7));
    }

    @Test
    void anUnsupportedValueIsReportedNotDropped() throws Exception {
        Map<String, Object> pdc = view(",\"pdc\":{\"p:x\":{\"type\":\"unsupported\",\"value\":\"a list\"}}").pdc();

        assertThat(pdc).containsEntry("p:x", Map.of("unsupported", "a list"));
    }

    @Test
    void noPdcFieldIsNullAndAnEmptyOneIsEmpty() throws Exception {
        assertThat(view("").pdc()).isNull();
        assertThat(view(",\"pdc\":{}").pdc()).isEmpty();
    }

    @Test
    void anUnknownTypeOrShapeFailsLoudly() {
        assertThatThrownBy(() -> view(",\"pdc\":{\"p:x\":{\"type\":\"quaternion\",\"value\":1}}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("p:x").hasMessageContaining("quaternion");
        assertThatThrownBy(() -> view(",\"pdc\":{\"p:x\":5}")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p:x");
        assertThatThrownBy(() -> view(",\"pdc\":{\"p:x\":{\"type\":\"byte\",\"value\":\"a\"}}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not a number");
        assertThatThrownBy(() -> view(",\"pdc\":{\"p:x\":{\"type\":\"byte[]\"}}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("p:x");
    }
}
