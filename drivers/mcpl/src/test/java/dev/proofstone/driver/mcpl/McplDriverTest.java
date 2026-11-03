package dev.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.proofstone.core.driver.ClientDriverException;
import dev.proofstone.core.driver.ConnectSpec;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class McplDriverTest {

    private final McplDriver driver = new McplDriver();

    @Test
    void flattensTextTranslationsAndChildrenToPlainText() {
        Component component = Component.text("Hello ")
                .append(Component.translatable("multiplayer.player.joined", Component.text("Bot")))
                .append(Component.text("!"));

        assertThat(McplSession.flatten(component)).isEqualTo("Hello multiplayer.player.joined Bot!");
    }

    @Test
    void aServerThatNeverAnswersFailsAtTheConnectTimeout() throws IOException {
        try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            ConnectSpec spec = new ConnectSpec("127.0.0.1", silent.getLocalPort(), "Bot", Duration.ofSeconds(2));

            assertThatThrownBy(() -> driver.connect(spec))
                    .isInstanceOf(ClientDriverException.class)
                    .hasMessageContaining("timed out joining");
        }
    }

    @Test
    void aRefusedConnectionFailsWithTheCause() throws IOException {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = probe.getLocalPort();
        }
        ConnectSpec spec = new ConnectSpec("127.0.0.1", closedPort, "Bot", Duration.ofSeconds(10));

        assertThatThrownBy(() -> driver.connect(spec))
                .isInstanceOf(ClientDriverException.class)
                .hasMessageContaining("could not join");
    }
}
