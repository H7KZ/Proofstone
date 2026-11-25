package io.github.h7kz.proofstone.driver.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.ConnectRequest;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc.JoinStage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.jose4j.jws.JsonWebSignature;

/**
 * One live Bedrock connection of the driver host: RakNet to the server, the join of {@link ClientPackets}, then the
 * session state the RPC reads. It never retries and never sleeps: the join waits on the state's condition with the
 * caller's timeout, and a stall is reported with the stage it stopped in (ADR-0018 section 7).
 */
final class HostSession implements Wire, RpcServer.Connection {

    /**
     * The read limits of a client: the library's client profile. A client initialiser starts with the default profile,
     * whose list limit (1536) is below the server's item registry (the spike's first decode error, "Tried to read 2088
     * bytes but maximum is 1536"); the client profile allows 10240.
     */
    static final EncodingSettings LIMITS = EncodingSettings.CLIENT;

    private static final BedrockCodec CODEC = Bedrock_v2225.CODEC;

    private final Identity identity;
    private final SessionState state = new SessionState();
    private volatile BedrockClientSession session;
    private volatile Channel channel;
    private final CountDownLatch sessionUp = new CountDownLatch(1);

    private HostSession(Identity identity) {
        this.identity = identity;
    }

    @Override
    public String name() {
        return identity.name();
    }

    @Override
    public String xuid() {
        return identity.xuid();
    }

    @Override
    public SessionState state() {
        return state;
    }

