package dev.proofstone.core.versions;

import java.util.List;

/**
 * One platform in the matrix: its host image and its version rows, in file order.
 *
 * @param id platform id, for example {@code paper}
 * @param host the host image every cell of this platform runs on
 * @param rows the version rows
 */
public record Platform(String id, Host host, List<Row> rows) {
}
