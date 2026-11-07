package dev.proofstone.build.docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Keeps docs and task cards consistent (ai/workflow.md card format, AGENTS.md §4). Errors fail the build.
 * Glossary "avoid saying" terms only warn.
 */
public final class DocsCheck {

    /** Result of one run. The build fails only on errors. */
    public record Report(List<String> errors, List<String> warnings) {
        public boolean passed() {
            return errors.isEmpty();
        }
    }

    private static final Pattern LINK = Pattern.compile("\\[[^\\]]*\\]\\(([^)\\s]+)\\)");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*)$");
    // A letter suffix marks a follow-up split of a card (T-M4-05b), so the card keeps its number.
    private static final Pattern CARD_HEADING = Pattern.compile("^### (T-M(\\d+)-\\d{2}[a-z]?) · .+$");
    private static final Pattern CARD_ID = Pattern.compile("T-M\\d+-\\d{2}[a-z]?");
    private static final Pattern MILESTONE_FILE = Pattern.compile("^M(\\d+)[-.].*\\.md$");
    private static final List<String> METADATA = List.of("tier", "depends", "modules", "adrs", "specs", "test-first");
    private static final List<String> TIERS = List.of("haiku", "sonnet", "opus");
    private static final List<String> SECTIONS = List.of("**Goal.**", "**Do.**", "**Don't.**", "**Acceptance.**", "**Verify.**");
    private static final Set<String> SKIPPED_DIRS = Set.of(".git", "build", ".gradle", "node_modules");

    private DocsCheck() {
    }

    public static Report run(Path root) throws IOException {
        List<Path> markdown = files(root, ".md");
        List<Path> java = files(root, ".java");
        List<String> errors = new ArrayList<>(checkLinks(markdown));
        errors.addAll(checkCards(root.resolve("docs/plan/tasks")));
        List<String> warnings = avoidedTerms(root, markdown, java);
        return new Report(errors, warnings);
    }

    // Markdown links: relative files and #anchors resolve. Fenced code is skipped.

    static List<String> checkLinks(List<Path> markdown) throws IOException {
        List<String> errors = new ArrayList<>();
        for (Path file : markdown) {
            boolean inFence = false;
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().startsWith("```")) {
                    inFence = !inFence;
                    continue;
                }
                if (inFence) {
                    continue;
                }
                Matcher link = LINK.matcher(line);
                while (link.find()) {
                    String target = link.group(1);
                    if (target.startsWith("http://") || target.startsWith("https://") || target.startsWith("mailto:")) {
                        continue;
                    }
                    String where = file + ":" + (i + 1);
                    int hash = target.indexOf('#');
                    String path = hash < 0 ? target : target.substring(0, hash);
                    String anchor = hash < 0 ? "" : target.substring(hash + 1);
                    Path targetFile = path.isEmpty() ? file : file.getParent().resolve(path).normalize();
                    if (!Files.exists(targetFile)) {
                        errors.add(where + ": broken link to " + target);
                    } else if (!anchor.isEmpty() && targetFile.toString().endsWith(".md")
                            && !anchors(targetFile).contains(anchor)) {
                        errors.add(where + ": broken anchor in " + target);
                    }
                }
            }
        }
        return errors;
    }

    static Set<String> anchors(Path markdown) throws IOException {
        Set<String> anchors = new HashSet<>();
        boolean inFence = false;
        for (String line : Files.readAllLines(markdown)) {
            if (line.trim().startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            Matcher heading = HEADING.matcher(line);
            if (!inFence && heading.matches()) {
                anchors.add(slug(heading.group(1)));
            }
        }
        return anchors;
    }

    /** GitHub-style heading slug: lower case, punctuation removed, spaces become hyphens. */
    static String slug(String heading) {
        return heading.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} _-]", "").replace(' ', '-');
    }

    // Task cards: format, milestone match and depends IDs.

    static List<String> checkCards(Path tasksDir) throws IOException {
        List<String> errors = new ArrayList<>();
        if (!Files.isDirectory(tasksDir)) {
            return errors;
        }
        List<Card> cards = new ArrayList<>();
        try (Stream<Path> files = Files.list(tasksDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
                cards.addAll(cardsIn(file));
            }
        }
        Set<String> knownIds = new HashSet<>();
        for (Card card : cards) {
            knownIds.add(card.id());
        }
        for (Card card : cards) {
            for (String problem : card.problems()) {
                errors.add(card.where() + ": " + problem);
            }
            for (String dependency : card.depends()) {
                if (!knownIds.contains(dependency)) {
                    errors.add(card.where() + ": depends on unknown card " + dependency);
                }
            }
        }
        return errors;
    }

    private static List<Card> cardsIn(Path file) throws IOException {
        List<Card> cards = new ArrayList<>();
        Matcher name = MILESTONE_FILE.matcher(file.getFileName().toString());
        String fileMilestone = name.matches() ? name.group(1) : null;
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            Matcher heading = CARD_HEADING.matcher(lines.get(i));
            if (!heading.matches()) {
                continue;
            }
            int end = i + 1;
            while (end < lines.size() && !lines.get(end).startsWith("### ") && !lines.get(end).startsWith("## ")) {
                end++;
            }
            cards.add(new Card(file + ":" + (i + 1), heading.group(1), lines.subList(i + 1, end),
                    fileMilestone, heading.group(2)));
        }
        return cards;
    }

    /** One task card, parsed from its heading to the next heading. */
    private record Card(String where, String id, List<String> body, String fileMilestone, String idMilestone) {

        List<String> problems() {
            List<String> problems = new ArrayList<>();
            if (fileMilestone == null) {
                problems.add("file name must look like M<n>-<name>.md");
            } else if (!fileMilestone.equals(idMilestone)) {
                problems.add("card " + id + " is in the M" + fileMilestone + " file but its ID is M" + idMilestone);
            }
            for (String key : METADATA) {
                if (metadata(key) == null) {
                    problems.add("missing metadata bullet '- " + key + ":'");
                }
            }
            String tier = metadata("tier");
            if (tier != null && !TIERS.contains(tier)) {
                problems.add("tier '" + tier + "' must be haiku, sonnet or opus");
            }
            for (String section : SECTIONS) {
                if (body.stream().noneMatch(line -> line.startsWith(section))) {
                    problems.add("missing section " + section);
                }
            }
            return problems;
        }

        List<String> depends() {
            String value = metadata("depends");
            List<String> ids = new ArrayList<>();
            if (value == null) {
                return ids;
            }
            for (String token : value.split(",")) {
                String trimmed = token.trim();
                if (CARD_ID.matcher(trimmed).matches()) {
                    ids.add(trimmed);
                }
            }
            return ids;
        }

        String metadata(String key) {
            String prefix = "- " + key + ":";
            for (String line : body) {
                if (line.startsWith(prefix)) {
                    return line.substring(prefix.length()).trim();
                }
            }
            return null;
        }
    }

    // Glossary "avoid saying" terms: warnings only.

    static List<String> avoidedTerms(Path root, List<Path> markdown, List<Path> java) throws IOException {
        Path glossary = root.resolve("docs/glossary.md");
        List<String> terms = Files.exists(glossary) ? avoidTerms(glossary) : List.of();
        List<String> warnings = new ArrayList<>();
        for (String term : terms) {
            Pattern word = Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE);
            for (Path file : java) {
                scanForTerm(file, term, word, warnings);
            }
            for (Path file : markdown) {
                if (!file.equals(glossary)) {
                    scanForTerm(file, term, word, warnings);
                }
            }
        }
        return warnings;
    }

    private static void scanForTerm(Path file, String term, Pattern word, List<String> warnings) throws IOException {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            if (word.matcher(lines.get(i)).find()) {
                warnings.add(file + ":" + (i + 1) + ": avoid " + term + " (see docs/glossary.md)");
            }
        }
    }

    /** Reads the "Avoid saying" column of the glossary table. */
    static List<String> avoidTerms(Path glossary) throws IOException {
        List<String> terms = new ArrayList<>();
        for (String line : Files.readAllLines(glossary)) {
            String[] cells = line.split("\\|", -1);
            if (cells.length < 5 || !cells[1].contains("**")) {
                continue;
            }
            String avoid = cells[3].trim();
            if (avoid.equals("—") || avoid.isEmpty()) {
                continue;
            }
            for (String term : avoid.split(",")) {
                String bare = term.replaceAll("\\(.*\\)", "").trim();
                if (!bare.isEmpty()) {
                    terms.add(bare);
                }
            }
        }
        return terms;
    }

    private static List<Path> files(Path root, String extension) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(extension))
                    .filter(p -> !inSkippedDir(root.relativize(p)))
                    .sorted()
                    .toList();
        }
    }

    private static boolean inSkippedDir(Path relative) {
        for (Path segment : relative) {
            if (SKIPPED_DIRS.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }
}
