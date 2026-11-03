package dev.proofstone.core.versions;

/**
 * Numeric comparison of dotted Minecraft versions, so {@code 1.10.2} sorts after {@code 1.9.4} and
 * {@code 26.1} after {@code 1.21.11}. Missing trailing components count as zero.
 */
public final class VersionNumber {

    private VersionNumber() {
    }

    /**
     * @return negative, zero or positive as {@code a} sorts before, equal to or after {@code b}
     * @throws IllegalArgumentException when either side is not dotted digits
     */
    public static int compare(String a, String b) {
        return compare(parse(a), parse(b));
    }

    static int[] parse(String text) {
        String[] pieces = text.split("\\.", -1);
        int[] parts = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            if (!pieces[i].matches("\\d+")) {
                throw new IllegalArgumentException("not a dotted version: \"" + text + "\"");
            }
            parts[i] = Integer.parseInt(pieces[i]);
        }
        return parts;
    }

    static int compare(int[] a, int[] b) {
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int c = Integer.compare(component(a, i), component(b, i));
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    static boolean startsWith(int[] version, int[] prefix) {
        if (version.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (version[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int component(int[] parts, int index) {
        return index < parts.length ? parts[index] : 0;
    }
}
