package io.github.h7kz.proofstone.protocol;

/** One backend as a proxy knows it (proxy.json): reply of {@code GET /v1/proxy/servers/{name}}. Protocol 1.2. */
public record ProxyServer(String name, String address, boolean online, int players) {
}
