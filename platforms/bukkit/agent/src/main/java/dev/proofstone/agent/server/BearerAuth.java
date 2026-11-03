package dev.proofstone.agent.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Checks {@code Authorization: Bearer <token>} (spec §1). Compares in constant time. */
final class BearerAuth {

    private BearerAuth() {
    }

    static boolean authorised(String authorizationHeader, String expectedToken) {
        if (authorizationHeader == null) {
            return false;
        }
        byte[] expected = ("Bearer " + expectedToken).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, authorizationHeader.getBytes(StandardCharsets.UTF_8));
    }
}
