package dev.proofstone.agent.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

/** Event getters differ by version (T-M3-06): one that cannot run on this server is one field's error. */
class EventProbesTest {

    @Test
    void aGetterThatNeedsAMissingClassIsRecordedAsAnErrorAndTheOtherFieldsSurvive() {
        Map<String, Object> fields = EventProbes.fields(new FakeEvent());

        assertThat(fields.get("getFine")).isEqualTo("fine");
        assertThat(fields.get("getFromNewerApi")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) fields.get("getFromNewerApi")).get("$type")).isEqualTo("error");
        assertThat(String.valueOf(((Map<?, ?>) fields.get("getFromNewerApi")).get("value")))
                .contains("NoSuchMethodError");
    }

    public static final class FakeEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        public String getFine() {
            return "fine";
        }

        public String getFromNewerApi() {
            throw new NoSuchMethodError("newer API");
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }
    }
}
