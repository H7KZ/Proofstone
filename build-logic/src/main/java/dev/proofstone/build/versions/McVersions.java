package dev.proofstone.build.versions;

/** Minecraft version strings: numeric order and the stable check. Both "1.21.11" and "26.3" sort correctly. */
public final class McVersions {

    private McVersions() {
    }

    /** Compares dot-separated numeric segments; a missing segment counts as 0. Non-numeric input fails loudly. */
    public static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int c = Integer.compare(segment(x, i), segment(y, i));
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    /** Release versions only. Pre-releases and release candidates contain a hyphen ("26.3-rc-3", "1.13-pre7"). */
    public static boolean isStable(String mc) {
        return !mc.contains("-");
    }

    private static int segment(String[] parts, int i) {
        return i < parts.length ? Integer.parseInt(parts[i]) : 0;
    }
}
