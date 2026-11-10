package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.cell.Cell;
import io.github.h7kz.proofstone.core.cell.TopologyCell;
import io.github.h7kz.proofstone.core.versions.Status;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The proxy half of a planned topology cell becomes the host's options with no pin of its own (T-M7-08). */
class ProxyHostOptionsTest {

    private static final Path AGENT = Path.of("agent-all.jar");
    private static final String SHA = "ef".repeat(32);
    private static final String DIGEST = "sha256:" + "12".repeat(32);

    private static TopologyCell cell(String proxy, String version, int build, String java, String forwarding) {
        return new TopologyCell(proxy + "@" + version + "+paper@26.3",
                new TopologyCell.Proxy(proxy, version, build, SHA, java, forwarding, "itzg/mc-proxy", DIGEST,
                        Status.EXPERIMENTAL),
                new Cell("paper", "26.3", 244, "ab".repeat(32), "java25", Status.REQUIRED, "itzg/minecraft-server",
                        "sha256:" + "cd".repeat(32), List.of()),
                List.of("lobby", "survival"), Status.EXPERIMENTAL, Status.EXPERIMENTAL, List.of());
    }

    @Test
    void velocityTakesEveryPinFromTheCell() {
        ProxyHostOptions options = ProxyHostOptions.of(cell("velocity", "4.3.1", 45, "java25", "modern"), AGENT);

        assertThat(options.kind()).isEqualTo(ProxyKind.VELOCITY);
        assertThat(options.image()).isEqualTo("itzg/mc-proxy@" + DIGEST);
        assertThat(options.version()).isEqualTo("4.3.1");
        assertThat(options.build()).isEqualTo("45");
        assertThat(options.jarSha256()).isEqualTo(SHA);
        assertThat(options.javaMajor()).isEqualTo(25);
        assertThat(options.agentJar()).isEqualTo(AGENT);
    }

    @Test
    void bungeeCordsVersionIsItsBuildNumber() {
        ProxyHostOptions options = ProxyHostOptions.of(cell("bungeecord", "2111", 2111, "java21", "legacy"), AGENT);

        assertThat(options.kind()).isEqualTo(ProxyKind.BUNGEECORD);
        assertThat(options.version()).isEqualTo("2111");
        assertThat(options.build()).isEqualTo("2111");
        assertThat(options.javaMajor()).isEqualTo(21);
    }

    @Test
    void aForwardingModeOtherThanTheProxysOneIsRefused() {
        assertThatThrownBy(() -> ProxyHostOptions.of(cell("velocity", "4.3.1", 45, "java25", "legacy"), AGENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("velocity@4.3.1+paper@26.3")
                .hasMessageContaining("modern");
    }

    @Test
    void anUnknownProxyAndAMalformedJavaTagAreRefused() {
        assertThatThrownBy(() -> ProxyHostOptions.of(cell("waterfall", "615", 615, "java21", "legacy"), AGENT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("waterfall");
        assertThatThrownBy(() -> ProxyHostOptions.of(cell("velocity", "4.3.1", 45, "jdk25", "modern"), AGENT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("jdk25");
    }
}
