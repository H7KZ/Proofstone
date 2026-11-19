package io.github.h7kz.proofstone.build.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * What {@code metaTest} asserts about one bad-test run (T-M4-13, ADR-0007): every bad test on its own, not "at least one
 * failure per task". A run's JUnit XML, its {@code proofstone-report.json} and its repro bundles are read and held against
 * one {@link Expected} per test. Nothing is exempt: a failure, a report row or a skip that no expectation names is a problem.
 *
 * <p>Lives in build-logic so a unit test can show it red (MetaCheckTest); the bad-tests build script only feeds it.
 */
public final class MetaCheck {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** What a repro bundle holds at least (BundleWriter). */
    private static final List<String> BUNDLE_FILES = List.of("cell.json", "rerun.txt", "server.log");

    private MetaCheck() {
    }

    /**
     * One bad test's expectation.
     *
     * @param test the report's test name without the package: {@code Class.method}, plus {@code " [driver]"} in a
     *        differential run
     * @param verdict the verdict the report must hold for it
     * @param reasonHas text the report's reason must contain (the fault's own reason)
     * @param reasonLacks text the reason must not contain (a different red that must not satisfy the run)
     * @param bundles bundle directories this test must have written in this run, relative to the bundle root and cell
     * @param testcases how many JUnit testcases this test makes (a flake run makes one per repeat)
     * @param xmlFailures one entry per JUnit failure this test must have: all the texts of an entry are in one failure,
     *        most specific entry first. One failure answers one entry, so a failure no entry names is a problem
     * @param xmlLacks text that no failure claimed by this test may hold
     * @param skipReason the report's skip reason, for a SKIPPED test; null otherwise
     */
    public record Expected(String test, String verdict, List<String> reasonHas, List<String> reasonLacks,
            List<String> bundles, int testcases, List<List<String>> xmlFailures, List<String> xmlLacks,
            String skipReason) {

        public Expected {
            reasonHas = reasonHas == null ? List.of() : List.copyOf(reasonHas);
            reasonLacks = reasonLacks == null ? List.of() : List.copyOf(reasonLacks);
            bundles = bundles == null ? List.of() : List.copyOf(bundles);
            xmlFailures = xmlFailures == null ? List.of() : xmlFailures.stream().map(List::copyOf).toList();
            xmlLacks = xmlLacks == null ? List.of() : List.copyOf(xmlLacks);
        }
    }

    /**
     * One JUnit failure: its message without the exception class, and its body (the stack, with suppressed exceptions).
     * An entry's first text must START the message (or a suppressed exception's message, where a verdict judged after the
     * test is attached), so a failure that only quotes the verdict somewhere in its stack does not satisfy it. A first
     * text starting with {@code *} may be anywhere; the other texts of an entry may be anywhere in the failure.
     */
    private record Failure(String message, String body) {

        String text() {
            return message + "\n" + body;
        }

        boolean matches(List<String> needles) {
            String first = needles.get(0);
            boolean head;
            if (first.startsWith("*")) {
                first = first.substring(1);
                head = text().contains(first);
            } else {
                head = message.startsWith(first)
                        || java.util.regex.Pattern.compile("Suppressed: [\\w.$]+: " + java.util.regex.Pattern.quote(first))
                                .matcher(body).find();
            }
            String all = text();
            return head && needles.stream().skip(1).allMatch(all::contains);
        }
    }

    /** Reads the expectations of a run from the JSON the build script writes (a string, so the configuration cache keeps it). */
    public static List<Expected> parse(String json) {
        try {
            return List.of(JSON.readValue(json, Expected[].class));
        } catch (IOException e) {
            throw new IllegalArgumentException("unreadable meta expectations: " + e.getMessage(), e);
        }
    }

    /** @return the problems of one task's run; empty means every bad test did what it was built to do */
    public static List<String> check(String task, Path xmlDir, Path reportDir, Path bundleRoot, List<Expected> expected) {
        List<String> problems = new ArrayList<>();
        Instant start = checkXml(task, xmlDir, expected, problems);
        checkReport(task, reportDir, bundleRoot, start, expected, problems);
        return problems;
    }

