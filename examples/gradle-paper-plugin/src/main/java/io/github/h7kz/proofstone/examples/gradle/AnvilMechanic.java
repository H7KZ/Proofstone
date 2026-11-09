package io.github.h7kz.proofstone.examples.gradle;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The anvil mechanic under test (T-M6-04): one iron ingot on the left and one gold ingot on the right make one diamond.
 *
 * <p>Two bugs are injected and off by default. Each is a config flag under {@code bugs.} that the suite turns on for
 * one test (see the README), to prove the suite catches it:
 * <ul>
 *   <li>{@code bugs.double-output}: the result has two diamonds, not one;</li>
 *   <li>{@code bugs.accept-any-right}: any right input makes a diamond, not only gold.</li>
 * </ul>
 */
public final class AnvilMechanic extends JavaPlugin implements Listener {

    static final String BUG_DOUBLE_OUTPUT = "double-output";
    static final String BUG_ACCEPT_ANY_RIGHT = "accept-any-right";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        AnvilInventory anvil = event.getInventory();
        ItemStack left = anvil.getItem(0);
        ItemStack right = anvil.getItem(1);
        if (left == null || right == null || left.getType() != Material.IRON_INGOT) {
            // Not this mechanic: leave the vanilla result alone.
            return;
        }
        boolean goldRecipe = right.getType() == Material.GOLD_INGOT;
        if (!goldRecipe && !bugOn(BUG_ACCEPT_ANY_RIGHT)) {
            return;
        }
        event.setResult(new ItemStack(Material.DIAMOND, bugOn(BUG_DOUBLE_OUTPUT) ? 2 : 1));
    }

    /** Read on every event, so a flag patched by a test takes effect at once. */
    boolean bugOn(String bug) {
        return getConfig().getBoolean("bugs." + bug, false);
    }
}
