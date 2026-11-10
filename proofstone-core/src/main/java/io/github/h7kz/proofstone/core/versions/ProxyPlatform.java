package io.github.h7kz.proofstone.core.versions;

import java.util.List;

/**
 * One proxy platform of {@code versions.yml} schema 3 (ADR-0016 section 2): its host image, its one forwarding mode and
 * its rows. A proxy is never a {@link Platform}: nothing that reads {@code platforms} sees it.
 *
 * @param id proxy id, for example {@code velocity}
 * @param host the host image; its digests are by Java tag, as ADR-0013 says
 * @param forwarding {@code modern} or {@code legacy}: one mode per proxy platform in M7
 * @param rows the proxy rows, in file order
 */
public record ProxyPlatform(String id, Host host, String forwarding, List<ProxyRow> rows) {
}
