// SPIKE (T-M8-01). Only with PROOFSTONE_SPIKE_CONTENT=1: registers one item, the smallest "content mod". Shows whether a
// vanilla client can join once a mod adds a registry entry the client does not have.
package proofstone.spike;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

final class SpikeFabricContent {
    private SpikeFabricContent() {
    }

    static void register() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("proofstone_spike", "probe_item"));
        Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
        System.out.println("[proofstone-spike] registered item proofstone_spike:probe_item");
    }
}
