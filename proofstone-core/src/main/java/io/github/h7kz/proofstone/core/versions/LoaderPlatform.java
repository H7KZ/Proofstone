package io.github.h7kz.proofstone.core.versions;

import java.util.List;

/**
 * One loader of {@code versions.yml} schema 4 (ADR-0017 section 3): Fabric or NeoForge, its host image and its rows. A
 * loader is never a {@link Platform}: nothing that reads {@code platforms} sees it.
 *
 * @param id loader id, {@code fabric} or {@code neoforge}
 * @param host the host image; its digests are by Java tag, as ADR-0013 says
 * @param rows the loader rows, in file order
 */
public record LoaderPlatform(String id, Host host, List<LoaderRow> rows) {
}
