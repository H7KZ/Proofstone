package dev.proofstone.build.versions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Fills in versions.yml for the version-watch workflow (T-M3-01; docs/versioning.md §2; ADR-0006, ADR-0008).
 *
 * <p>For every Paper and Folia row it asks the fill API for the newest build. A row is re-pinned when its build
 * changed, its sha256 is TODO, or it has no protocol. A re-pin downloads the jar, checks it against the API
 * checksum, and reads {@code protocol_version} from the jar's {@code version.json} (the Mojang manifest has no
 * protocol number). Stable versions newer than a platform's newest row become new {@code experimental} rows.
 * Nothing else changes: the comments, the java-rules and the statuses stay as written.
 */
public final class VersionsUpdater {

    static final String FILL = "https://fill.papermc.io/v3/projects/";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Pattern PLATFORM = Pattern.compile("^  ([a-z][a-z0-9-]*):\\s*$");
    private static final Pattern ROW = Pattern.compile("^(\\s*)- \\{ mc: \"([^\"]+)\",.*\\}\\s*$");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private VersionsUpdater() {
    }

    /** The updated text, the input it was made from, and one log line per row that changed. */
    public record Result(String text, String original, List<String> log) {

        /** The changed lines only: "-" for the old line, "+" for the new one. Empty when nothing changed. */
        public String diff() {
            return VersionsUpdater.diff(original, text);
        }
    }

    private record Pin(int build, String sha256, int protocol) {
    }

