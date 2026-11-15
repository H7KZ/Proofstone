package io.github.h7kz.proofstone.core.host;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * A vanilla data pack under test (ADR-0017 section 4): a {@code .zip} or a directory holding {@code pack.mcmeta}. It
 * always reaches the server as a zip, copied into the container before start (ADR-0005, no bind mount) and installed by the
 * image's {@code DATAPACKS} setting into the world's {@code datapacks} folder, so the server loads it at boot and nothing
 * reloads it while a test runs. A directory is zipped here, deterministically, so a pack is the same bytes on every run;
 * the server then names it {@code file/<directory>.zip}.
 */
final class DataPack {

    /** Where the packs are copied in; the image copies them from here into {@code /data/<level>/datapacks}. */
    static final String DIR = "/datapacks-in/";
    private static final String MCMETA = "pack.mcmeta";

    private final String name;
    private final byte[] zip;

    private DataPack(String name, byte[] zip) {
        this.name = name;
        this.zip = zip;
    }

    /**
     * Resolves each glob, relative to {@code base}, to exactly one data pack.
     *
     * @throws PaperHostException naming the glob (or the pack) when it matches none, or more than one, or something that is
     *     not a pack, or when two packs would have the same name
     */
    static List<DataPack> resolve(Path base, List<String> globs) {
        List<DataPack> packs = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (String glob : globs) {
            DataPack pack = resolveOne(base, glob);
            if (!names.add(pack.name)) {
                throw new PaperHostException("two data packs would load as " + pack.id() + " (glob \"" + glob
                        + "\" and an earlier one): rename one, the server names a pack by its file name");
            }
            packs.add(pack);
        }
        return List.copyOf(packs);
    }

    private static DataPack resolveOne(Path base, String glob) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        List<Path> matches;
        try (Stream<Path> walk = Files.walk(base)) {
            matches = walk.filter(path -> !path.equals(base)).filter(path -> matcher.matches(base.relativize(path)))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot scan " + base + " for data pack glob \"" + glob + "\"", e);
        }
        if (matches.isEmpty()) {
            throw new PaperHostException("data pack glob \"" + glob + "\" matched no file or directory under " + base);
        }
        if (matches.size() > 1) {
            throw new PaperHostException("data pack glob \"" + glob + "\" must match exactly one .zip or one pack "
                    + "directory, it matched " + matches.size() + ": " + matches.stream()
                            .map(path -> base.relativize(path).toString()).collect(Collectors.joining(", ")));
        }
        Path match = matches.get(0);
        if (Files.isDirectory(match)) {
            if (!Files.isRegularFile(match.resolve(MCMETA))) {
                throw new PaperHostException("data pack directory " + match + " (glob \"" + glob + "\") has no "
                        + MCMETA + ": a pack directory holds it at its top");
            }
            return new DataPack(match.getFileName() + ".zip", zipDirectory(match));
        }
        String file = match.getFileName().toString();
        if (!file.endsWith(".zip")) {
            throw new PaperHostException("data pack " + match + " (glob \"" + glob + "\") is not a .zip and not a directory: "
                    + "a data pack is a .zip or a directory holding " + MCMETA);
        }
        try (ZipFile zip = new ZipFile(match.toFile())) {
            if (zip.getEntry(MCMETA) == null) {
                throw new PaperHostException("data pack " + match + " (glob \"" + glob + "\") has no " + MCMETA
                        + " at the top of the zip");
            }
            return new DataPack(file, Files.readAllBytes(match));
        } catch (IOException e) {
            throw new PaperHostException("data pack " + match + " (glob \"" + glob + "\") cannot be read as a zip: "
                    + e.getMessage(), e);
        }
    }

    /** Every file under {@code dir}, sorted, with a fixed time, so the same tree is the same bytes on every run. */
    private static byte[] zipDirectory(Path dir) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes); Stream<Path> walk = Files.walk(dir)) {
            List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
            for (Path file : files) {
                ZipEntry entry = new ZipEntry(dir.relativize(file).toString().replace('\\', '/'));
                entry.setTime(0);
                out.putNextEntry(entry);
                out.write(Files.readAllBytes(file));
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot zip the data pack directory " + dir, e);
        }
        return bytes.toByteArray();
    }

    /** The comma-separated container paths for the image's {@code DATAPACKS} setting, or {@code null} when there is none. */
    static String env(List<DataPack> packs) {
        return packs.isEmpty() ? null : packs.stream().map(DataPack::containerPath).collect(Collectors.joining(","));
    }

    /** The pack's file name in the world's {@code datapacks} folder. */
    String name() {
        return name;
    }

    /** The id the server lists it under ({@code GET /v1/datapacks}). */
    String id() {
        return "file/" + name;
    }

    String containerPath() {
        return DIR + name;
    }

    /** The namespaces the pack defines, the folder names under {@code data/}: how a log line is attributed to this pack. */
    List<String> namespaces() {
        Set<String> found = new java.util.TreeSet<>();
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                String[] parts = entry.getName().split("/");
                if (parts.length > 2 && parts[0].equals("data")) {
                    found.add(parts[1]);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the namespaces of data pack " + name, e);
        }
        return List.copyOf(found);
    }

    byte[] zip() {
        return zip;
    }

    /** The file as copied in, owned by the server user like every other input (T-M8-02). */
    ServerFile file() {
        return ServerFile.ofBytes(zip);
    }
}
