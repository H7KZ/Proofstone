package dev.proofstone.fixture;

import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Test-only plugin for the agent's config and reload contract (T-M2-03). Its only job is to be a plugin with a
 * config.yml and a declared reload hook. It is never shipped.
 */
public final class FixturePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // One WARNING with a stack trace per enable, so golden/log/read has a deterministic record to capture. The
        // agent loads at STARTUP and attaches its log handler before this plugin enables. The log case replays before
        // the reload cases, which enable this plugin again.
        getLogger().log(Level.WARNING, "fixture warning for the log contract",
                new IllegalStateException("fixture log record"));
        // The hook a reload request names. It does nothing: the reply shows the command was found and ran.
        getCommand("fixture-reload").setExecutor((sender, command, label, args) -> true);
    }
}
