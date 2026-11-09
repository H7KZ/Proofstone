package io.github.h7kz.proofstone.protocol;

import java.util.Map;

/** Request body of {@code PUT /v1/world/block} (block.json#/$defs/write). {@code state} may be null: the default state. */
public record BlockWrite(String type, Map<String, String> state) {
}
