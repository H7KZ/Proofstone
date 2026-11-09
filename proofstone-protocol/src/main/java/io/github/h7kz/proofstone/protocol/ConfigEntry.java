package io.github.h7kz.proofstone.protocol;

/**
 * A config path and its value (config.json). Used as the PATCH body and as the reply of GET
 * {@code /v1/plugins/{id}/config?path=}. {@code value} is a YAML-compatible JSON value.
 */
public record ConfigEntry(String path, Object value) {
}
