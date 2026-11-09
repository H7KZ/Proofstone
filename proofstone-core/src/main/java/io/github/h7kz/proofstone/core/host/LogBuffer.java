package io.github.h7kz.proofstone.core.host;

import java.util.ArrayList;
import java.util.List;

/** Thread-safe line buffer for container output. Kept whole so repro bundles can include it. */
public final class LogBuffer {

    private final List<String> lines = new ArrayList<>();

    public synchronized void append(String chunk) {
        for (String line : chunk.split("\\R")) {
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
    }

    public synchronized boolean contains(String text) {
        return lines.stream().anyMatch(line -> line.contains(text));
    }

    /** The last {@code count} lines, newline separated. */
    public synchronized String tail(int count) {
        return String.join("\n", lines.subList(Math.max(0, lines.size() - count), lines.size()));
    }

    /** How many lines there are now; a mark for {@link #since}. */
    public synchronized int size() {
        return lines.size();
    }

    /** The lines added after {@code mark}, a value of {@link #size}. */
    public synchronized List<String> since(int mark) {
        return List.copyOf(lines.subList(Math.min(mark, lines.size()), lines.size()));
    }

    public synchronized String all() {
        return String.join("\n", lines);
    }
}