    /**
     * Connects and joins until the spawn status. The result is open on success; on a stall, a refusal or an error the
     * connection is closed and the exception names the stage or the server's own text.
     */
    static HostSession join(String id, ConnectRequest request, EventLoopGroup group) {
        boolean nethernet = BedrockRpc.TRANSPORT_NETHERNET.equals(BedrockRpc.requireTransport(request.transport()));
        Identity identity = new Identity(request.name(), request.xuid(), Clock.systemUTC());
        // Only the T-M10-05 negative control changes the protocol number (ProtocolOverride); read before any connection opens.
        int protocol = ProtocolOverride.fromSystemProperties(CODEC.getProtocolVersion());
        HostSession host = new HostSession(identity);
        ClientPackets packets = new ClientPackets(host.state, host, identity, request.host() + ":" + request.port(),
                CODEC.getProtocolVersion(), CODEC.getMinecraftVersion(),
                nethernet ? ClientPackets.NETHERNET_CHUNK_RADIUS : ClientPackets.CHUNK_RADIUS);
        Duration timeout = Duration.ofMillis(request.joinTimeoutMillis());
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            NetherNetTransport.SessionInitializer sessionSetup = new NetherNetTransport.SessionInitializer() {
                @Override
                public void init(BedrockClientSession created) {
                    host.session = created;
                    created.setCodec(CODEC);
                    created.getPeer().getCodecHelper().setEncodingSettings(LIMITS);
                    created.getPeer().getCodecHelper().setBlockDefinitions(new RuntimeIdBlocks());
                    created.setPacketHandler(packets);
                    host.sessionUp.countDown();
                }

                @Override
                public void postInit(Channel channel) {
                    // A packet that does not decode surfaces here; the driver never skips it (AGENTS.md section 3).
                    channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            host.state.fail(BedrockRpc.PROTOCOL_ERROR, "a packet could not be decoded: " + cause + rootCause(cause));
                        }
                    });
                }
            };
            InetSocketAddress target = new InetSocketAddress(request.host(), request.port());
            Bootstrap bootstrap = new Bootstrap().group(group);
            JoinStage connectStage;
            if (nethernet) {
                connectStage = JoinStage.NETHERNET_CONNECT;
                host.state.stage(JoinStage.SIGNALLING);
                try {
                    NetherNetTransport.loadNative();
                    host.state.packet("nethernet probe " + NetherNetTransport.probe(target, remainingMillis(deadline)));
                    NetherNetTransport.configure(bootstrap, request.xuid(), request.name(), sessionSetup);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    host.close();
                    throw new HostError(BedrockRpc.INTERNAL, "interrupted while joining", null, null);
                } catch (Exception | LinkageError e) {
                    // LinkageError: the native library did not load (wrong architecture or libc); it names the stage, never escapes unclosed
                    host.close();
                    throw new HostError(BedrockRpc.DISCONNECTED, "NetherNet signalling at " + request.host() + ":" + request.port()
                            + " failed: " + e + rootCause(e), JoinStage.SIGNALLING.name(), null);
                }
                host.state.stage(JoinStage.NETHERNET_CONNECT);
            } else {
                connectStage = JoinStage.RAKNET_CONNECT;
                bootstrap.channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                        .option(RakChannelOption.RAK_PROTOCOL_VERSION, CODEC.getRaknetProtocolVersion())
                        .option(RakChannelOption.RAK_GUID, ThreadLocalRandom.current().nextLong())
                        .handler(new BedrockClientInitializer() {
                            @Override
                            protected void initSession(BedrockClientSession created) {
                                sessionSetup.init(created);
                            }

                            @Override
                            protected void postInitChannel(Channel channel) throws Exception {
                                super.postInitChannel(channel);
                                sessionSetup.postInit(channel);
                            }
                        });
            }
            ChannelFuture connected = bootstrap.connect(target);
            host.channel = connected.channel();
            if (!connected.await(remainingMillis(deadline), TimeUnit.MILLISECONDS)) {
                throw host.timeout(BedrockRpc.JOIN_TIMEOUT);
            }
            if (!connected.isSuccess()) {
                host.close();
                throw new HostError(BedrockRpc.DISCONNECTED, (nethernet ? "NetherNet" : "RakNet") + " connect to " + request.host() + ":"
                        + request.port() + " failed: " + connected.cause() + rootCause(connected.cause()), connectStage.name(), null);
            }
            // On NetherNet the session is made when the data channel is active, after connect() returns (nethernet.md r1).
            if (!host.sessionUp.await(remainingMillis(deadline), TimeUnit.MILLISECONDS)) {
                throw host.timeout(BedrockRpc.JOIN_TIMEOUT);
            }
            host.state.stage(JoinStage.NETWORK_SETTINGS);
            RequestNetworkSettingsPacket settings = new RequestNetworkSettingsPacket();
            settings.setProtocolVersion(protocol);
            host.session.sendPacketImmediately(settings);
            SessionState.JoinResult result = host.state.awaitJoin(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
            if (result.failure() != null) {
                host.close();
                throw new HostError(result.failure().code(), result.failure().message() + " (at join stage "
                        + result.stage() + ")", result.stage().name(), result.lastPacket());
            }
            if (!result.spawned()) {
                throw host.timeout(BedrockRpc.JOIN_TIMEOUT);
            }
            return host;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            host.close();
            throw new HostError(BedrockRpc.INTERNAL, "interrupted while joining", null, null);
        }
    }

    /** Milliseconds left until the deadline, never negative; every wait of a join is bounded by it. */
    private static long remainingMillis(long deadlineNanos) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
    }

    private HostError timeout(String code) {
        HostError error = new HostError(code, "no progress past this stage", state.stage().name(), lastPacket());
        close();
        return error;
    }

    private String lastPacket() {
        List<String> lines = state.transcript();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            int space = line.indexOf(" ms ");
            if (space > 0) {
                return line.substring(space + 4);
            }
        }
        return "none";
    }

    @Override
    public void chat(String message) {
        send(TextPackets.chat(identity, message));
    }

    @Override
    public void command(String command) {
        send(TextPackets.command(identity, command));
    }

    @Override
    public void close() {
        BedrockClientSession open = session;
        if (open != null && open.isConnected()) {
            open.disconnect();
        }
        Channel c = channel;
        if (c != null) {
            c.close();
        }
    }

    @Override
    public void send(BedrockPacket packet) {
        session.sendPacket(packet);
    }

    @Override
    public void setCompression(PacketCompressionAlgorithm algorithm) {
        session.setCompression(algorithm);
    }

    @Override
    public void enableEncryption(SecretKey key) {
        session.enableEncryption(key);
    }

    /** The innermost cause, which the library's wrapper exceptions hide (the first real join showed only "Error whilst deserializing"). */
    static String rootCause(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root == error ? "" : " [root cause: " + root + "]";
    }

    @Override
    public void useItemDefinitions(List<ItemDefinition> definitions) {
        session.getPeer().getCodecHelper().setItemDefinitions(
                SimpleDefinitionRegistry.<ItemDefinition>builder().addAll(definitions).build());
    }

    @Override
    public SecretKey secret(PrivateKey key, String serverJwt) {
        try {
            JsonWebSignature jws = new JsonWebSignature();
            jws.setCompactSerialization(serverJwt);
            JsonNode payload = BedrockRpc.JSON.readTree(jws.getUnverifiedPayload());
            if (!payload.hasNonNull("salt") || jws.getHeader("x5u") == null) {
                throw new IllegalStateException("the server's handshake has no salt or key: " + payload);
            }
            // The library's own derivation, as the spike ran it. Loading it fetches Microsoft's discovery names; in the
            // driver host they resolve to the cell's stand-in (ADR-0018 section 4).
            return EncryptionUtils.getSecretKey(key, EncryptionUtils.parseKey(jws.getHeader("x5u")),
                    Base64.getDecoder().decode(payload.get("salt").asText()));
        } catch (Exception e) {
            throw new IllegalStateException("cannot answer the encryption handshake: " + e, e);
        }
    }

    /** A join failure with the RPC code, the stalled stage and the last packet; the RPC layer turns it into an envelope. */
    static final class HostError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String code;
        private final String stage;
        private final String lastPacket;

        HostError(String code, String message, String stage, String lastPacket) {
            super(message);
            this.code = code;
            this.stage = stage;
            this.lastPacket = lastPacket;
        }

        BedrockRpc.RpcError toRpc() {
            return new BedrockRpc.RpcError(code, getMessage(), stage, lastPacket);
        }
    }
}
