package io.github.h7kz.proofstone.core.host;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * The per-cell Floodgate key (ADR-0018 section 5): 16 random bytes held in memory and copied to
 * {@code plugins/floodgate/key.pem} before the first start. Geyser on the same server uses Floodgate's key, so no second
 * file is written. It is never written to a file on the host, never logged, and redacted from every bundle and message in
 * every form it could appear in ({@link #forms()}), as the forwarding secret of a topology is (ADR-0016 section 8).
 */
final class FloodgateKey {

    /** Where Floodgate reads its key; the image syncs {@code /plugins} into {@code /data/plugins} (spike, 3 of 3 joins). */
    static final String PATH = "/plugins/floodgate/key.pem";
    static final String REDACTED = "<redacted: floodgate key>";
    static final int LENGTH = 16;

    private final byte[] bytes;

    private FloodgateKey(byte[] bytes) {
        if (bytes.length != LENGTH) {
            throw new IllegalArgumentException("a Floodgate key is " + LENGTH + " bytes, got " + bytes.length);
        }
        this.bytes = bytes.clone();
    }

    static FloodgateKey generate() {
        byte[] bytes = new byte[LENGTH];
        new SecureRandom().nextBytes(bytes);
        return new FloodgateKey(bytes);
    }

    /** A key of known bytes, for a test. */
    static FloodgateKey of(byte[] bytes) {
        return new FloodgateKey(bytes);
    }

    byte[] bytes() {
        return bytes.clone();
    }

    /** The key as it could appear in a log, a message or a file: raw bytes, hex and base64, each in its usual spellings. */
    List<String> forms() {
        HexFormat hex = HexFormat.of();
        Base64.Encoder url = Base64.getUrlEncoder();
        Base64.Encoder standard = Base64.getEncoder();
        // Padded forms before their unpadded prefixes, so a padded form is replaced whole.
        return List.of(
                hex.formatHex(bytes),
                hex.withUpperCase().formatHex(bytes),
                standard.encodeToString(bytes),
                url.encodeToString(bytes),
                standard.withoutPadding().encodeToString(bytes),
                url.withoutPadding().encodeToString(bytes),
                new String(bytes, StandardCharsets.ISO_8859_1));
    }

    /** {@code text} with every form of the key replaced. */
    String redact(String text) {
        String redacted = text;
        for (String form : forms()) {
            redacted = redacted.replace(form, REDACTED);
        }
        return redacted;
    }

    /** The key is never in the text of the object. */
    @Override
    public String toString() {
        return "FloodgateKey[" + LENGTH + " bytes]";
    }
}
