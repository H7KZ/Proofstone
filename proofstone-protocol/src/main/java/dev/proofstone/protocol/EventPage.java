package dev.proofstone.protocol;

import java.util.List;

/** Reply of {@code GET /v1/events/{probeId}?since=} (event.json#/$defs/page). {@code next} is the cursor for the next poll. */
public record EventPage(List<ProbeEvent> events, long next) {
}
