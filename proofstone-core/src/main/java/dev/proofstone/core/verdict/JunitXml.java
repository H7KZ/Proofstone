package dev.proofstone.core.verdict;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes {@code proofstone-junit.xml}, so CI shows every verdict (T-M4-01). A green result is a passing testcase,
 * a SKIPPED result is a {@code skipped} element, and every other verdict is a {@code failure} whose message starts
 * with the verdict name. Nothing other than PASS or SKIPPED is green here either.
 */
public final class JunitXml {

    public static final String FILE_NAME = "proofstone-junit.xml";

    private JunitXml() {
    }

    /** @return the written file */
    public static Path write(Path directory, List<CellResult> results) throws IOException {
        Path file = directory.resolve(FILE_NAME);
        Files.createDirectories(directory);
        Files.writeString(file, render(results), StandardCharsets.UTF_8);
        return file;
    }

    static String render(List<CellResult> results) {
        List<CellResult> ordered = ReportWriter.ordered(results);
        long failures = ordered.stream().filter(r -> !r.green()).count();
        long skipped = ordered.stream().filter(r -> r.verdict() == Verdict.SKIPPED).count();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuite name=\"proofstone\" tests=\"").append(ordered.size())
                .append("\" failures=\"").append(failures).append("\" errors=\"0\" skipped=\"")
                .append(skipped).append("\">\n");
        for (CellResult result : ordered) {
            xml.append("  <testcase name=\"").append(escape(result.test() + " [" + result.cell() + "]"))
                    .append("\" classname=\"proofstone\"");
            if (result.verdict() == Verdict.PASS) {
                xml.append("/>\n");
            } else if (result.verdict() == Verdict.SKIPPED) {
                xml.append(">\n    <skipped message=\"").append(escape(result.skipReason() + ": " + result.reason()))
                        .append("\"/>\n  </testcase>\n");
            } else if (!result.green()) {
                xml.append(">\n    <failure message=\"").append(escape(result.verdict() + ": " + result.reason()))
                        .append("\" type=\"").append(result.verdict()).append("\"/>\n  </testcase>\n");
            }
        }
        xml.append("</testsuite>\n");
        return xml.toString();
    }

    /** Escapes attribute text and replaces characters XML 1.0 forbids, so the file always parses. */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    boolean allowed = c >= 0x20 || c == '\t' || c == '\n' || c == '\r';
                    out.append(allowed ? c : '�');
                }
            }
        }
        return out.toString();
    }
}
