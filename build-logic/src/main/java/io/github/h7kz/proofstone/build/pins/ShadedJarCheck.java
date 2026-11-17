package io.github.h7kz.proofstone.build.pins;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Checks a shaded driver jar: every class is Proofstone's own, or relocated under it, so no library class (Cloudburst
 * included) can clash with the host's JVM, and no entry names a snapshot (ADR-0018 section 2).
 */
public final class ShadedJarCheck {

    private static final String OWN = "io/github/h7kz/proofstone/";

    private ShadedJarCheck() {
    }

    public static List<String> check(Path jar) throws IOException {
        List<String> violations = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            boolean ours = false;
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
                String entry = e.nextElement().getName();
                // A multi-release class sits under META-INF/versions/<n>/ and is judged by the path after it.
                String name = entry.replaceFirst("^META-INF/versions/[0-9]+/", "");
                if (name.endsWith(".class") && !name.equals("module-info.class")) {
                    ours |= name.startsWith(OWN);
                    if (!name.startsWith(OWN)) {
                        violations.add("unrelocated class " + entry);
                    }
                }
                if (entry.contains("SNAPSHOT")) {
                    violations.add("entry names a snapshot: " + entry);
                }
            }
            if (!ours) {
                violations.add(jar.getFileName() + " holds no Proofstone class");
            }
        }
        return violations;
    }
}
