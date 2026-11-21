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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * T-M11-11: {@code Window.awaitCursor} polls the client's cursor like {@code awaitSlot} polls a slot. No Docker: the late
 * case delivers the cursor from another thread {@value #LATE_MS} ms after the click returned. The delay models the
 * network, it is not a wait of the code under test.
 */
class SessionWindowAwaitCursorTest {

    private static final Duration SHORT = Duration.ofMillis(300);
    private static final int LATE_MS = 250;

    private final ScheduledExecutorService network = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "fake-network");
        thread.setDaemon(true);
        return thread;
    });

    @AfterEach
    void stopNetwork() {
        network.shutdownNow();
    }

    private static ItemView stack(String type) {
        return new ItemView("minecraft:" + type, 1, Map.of(), Map.of(), null, List.of(), 0, 0, null);
    }

    @Test
    void waitsForACursorThatArrivesAfterTheClickReturned() {
        ItemView sword = stack("diamond_sword");
        FakeSession session = new FakeSession(sword, 0);
        SessionWindow window = new SessionWindow(session, Duration.ofSeconds(10));

        // The click has returned; the cursor packet lands LATE_MS later, on the network thread.
        network.schedule(session::deliverCursor, LATE_MS, TimeUnit.MILLISECONDS);
        assertThat(window.cursor()).as("a one-shot read right after the click").isEqualTo(ItemView.EMPTY);
        ItemView got = window.awaitCursor(c -> c.is("diamond_sword"));

        assertThat(got).isEqualTo(sword);
    }

    @Test
    void anAlreadySatisfiedCursorReturnsAtOnce() {
        FakeSession session = new FakeSession(stack("diamond_sword"), 0);
        session.deliverCursor();
        SessionWindow window = new SessionWindow(session, SHORT);

        assertThat(window.awaitCursor(c -> c.is("diamond_sword")).type()).isEqualTo("minecraft:diamond_sword");
        assertThat(session.reads.get()).isEqualTo(1);
    }

    @Test
    void aCursorThatNeverMatchesTimesOutLoudlyWithTheLastObservedCursor() {
        FakeSession session = new FakeSession(stack("dirt"), 0);
        session.deliverCursor();
        SessionWindow window = new SessionWindow(session, SHORT);

        assertThatThrownBy(() -> window.awaitCursor(c -> c.is("diamond_sword")))
                .isInstanceOf(AwaitTimeoutException.class)
                .hasMessageContaining("the cursor of minecraft:anvil to match")
                .hasMessageContaining("dirt");
    }

    @Test
    void aCursorThatNeverArrivesTimesOutShowingEmpty() {
        FakeSession session = new FakeSession(stack("diamond_sword"), 0);
        SessionWindow window = new SessionWindow(session, SHORT);

        assertThatThrownBy(() -> window.awaitCursor(c -> c.is("diamond_sword")))
                .isInstanceOf(AwaitTimeoutException.class)
                .hasMessageContaining("minecraft:air");
    }

    private static final class FakeSession implements WindowSession {
        final AtomicInteger reads = new AtomicInteger();
        private final ItemView arriving;
        private volatile ItemView cursor = ItemView.EMPTY;

        FakeSession(ItemView arriving, int unused) {
            this.arriving = arriving;
        }

        /** The cursor packet: from now on the client reads the item. */
        void deliverCursor() {
            cursor = arriving;
        }

        @Override public ItemView cursorView() {
            reads.incrementAndGet();
            return cursor;
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
