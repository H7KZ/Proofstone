package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The files a topology copies in (ADR-0016 sections 5 and 8, the T-M7-01 recipes in docs/platforms). */
class TopologyConfigTest {

    private static final String SECRET = "5e".repeat(32);
    private static final List<String> BACKENDS = List.of("lobby", "survival");

    @Test
    void velocityIsModernWithTheSecretFileAndTheHarnessRateLimitsInAdvanced() {
        Map<String, String> files = TopologyConfig.proxyFiles(ProxyKind.VELOCITY, BACKENDS, SECRET);

        assertThat(files).containsOnlyKeys("/server/velocity.toml", "/server/forwarding.secret",
                "/server/plugins/bStats/config.txt");
        assertThat(files.get("/server/forwarding.secret")).isEqualTo(SECRET);
        String toml = files.get("/server/velocity.toml");
        assertThat(toml)
                .contains("player-info-forwarding-mode = \"modern\"")
                .contains("forwarding-secret-file = \"forwarding.secret\"")
                .contains("online-mode = false")
                .contains("force-key-authentication = false")
                .contains("bind = \"0.0.0.0:25577\"")
                .contains("lobby = \"server-lobby:25565\"")
                .contains("survival = \"server-survival:25565\"")
                .contains("try = [\"lobby\"]")
                .doesNotContain(SECRET);
        // Velocity reads both limits from [advanced] only; a top-level key is ignored (default-velocity.toml of 4.3.1).
        String advanced = toml.substring(toml.indexOf("[advanced]"));
        assertThat(advanced).contains("command-rate-limit = 0").contains("login-ratelimit = 0");
        assertThat(toml.substring(0, toml.indexOf("[advanced]"))).doesNotContain("rate");
        assertThat(files.get("/server/plugins/bStats/config.txt")).isEqualTo("enabled=false\n");
    }

    @Test
    void bungeeCordIsLegacyWithTheThrottleOffAndNoSecret() {
        Map<String, String> files = TopologyConfig.proxyFiles(ProxyKind.BUNGEECORD, BACKENDS, SECRET);

        assertThat(files).containsOnlyKeys("/server/config.yml");
        assertThat(files.get("/server/config.yml"))
                .contains("online_mode: false")
                .contains("ip_forward: true")
                .contains("connection_throttle: -1")
                .contains("  lobby:\n    motd: lobby\n    address: server-lobby:25565\n")
                .contains("  survival:\n    motd: survival\n    address: server-survival:25565\n")
                .contains("  priorities:\n  - lobby\n")
                .contains("host: 0.0.0.0:25577")
                .contains("forced_hosts: {}")
                .doesNotContain(SECRET);
    }

    @Test
    void aVelocityBackendGetsModernForwardingWithTheSecretInConfigNotData() {
        Map<String, String> files = TopologyConfig.backendFiles(ProxyKind.VELOCITY, SECRET);

        // Into /config, which the image syncs as the server user; a root-owned /data/config crashes Paper (velocity.md).
        assertThat(files).containsOnlyKeys("/config/paper-global.yml");
        assertThat(files.get("/config/paper-global.yml")).isEqualTo(
                "proxies:\n  velocity:\n    enabled: true\n    online-mode: false\n    secret: " + SECRET + "\n");
    }

    @Test
    void aBungeeCordBackendGetsLegacyForwardingAndNoSecret() {
        Map<String, String> files = TopologyConfig.backendFiles(ProxyKind.BUNGEECORD, SECRET);

        assertThat(files).containsOnlyKeys("/data/spigot.yml", "/config/paper-global.yml");
        assertThat(files.get("/data/spigot.yml")).isEqualTo("settings:\n  bungeecord: true\n");
        // Paper's proxies.bungee-cord.online-mode defaults to true; then every login fetches the profile's properties
        // from sessionserver.mojang.com (T-M7-10 finding, bungeecord.md Gotchas). Off, as the proxy's online_mode is.
        assertThat(files.get("/config/paper-global.yml")).isEqualTo(
                "proxies:\n  bungee-cord:\n    online-mode: false\n");
    }

    @Test
    void backendNamesFollowTheAdr() {
        TopologyConfig.requireBackendNames(List.of("lobby", "survival", "s2"));

        assertThatThrownBy(() -> TopologyConfig.requireBackendNames(List.of()))
                .hasMessageContaining("at least one backend");
        assertThatThrownBy(() -> TopologyConfig.requireBackendNames(List.of("lobby", "proxy")))
                .hasMessageContaining("proxy").hasMessageContaining("reserved");
        assertThatThrownBy(() -> TopologyConfig.requireBackendNames(List.of("lobby", "lobby")))
                .hasMessageContaining("duplicate").hasMessageContaining("lobby");
        for (String bad : List.of("Lobby", "1st", "a-b", "abcdefghijklmnopq", "")) {
            assertThatThrownBy(() -> TopologyConfig.requireBackendNames(List.of(bad)))
                    .as(bad).hasMessageContaining("[a-z][a-z0-9]{0,15}");
        }
    }

    @Test
    void theSecretIs32RandomBytesInHexAndNewEveryTime() {
        String one = TopologyConfig.newSecret();
        String two = TopologyConfig.newSecret();

        assertThat(one).matches("[0-9a-f]{64}");
        assertThat(two).matches("[0-9a-f]{64}").isNotEqualTo(one);
    }

    @Test
    void theAliasOfABackendIsServerDashName() {
        assertThat(TopologyConfig.alias("lobby")).isEqualTo("server-lobby");
        assertThat(TopologyHost.PROXY_ALIAS).isEqualTo("proxy");
    }
}
