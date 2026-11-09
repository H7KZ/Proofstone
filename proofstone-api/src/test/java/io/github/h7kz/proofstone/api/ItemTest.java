package io.github.h7kz.proofstone.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemTest {

    @Test
    void namesAreNamespacedAndLevelsAreNeverClamped() {
        Item sword = Item.of("diamond_sword").enchant("sharpness", 255).enchant("minecraft:unbreaking", 3)
                .name("Blade").lore("a", "b").damage(12).amount(2);

        assertThat(sword.type()).isEqualTo("minecraft:diamond_sword");
        assertThat(sword.enchants()).containsOnly(Map.entry("minecraft:sharpness", 255),
                Map.entry("minecraft:unbreaking", 3));
        assertThat(sword.lore()).containsExactly("a", "b");
        assertThat(sword.damage()).isEqualTo(12);
        assertThat(sword.amount()).isEqualTo(2);
    }

    @Test
    void itemsAreImmutable() {
        Item base = Item.of("enchanted_book");
        Item stored = base.storedEnchant("sharpness", 5);

        assertThat(base.storedEnchants()).isEmpty();
        assertThat(stored.storedEnchants()).containsOnly(Map.entry("minecraft:sharpness", 5));
    }

    @Test
    void invalidInputFailsLoudly() {
        assertThatThrownBy(() -> Item.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Item.of("stick").amount(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Item.of("stick").enchant("sharpness", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Item.of("stick").damage(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemViewMatchesTypesWithAndWithoutTheNamespace() {
        ItemView view = new ItemView("minecraft:stick", 1, Map.of(), Map.of(), null, List.of(), 0, 0, null);

        assertThat(view.is("stick")).isTrue();
        assertThat(view.is("minecraft:stick")).isTrue();
        assertThat(view.is("diamond")).isFalse();
        assertThat(ItemView.EMPTY.isEmpty()).isTrue();
    }
}
