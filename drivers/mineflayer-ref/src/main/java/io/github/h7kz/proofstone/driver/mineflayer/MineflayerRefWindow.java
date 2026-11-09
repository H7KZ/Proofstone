package io.github.h7kz.proofstone.driver.mineflayer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.h7kz.proofstone.api.Click;
import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.SlotStack;
import io.github.h7kz.proofstone.core.driver.WindowSession;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A window read through the sidecar. Every read is the server's last sync at that moment: the sidecar sends clicks
 * without a predicted result, so nothing here is applied locally (client-driver spec section 2).
 */
final class MineflayerRefWindow implements WindowSession {

    private static final Duration SYNC_WAIT = Duration.ofSeconds(10);

    private final MineflayerRefSession session;
    private final int id;
    /** Whether {@link MineflayerRefFault} corrupts this window's item views (the player inventory only). */
    private final boolean faulty;

    MineflayerRefWindow(MineflayerRefSession session, int id) {
        this(session, id, false);
    }

    MineflayerRefWindow(MineflayerRefSession session, int id, boolean faulty) {
        this.session = session;
        this.id = id;
        this.faulty = faulty;
    }

    @Override
    public String type() {
        return current().path("type").asText();
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public int stateId() {
        return current().path("stateId").asInt();
    }

    @Override
    public String title() {
        return current().path("title").asText();
    }

    @Override
    public List<SlotStack> slots() {
        List<SlotStack> out = new ArrayList<>();
        for (JsonNode stack : current().path("slots")) {
            out.add(MineflayerRefItems.slot(stack));
        }
        return out;
    }

    @Override
    public SlotStack cursor() {
        return MineflayerRefItems.slot(current().path("cursor"));
    }

    @Override
    public List<ItemView> itemViews() {
        List<ItemView> out = new ArrayList<>();
        for (JsonNode stack : current().path("slots")) {
            out.add(MineflayerRefItems.view(stack));
        }
        return faulty ? MineflayerRefFault.corrupt(out) : out;
    }

    @Override
    public ItemView cursorView() {
        return MineflayerRefItems.view(current().path("cursor"));
    }

    @Override
    public void click(int slot, Click click) {
        int before = stateId();
        ObjectNode params = session.params().put("slot", slot);
        params.put("button", button(click)).put("mode", mode(click));
        session.call("click", params, SYNC_WAIT);
        // The server answers a click with a sync that carries a newer state id. A click it treats as a no-op sends
        // none, and that is an error, not a silent pass (client-driver spec section 2).
        try {
            Await.until("a sync answering the click on slot " + slot + " of window " + id, this::stateId,
                    state -> state != before, SYNC_WAIT);
        } catch (io.github.h7kz.proofstone.core.await.AwaitTimeoutException e) {
            throw new ClientDriverException("the server did not answer the click on slot " + slot + " of window " + id
                    + " (a no-op sends no sync)", e);
        }
    }

    @Override
    public void close() {
        if (id == 0) {
            return;
        }
        session.call("closeWindow", session.params(), SYNC_WAIT);
    }

    @Override
    public void rename(String name) {
        String type = type();
        if (!"minecraft:anvil".equals(type)) {
            throw new ClientDriverException("cannot rename in " + type + ": only an anvil has a rename box");
        }
        session.call("rename", session.params().put("name", name), SYNC_WAIT);
    }

    private JsonNode current() {
        if (id == 0) {
            return session.call("inventory", session.params(), SYNC_WAIT);
        }
        JsonNode window = session.call("window", session.params(), SYNC_WAIT);
        if (!window.path("open").asBoolean() || window.path("id").asInt() != id) {
            throw new ClientDriverException("window " + id + " is closed");
        }
        return window;
    }

    /** The mouse button of a click, as the Minecraft protocol numbers it. */
    static int button(Click click) {
        return switch (click) {
            case LEFT, SHIFT_LEFT, DOUBLE, DROP -> 0;
            case RIGHT, SHIFT_RIGHT, CTRL_DROP -> 1;
            case NUMBER_1, NUMBER_2, NUMBER_3, NUMBER_4, NUMBER_5, NUMBER_6, NUMBER_7, NUMBER_8, NUMBER_9 ->
                click.ordinal() - Click.NUMBER_1.ordinal();
            case SWAP_OFFHAND -> 40;
        };
    }

    /** The click mode: 0 pick up, 1 quick move, 2 swap with a hotbar key, 4 drop, 6 pick up all. */
    static int mode(Click click) {
        return switch (click) {
            case LEFT, RIGHT -> 0;
            case SHIFT_LEFT, SHIFT_RIGHT -> 1;
            case NUMBER_1, NUMBER_2, NUMBER_3, NUMBER_4, NUMBER_5, NUMBER_6, NUMBER_7, NUMBER_8, NUMBER_9,
                    SWAP_OFFHAND -> 2;
            case DROP, CTRL_DROP -> 4;
            case DOUBLE -> 6;
        };
    }
}
