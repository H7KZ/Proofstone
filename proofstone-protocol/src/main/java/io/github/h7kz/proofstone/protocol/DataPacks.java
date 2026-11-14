package io.github.h7kz.proofstone.protocol;

import java.util.List;

/**
 * Reply of {@code GET /v1/datapacks} (datapacks.json). Protocol 1.3. Ids are as the server names them, in its order.
 */
public record DataPacks(List<String> enabled, List<String> available) {
}
