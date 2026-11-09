package io.github.h7kz.proofstone.driver.mcpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;

/**
 * Maps registry ids in item packets to namespaced keys. Nothing here is a hard-coded number.
 *
 * <ul>
 *   <li>Enchantments are a data-driven registry. The server sends them in the configuration phase and an id is the
 *       entry's position, so {@link #record} keeps every registry the session receives.</li>
 *   <li>Item types are a built-in registry that the server does not send. The list in
 *       {@code items-<protocol>.txt} is the server's own {@code registries.json} report for the pinned build, one
 *       key per line in id order (see docs/platforms/paper.md for how it is regenerated).</li>
 * </ul>
 *
 * An id that is not known is an error, never a guess (AGENTS.md section 3).
 */
final class ItemRegistries {

    static final String ENCHANTMENT = "minecraft:enchantment";

    private final List<String> items;
    private final Map<String, List<String>> received = new ConcurrentHashMap<>();

    ItemRegistries(List<String> items) {
        this.items = List.copyOf(items);
    }

    /** The item list bundled for a protocol version, such as {@code 26.3}. */
    static ItemRegistries forProtocol(String protocolVersion) {
        String resource = "items-" + protocolVersion + ".txt";
        try (InputStream in = ItemRegistries.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("no bundled item registry " + resource + " for protocol "
                        + protocolVersion);
            }
            List<String> keys = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        keys.add(line.strip());
                    }
                }
            }
            return new ItemRegistries(keys);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Remembers the entries of one configuration-phase registry. */
    void record(ClientboundRegistryDataPacket packet) {
        List<String> keys = new ArrayList<>();
        for (RegistryEntry entry : packet.getEntries()) {
            keys.add(entry.getId().asString());
        }
        received.put(packet.getRegistry().asString(), List.copyOf(keys));
    }

    /** Test seam: the same as receiving the registry packet. */
    void record(String registry, List<String> keys) {
        received.put(registry, List.copyOf(keys));
    }

    /** The numeric id of an item key; an unknown key is an error, never a guess. */
    int itemId(String key) {
        int id = items.indexOf(key);
        if (id < 0) {
            throw new IllegalStateException("unknown minecraft:item key " + key);
        }
        return id;
    }

    String item(int id) {
        return lookup("minecraft:item", items, id);
    }

    String enchantment(int id) {
        List<String> keys = received.get(ENCHANTMENT);
        if (keys == null) {
            throw new IllegalStateException("the server sent no " + ENCHANTMENT
                    + " registry in the configuration phase, so enchantment id " + id + " cannot be named");
        }
        return lookup(ENCHANTMENT, keys, id);
    }

    private static String lookup(String registry, List<String> keys, int id) {
        if (id < 0 || id >= keys.size()) {
            throw new IllegalStateException("unknown " + registry + " id " + id + " (registry has " + keys.size()
                    + " entries)");
        }
        return keys.get(id);
    }
}
