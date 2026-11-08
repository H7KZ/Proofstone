package dev.proofstone.core.host;

import java.util.ArrayList;
import java.util.List;

/**
 * The leak detector (robustness R6): a server state that a test changes and leaves behind fails that test. The
 * snapshot is the deterministic world's rules, read back through the console with {@code gamerule <name>}, so it covers
 * what the harness sets at boot and nothing else. The agent's full-state dump ({@code GET /v1/snapshot}) is not
 * implemented yet (docs/specs/agent-protocol.md), so a leak outside these rules is not seen.
 *
 * <p>The console reply is best effort (ADR-0002). The detector therefore compares replies as they are and fails on a
 * blank one: an unreadable state can never count as unchanged.
 */
public final class LeakDetector {

    private LeakDetector() {
    }

    /**
     * @return one entry per rule, the rule name with the reply that names its value
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
        return List.copyOf(entries);
    }

    /**
     * @return one line per entry that differs between the two snapshots, empty when nothing changed
     * @throws IllegalArgumentException when the snapshots do not cover the same rules
     */
    public static List<String> changed(List<String> before, List<String> after) {
        if (before.size() != after.size()) {
            throw new IllegalArgumentException("snapshots cover different rules: " + before.size() + " and "
                    + after.size());
        }
        List<String> changed = new ArrayList<>();
        for (int i = 0; i < before.size(); i++) {
            if (!before.get(i).equals(after.get(i))) {
                changed.add(before.get(i) + " -> " + after.get(i));
            }
        }
        return changed;
    }
}
