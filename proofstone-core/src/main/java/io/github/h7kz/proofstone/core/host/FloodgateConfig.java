package io.github.h7kz.proofstone.core.host;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipFile;

/**
 * Floodgate's {@code config.yml} for the key-mismatch fault (T-M9-11): the pinned jar's own default file with one value replaced,
 * the name of the key file Floodgate reads, plus the one value Floodgate fills in itself on its first start (the metrics id).
 * Taken from the jar that is already pinned by sha256, so no template of ours can drift from the build. Never used in a clean cell.
 */
final class FloodgateConfig {

    private static final String KEY_LINE = "key-file-name: key.pem";
    private static final String METRICS_ID = "${metrics.uuid}";

    private FloodgateConfig() {
    }

    /** The jar's default {@code config.yml} with {@code keyFile} as the key Floodgate reads. */
    static String withKeyFile(Path floodgateJar, String keyFile) {
        String template;
        try (ZipFile zip = new ZipFile(floodgateJar.toFile())) {
            var entry = zip.getEntry("config.yml");
            if (entry == null) {
                throw new PaperHostException("the Floodgate jar " + floodgateJar.getFileName() + " has no config.yml: cannot "
                        + "render the key-mismatch fault; read the pinned build's default file");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new PaperHostException("cannot read config.yml from " + floodgateJar + ": " + e.getMessage(), e);
        }
        if (!template.contains(KEY_LINE) || !template.contains(METRICS_ID)) {
            throw new PaperHostException("the Floodgate config.yml of " + floodgateJar.getFileName() + " has no \"" + KEY_LINE
                    + "\" or no " + METRICS_ID + " line: the key-mismatch fault needs updating for this build");
        }
        return template.replace(KEY_LINE, "key-file-name: " + keyFile).replace(METRICS_ID, UUID.randomUUID().toString());
    }
}
