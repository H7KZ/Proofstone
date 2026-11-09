package io.github.h7kz.proofstone.protocol;

import java.util.List;

/** Reply of {@code GET /v1/health}. {@code protocol} is {@code "1.<minor>"}; core refuses another major. */
public record Health(
        String agentVersion,
        String protocol,
        String platform,
        String mcVersion,
        List<String> capabilities) {
}
