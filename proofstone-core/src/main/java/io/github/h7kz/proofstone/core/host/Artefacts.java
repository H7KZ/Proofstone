package io.github.h7kz.proofstone.core.host;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Resolves artefact globs to files. A glob that matches nothing is an error that names the glob. */
final class Artefacts {

    private Artefacts() {
    }

    /** @param globs glob patterns relative to {@code base}, for example {@code build/libs/*.jar} */
    static List<Path> resolve(Path base, List<String> globs) {
        List<Path> found = new ArrayList<>();
        for (String glob : globs) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            List<Path> matches;
            try (Stream<Path> walk = Files.walk(base)) {
                matches = walk.filter(Files::isRegularFile)
                        .filter(path -> matcher.matches(base.relativize(path)))
                        .sorted()
                        .toList();
            } catch (IOException e) {
                throw new UncheckedIOException("cannot scan " + base + " for artefact glob \"" + glob + "\"", e);
            }
            if (matches.isEmpty()) {
                throw new PaperHostException("artefact glob \"" + glob + "\" matched no file under " + base);
            }
            for (Path match : matches) {
                if (!found.contains(match)) {
                    found.add(match);
                }
            }
        }
        return found;
    }
}
