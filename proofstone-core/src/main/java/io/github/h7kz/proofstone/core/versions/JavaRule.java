package io.github.h7kz.proofstone.core.versions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One {@code java-rules} entry: a space-separated list of comparators, all of which must hold. A
 * trailing {@code .x} matches a version prefix, for example {@code 1.17.x}. Spec: versions-yml.md.
 */
final class JavaRule {

    private static final Pattern COMPARATOR = Pattern.compile("(>=|<=|>|<|=)?(.+)");

    private final List<Bound> bounds;
    private final String image;

    private JavaRule(List<Bound> bounds, String image) {
        this.bounds = bounds;
        this.image = image;
    }

    static JavaRule parse(String range, String image) {
        List<Bound> bounds = new ArrayList<>();
        for (String token : range.trim().split("\\s+")) {
            bounds.add(Bound.parse(token, range));
        }
        return new JavaRule(List.copyOf(bounds), image);
    }

    String image() {
        return image;
    }

    boolean matches(String mc) {
        int[] version = VersionNumber.parse(mc);
        for (Bound bound : bounds) {
            if (!bound.matches(version)) {
                return false;
            }
        }
        return true;
    }

    private record Bound(String op, int[] parts, boolean wildcard) {

        static Bound parse(String token, String range) {
            Matcher matcher = COMPARATOR.matcher(token);
            if (!matcher.matches()) {
                throw new VersionsException("bad java-rules range \"" + range + "\": " + token);
            }
            String op = matcher.group(1) == null ? "=" : matcher.group(1);
            String version = matcher.group(2);
            boolean wildcard = version.endsWith(".x");
            if (wildcard) {
                if (!"=".equals(op)) {
                    throw new VersionsException("bad java-rules range \"" + range + "\": .x needs a plain version");
                }
                version = version.substring(0, version.length() - 2);
            }
            return new Bound(op, VersionNumber.parse(version), wildcard);
        }

        boolean matches(int[] version) {
            if (wildcard) {
                return VersionNumber.startsWith(version, parts);
            }
            int c = VersionNumber.compare(version, parts);
            return switch (op) {
                case ">=" -> c >= 0;
                case "<=" -> c <= 0;
                case ">" -> c > 0;
                case "<" -> c < 0;
                default -> c == 0;
            };
        }
    }
}
