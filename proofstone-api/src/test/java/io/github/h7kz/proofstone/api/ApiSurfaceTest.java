package io.github.h7kz.proofstone.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The binary-compatibility baseline of the public API (T-M2-07). It dumps every public and protected member of every
 * class in {@code io.github.h7kz.proofstone.api} and compares the dump with the committed {@code api-baseline.txt}. A change
 * to the surface fails the test until the baseline is regenerated on purpose
 * ({@code -Dproofstone.updateApiBaseline=true}) and the diff is reviewed. It is a reflection dump instead of japicmp
 * because that needs a published earlier jar to compare with, and v0 has none; T-M2-99 freezes this file.
 */
class ApiSurfaceTest {

    private static final Path BASELINE = Path.of(System.getProperty("proofstone.apiBaseline", "api-baseline.txt"));

    @Test
    void publicSurfaceMatchesTheCommittedBaseline() throws IOException {
        String actual = String.join("\n", dump()) + "\n";
        if (Boolean.getBoolean("proofstone.updateApiBaseline")) {
            Files.writeString(BASELINE, actual);
        }
        assertThat(BASELINE).as("API baseline file").exists();
        assertThat(actual).as("the public API changed: review the diff, then regenerate %s with "
                + "-Dproofstone.updateApiBaseline=true", BASELINE).isEqualTo(Files.readString(BASELINE));
    }

    private static List<String> dump() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Class<?> type : classes()) {
            lines.add(Modifier.toString(type.getModifiers() & ~Modifier.STATIC) + " " + type.getName()
                    + (type.getSuperclass() == null || type.getSuperclass() == Object.class ? ""
                            : " extends " + type.getGenericSuperclass().getTypeName())
                    + (type.getInterfaces().length == 0 ? "" : " implements "
                            + Arrays.stream(type.getGenericInterfaces()).map(java.lang.reflect.Type::getTypeName)
                                    .sorted().collect(Collectors.joining(", "))));
            List<String> members = new ArrayList<>();
            for (Field field : type.getDeclaredFields()) {
                if (visible(field.getModifiers(), field.isSynthetic())) {
                    members.add("  field " + Modifier.toString(field.getModifiers()) + " "
                            + field.getGenericType().getTypeName() + " " + field.getName());
                }
            }
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (visible(constructor.getModifiers(), constructor.isSynthetic())) {
                    members.add("  constructor " + constructor.toGenericString());
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                if (visible(method.getModifiers(), method.isSynthetic())) {
                    members.add("  method " + method.toGenericString());
                }
            }
            Collections.sort(members);
            lines.addAll(members);
        }
        return lines;
    }

    private static boolean visible(int modifiers, boolean synthetic) {
        return !synthetic && (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers));
    }

    private static List<Class<?>> classes() throws IOException {
        Path root;
        try {
            root = Path.of(Item.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the API classes", e);
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".class"))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'))
                    .filter(name -> !name.equals("module-info"))
                    .sorted()
                    .map(ApiSurfaceTest::load)
                    .filter(type -> Modifier.isPublic(type.getModifiers()) || Modifier.isProtected(type.getModifiers()))
                    .collect(Collectors.toList());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("cannot load " + name, e);
        }
    }
}
