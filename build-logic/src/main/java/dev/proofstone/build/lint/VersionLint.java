package dev.proofstone.build.lint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fails on floating versions in build files and {@code versions.yml} (AGENTS.md §3, ADR-0006).
 * Allowlist: none. Comment lines are skipped, so a comment may say "latest".
 */
public final class VersionLint {

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("\\blatest\\b"), "'latest' is not a pin"),
            new Rule(Pattern.compile("SNAPSHOT"), "SNAPSHOT is not a pin"),
            new Rule(Pattern.compile("[\"'][^\"'\\s]*\\+[\"']"), "'+' is a dynamic version"),
            // A range sits right after a quote (version = "[1.0,2.0)") or a colon (group:name:[1.0,2.0)).
            new Rule(Pattern.compile("[\"':][\\[(][^\"'\\]\\)\\s]*,[^\"'\\]\\)\\s]*[\\])]"), "version range"));

    private VersionLint() {
    }

    /** Scans every lint target under {@code root}. Each message names the file and line. */
    public static List<String> scan(Path root) throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                if (isLintTarget(root.relativize(file))) {
                    violations.addAll(violations(file));
                }
            }
        }
        return violations;
    }

    /** Scans one file. Each message names the line. */
    public static List<String> violations(Path file) throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("//") || line.startsWith("#")) {
                continue;
            }
            for (Rule rule : RULES) {
                if (rule.pattern().matcher(line).find()) {
                    violations.add(file + ":" + (i + 1) + ": " + rule.message());
                }
            }
        }
        return violations;
    }

    private static boolean isLintTarget(Path relative) {
        for (Path segment : relative) {
            String name = segment.toString();
            if (name.equals("build") || name.equals(".gradle") || name.equals(".git") || name.equals("node_modules")) {
                return false;
            }
        }
        String name = relative.getFileName().toString();
        String path = relative.toString().replace('\\', '/');
        return name.endsWith(".gradle.kts")
                || name.endsWith(".gradle")
                || path.equals("gradle/libs.versions.toml")
                || name.equals("gradle.properties")
                || name.equals("versions.yml");
    }

    private record Rule(Pattern pattern, String message) {
    }
}
