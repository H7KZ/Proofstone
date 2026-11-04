package dev.proofstone.core.junit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.proofstone.api.CapturedEvent;
import dev.proofstone.api.EventProbe;
import dev.proofstone.api.Events;
import dev.proofstone.core.await.Await;
import dev.proofstone.core.host.PaperHost;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** {@link Events} over the agent's {@code /v1/events} endpoints. */
final class HostEvents implements Events {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PaperHost host;

    HostEvents(PaperHost host) {
        this.host = host;
    }

    @Override
    public EventProbe subscribe(String className) {
        // The wire record names its component className, but the JSON key is "class".
        JsonNode reply = host.request("POST", "/v1/events/subscribe", Map.of("class", className));
        JsonNode id = reply.get("probeId");
        if (id == null || !id.isTextual()) {
            throw new IllegalStateException("agent subscribe reply for " + className + " has no probeId: " + reply);
        }
        return new Probe(host, className, id.asText());
    }

    private static final class Probe implements EventProbe {

        private final PaperHost host;
        private final String className;
        private final String id;

        Probe(PaperHost host, String className, String id) {
            this.host = host;
            this.className = className;
            this.id = id;
        }

        @Override
        public CapturedEvent awaitOne() {
            return Await.until("first " + className + " event", this::events, list -> !list.isEmpty(),
                    SessionWindow.DEFAULT_TIMEOUT).get(0);
        }

        @Override
        public List<CapturedEvent> events() {
            JsonNode page = host.request("GET", "/v1/events/" + id + "?since=0", null);
            JsonNode events = page.get("events");
            if (events == null || !events.isArray()) {
                throw new IllegalStateException("agent event page of " + id + " has no events array: " + page);
            }
            // The wire key is "class", which the protocol record cannot name (see ProbeEvent), so read it by hand.
            List<CapturedEvent> out = new ArrayList<>();
            for (JsonNode event : events) {
                out.add(new CapturedEvent(text(event, "class"), event.path("tick").asLong(), text(event, "thread"),
                        fields(event.get("fields"))));
            }
            return List.copyOf(out);
        }

        @Override
        public void close() {
            host.request("DELETE", "/v1/events/" + id, null);
        }

        private static String text(JsonNode event, String key) {
            JsonNode value = event.get(key);
            if (value == null || !value.isTextual()) {
                throw new IllegalStateException("captured event has no \"" + key + "\": " + event);
            }
            return value.asText();
        }

        private static Map<String, Object> fields(JsonNode node) {
            if (node == null || !node.isObject()) {
                throw new IllegalStateException("captured event has no fields object: " + node);
            }
            try {
                return JSON.convertValue(node, new TypeReference<Map<String, Object>>() { });
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("unreadable event fields: " + node, e);
            }
        }
    }
}
