package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import io.github.h7kz.proofstone.core.driver.SlotStack;
import io.github.h7kz.proofstone.core.driver.WindowSession;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * T-M11-11: {@code Window.awaitCursor} polls the client's cursor like {@code awaitSlot} polls a slot. No sleeps: the
 * fake window shows its cursor only from the Nth read on, which models a cursor packet that trails the click.
 */
class SessionWindowAwaitCursorTest {

    private static final Duration SHORT = Duration.ofMillis(300);

    private static ItemView stack(String type) {
        return new ItemView("minecraft:" + type, 1, Map.of(), Map.of(), null, List.of(), 0, 0, null);
    }

    @Test
    void waitsForACursorThatArrivesAfterTheFirstReads() {
        ItemView sword = stack("diamond_sword");
        FakeSession session = new FakeSession(sword, 4);
        SessionWindow window = new SessionWindow(session, Duration.ofSeconds(10));

        assertThat(window.cursor()).as("a one-shot read right after the click").isEqualTo(ItemView.EMPTY);
        ItemView got = window.awaitCursor(c -> c.is("diamond_sword"));

        assertThat(got).isEqualTo(sword);
        assertThat(session.reads.get()).as("it polled until the cursor showed").isGreaterThan(2);
    }

    @Test
    void anAlreadySatisfiedCursorReturnsAtOnce() {
        FakeSession session = new FakeSession(stack("diamond_sword"), 0);
        SessionWindow window = new SessionWindow(session, SHORT);

        assertThat(window.awaitCursor(c -> c.is("diamond_sword")).type()).isEqualTo("minecraft:diamond_sword");
        assertThat(session.reads.get()).isEqualTo(1);
    }

    @Test
    void aCursorThatNeverMatchesTimesOutLoudlyWithTheLastObservedCursor() {
        FakeSession session = new FakeSession(stack("dirt"), 0);
        SessionWindow window = new SessionWindow(session, SHORT);

        assertThatThrownBy(() -> window.awaitCursor(c -> c.is("diamond_sword")))
                .isInstanceOf(AwaitTimeoutException.class)
                .hasMessageContaining("the cursor of minecraft:anvil to match")
                .hasMessageContaining("dirt");
    }

    @Test
    void aCursorThatNeverArrivesTimesOutShowingEmpty() {
        FakeSession session = new FakeSession(stack("diamond_sword"), Integer.MAX_VALUE);
        SessionWindow window = new SessionWindow(session, SHORT);

        assertThatThrownBy(() -> window.awaitCursor(c -> c.is("diamond_sword")))
                .isInstanceOf(AwaitTimeoutException.class)
                .hasMessageContaining("minecraft:air");
    }

    private static final class FakeSession implements WindowSession {
        final AtomicInteger reads = new AtomicInteger();
        private final ItemView cursor;
        private final int emptyReads;

        FakeSession(ItemView cursor, int emptyReads) {
            this.cursor = cursor;
            this.emptyReads = emptyReads;
        }

        @Override public ItemView cursorView() {
            return reads.incrementAndGet() > emptyReads ? cursor : ItemView.EMPTY;
        }
        @Override public List<ItemView> itemViews() { return List.of(); }
        @Override public String type() { return "minecraft:anvil"; }
        @Override public int id() { return 1; }
        @Override public int stateId() { return 0; }
        @Override public List<SlotStack> slots() { return List.of(); }
        @Override public SlotStack cursor() { return SlotStack.EMPTY; }
        @Override public String title() { return "Anvil"; }
        @Override public void click(int slot, Click click) { }
        @Override public void close() { }
        @Override public void rename(String name) { }
    }
}
