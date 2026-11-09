package io.github.h7kz.proofstone.agent.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.chat.ChatType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;

/**
 * The sender a console-style command runs as. Velocity commands answer through {@code sendMessage}, so a sender of our
 * own collects the reply exactly, where the log would only approximate it. It holds every permission, like the console,
 * and is not a player, so a command that needs one refuses it. Components are translated the way Velocity translates
 * them for a console (English) and flattened to plain text.
 */
final class CapturingSource implements CommandSource {

    private final List<String> lines = new ArrayList<>();

    @Override
    public void sendMessage(Component message) {
        record(message);
    }

    @Override
    public void sendMessage(Component message, ChatType.Bound boundChatType) {
        record(message);
    }

    private void record(Component message) {
        String text = PlainTextComponentSerializer.plainText().serialize(GlobalTranslator.render(message, Locale.ENGLISH));
        synchronized (lines) {
            lines.add(text);
        }
    }

    @Override
    public Tristate getPermissionValue(String permission) {
        return Tristate.TRUE;
    }

    /** What the command printed, one entry per message. */
    List<String> lines() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }
}
