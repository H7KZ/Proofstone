package dev.proofstone.examples.bedrock;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Example plugin for the simulated Bedrock tier (T-M5-06). It detects Bedrock players the way Geyser and Floodgate
 * name them: a leading {@code .} on the username. {@code /bedrock-check <name>} reports what it sees, so a test can read
 * the detection from its own client.
 *
 * <p>This is test-only and never shipped. It is Java 8 bytecode, like the other fixtures, so it loads on 1.8.8.
 */
public final class BedrockPrefixFixture extends JavaPlugin {

    /** The name prefix Floodgate uses by default. A Java client with this prefix stands in for a Bedrock player. */
    static final String PREFIX = ".";

    /** The detection under test: an online player whose name starts with the prefix is a Bedrock player. */
    static boolean isBedrock(Player player) {
        return player != null && player.getName().startsWith(PREFIX);
    }

    @Override
    public void onEnable() {
        getCommand("bedrock-check").setExecutor((sender, command, label, args) -> {
            if (args.length != 1) {
                return false;
            }
            Player player = Bukkit.getPlayerExact(args[0]);
            sender.sendMessage("bedrock-check " + args[0] + " online=" + (player != null)
                    + " bedrock=" + isBedrock(player));
            return true;
        });
    }
}
