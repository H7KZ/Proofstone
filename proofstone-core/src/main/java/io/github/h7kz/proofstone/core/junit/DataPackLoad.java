package io.github.h7kz.proofstone.core.junit;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loaded or loud (ADR-0017 section 4): every declared data pack must be in the agent's {@code enabled} list. The list
 * comes from {@code GET /v1/datapacks}, never from the console (ADR-0002).
 */
final class DataPackLoad {

    private DataPackLoad() {
    }

    /** A Paper line ({@code [04:11:04 ERROR]: ...}) or a loader's log4j line ({@code [time] [thread/WARN] ...}). */
    private static final Pattern PROBLEM = Pattern.compile(
            "^\\[[^\\]]* (?:ERROR|WARN)\\]:|^\\[[^\\]]*\\]\\s*\\[[^\\]]*/(?:ERROR|WARN|FATAL)\\]");
    private static final int MAX_PER_PACK = 3;

    /**
     * A data pack that loads but logs an error is not a pack the test can trust (ADR-0017 section 4): a function that does
     * not parse is skipped by the server and the pack stays enabled, so {@link #invalid} passes it. The server logs the
     * error while it loads the pack, before the agent runs, so it is read from the host's log of the whole boot and not from
     * the agent's window. A line counts when it is WARN or worse and mentions a namespace of the pack ({@code ns:name}), so
     * the vanilla noise at boot ({@code No key layers}) is never blamed on a pack.
     *
     * @param namespacesByPack each declared pack's id ({@code file/<name>}) and the namespaces its {@code data} folder holds
     * @param log the server log from the container's start
     * @return the INVALID message naming the pack and quoting the server's lines, or {@code null} when none is logged
     */
    static String errorsLogged(Map<String, List<String>> namespacesByPack, String log, String cell) {
        String[] lines = log.split("\\R");
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, List<String>> pack : namespacesByPack.entrySet()) {
            int found = 0;
            for (int index = 0; index < lines.length && found < MAX_PER_PACK; index++) {
                if (PROBLEM.matcher(lines[index]).find() && mentions(lines[index], pack.getValue())) {
                    problems.add(pack.getKey() + " logged an error while the server loaded it: " + quote(lines, index));
                    found++;
                }
            }
        }
        if (problems.isEmpty()) {
            return null;
        }
        return "INVALID test declaration: data pack " + String.join("; data pack ", problems) + " (on " + cell
                + "; the pack is enabled, but the server logged the error)";
    }

    private static boolean mentions(String line, List<String> namespaces) {
        for (String namespace : namespaces) {
            if (Pattern.compile("(?<![a-z0-9_.-])" + Pattern.quote(namespace) + ":").matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    /** The line, and the line after it when that is the exception's message and not a stack frame or another record. */
    private static String quote(String[] lines, int at) {
        String quoted = lines[at].strip();
        if (at + 1 < lines.length) {
            String next = lines[at + 1];
            if (!next.isBlank() && !next.startsWith("[") && !next.stripLeading().startsWith("at ")) {
                quoted += " / " + next.strip();
            }
        }
        return quoted;
    }

    /**
     * The INVALID message naming the declared packs that are not enabled, or {@code null} when all are.
     *
     * @param declared the ids the host installed ({@code file/<name>})
     * @param reply the {@code data} of {@code GET /v1/datapacks}
     */
    static String invalid(List<String> declared, JsonNode reply, String cell, String logTail) {
        if (declared.isEmpty()) {
            return null;
        }
        JsonNode enabled = reply == null ? null : reply.get("enabled");
        List<String> missing = new ArrayList<>();
        List<String> listed = new ArrayList<>();
        if (enabled != null && enabled.isArray()) {
            enabled.forEach(id -> listed.add(id.asText()));
        }
        for (String id : declared) {
            if (!listed.contains(id)) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return null;
        }
        String why = enabled == null || !enabled.isArray() ? "the agent's reply has no enabled list"
                : "the server enabled " + listed;
        return "INVALID test declaration: declared data pack " + String.join(", ", missing) + " did not load on " + cell
                + " (" + why + ")\n--- log tail ---\n" + logTail;
    }
}
