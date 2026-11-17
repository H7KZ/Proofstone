package io.github.h7kz.proofstone.core.junit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.core.cell.GeyserCell;
import io.github.h7kz.proofstone.core.host.GeyserHost;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The repro bundle of a Geyser cell (ADR-0018 sections 5 and 8): one directory per container ({@code server/},
 * {@code stand-in/}, {@code driver-host/}) with its log, the server's agent transcript and snapshot, the client transcript,
 * and {@code cell.json} (which says {@code "bedrockAuth": "offline-stub"}). Every agent token, the driver host's token and
 * the Floodgate key, in raw, hex and base64 form, are redacted from every file and each file is read back
 * ({@link BundleWriter#writeFiles}). {@code plugins/floodgate} is never part of a bundle, whoever asks for it.
 */
public final class GeyserBundle {

    /** The directory under a server's data that holds Floodgate's key; a bundle never includes anything below it. */
    static final String FLOODGATE_DIR = "plugins/floodgate";
    private static final ObjectMapper JSON = new ObjectMapper();

    private GeyserBundle() {
    }

    /**
     * Writes the bundle of a running cell to {@code dir} and returns it.
     *
     * @param driverTranscript the Bedrock client's transcript, or empty
     */
    public static Path write(Path dir, GeyserCell cell, GeyserHost host, List<String> driverTranscript) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("server/server.log", host.server().logs().all() + "\n");
        files.put("server/agent-transcript.jsonl", lines(host.server().agentTranscript()));
        files.put("server/snapshot.json", BundleWriter.snapshot(host.server()) + "\n");
        files.put("stand-in/stand-in.log", host.standInLogs().all() + "\n");
        files.put("driver-host/driver-host.log", host.driverHostLogs().all() + "\n");
        files.put("driver-transcript.jsonl", lines(driverTranscript.stream()
                .map(line -> JSON.createObjectNode().put("line", line).toString()).toList()));
        files.put("cell.json", JSON.valueToTree(cell).toPrettyString() + "\n");
        return write(dir, files, List.of(host.server().agentToken(), host.driverToken()), host.floodgateKeyForms());
    }

    /**
     * Writes {@code files} after dropping every path under {@code plugins/floodgate}, redacting {@code tokens} and each form
     * of the key, and reading every file back.
     */
    static Path write(Path dir, Map<String, String> files, List<String> tokens, List<String> keyForms) {
        for (String form : keyForms) {
            if (form == null || form.isEmpty()) {
                throw new IllegalArgumentException("a key form to redact must not be empty: it would match everywhere");
            }
        }
        Map<String, String> kept = new LinkedHashMap<>();
        files.forEach((path, content) -> {
            if (!isFloodgateFile(path)) {
                kept.put(path, content);
            }
        });
        return BundleWriter.writeFiles(dir, kept, tokens, keyForms);
    }

    /** Whether {@code path} (any separator, any leading directories) is Floodgate's directory or something in it. */
    static boolean isFloodgateFile(String path) {
        String normal = path.replace('\\', '/');
        return normal.equals(FLOODGATE_DIR) || normal.startsWith(FLOODGATE_DIR + "/")
                || normal.contains("/" + FLOODGATE_DIR + "/") || normal.endsWith("/" + FLOODGATE_DIR);
    }

    private static String lines(List<String> lines) {
        return lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
    }
}
