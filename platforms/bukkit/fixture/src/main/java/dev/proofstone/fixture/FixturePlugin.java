package dev.proofstone.fixture;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Test-only plugin for the agent's config and reload contract (T-M2-03). Its only job is to be a plugin with a
 * config.yml and a declared reload hook. It is never shipped.
 */
public final class FixturePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // The hook a reload request names. It does nothing: the reply shows the command was found and ran.
        getCommand("fixture-reload").setExecutor((sender, command, label, args) -> true);
    }
}
