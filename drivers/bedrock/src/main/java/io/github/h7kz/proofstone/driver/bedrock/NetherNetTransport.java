package io.github.h7kz.proofstone.driver.bedrock;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannelFactory;
import org.cloudburstmc.netty.channel.nethernet.NetherNetNative;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption;
import org.cloudburstmc.netty.channel.nethernet.signaling.HttpSignalingSettings;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPClientSignaling;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.PacketDirection;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;

/**
 * The NetherNet side of a join (ADR-0021): HTTP signalling, WebRTC data channels, a self-made offer identity. It is the only
 * place that touches the NetherNet library; {@link HostSession} calls it for a session whose transport is {@code nethernet}.
 * The driver never retries a handshake (ADR-0007) and holds no account, token or secret: the identity is a key made for this
 * session, which only a server that reads offer identities unverified accepts (ADR-0021 decision 2).
 */
final class NetherNetTransport {

    /** RakNet protocol version the compression setup reads from the channel; a NetherNet channel has none, so it is pinned (nethernet.md r2). */
    static final int RAK_VERSION = 11;

    /** The classpath resource property of libdatachannel-java's loader, and where the build puts each architecture's library. */
    static final String NATIVE_PROPERTY = "libdatachannel.native.datachannel-java.classpath";

    private NetherNetTransport() {
    }

    /** The resource path of the native library for an {@code os.arch} value, or an error naming the architecture (never a guess). */
    static String nativeResource(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = osArch.toLowerCase(Locale.ROOT);
        if (!os.contains("linux")) {
            throw new IllegalStateException("the NetherNet native library ships for Linux only (the driver host runs in a container), not " + osName);
        }
        return switch (arch) {
            case "amd64", "x86_64" -> "/native/linux-x86_64/libdatachannel-java.so";
            case "aarch64", "arm64" -> "/native/linux-aarch64/libdatachannel-java.so";
            default -> throw new IllegalStateException("no NetherNet native library for CPU architecture " + osArch);
        };
    }

    /** Points the loader at this architecture's library inside the shaded jar, then loads it. Idempotent. */
    static synchronized void loadNative() {
        // A value set from outside (a test or a custom image) is kept: it is the operator's explicit choice.
        if (System.getProperty(NATIVE_PROPERTY) == null) {
            System.setProperty(NATIVE_PROPERTY, nativeResource(System.getProperty("os.name"), System.getProperty("os.arch")));
        }
        NetherNetNative.initialize();
    }

    /**
     * The identity assertion of the offer: signed with the session's own key pair, so the key it names is the key of the login chain.
     * Geyser refuses a login whose key differs ("the login key does not match the admitted transport identity", T-M12-10 run 1);
     * PowerNukkitX reads the offer identity unverified and never noticed. The pair is the one {@link Identity} made for this session.
     */
    static OperatorIdentity offerIdentity(KeyPair keys, String xuid, String name) throws Exception {
        return new OperatorIdentity(keys.getPrivate(), keys.getPublic(), Instant.now().plus(Duration.ofDays(1)), "proofstone")
                .forPlayer(xuid, name);
    }

    /** The probe result: the HTTP scheme the server answered on and its status text. */
    record Probe(String scheme, String motd) {
    }

    /** {@code GET /v1/join}: 2xx means the server serves NetherNet; anything else is an error naming the target. */
    static Probe probe(InetSocketAddress target, long timeoutMillis) throws Exception {
        NetherNetHTTPClientSignaling.Probe probe = NetherNetHTTPClientSignaling.probe(target, HttpSignalingSettings.DEFAULT)
                .get(timeoutMillis, TimeUnit.MILLISECONDS);
        return new Probe(String.valueOf(probe.scheme()), String.valueOf(probe.motd()));
    }

    /**
     * A bootstrap that connects over NetherNet and builds the Bedrock session as RakNet does. The frame codec stands in for RakNet's
     * frame id codec; the rest of the pipeline is the library's own.
     */
    static void configure(Bootstrap bootstrap, KeyPair keys, String xuid, String name, SessionInitializer session) throws Exception {
        OperatorIdentity identity = offerIdentity(keys, xuid, name);
        bootstrap.channelFactory(NetherNetChannelFactory.client(() -> new NetherNetHTTPClientSignaling(HttpSignalingSettings.DEFAULT)))
                .option(NetherChannelOption.NETHER_CLIENT_IDENTITY, identity)
                // One handshake attempt: the library would otherwise send another offer after a failed one (ADR-0007, ADR-0021).
                .option(NetherChannelOption.NETHER_CLIENT_MAX_HANDSHAKE_ATTEMPTS, 1)
                .handler(new BedrockClientInitializer() {
                    @Override
                    protected void preInitChannel(Channel channel) {
                        channel.attr(PacketDirection.ATTRIBUTE).set(PacketDirection.SERVER_BOUND);
                        channel.pipeline().addLast("nethernet-frame-codec", new NetherNetFrameCodec());
                        channel.pipeline().addLast("compression-codec", new CompressionCodec(
                                BedrockChannelInitializer.getCompression(PacketCompressionAlgorithm.ZLIB, RAK_VERSION, true), false));
                    }

                    // The library's own version reads the RakNet protocol option, which a NetherNet channel lacks (a NullPointerException
                    // that closed the channel in T-M12-09 run r1); the codec is the one RakNet protocol 11 gets.
                    @Override
                    protected void initPacketCodec(Channel channel) {
                        channel.pipeline().addLast("bedrock-packet-codec", new BedrockPacketCodec_v3());
                    }

                    @Override
                    protected BedrockPeer createPeer(Channel channel) {
                        return new BedrockPeer(channel, this::createSession) {
                            @Override
                            public int getRakVersion() {
                                return RAK_VERSION;
                            }
                        };
                    }

                    @Override
                    protected void initSession(BedrockClientSession created) {
                        session.init(created);
                    }

                    @Override
                    protected void postInitChannel(Channel channel) throws Exception {
                        super.postInitChannel(channel);
                        session.postInit(channel);
                    }
                });
    }

    /** What {@link HostSession} does with the session and the channel, the same on both transports. */
    interface SessionInitializer {
        void init(BedrockClientSession session);

        void postInit(Channel channel);
    }
}
