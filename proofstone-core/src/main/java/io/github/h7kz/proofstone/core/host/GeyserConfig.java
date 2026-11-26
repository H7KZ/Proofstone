package io.github.h7kz.proofstone.core.host;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Geyser's {@code config.yml} for a cell (ADR-0018 section 5): the pinned build's own default file, taken from a first
 * start of that build, with five values replaced and nothing else touched. The template is a resource per build
 * ({@code geyser-<version>-b<build>-config.yml}); a build with no template fails loudly, so a pin bump needs its default
 * file read first.
 */
final class GeyserConfig {

    /** The container path the file is copied to; the image syncs {@code /plugins} into {@code /data/plugins}. */
    static final String PATH = "/plugins/Geyser-Spigot/config.yml";
    /** The NetherNet UDP port; it must differ from RakNet's 19132 (geyser.md, Gotchas). */
    static final String WEBRTC_PORT = "19133";

    /** Section, key and the value the cell needs; the line {@code <key>: <default>} of that section is replaced. */
    private static List<String[]> edits(GeyserTransport transport) {
        return List.of(
                new String[] {"bedrock", "transport", transport.geyserValue()},
                new String[] {"bedrock", "webrtc-port", WEBRTC_PORT},
                new String[] {"bedrock.signaling", "stun-discovery", "false"},
                new String[] {"java", "auth-type", "floodgate"},
                new String[] {"advanced.bedrock", "validate-bedrock-login", "false"});
    }

    private GeyserConfig() {
    }

    /** The rendered file for the pinned build. */
    static String render(String version, int build, GeyserTransport transport) {
        return render(template(version, build), version, build, transport);
    }

    /** The edit of {@code template}; each key is found once in its own section, or it is an error. */
    static String render(String template, String version, int build, GeyserTransport transport) {
        List<String> lines = new ArrayList<>(template.lines().toList());
        for (String[] edit : edits(transport)) {
            replace(lines, edit[0], edit[1], edit[2], version, build);
        }
        return String.join("\n", lines) + "\n";
    }

    /** The values the cell sets, for a test and the docs. */
    static Map<String, String> values() {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        edits(GeyserTransport.BOTH).forEach(edit -> values.put(edit[0] + "." + edit[1], edit[2]));
        return values;
    }

    private static void replace(List<String> lines, String section, String key, String value, String version, int build) {
        String[] path = section.split("\\.");
        List<String> stack = new ArrayList<>();
        int found = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String stripped = line.stripLeading();
            if (stripped.isEmpty() || stripped.startsWith("#")) {
                continue;
            }
            int indent = line.length() - stripped.length();
            int level = indent / 2;
            while (stack.size() > level) {
                stack.remove(stack.size() - 1);
            }
            int colon = stripped.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String name = stripped.substring(0, colon);
            String rest = stripped.substring(colon + 1).strip();
            if (rest.isEmpty()) {
                if (stack.size() == level) {
                    stack.add(name);
                }
                continue;
            }
            if (name.equals(key) && stack.equals(List.of(path))) {
                if (found >= 0) {
                    throw new PaperHostException("Geyser " + version + " build " + build + " config template has " + section
                            + "." + key + " twice");
                }
                found = i;
            }
        }
        if (found < 0) {
            throw new PaperHostException("Geyser " + version + " build " + build + " config template has no " + section
                    + "." + key + " line; read the build's default file again and update the template");
        }
        String line = lines.get(found);
        lines.set(found, line.substring(0, line.indexOf(':') + 1) + " " + value);
    }

    private static String template(String version, int build) {
        String name = "geyser-" + version + "-b" + build + "-config.yml";
        try (InputStream in = GeyserConfig.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new PaperHostException("no Geyser config template " + name + " for the pinned build: start that build "
                        + "once, copy its default plugins/Geyser-Spigot/config.yml here and check the five keys "
                        + values().keySet() + " (ADR-0018 section 5)");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the Geyser config template " + name, e);
        }
    }
}
