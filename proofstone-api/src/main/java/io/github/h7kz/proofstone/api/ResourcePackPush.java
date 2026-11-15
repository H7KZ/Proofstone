package io.github.h7kz.proofstone.api;

import java.util.List;

/**
 * One resource pack the server pushed to a client, as the client saw and answered it (client-driver spec section 2b). A
 * snapshot: read {@link Client#resourcePacks()} again for later statuses.
 *
 * @param id the pack's id as the server sent it
 * @param url where the server said the pack is
 * @param hash the sha1 the server pushed, in hex; empty when it sent none
 * @param required whether the server requires the pack
 * @param prompt the prompt as plain text, or {@code null} when there is none
 * @param answer the answer the test declared, or {@code null} when it declared none (the join then ended)
 * @param statuses the statuses the client sent for this pack, in order ({@code ACCEPTED}, {@code DOWNLOADED}, ...)
 * @param problem what went wrong on the client's side ({@code hash mismatch: expected <h>, got <h>}, a failed download,
 *     no declared answer), or {@code null}; the test decides whether it is a failure
 * @param popped whether the server has removed the pack since
 */
public record ResourcePackPush(String id, String url, String hash, boolean required, String prompt,
        ResourcePackAnswer.Answer answer, List<String> statuses, String problem, boolean popped) {

    public ResourcePackPush {
        statuses = List.copyOf(statuses);
    }
}
