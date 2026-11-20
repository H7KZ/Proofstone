package io.github.h7kz.proofstone.agent.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    /**
     * Spigot's PluginClassLoader answers a class it does not own through a global lookup in the other plugins' loaders and
     * logs "Loaded class X from Y which is not a depend or softdepend of this plugin" (T-M6-111). So a loader that does not
     * hold the class must never be asked to load it, whatever its place in the plugin order.
     */
    @Test
    void onlyTheLoaderThatHoldsTheClassIsAskedToLoadIt() {
        String name = FakeEvent.class.getName();
        RecordingLoader stranger = new RecordingLoader(false);
        RecordingLoader owner = new RecordingLoader(true);

        Class<?> found = EventProbes.resolve(name, Arrays.<ClassLoader>asList(stranger, owner), stranger);

        assertThat(found).isEqualTo(FakeEvent.class);
        assertThat(stranger.asked).as("a loader that does not hold the class was asked to load it").isEmpty();
        assertThat(owner.asked).containsExactly(name);
    }

    @Test
    void aClassNoPluginHoldsFallsBackToTheAgentsOwnLoader() {
        RecordingLoader own = new RecordingLoader(false);

        Class<?> found = EventProbes.resolve(FakeEvent.class.getName(),
                Arrays.<ClassLoader>asList(new RecordingLoader(false)), own);

        assertThat(found).isEqualTo(FakeEvent.class);
        assertThat(own.asked).containsExactly(FakeEvent.class.getName());
    }

    @Test
    void anUnknownClassResolvesToNull() {
        assertThat(EventProbes.resolve("no.such.Event", Arrays.<ClassLoader>asList(new RecordingLoader(true)),
                new RecordingLoader(false))).isNull();
    }

    /** A loader that serves test classes, holds the class file of {@code FakeEvent} only when told to, and records loads. */
    private static final class RecordingLoader extends ClassLoader {
        private final boolean holds;
        final List<String> asked = new ArrayList<>();

        RecordingLoader(boolean holds) {
            super(EventProbesTest.class.getClassLoader());
            this.holds = holds;
        }

        @Override
        public URL getResource(String name) {
            return holds ? getParent().getResource(name) : null;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            asked.add(name);
            return super.loadClass(name, resolve);
        }
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
