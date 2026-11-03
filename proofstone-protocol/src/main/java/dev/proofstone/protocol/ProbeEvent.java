package dev.proofstone.protocol;

import java.util.Map;

/**
 * One captured event (event.json). The JSON key of {@code className} is {@code class}, which Java reserves; the
 * agent's serialiser and the JSON schema use that key. The Jackson round-trip test does not cover this record.
 * Field values are JSON primitives, {@link Item}-shaped maps, {@code {name}} maps or {@code {$type, value}} maps.
 */
public record ProbeEvent(String className, long tick, String thread, Map<String, Object> fields) {
}
