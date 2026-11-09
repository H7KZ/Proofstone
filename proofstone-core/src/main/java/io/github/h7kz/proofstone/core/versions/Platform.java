package io.github.h7kz.proofstone.core.versions;

import java.util.List;

/**
 * One platform in the matrix: its host image and its version rows, in file order.
 *
 * @param id platform id, for example {@code paper}
 * @param host the host image every cell of this platform runs on
 * @param rows the version rows
 * @param builder how the server jar is built from source, or {@code null} when it is downloaded
 */
public record Platform(String id, Host host, List<Row> rows, Builder builder) {
}
