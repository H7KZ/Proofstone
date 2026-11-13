package io.github.h7kz.proofstone.fixture;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Test-only plugin for the agent's config and reload contract (T-M2-03). Its only job is to be a plugin with a
 * config.yml and a declared reload hook. It is never shipped.
 */
public final class FixturePlugin extends JavaPlugin {

    /** The proxy plugin messaging channel every backend plugin uses; Paper maps this legacy name for modern servers. */
    static final String BUNGEE_CHANNEL = "BungeeCord";

    @Override
    public void onEnable() {
        // One WARNING with a stack trace per enable, so golden/log/read has a deterministic record to capture. The
        // agent loads at STARTUP and attaches its log handler before this plugin enables. The log case replays before
        // the reload cases, which enable this plugin again.
        getLogger().log(Level.WARNING, "fixture warning for the log contract",
                new IllegalStateException("fixture log record"));
        // The hook a reload request names. It does nothing: the reply shows the command was found and ran.
        getCommand("fixture-reload").setExecutor((sender, command, label, args) -> true);
        // Logs one SEVERE record with a stack trace on demand, so a test can provoke a server error (T-M2-08).
        // Its arguments become the message, which lets the test find its own record.
        getCommand("fixture-error").setExecutor((sender, command, label, args) -> {
            getLogger().log(Level.SEVERE, "fixture provoked error: " + String.join(" ", args),
                    new IllegalStateException("fixture error"));
            return true;
        });
        // Topology conformance P04 (T-M7-12): ask the proxy which server a player is on, and record the answer. It only
        // sends GetServer and logs the reply; the test reads the reply from this server's log.
        getServer().getMessenger().registerOutgoingPluginChannel(this, BUNGEE_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, BUNGEE_CHANNEL,
                (channel, player, message) -> recordReply(message));
        getCommand("fixture-getserver").setExecutor((sender, command, label, args) -> {
            Player player = args.length == 1 ? getServer().getPlayerExact(args[0]) : null;
            if (player == null) {
                sender.sendMessage("fixture-getserver needs the name of an online player");
                return false;
            }
            sendGetServer(player);
            return true;
        });
        // The same, but straight through Log4j2, the way the server's own code logs. Reflection keeps the fixture
        // compiled against the Bukkit API only.
        getCommand("fixture-log4j-error").setExecutor((sender, command, label, args) -> {
            try {
                Class<?> manager = Class.forName("org.apache.logging.log4j.LogManager");
                Object logger = manager.getMethod("getLogger", String.class).invoke(null, "ProofstoneFixtureLog4j");
                logger.getClass().getMethod("error", String.class, Throwable.class).invoke(logger,
                        "fixture log4j error: " + String.join(" ", args), new IllegalStateException("fixture error"));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Log4j2 is not available to the fixture", e);
            }
            return true;
        });
    }

    private void sendGetServer(Player player) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("GetServer");
        } catch (IOException e) {
            throw new IllegalStateException("cannot write the GetServer message", e);
        }
        player.sendPluginMessage(this, BUNGEE_CHANNEL, bytes.toByteArray());
    }

    /** The one place a reply is recorded: a line the conformance test finds in this server's log. */
    private void recordReply(byte[] message) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String subchannel = in.readUTF();
            if ("GetServer".equals(subchannel)) {
                getLogger().info("fixture BungeeCord reply: GetServer " + in.readUTF());
            } else {
                getLogger().info("fixture BungeeCord reply: other " + subchannel);
            }
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "fixture could not read a BungeeCord message", e);
        }
    }
}
