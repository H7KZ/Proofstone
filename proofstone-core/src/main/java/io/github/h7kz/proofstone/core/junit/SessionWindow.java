package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.api.Window;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.driver.WindowSession;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/** A {@link Window} over a driver window session. Reads are the server's last sync; clicks wait for its answer. */
final class SessionWindow implements Window {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final int PLAYER_SLOTS = 36;

    private final WindowSession window;

    SessionWindow(WindowSession window) {
        this.window = window;
    }

    @Override
    public String type() {
        return window.type();
    }

    @Override
    public String title() {
        return window.title();
    }

    @Override
    public List<ItemView> slots() {
        return window.itemViews();
    }

    @Override
    public ItemView slot(int index) {
        List<ItemView> slots = slots();
        if (index < 0 || index >= slots.size()) {
            throw new IllegalArgumentException("slot " + index + " is outside the " + slots.size() + " slots of "
                    + type());
        }
        return slots.get(index);
    }

    @Override
    public ItemView awaitSlot(int index, Predicate<ItemView> predicate) {
        return Await.until("slot " + index + " of " + type() + " to match", () -> slot(index), predicate,
                DEFAULT_TIMEOUT);
    }

    @Override
    public void click(int slot, Click click) {
        window.click(slot, click);
    }

    @Override
    public ItemView cursor() {
        return window.cursorView();
    }

    @Override
    public void moveFromInventory(Predicate<ItemView> predicate, int toSlot) {
        List<ItemView> slots = slots();
        // The player's 36 slots trail the container's own, in every window.
        for (int i = Math.max(0, slots.size() - PLAYER_SLOTS); i < slots.size(); i++) {
            if (!slots.get(i).isEmpty() && predicate.test(slots.get(i))) {
                click(i, Click.LEFT);
                click(toSlot, Click.LEFT);
                return;
            }
        }
        throw new IllegalStateException("no player-inventory item matches in " + type() + ": "
                + slots.subList(Math.max(0, slots.size() - PLAYER_SLOTS), slots.size()).stream()
                        .filter(item -> !item.isEmpty()).toList());
    }

    @Override
    public void rename(String name) {
        window.rename(name);
    }

    @Override
    public void dropCursorIntoFirstEmptySlot() {
        if (cursor().isEmpty()) {
            throw new IllegalStateException("the cursor is empty in " + type());
        }
        List<ItemView> slots = slots();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).isEmpty()) {
                click(i, Click.LEFT);
                return;
            }
        }
        throw new IllegalStateException("no empty slot in " + type() + " for the cursor " + cursor());
    }

    @Override
    public void close() {
        window.close();
    }
}
