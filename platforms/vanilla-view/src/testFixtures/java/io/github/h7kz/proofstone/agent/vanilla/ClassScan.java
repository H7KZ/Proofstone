package io.github.h7kz.proofstone.agent.vanilla;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * The compiled classes of the module (class directory or jar) that holds a given class, for the observe-only scan of the
 * agents' tests (AGENTS.md section 3, ADR-0017 section 5). Each agent scans its own classes and the shared vanilla-view
 * classes it shades; scanning both through one helper means a test cannot quietly cover only one of them.
 */
public final class ClassScan {

    private ClassScan() {
    }

    /**
     * Class entry name ({@code io/.../Foo.class}, slashes) to its bytes read as ISO-8859-1, for every class in the same
     * class directory or jar as {@code anchor}. Constant-pool names are UTF-8, so a byte scan finds every method, class and
     * field the code refers to.
     */
    public static Map<String, String> classesOf(Class<?> anchor) throws IOException, URISyntaxException {
        Path root = new File(anchor.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath();
        Map<String, String> classes = new TreeMap<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path file : (Iterable<Path>) walk.filter(path -> path.toString().endsWith(".class"))::iterator) {
                    classes.put(root.relativize(file).toString().replace(File.separatorChar, '/'), read(Files.readAllBytes(file)));
                }
            }
        } else {
            try (JarFile jar = new JarFile(root.toFile())) {
                for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        classes.put(entry.getName(), read(jar.getInputStream(entry).readAllBytes()));
                    }
                }
            }
        }
        return classes;
    }

    private static String read(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }
}
