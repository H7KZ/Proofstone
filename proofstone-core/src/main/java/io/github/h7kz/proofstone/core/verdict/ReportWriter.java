package io.github.h7kz.proofstone.core.verdict;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes {@code proofstone-report.json}, the machine-readable report (test-api spec section 7, T-M4-01). The output
 * is deterministic: results are sorted by cell, then test, and the line separator is always {@code \n}.
 *
 * <p>Schema {@value #SCHEMA}: {@code green} (true only when there is at least one result and all are green),
 * {@code summary} (a count per verdict, zeros included) and {@code results} (one object per test and cell).
 */
public final class ReportWriter {

    /** The schema id. Bump it when a field changes meaning; adding a field keeps the id. */
    public static final String SCHEMA = "proofstone-report/1";
    public static final String FILE_NAME = "proofstone-report.json";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DefaultPrettyPrinter PRETTY = new DefaultPrettyPrinter()
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n"));

    private ReportWriter() {
    }

    /**
     * @param directory the output directory, for example {@code build/proofstone}; created when missing
     * @return the written file
     */
    public static Path write(Path directory, List<CellResult> results) throws IOException {
        Path file = directory.resolve(FILE_NAME);
        Files.createDirectories(directory);
        Files.writeString(file, render(results), StandardCharsets.UTF_8);
        return file;
    }

    /**
     * @return the report text, ending with a newline
     * @throws IllegalArgumentException when two results share a test and cell
     */
    static String render(List<CellResult> results) throws JsonProcessingException {
        List<CellResult> ordered = ordered(results);
        ObjectNode root = JSON.createObjectNode();
        root.put("schema", SCHEMA);
        root.put("green", !ordered.isEmpty() && ordered.stream().allMatch(CellResult::green));
        ObjectNode summary = root.putObject("summary");
        for (Verdict verdict : Verdict.values()) {
            summary.put(verdict.name(), (int) ordered.stream().filter(r -> r.verdict() == verdict).count());
        }
        ArrayNode items = root.putArray("results");
        for (CellResult result : ordered) {
            ObjectNode item = items.addObject();
            item.put("cell", result.cell());
            item.put("test", result.test());
            item.put("verdict", result.verdict().name());
            item.put("green", result.green());
            item.put("runs", result.runs());
            if (result.reason() == null) {
                item.putNull("reason");
            } else {
                item.put("reason", result.reason());
            }
            if (result.skipReason() == null) {
                item.putNull("skipReason");
            } else {
                item.put("skipReason", result.skipReason().name());
            }
        }
        return JSON.writer(PRETTY).writeValueAsString(root) + "\n";
    }

    /** Sorted by cell, then test, so the report and the JUnit XML list the same order. Duplicates are an error. */
    static List<CellResult> ordered(List<CellResult> results) {
        List<CellResult> sorted = results.stream()
                .sorted(Comparator.comparing(CellResult::cell).thenComparing(CellResult::test))
                .toList();
        Set<String> seen = new HashSet<>();
        for (CellResult result : sorted) {
            if (!seen.add(result.cell() + "\u0000" + result.test())) {
                throw new IllegalArgumentException("two results for " + result.test() + " on " + result.cell());
            }
        }
        return sorted;
    }
}
