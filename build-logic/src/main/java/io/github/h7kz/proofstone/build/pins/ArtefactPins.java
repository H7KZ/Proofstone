package io.github.h7kz.proofstone.build.pins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a resolved dependency graph against a checksum list (ADR-0018 section 2, as the NeoForge jars are checked).
 * A pin line is {@code <sha256>  <group>:<name>:<version>}; {@code #} starts a comment. The check fails when the graph
 * holds a snapshot or a dynamic version, when a resolved artefact has no pin or a different sha256, and when a pin no
 * artefact used is left over, so the list is the authority and the Gradle cache is not.
 */
public final class ArtefactPins {

    /** One resolved artefact: its {@code group:name:version} and the file Gradle resolved. */
    public record Resolved(String module, Path file) {
    }

    private ArtefactPins() {
    }

    /** Every violation, one message each; empty when the graph is exactly what the list pins. */
    public static List<String> check(List<Resolved> resolved, Path pinFile) throws IOException {
        List<String> violations = new ArrayList<>();
        Map<String, String> pins = read(pinFile, violations);
        Set<String> used = new HashSet<>();
        for (Resolved artefact : resolved) {
            String version = artefact.module().substring(artefact.module().lastIndexOf(':') + 1);
            if (isFloating(version)) {
                violations.add(artefact.module() + ": a snapshot or dynamic version is not a pin");
            }
            String expected = pins.get(artefact.module());
            String actual = sha256(artefact.file());
            if (expected == null) {
                violations.add(artefact.module() + ": no pin in " + pinFile.getFileName() + " (sha256 is " + actual + ")");
            } else if (!expected.equals(actual)) {
                violations.add(artefact.module() + ": sha256 " + actual + ", pinned " + expected);
            }
            used.add(artefact.module());
        }
        for (String module : pins.keySet()) {
            if (!used.contains(module)) {
                violations.add(module + ": pinned in " + pinFile.getFileName() + " but not in the resolved graph");
            }
        }
        return violations;
    }

    /** A version that names a moving target: a snapshot, a range, {@code +} or {@code latest}. */
    public static boolean isFloating(String version) {
        return version.contains("SNAPSHOT") || version.contains("+") || version.contains(",")
                || version.contains("[") || version.contains("(") || version.startsWith("latest");
    }

    private static Map<String, String> read(Path pinFile, List<String> violations) throws IOException {
        Map<String, String> pins = new LinkedHashMap<>();
        for (String line : Files.readAllLines(pinFile)) {
            String text = line.trim();
            if (text.isEmpty() || text.startsWith("#")) {
                continue;
            }
            String[] parts = text.split("\\s+");
            if (parts.length != 2 || !parts[0].matches("[0-9a-f]{64}")) {
                violations.add(pinFile.getFileName() + ": not a line of sha256 then group:name:version: " + text);
            } else if (pins.put(parts[1], parts[0]) != null) {
                violations.add(pinFile.getFileName() + ": " + parts[1] + " is pinned twice");
            }
        }
        return pins;
    }

    static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
