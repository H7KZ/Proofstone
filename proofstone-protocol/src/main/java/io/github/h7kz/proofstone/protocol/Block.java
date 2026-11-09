package io.github.h7kz.proofstone.protocol;

import java.util.Map;

/**
 * Reply of {@code GET} and {@code PUT /v1/world/block} (block.json). The position is in the query string; {@code state}
 * maps BlockData property names to values and is empty for a block with none.
 */
public record Block(String world, int x, int y, int z, String type, Map<String, String> state) {
}
