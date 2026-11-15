package io.github.h7kz.proofstone.core.driver;

import io.github.h7kz.proofstone.api.ResourcePackAnswer;

/**
 * How a client answers a resource-pack push, set before it connects (client-driver spec section 2b, ADR-0017 section 7).
 *
 * @param answer the test's declared answer, or {@code null} when it declared none: a push then ends the join loudly
 * @param source the harness pack source's base URL ({@code http://127.0.0.1:<port>/}), the only place a driver may
 *     download from; {@code null} when the cell serves no pack
 */
public record ResourcePackPolicy(ResourcePackAnswer.Answer answer, String source) {

    /** No answer declared and no source: any push ends the join with an error. */
    public static final ResourcePackPolicy NONE = new ResourcePackPolicy(null, null);

    /** Whether {@code url} is served by the harness source, so an accept may download it. */
    public boolean fromSource(String url) {
        return source != null && url != null && source.endsWith("/") && url.startsWith(source);
    }
}
