package io.github.h7kz.proofstone.core.driver;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.BlockPos;
import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.api.DragMode;
import io.github.h7kz.proofstone.api.Face;
import io.github.h7kz.proofstone.api.ItemView;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * T-M11-13: a driver that leaves an optional operation to the interface default (the reference Mineflayer driver does) fails
 * with the same type as Bedrock and the public {@code Client}/{@code Window} defaults: {@link UnsupportedOperationException}.
 */
class DriverUnsupportedRuleTest {

    private final ClientSession bare = new ClientSession() {
        @Override public void chat(String message) { }
        @Override public void command(String commandWithoutSlash) { }
        @Override public ChatMessage awaitMessage(Pattern pattern, Duration timeout) { return null; }
        @Override public void useBlock(BlockPos pos, Face face) { }
        @Override public WindowSession awaitWindow(Duration timeout) { return null; }
        @Override public WindowView inventory() { return null; }
        @Override public int experienceLevel() { return 0; }
        @Override public double[] position() { return null; }
        @Override public List<String> transcript() { return List.of(); }
        @Override public void close() { }
    };

    private final WindowSession window = new WindowSession() {
        @Override public String type() { return "minecraft:generic_9x3"; }
        @Override public int id() { return 1; }
        @Override public int stateId() { return 0; }
        @Override public List<SlotStack> slots() { return List.of(); }
        @Override public SlotStack cursor() { return null; }
        @Override public ItemView cursorView() { return null; }
        @Override public List<ItemView> itemViews() { return List.of(); }
        @Override public String title() { return "Chest"; }
        @Override public void click(int slot, Click click) { }
        @Override public void close() { }
        @Override public void rename(String name) { }
    };

    @Test
    void everyOptionalSessionDefaultThrowsUnsupportedOperation() {
        assertThatThrownBy(() -> bare.placeBlock(new BlockPos(0, 0, 0), Face.UP))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("place blocks");
        assertThatThrownBy(() -> bare.breakBlock(new BlockPos(0, 0, 0)))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("break blocks");
        assertThatThrownBy(() -> bare.awaitServerSwitch(Duration.ofMillis(1)))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("proxy server switch");
        assertThatThrownBy(bare::resourcePacks)
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("resource packs");
    }

    @Test
    void theWindowDragDefaultThrowsUnsupportedOperation() {
        assertThatThrownBy(() -> window.drag(List.of(1, 2), DragMode.LEFT))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("drag");
    }
}
