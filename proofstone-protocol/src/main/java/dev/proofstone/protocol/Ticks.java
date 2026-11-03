package dev.proofstone.protocol;

import java.util.Map;

/**
 * Tick counter (ticks.json): reply of {@code GET /v1/ticks} and of {@code POST /v1/ticks/await}. {@code regions} is
 * null off Folia.
 */
public record Ticks(long global, Map<String, Long> regions) {
}
