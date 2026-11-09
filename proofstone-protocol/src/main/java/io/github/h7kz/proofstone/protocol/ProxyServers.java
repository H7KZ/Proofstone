package io.github.h7kz.proofstone.protocol;

import java.util.List;

/** Reply of {@code GET /v1/proxy/servers} (proxy.json#/$defs/servers). Protocol 1.2. */
public record ProxyServers(List<ProxyServer> servers) {
}