    /** The JUnit XML: counts, errors, skips, and every failure claimed by exactly one expectation. @return the run's start */
    private static Instant checkXml(String task, Path xmlDir, List<Expected> expected, List<String> problems) {
        Path[] files;
        try (var stream = xmlDir.toFile().isDirectory() ? Files.list(xmlDir) : java.util.stream.Stream.<Path>empty()) {
            files = stream.filter(f -> f.getFileName().toString().startsWith("TEST-")
                    && f.getFileName().toString().endsWith(".xml")).sorted().toArray(Path[]::new);
        } catch (IOException e) {
            throw new IllegalStateException("cannot list " + xmlDir, e);
        }
        if (files.length == 0) {
            problems.add(task + ": no JUnit XML in " + xmlDir + ", so the run did not happen");
            return null;
        }
        int testcases = 0;
        int skipped = 0;
        Instant start = null;
        List<Failure> failures = new ArrayList<>();
        for (Path file : files) {
            Element suite = parse(file);
            if (!"0".equals(suite.getAttribute("errors"))) {
                problems.add(task + ": errors=" + suite.getAttribute("errors") + " in " + file.getFileName());
            }
            Instant at = Instant.parse(suite.getAttribute("timestamp"));
            start = start == null || at.isBefore(start) ? at : start;
            NodeList cases = suite.getElementsByTagName("testcase");
            testcases += cases.getLength();
            for (int index = 0; index < cases.getLength(); index++) {
                Element testcase = (Element) cases.item(index);
                if (testcase.getElementsByTagName("skipped").getLength() > 0) {
                    skipped++;
                }
                NodeList red = testcase.getElementsByTagName("failure");
                for (int f = 0; f < red.getLength(); f++) {
                    Element failure = (Element) red.item(f);
                    failures.add(new Failure(failure.getAttribute("message").replaceFirst("^[\\w.$]+: ", ""),
                            failure.getTextContent()));
                }
            }
        }
        int wantedCases = expected.stream().mapToInt(e -> Math.max(1, e.testcases())).sum();
        if (testcases != wantedCases) {
            problems.add(task + ": " + testcases + " testcases ran, expected " + wantedCases);
        }
        long wantedSkips = expected.stream().filter(e -> e.verdict().equals("SKIPPED")).count();
        if (skipped != wantedSkips) {
            problems.add(task + ": skipped=" + skipped + ", expected " + wantedSkips + " (a skip is not a pass)");
        }
        Set<Integer> claimed = new HashSet<>();
        for (Expected one : expected) {
            for (List<String> needles : one.xmlFailures()) {
                int found = -1;
                for (int i = 0; i < failures.size() && found < 0; i++) {
                    if (!claimed.contains(i) && failures.get(i).matches(needles)) {
                        found = i;
                    }
                }
                if (found < 0) {
                    problems.add(task + ": " + one.test() + ": no JUnit failure holds " + needles + " together");
                    continue;
                }
                claimed.add(found);
                for (String lacks : one.xmlLacks()) {
                    if (failures.get(found).text().contains(lacks)) {
                        problems.add(task + ": " + one.test() + " failure holds \"" + lacks
                                + "\", a different red from the one this fault must give");
                    }
                }
            }
        }
        for (int i = 0; i < failures.size(); i++) {
            if (!claimed.contains(i)) {
                problems.add(task + ": a JUnit failure no expected test claims: "
                        + abbreviate(failures.get(i).text().replace('\n', ' ')));
            }
        }
        return start;
    }

    /** The report's row per test, then each test's bundles. */
    private static void checkReport(String task, Path reportDir, Path bundleRoot, Instant start, List<Expected> expected,
            List<String> problems) {
        Path file = reportDir.resolve("proofstone-report.json");
        if (!Files.isRegularFile(file)) {
            problems.add(task + ": no proofstone-report.json, so its verdicts are missing from the report");
            return;
        }
        JsonNode results;
        try {
            results = JSON.readTree(file.toFile()).path("results");
        } catch (IOException e) {
            problems.add(task + ": unreadable proofstone-report.json: " + e.getMessage());
            return;
        }
        List<JsonNode> rows = new ArrayList<>();
        results.forEach(rows::add);
        Set<JsonNode> used = new HashSet<>();
        for (Expected one : expected) {
            List<JsonNode> named = rows.stream().filter(row -> row.path("test").asText().endsWith("." + one.test())).toList();
            if (named.size() != 1) {
                problems.add(task + ": " + one.test() + ": " + (named.isEmpty() ? "no row" : named.size() + " rows")
                        + " in the proofstone report, expected one");
                continue;
            }
            JsonNode row = named.get(0);
            used.add(row);
            String verdict = row.path("verdict").asText();
            if (!verdict.equals(one.verdict())) {
                problems.add(task + ": " + one.test() + " has verdict " + verdict + ", expected " + one.verdict() + ": "
                        + row.path("reason").asText());
            }
            String reason = row.path("reason").asText("");
            JsonNode code = row.path("skipReason");
            if (one.skipReason() == null && !code.isMissingNode() && !code.isNull()) {
                problems.add(task + ": " + one.test() + " carries skipReason " + code.asText() + " but is expected "
                        + one.verdict() + ", not a skip");
            }
            if (one.skipReason() != null && !one.skipReason().equals(row.path("skipReason").asText())) {
                problems.add(task + ": " + one.test() + " skipReason " + row.path("skipReason").asText() + ", expected "
                        + one.skipReason());
            }
            for (String needle : one.reasonHas()) {
                if (!reason.contains(needle)) {
                    problems.add(task + ": " + one.test() + " reason lacks \"" + needle + "\": " + abbreviate(reason));
                }
            }
            for (String needle : one.reasonLacks()) {
                if (reason.contains(needle)) {
                    problems.add(task + ": " + one.test() + " reason holds \"" + needle
                            + "\", a different red from the one this fault must give: " + abbreviate(reason));
                }
            }
            for (String bundle : one.bundles()) {
                checkBundle(task, one.test(), bundleRoot.resolve(row.path("cell").asText()).resolve(bundle), start, problems);
            }
        }
        for (JsonNode row : rows) {
            if (!used.contains(row)) {
                problems.add(task + ": the proofstone report holds " + row.path("test").asText() + " ("
                        + row.path("verdict").asText() + "), which no expectation names: not expected");
            }
        }
    }

    private static void checkBundle(String task, String test, Path dir, Instant start, List<String> problems) {
        for (String name : BUNDLE_FILES) {
            Path file = dir.resolve(name);
            if (!Files.isRegularFile(file)) {
                problems.add(task + ": " + test + " has no bundle file " + name + " in " + dir);
                continue;
            }
            try {
                // A bundle replaces an earlier one of the same test, so one older than this run is not this run's.
                if (start != null && Files.getLastModifiedTime(file).toInstant().isBefore(start)) {
                    problems.add(task + ": " + test + " bundle " + file + " is from an earlier run (older than " + start + ")");
                }
            } catch (IOException e) {
                problems.add(task + ": " + test + " cannot read " + file + ": " + e.getMessage());
            }
        }
    }

    private static String abbreviate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300);
    }

    private static Element parse(Path file) {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile()).getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("unreadable JUnit XML " + file, e);
        }
    }
}
