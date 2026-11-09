package io.github.h7kz.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.ConnectSpec;
import io.github.h7kz.proofstone.core.driver.JoinTimeoutException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
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

    /**
     * T-M6-14, real network path: the real MCProtocolLib client sends its handshake and login start to a listener that
     * accepts TCP and then never answers, the shape of the unexplained paper@26.3 reds. The names in the message come
     * from the packets the library really sent, so a renamed packet class fails here, not in a flake.
     */
    @Test
    void aListenerThatAcceptsTcpAndGoesSilentIsNamedAsLoginStartWithNoReply() throws IOException {
        try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            ConnectSpec spec = new ConnectSpec("127.0.0.1", silent.getLocalPort(), "Bot", Duration.ofSeconds(2));

            assertThatThrownBy(() -> driver.connect(spec))
                    .isInstanceOfSatisfying(JoinTimeoutException.class, e -> {
                        assertThat(e.stage()).isEqualTo("LOGIN_START_SENT_NO_REPLY");
                        assertThat(e.player()).isEqualTo("Bot");
                        assertThat(e.lastReceived()).isEqualTo("none");
                        assertThat(e.protocolOffered()).matches("\\d+");
                        assertThat(e.getMessage()).startsWith("timed out joining Bot -> 127.0.0.1:")
                                .contains("stalled at LOGIN_START_SENT_NO_REPLY")
                                .contains("protocol offered: " + e.protocolOffered())
                                .contains("-> ClientIntentionPacket protocol=" + e.protocolOffered())
                                .contains("-> ServerboundHelloPacket");
                    });
        }
    }

    /** A listener that sends only "disable compression" after the login start, then stops: the stall is one step later. */
    @Test
    void aListenerThatStopsAfterTheCompressionPacketIsNamedAsSuch() throws Exception {
        // length 6, packet id 0x03 (login compression), threshold -1 as a VarInt: no compression is turned on.
        byte[] compressionOff = {6, 3, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F};
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread listener = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    // The client's handshake and login start have to arrive first, then the one reply.
                    socket.getInputStream().read();
                    socket.getOutputStream().write(compressionOff);
                    socket.getOutputStream().flush();
                    // Holds the connection open and silent until the client gives up and closes it.
                    while (socket.getInputStream().read() != -1) {
                        // discard
                    }
                } catch (IOException ignored) {
                    // closed by the test
                }
            }, "fake-server");
            listener.setDaemon(true);
            listener.start();
            ConnectSpec spec = new ConnectSpec("127.0.0.1", server.getLocalPort(), "Bot", Duration.ofSeconds(3));

            assertThatThrownBy(() -> driver.connect(spec))
                    .isInstanceOfSatisfying(JoinTimeoutException.class, e -> {
                        assertThat(e.stage()).isEqualTo("COMPRESSION_SET_NO_LOGIN_SUCCESS");
                        assertThat(e.lastReceived()).isEqualTo("ClientboundLoginCompressionPacket");
                    });
            listener.join();
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
