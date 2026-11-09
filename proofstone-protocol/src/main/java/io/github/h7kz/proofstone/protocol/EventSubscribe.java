package io.github.h7kz.proofstone.protocol;

/**
 * Request body of {@code POST /v1/events/subscribe} (event.json#/$defs/subscribe). The JSON key is {@code class}; see
 * {@link ProbeEvent} for why the component is {@code className}.
 */
public record EventSubscribe(String className) {
}