    /**
     * Updates {@code versionsFile} in place, unless {@code dryRun}. The new text is validated against the schema and
     * the semantic rules before it replaces the file, so an invalid result writes nothing.
     */
    public static Result run(Path versionsFile, Path schemaFile, boolean dryRun, VersionSource source)
            throws IOException {
        String original = Files.readString(versionsFile);
        Result result = update(original, source);
        if (dryRun || result.text().equals(original)) {
            return result;
        }
        Path candidate = Files.createTempFile(versionsFile.toAbsolutePath().getParent(), "versions-", ".yml");
        try {
            Files.writeString(candidate, result.text());
            List<String> errors = VersionsValidator.validate(candidate, schemaFile);
            if (!errors.isEmpty()) {
                throw new IOException("updated versions.yml fails validation, nothing written:\n"
                        + String.join("\n", errors));
            }
            Files.move(candidate, versionsFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(candidate);
        }
        return result;
    }

    /** Pure text-in, text-out update. Network access goes only through {@code source}. */
    public static Result update(String yaml, VersionSource source) throws IOException {
        JsonNode doc = YAML.readTree(yaml);
        List<String> lines = new ArrayList<>(Arrays.asList(yaml.split("\n", -1)));
        List<String> log = new ArrayList<>();
        JsonNode javaRules = doc.path("java-rules");
        Iterator<String> names = doc.path("platforms").fieldNames();
        while (names.hasNext()) {
            String platform = names.next();
            updatePlatform(platform, doc.path("platforms").path(platform), javaRules, lines, source, log);
        }
        return new Result(String.join("\n", lines), yaml, List.copyOf(log));
    }

    private static void updatePlatform(String name, JsonNode platform, JsonNode javaRules, List<String> lines,
            VersionSource source, List<String> log) throws IOException {
        JsonNode rows = platform.path("rows");
        List<Integer> rowIndexes = rowLines(lines, name);
        if (rows.isEmpty() || rowIndexes.size() != rows.size()) {
            throw new IOException("platforms." + name + ": " + rows.size() + " rows in versions.yml, but "
                    + rowIndexes.size() + " one-line rows found (each row must be one flow mapping on one line)");
        }

        Set<String> known = new HashSet<>();
        String newest = null;
        for (int i = 0; i < rows.size(); i++) {
            int index = rowIndexes.get(i);
            String mc = rows.get(i).path("mc").asText();
            Matcher line = ROW.matcher(lines.get(index));
            if (!line.matches() || !line.group(2).equals(mc)) {
                throw new IOException("platforms." + name + ": row " + i + " (mc " + mc + ") is not on the line "
                        + (index + 1) + " it was read from");
            }
            known.add(mc);
            if (newest == null || McVersions.compare(mc, newest) > 0) {
                newest = mc;
            }
            Pin pin = pinFor(name, mc, rows.get(i), source, log);
            if (pin != null) {
                lines.set(index, withPin(lines.get(index), pin));
            }
        }

        // Stable versions newer than the newest row arrive as experimental rows; they never start as required.
        String top = newest;
        List<String> fresh = stableVersions(name, source).stream()
                .filter(mc -> !known.contains(mc) && McVersions.compare(mc, top) > 0)
                .distinct()
                .sorted(McVersions::compare)
                .toList();
        int last = rowIndexes.get(rowIndexes.size() - 1);
        Matcher lastLine = ROW.matcher(lines.get(last));
        lastLine.matches();
        String indent = lastLine.group(1);
        int insertAt = last + 1;
        for (String mc : fresh) {
            JavaRules.tagFor(javaRules, mc).orElseThrow(() -> new IOException("java-rules has no tag for " + mc
                    + ", so version-watch cannot add its row; add a rule first"));
            Pin pin = pinFor(name, mc, null, source, log);
            lines.add(insertAt++, indent + "- { mc: \"" + mc + "\", build: " + pin.build() + ", sha256: \""
                    + pin.sha256() + "\", protocol: " + pin.protocol() + ", status: experimental }");
        }
    }

    /**
     * @return the pin for {@code mc}, or null when the row already holds the newest build with its checksum and
     *     protocol (then nothing is downloaded)
     */
    private static Pin pinFor(String platform, String mc, JsonNode row, VersionSource source, List<String> log)
            throws IOException {
        JsonNode latest = JSON.readTree(source.getText(URI.create(FILL + platform + "/versions/" + mc + "/builds/latest")));
        int build = latest.path("id").asInt(0);
        if (build <= 0) {
            throw new IOException(platform + " " + mc + ": builds/latest has no build id");
        }
        if (row != null && row.path("build").isInt() && row.path("build").asInt() == build
                && SHA256.matcher(row.path("sha256").asText()).matches() && row.has("protocol")) {
            return null;
        }

        JsonNode server = latest.path("downloads").path("server:default");
        String url = server.path("url").asText();
        String expected = server.path("checksums").path("sha256").asText();
        if (url.isEmpty() || !SHA256.matcher(expected).matches()) {
            throw new IOException(platform + " " + mc + ": builds/latest has no server:default download with a sha256");
        }
        Path jar = source.download(URI.create(url));
        try {
            String actual = sha256(jar);
            if (!actual.equals(expected)) {
                throw new IOException(platform + " " + mc + ": sha256 mismatch for " + url + " (fill says "
                        + expected + ", download is " + actual + ")");
            }
            Integer fromJar = protocolVersion(jar, platform + " " + mc);
            int protocol;
            if (fromJar != null) {
                protocol = fromJar;
            } else if (row != null && row.path("protocol").isInt()) {
                // Paperclip jars before 1.17 do not embed version.json. The row's own protocol stays the truth, and the
                // log says so; a new row without one fails instead of guessing (AGENTS.md: fail loudly on unknowns).
                protocol = row.path("protocol").asInt();
                log.add(platform + " " + mc + ": jar has no version.json, protocol " + protocol + " kept from versions.yml");
            } else {
                throw new IOException(platform + " " + mc + ": the server jar has no version.json, so no protocol_version,"
                        + " and the row has none to keep");
            }
            String change = row == null ? "new row" : "build " + row.path("build").asText() + " -> " + build;
            log.add(platform + " " + mc + ": " + change + ", sha256 verified, protocol " + protocol
                    + ", channel " + latest.path("channel").asText());
            return new Pin(build, actual, protocol);
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    private static List<String> stableVersions(String platform, VersionSource source) throws IOException {
        JsonNode versions = JSON.readTree(source.getText(URI.create(FILL + platform))).path("versions");
        if (!versions.isObject() || versions.isEmpty()) {
            throw new IOException(platform + ": the fill API lists no versions");
        }
        List<String> stable = new ArrayList<>();
        versions.forEach(group -> group.forEach(version -> {
            if (McVersions.isStable(version.asText())) {
                stable.add(version.asText());
            }
        }));
        return stable;
    }

    /** Protocol number from the server jar's version.json, or null when the jar has none (old Paperclip jars). */
    static Integer protocolVersion(Path jar, String what) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("version.json");
            if (entry == null) {
                return null;
            }
            JsonNode protocol = JSON.readTree(zip.getInputStream(entry)).path("protocol_version");
            if (!protocol.isInt()) {
                throw new IOException(what + ": version.json has no integer protocol_version");
            }
            return protocol.asInt();
        }
    }

    /** Indexes of the one-line rows that belong to {@code platform} under the top-level {@code platforms:} key. */
    private static List<Integer> rowLines(List<String> lines, String platform) {
        List<Integer> found = new ArrayList<>();
        boolean inPlatforms = false;
        String current = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.equals("platforms:")) {
                inPlatforms = true;
                current = null;
                continue;
            }
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && !line.startsWith("#")) {
                inPlatforms = false;
                continue;
            }
            if (!inPlatforms) {
                continue;
            }
            Matcher header = PLATFORM.matcher(line);
            if (header.matches()) {
                current = header.group(1);
            } else if (platform.equals(current) && ROW.matcher(line).matches()) {
                found.add(i);
            }
        }
        return found;
    }

    // Edits only the fields that changed, so the rest of the line (observed, notes) is kept as written.
    private static String withPin(String line, Pin pin) {
        String out = line.replaceFirst("\\bbuild: [^,}]+", "build: " + pin.build())
                .replaceFirst("\\bsha256: [^,}]+", "sha256: \"" + pin.sha256() + "\"");
        if (out.contains("protocol: ")) {
            return out.replaceFirst("\\bprotocol: [^,}]+", "protocol: " + pin.protocol());
        }
        return out.replaceFirst("\\bstatus: ", "protocol: " + pin.protocol() + ", status: ");
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JVM provides SHA-256", e);
        }
    }

    // Longest-common-subsequence line diff. versions.yml is about 60 lines, so the quadratic table is fine.
    static String diff(String before, String after) {
        String[] a = before.split("\n", -1);
        String[] b = after.split("\n", -1);
        int[][] lcs = new int[a.length + 1][b.length + 1];
        for (int i = a.length - 1; i >= 0; i--) {
            for (int j = b.length - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        int j = 0;
        while (i < a.length || j < b.length) {
            if (i < a.length && j < b.length && a[i].equals(b[j])) {
                i++;
                j++;
            } else if (j == b.length || (i < a.length && lcs[i + 1][j] >= lcs[i][j + 1])) {
                out.append('-').append(a[i++]).append('\n');
            } else {
                out.append('+').append(b[j++]).append('\n');
            }
        }
        return out.toString();
    }
}
