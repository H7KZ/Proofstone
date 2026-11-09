package io.github.h7kz.proofstone.agent.bungee;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;

/**
 * The sender a console-style command runs as. BungeeCord commands answer through {@code sendMessage}, so a sender of our
 * own collects the reply exactly, where the log would only approximate it. It holds every permission, like the console,
 * and is not a player, so a command that needs one refuses it. Messages are flattened to plain text with the colour
 * codes dropped, as a console shows them.
 */
final class CapturingSender implements CommandSender {

    private final List<String> lines = new ArrayList<>();

    @Override
    public String getName() {
        return "CONSOLE";
    }

    @Override
    public void sendMessage(String message) {
        record(message);
    }

    @Override
    public void sendMessages(String... messages) {
        for (String message : messages) {
            record(message);
        }
    }

    @Override
    public void sendMessage(BaseComponent... message) {
        record(BaseComponent.toPlainText(message));
    }

    @Override
    public void sendMessage(BaseComponent message) {
        record(message.toPlainText());
    }

    private void record(String text) {
        String plain = ChatColor.stripColor(text);
        synchronized (lines) {
            lines.add(plain);
        }
    }

    // The permission and group API: the console holds everything and has no groups to change.

    @Override
    public Collection<String> getGroups() {
        return Collections.emptyList();
    }

    @Override
    public void addGroups(String... groups) {
        throw new UnsupportedOperationException("the agent's command sender has no groups");
    }

    @Override
    public void removeGroups(String... groups) {
        throw new UnsupportedOperationException("the agent's command sender has no groups");
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }

    @Override
    public void setPermission(String permission, boolean value) {
        throw new UnsupportedOperationException("the agent's command sender holds every permission already");
    }

    @Override
    public Collection<String> getPermissions() {
        return Collections.emptyList();
    }

    /** What the command printed, one entry per message. */
    List<String> lines() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }
}
