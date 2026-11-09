package dev.proofstone.core.host;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The leak detector (robustness R6): a server state that a test changes and leaves behind fails that test.
 *
 * <p>A snapshot has two parts. The world's rules are read back through the console with {@code gamerule <name>}. The
 * agent's full-state dump ({@code GET /v1/snapshot}, agent-protocol §2) adds each loaded plugin's enabled flag and config
 * values, and each online player's XP and inventory. Each entry is one line, {@code key: value}, and its key is unique.
 *
 * <p>The comparison is against the cleanup, not the test body (T-M4-12b). The baseline is taken before the test's client
 * joins. The after-snapshot is taken once the harness has restored the configs the test patched and disconnected its
 * clients. So what a test did to its own player (items given, XP spent) is not a leak, and only what the harness fails to
 * put back is. A player is compared only when it is online in both snapshots.
 *
 * <p>The console reply is best effort (ADR-0002). The detector therefore compares replies as they are and fails on a
 * blank one: an unreadable state can never count as unchanged.
 */
public final class LeakDetector {

    private static final String PLAYER = "player ";

    private LeakDetector() {
    }

    /**
     * @return one line per key: the world's rules, then the agent's plugins and players
     * @throws PaperHostException when a rule reads back blank, because the detector cannot tell what it holds
     */
    public static List<String> snapshot(PaperHost host) {
        List<String> entries = new ArrayList<>();
        for (String rule : GameRules.names(host.cell().mc())) {
            String reply = host.command("gamerule " + rule);
            if (reply == null || reply.isBlank()) {
                throw new PaperHostException("the leak detector read no reply for gamerule " + rule
                        + ": it cannot tell whether a test left it changed");
            }
            entries.add(rule + ": " + reply.trim());
        }
        entries.addAll(state(host.request("GET", "/v1/snapshot", null)));
        return List.copyOf(entries);
    }

    /**
     * The agent's dump as entries: each plugin's enabled flag and config values by dotted path, and each player's XP and
     * inventory slots. A dump with no plugin or player list is an error, never an empty state.
     */
    static List<String> state(JsonNode snapshot) {
        JsonNode plugins = snapshot.get("plugins");
        JsonNode players = snapshot.get("players");
        if (plugins == null || !plugins.isArray() || players == null || !players.isArray()) {
            throw new PaperHostException("the agent snapshot has no plugins and players lists: " + snapshot);
        }
        List<String> entries = new ArrayList<>();
        for (JsonNode plugin : plugins) {
            String name = plugin.get("name").asText();
            entries.add("plugin " + name + " enabled: " + plugin.get("enabled").asText());
            for (Map.Entry<String, JsonNode> value : fields(plugin.get("config"))) {
                entries.add("plugin " + name + " config " + value.getKey() + ": " + value.getValue());
            }
        }
        for (JsonNode player : players) {
            String name = player.get("name").asText();
            entries.add(PLAYER + name + " xp: level " + player.get("level") + " exp " + player.get("exp"));
            for (Map.Entry<String, JsonNode> slot : fields(player.get("inventory"))) {
                entries.add(PLAYER + name + " slot " + slot.getKey() + ": " + slot.getValue());
            }
        }
        return List.copyOf(entries);
    }

    private static Iterable<Map.Entry<String, JsonNode>> fields(JsonNode node) {
        if (node == null || !node.isObject()) {
            return List.of();
        }
        List<Map.Entry<String, JsonNode>> out = new ArrayList<>();
        node.fields().forEachRemaining(out::add);
        return out;
    }

    /**
     * @return one line per key whose entry differs, {@code before -> after}, or {@code -> gone} and {@code new:} for a
     *     key present on one side only. A player that is online on one side only is not compared (it is not the test's
     *     to leave behind), and a player is compared only while online on both sides.
     * @throws IllegalArgumentException when a snapshot holds the same key twice
     */
    public static List<String> changed(List<String> before, List<String> after) {
        Map<String, String> was = byKey(before);
        Map<String, String> now = byKey(after);
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> entry : was.entrySet()) {
            String value = now.get(entry.getKey());
            if (value == null) {
                if (!entry.getKey().startsWith(PLAYER)) {
                    changed.add(entry.getValue() + " -> gone");
                }
            } else if (!value.equals(entry.getValue())) {
                changed.add(entry.getValue() + " -> " + value);
            }
        }
        for (Map.Entry<String, String> entry : now.entrySet()) {
            if (!was.containsKey(entry.getKey()) && !entry.getKey().startsWith(PLAYER)) {
                changed.add("new: " + entry.getValue());
            }
        }
        return changed;
    }

    /** The key of an entry is everything before its first {@code ": "}, which no key contains. */
    private static Map<String, String> byKey(List<String> entries) {
        Map<String, String> keyed = new LinkedHashMap<>();
        for (String entry : entries) {
            int split = entry.indexOf(": ");
            String key = split < 0 ? entry : entry.substring(0, split);
            if (keyed.put(key, entry) != null) {
                throw new IllegalArgumentException("a snapshot holds the key twice: " + key);
            }
        }
        return keyed;
    }
}
