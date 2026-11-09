package io.github.h7kz.proofstone.driver.mineflayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.ItemView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The reference driver's fault injection (T-M4-06), without Docker. */
class MineflayerRefFaultTest {

    private static final ItemView SWORD = new ItemView("minecraft:diamond_sword", 1, Map.of(), Map.of(), "Blade",
            List.of(), 0, 0, null);

    @Test
    void theFlagIsStrict() {
        assertThat(MineflayerRefFault.parse(null)).isFalse();
        assertThat(MineflayerRefFault.parse("false")).isFalse();
        assertThat(MineflayerRefFault.parse("true")).isTrue();
        assertThatThrownBy(() -> MineflayerRefFault.parse("on")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("proofstone.injectDriverFault must be true or false");
    }

    @Test
    void theFirstNonEmptyItemGetsOneMoreDamageAndNothingElseChanges() {
        List<ItemView> corrupted = MineflayerRefFault.corrupt(List.of(ItemView.EMPTY, SWORD, SWORD));

        assertThat(corrupted.get(0)).isEqualTo(ItemView.EMPTY);
        assertThat(corrupted.get(1).damage()).isEqualTo(1);
        assertThat(corrupted.get(1).amount()).isEqualTo(1);
        assertThat(corrupted.get(1).name()).isEqualTo("Blade");
        assertThat(corrupted.get(2)).isEqualTo(SWORD);
    }

    @Test
    void anEmptyInventoryIsUnchanged() {
        assertThat(MineflayerRefFault.corrupt(List.of(ItemView.EMPTY))).containsExactly(ItemView.EMPTY);
    }
}
