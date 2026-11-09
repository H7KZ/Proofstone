package io.github.h7kz.proofstone.driver.mineflayer;

import io.github.h7kz.proofstone.api.ItemView;
import java.util.ArrayList;
import java.util.List;

/**
 * A deliberate driver fault for the self-tests (T-M4-06, self-tests section 3). With
 * {@code -Dproofstone.injectDriverFault=true} the reference driver reports the first non-empty player-inventory item
 * with one more point of damage than the server synced. Nothing else changes, so a test that asserts only type and
 * amount still passes on this driver, and only the differential comparator sees the difference.
 *
 * <p>Test-only: the flag is read per session, is off by default and is never set by the framework outside a
 * differential bad test (examples/bad-tests). Any other value of the property is an error.
 */
final class MineflayerRefFault {

    private MineflayerRefFault() {
    }

    /** @return whether the fault is injected; a value other than {@code true} or {@code false} is an error */
    static boolean enabled() {
        return parse(System.getProperty(MineflayerRefDriver.FAULT_PROPERTY));
    }

    static boolean parse(String value) {
        if (value == null || value.equals("false")) {
            return false;
        }
        if (value.equals("true")) {
            return true;
        }
        throw new IllegalStateException("-D" + MineflayerRefDriver.FAULT_PROPERTY + " must be true or false, not \""
                + value + "\"");
    }

    /** The items with the first non-empty one's damage raised by one; the list is returned unchanged when all are empty. */
    static List<ItemView> corrupt(List<ItemView> items) {
        List<ItemView> out = new ArrayList<>(items);
        for (int i = 0; i < out.size(); i++) {
            ItemView item = out.get(i);
            if (!item.isEmpty()) {
                out.set(i, new ItemView(item.type(), item.amount(), item.enchants(), item.storedEnchants(), item.name(),
                        item.lore(), item.damage() + 1, item.repairCost(), item.customData(), item.pdc(), item.raw()));
                break;
            }
        }
        return out;
    }
}
