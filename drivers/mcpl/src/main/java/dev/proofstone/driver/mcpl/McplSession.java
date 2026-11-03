package dev.proofstone.driver.mcpl;

import dev.proofstone.core.await.Await;
import dev.proofstone.core.await.AwaitTimeoutException;
import dev.proofstone.core.driver.ChatMessage;
import dev.proofstone.core.driver.ClientDriverException;
import dev.proofstone.core.driver.ClientSession;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

/**
 * One joined player. It keeps a buffer of received chat so a reply that beats {@link #awaitMessage} is not lost,
 * and a transcript of every packet name for repro bundles. A kick or disconnect ends the session with its reason;
 * waiting on a closed session fails with that reason and never hangs.
 */
final class McplSession implements ClientSession {

    enum State { JOINING, PLAYING, DISCONNECTED }

    private final Session network;
    private final String username;
    private final List<ChatMessage> chat = Collections.synchronizedList(new ArrayList<>());
    private final List<String> transcript = Collections.synchronizedList(new ArrayList<>());
    private volatile State state = State.JOINING;
    private volatile String disconnectReason;

    McplSession(Session network, String username) {
        this.network = network;
        this.username = username;
    }

    SessionListener listener() {
        return new SessionAdapter() {
            @Override
            public void packetReceived(Session session, Packet packet) {
                onReceived(packet);
            }

            @Override
            public void packetSent(Session session, Packet packet) {
                record("-> " + packet.getClass().getSimpleName());
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                String reason = event.getReason() == null ? "" : flatten(event.getReason());
                if (event.getCause() != null) {
                    reason += (reason.isEmpty() ? "" : " ") + "(" + event.getCause() + ")";
                }
                // The disconnect packet already named the reason; the event only fills in when none did.
                if (disconnectReason == null) {
                    disconnectReason = reason.isEmpty() ? "connection closed with no reason" : reason;
                }
                record("disconnected: " + disconnectReason);
                state = State.DISCONNECTED;
            }
        };
    }

    private void onReceived(Packet packet) {
        String summary = packet.getClass().getSimpleName();
        if (packet instanceof ClientboundLoginPacket) {
            state = State.PLAYING;
        } else if (packet instanceof ClientboundDisconnectPacket kick) {
            disconnectReason = flatten(kick.getReason());
            summary += " " + disconnectReason;
        } else if (packet instanceof ClientboundSystemChatPacket system) {
            // Overlay is the action bar, which is not chat.
            if (!system.isOverlay()) {
                summary += " " + buffer(flatten(system.getContent()), true);
            }
        } else if (packet instanceof ClientboundDisguisedChatPacket disguised) {
            summary += " " + buffer(flatten(disguised.getMessage()), false);
        } else if (packet instanceof ClientboundPlayerChatPacket player) {
            Component unsigned = player.getUnsignedContent();
            String body = unsigned != null ? flatten(unsigned) : player.getContent();
            summary += " " + buffer("<" + flatten(player.getName()) + "> " + body, false);
        }
        record("<- " + summary);
    }

    private String buffer(String text, boolean system) {
        chat.add(new ChatMessage(text, system));
        return text;
    }

    private void record(String line) {
        transcript.add(Instant.now() + " " + line);
    }

    State state() {
        return state;
    }

    String disconnectReason() {
        return disconnectReason;
    }

    private void requireOpen(String what) {
        if (state != State.PLAYING) {
            throw new ClientDriverException(what + " but " + username + " is " + state
                    + (disconnectReason == null ? "" : ": " + disconnectReason));
        }
    }

    @Override
    public void chat(String message) {
        requireOpen("cannot chat");
        // Offline mode has no signing keys, so the signature is absent and the acknowledgement list is empty.
        network.send(new ServerboundChatPacket(message, System.currentTimeMillis(), 0L, null, 0, new BitSet(), 0));
    }

    @Override
    public void command(String commandWithoutSlash) {
        requireOpen("cannot run a command");
        network.send(new ServerboundChatCommandPacket(commandWithoutSlash));
    }

    @Override
    public ChatMessage awaitMessage(Pattern pattern, Duration timeout) {
        Probe probe;
        try {
            // Await treats a throwing probe as "not yet", so a disconnect is reported as a value instead.
            probe = Await.until("chat message matching " + pattern + " for " + username, () -> take(pattern),
                    p -> p.message() != null || p.closed(), timeout);
        } catch (AwaitTimeoutException e) {
            throw new ClientDriverException("no message matching " + pattern + " within " + timeout
                    + "; unconsumed: " + chat + "; transcript tail: " + tail(), e);
        }
        if (probe.message() == null) {
            throw new ClientDriverException("disconnected while waiting for " + pattern + ": " + disconnectReason
                    + "; unconsumed: " + chat);
        }
        return probe.message();
    }

    private record Probe(ChatMessage message, boolean closed) {
    }

    private Probe take(Pattern pattern) {
        synchronized (chat) {
            for (Iterator<ChatMessage> it = chat.iterator(); it.hasNext();) {
                ChatMessage candidate = it.next();
                if (pattern.matcher(candidate.text()).find()) {
                    it.remove();
                    return new Probe(candidate, false);
                }
            }
        }
        return new Probe(null, state == State.DISCONNECTED);
    }

    private List<String> tail() {
        synchronized (transcript) {
            return new ArrayList<>(transcript.subList(Math.max(0, transcript.size() - 10), transcript.size()));
        }
    }

    @Override
    public List<String> transcript() {
        synchronized (transcript) {
            return List.copyOf(transcript);
        }
    }

    @Override
    public void close() {
        network.disconnect("proofstone closing");
    }

    /** Plain text of a component: text, translation keys with their arguments, then children. No formatting. */
    static String flatten(Component component) {
        StringBuilder out = new StringBuilder();
        append(component, out);
        return out.toString();
    }

    private static void append(Component component, StringBuilder out) {
        if (component instanceof TextComponent text) {
            out.append(text.content());
        } else if (component instanceof TranslatableComponent translatable) {
            // Language files are not loaded, so the key stands in for the sentence and its arguments follow.
            out.append(translatable.fallback() != null ? translatable.fallback() : translatable.key());
            translatable.arguments().forEach(arg -> {
                out.append(' ');
                append(arg.asComponent(), out);
            });
        }
        component.children().forEach(child -> append(child, out));
    }
}
