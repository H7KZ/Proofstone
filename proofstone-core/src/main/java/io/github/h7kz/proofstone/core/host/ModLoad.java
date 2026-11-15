package io.github.h7kz.proofstone.core.host;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Names the mod under test that a loader gave up on (ADR-0017 section 4, T-M8-11): the ids come from the mod's own
 * descriptor, and the log line that mentions one of them is quoted. A failed start that names no declared mod is not blamed
 * on one, because a loader also gives up for reasons that are not the mod's.
 */
final class ModLoad {

    private static final String FABRIC = "fabric.mod.json";
    private static final String NEOFORGE = "META-INF/neoforge.mods.toml";
    private static final Pattern TOML_MOD_ID = Pattern.compile("^\\s*modId\\s*=\\s*\"([^\"]+)\"", Pattern.MULTILINE);

    private ModLoad() {
    }

    /**
     * The mod ids a jar declares: {@code id} of {@code fabric.mod.json}, or every {@code modId} of
     * {@code META-INF/neoforge.mods.toml}.
     *
     * @throws PaperHostException when the jar has neither descriptor or declares no id: a mod that cannot be named is an error
     */
    static List<String> ids(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry fabric = zip.getEntry(FABRIC);
            if (fabric != null) {
                JsonNode id = AgentClient.JSON.readTree(zip.getInputStream(fabric)).get("id");
                if (id == null || id.asText().isBlank()) {
                    throw new PaperHostException("mod " + jar.getFileName() + ": " + FABRIC + " has no id");
                }
                return List.of(id.asText());
            }
            ZipEntry neoforge = zip.getEntry(NEOFORGE);
            if (neoforge != null) {
                String toml = new String(zip.getInputStream(neoforge).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                List<String> ids = new ArrayList<>();
                Matcher matcher = TOML_MOD_ID.matcher(toml);
                while (matcher.find()) {
                    ids.add(matcher.group(1));
                }
                if (ids.isEmpty()) {
                    throw new PaperHostException("mod " + jar.getFileName() + ": " + NEOFORGE + " declares no modId");
                }
                return List.copyOf(ids);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read mod " + jar, e);
        }
        throw new PaperHostException("mod " + jar.getFileName() + " has neither " + FABRIC + " nor " + NEOFORGE
                + ": it is not a Fabric or NeoForge mod");
    }

    /**
     * The INVALID message for a loader that gave up at start while {@code mods} were installed, naming the mod the log
     * mentions, or {@code null} when the log mentions none of them.
     *
     * @param cell the cell's identity
     * @param mods the mod jars copied into {@code /mods}
     * @param loaderLine the loader's own give-up line, or what ended the boot
     * @param log the server log up to the failure
     */
    static String invalid(String cell, List<Path> mods, String loaderLine, String log) {
        String[] lines = log.split("\\R");
        List<String> named = new ArrayList<>();
        for (Path mod : mods) {
            String file = mod.getFileName().toString();
            for (String id : ids(mod)) {
                int at = firstMention(lines, id, file);
                if (at >= 0) {
                    named.add("mod " + id + " (" + file + ") crashed the server at load on " + cell + ". The log says: "
                            + quote(lines, at));
                }
            }
        }
        if (named.isEmpty()) {
            return null;
        }
        return "INVALID test declaration: " + String.join("; ", named) + " The loader reported: " + loaderLine;
    }

    /**
     * The first line that mentions the mod and reports a problem. An INFO line that lists the loaded mods is not a failure:
     * it would blame every installed mod.
     */
    private static int firstMention(String[] lines, String id, String file) {
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            boolean problem = line.contains("ERROR") || line.contains("FATAL") || line.contains("WARN")
                    || line.contains("Exception");
            if (problem && (line.contains(id) || line.contains(file))) {
                return index;
            }
        }
        return -1;
    }

    /** The line, and the line after it when that is the exception's message and not a stack frame. */
    private static String quote(String[] lines, int at) {
        String quoted = lines[at].strip();
        if (at + 1 < lines.length && !lines[at + 1].isBlank() && !lines[at + 1].stripLeading().startsWith("at ")) {
            quoted += " / " + lines[at + 1].strip();
        }
        return quoted;
    }
}
