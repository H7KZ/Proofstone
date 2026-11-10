package io.github.h7kz.proofstone.core.host;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The files a topology copies into its containers: the proxy config and the backends' forwarding settings, exactly as
 * T-M7-01 ran them (docs/platforms/velocity.md, bungeecord.md) plus the harness rate limits of ADR-0016 section 5. All
 * are rendered in memory and copied in, never mounted (ADR-0005). The forwarding secret is in some of them, so no file
 * here is ever logged or written to the host (ADR-0016 section 8).
 */
public final class TopologyConfig {

    /** ADR-0016 section 1. */
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9]{0,15}");
    /** The port every proxy binds inside its container. */
    static final int PROXY_PORT = 25577;

    private TopologyConfig() {
    }

    /** A new forwarding secret: 32 random bytes in hex, like the agent token (ADR-0016 section 8). */
    public static String newSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    /** The network alias of a backend (ADR-0016 section 1). */
    public static String alias(String backend) {
        return "server-" + backend;
    }

    /** Fails on an empty list, a name outside {@code [a-z][a-z0-9]{0,15}}, {@code proxy} or a duplicate. */
    public static void requireBackendNames(List<String> names) {
        if (names.isEmpty()) {
            throw new IllegalArgumentException("a topology needs at least one backend");
        }
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("backend name \"" + name + "\" must match " + NAME.pattern());
            }
            if (name.equals(TopologyHost.PROXY_ALIAS)) {
                throw new IllegalArgumentException("backend name \"proxy\" is reserved for the proxy (ADR-0016)");
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("duplicate backend name \"" + name + "\"");
            }
        }
    }

    /** Container path to content for the proxy. The first backend is the default server. */
    public static Map<String, String> proxyFiles(ProxyKind kind, List<String> backends, String secret) {
        requireBackendNames(backends);
        Map<String, String> files = new LinkedHashMap<>();
        switch (kind) {
            case VELOCITY -> {
                files.put("/server/velocity.toml", velocityToml(backends));
                files.put("/server/forwarding.secret", secret);
                files.put("/server/plugins/bStats/config.txt", "enabled=false\n");
            }
            case BUNGEECORD -> files.put("/server/config.yml", bungeeConfig(backends));
        }
        return files;
    }

    /**
     * Container path to content for each backend. {@code /config} is synced into {@code /data/config} by the image as
     * the server user; a root-owned copy there crashes Paper at world load (docs/platforms/velocity.md, Gotchas).
     * {@code online-mode} is off on the server itself through {@code ONLINE_MODE=FALSE} (PaperHost), on every row.
     */
    public static Map<String, String> backendFiles(ProxyKind kind, String secret) {
        return switch (kind) {
            case VELOCITY -> Map.of("/config/paper-global.yml",
                    "proxies:\n  velocity:\n    enabled: true\n    online-mode: false\n    secret: " + secret + "\n");
            case BUNGEECORD -> Map.of("/data/spigot.yml", "settings:\n  bungeecord: true\n");
        };
    }

    private static String velocityToml(List<String> backends) {
        StringBuilder servers = new StringBuilder();
        for (String name : backends) {
            servers.append(name).append(" = \"").append(alias(name)).append(':').append(PaperHost.GAME_PORT)
                    .append("\"\n");
        }
        // Both rate limits are [advanced] keys; Velocity ignores them at the top level (default-velocity.toml, 4.3.1).
        return """
                config-version = "2.9"
                bind = "0.0.0.0:%d"
                motd = "proofstone topology"
                show-max-players = 500
                online-mode = false
                force-key-authentication = false
                prevent-client-proxy-connections = false
                player-info-forwarding-mode = "modern"
                forwarding-secret-file = "forwarding.secret"
                announce-forge = false
                kick-existing-players = false

                [servers]
                %stry = ["%s"]

                [forced-hosts]

                [advanced]
                login-ratelimit = 0
                command-rate-limit = 0
                log-player-connections = true
                """.formatted(PROXY_PORT, servers, backends.get(0));
    }

    private static String bungeeConfig(List<String> backends) {
        StringBuilder servers = new StringBuilder();
        for (String name : backends) {
            servers.append("  ").append(name).append(":\n    motd: ").append(name).append("\n    address: ")
                    .append(alias(name)).append(':').append(PaperHost.GAME_PORT).append("\n    restricted: false\n");
        }
        return """
                online_mode: false
                ip_forward: true
                enforce_secure_profile: false
                connection_throttle: -1
                connection_throttle_limit: 3
                server_connect_timeout: 5000
                timeout: 30000
                log_pings: false
                servers:
                %spermissions:
                  default:
                  - bungeecord.command.server
                  - bungeecord.command.list
                listeners:
                - host: 0.0.0.0:%d
                  query_port: %d
                  query_enabled: false
                  motd: proofstone topology
                  max_players: 20
                  tab_list: GLOBAL_PING
                  tab_size: 60
                  forced_hosts: {}
                  ping_passthrough: false
                  priorities:
                  - %s
                  bind_local_address: true
                  force_default_server: false
                  proxy_protocol: false
                """.formatted(servers, PROXY_PORT, PROXY_PORT, backends.get(0));
    }
}
