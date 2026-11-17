package io.github.h7kz.proofstone.core.driver.bedrock;

import io.github.h7kz.proofstone.api.ItemView;
import io.github.h7kz.proofstone.core.driver.SlotStack;
import io.github.h7kz.proofstone.core.driver.WindowView;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.Slot;
import java.util.List;
import java.util.Map;

/**
 * The player inventory as the host last synced it. Bedrock sends no data components, so an item view carries its
 * identifier and count only; a client's view is compared on what Geyser translates (ADR-0018 section 9).
 */
final class BedrockInventory implements WindowView {

    private final List<Slot> slots;

    BedrockInventory(List<Slot> slots) {
        this.slots = List.copyOf(slots);
    }

    @Override
    public String type() {
        return "minecraft:inventory";
    }

    @Override
    public int id() {
        return 0;
    }

    @Override
    public int stateId() {
        return 0;
    }

    @Override
    public List<SlotStack> slots() {
        return slots.stream().map(s -> s.count() <= 0 ? SlotStack.EMPTY : new SlotStack(s.runtimeId(), s.count())).toList();
    }

    @Override
    public SlotStack cursor() {
        return SlotStack.EMPTY;
    }

    @Override
    public List<ItemView> itemViews() {
        return slots.stream().map(s -> s.count() <= 0 ? ItemView.EMPTY
                : new ItemView(s.id(), s.count(), Map.of(), Map.of(), null, List.of(), 0, 0, null)).toList();
    }

    @Override
    public ItemView cursorView() {
        return ItemView.EMPTY;
    }
}
