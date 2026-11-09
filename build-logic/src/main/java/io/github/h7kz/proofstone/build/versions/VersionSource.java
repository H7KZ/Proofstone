package io.github.h7kz.proofstone.build.versions;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Where version-watch reads from. The real one is HTTP ({@link HttpVersionSource}); unit tests use recorded responses,
 * so they never touch the network (T-M3-01).
 */
public interface VersionSource {

    /** @return the body of a GET; any status other than 200 is an {@link IOException} */
    String getText(URI uri) throws IOException;

    /** @return a new temporary file holding the body of a GET; the caller deletes it */
    Path download(URI uri) throws IOException;
}
