// SPIKE (T-M10-19). Not production code. A minimal offline Bedrock client over NetherNet (WebRTC data channels), built on the
// CloudburstMC NetherNet transport and the CloudburstMC Protocol fork that PowerNukkitX 3.0.6 ships inside its own jar: it is compiled
// against and run with that jar only, so the spike downloads nothing. Derived from ../bds/BdsJoin.java (T-M10-01) and
// ../geyser/BedrockJoin.java (T-M9-01); what changed is the transport:
//   - HTTP signalling: GET /v1/join (probe), then POST /v1/join/<networkId> with the SDP offer, the SDP answer in the response;
//   - the offer carries a self-signed identity assertion (OperatorIdentity.forPlayer, a key made per run): no Xbox token;
//   - the Bedrock batch travels as one NetherNet message with no 0xFE RakNet frame id (PowerNukkitX's NetherNetFrameCodec);
//   - one handshake attempt only (NETHER_CLIENT_MAX_HANDSHAKE_ATTEMPTS = 1), no retry (ADR-0007).
// After the transport the join is the T-M9-01 one: network settings, a self-signed login chain, the encryption handshake,
// resource pack answers, then it waits for the spawn status. It holds no account, token or secret.
// Usage: java -cp powernukkitx.jar:. NetherNetJoin <host> <signalling port> <name> <xuid> <timeout s> [hook shell command]
// Every packet type received is printed as "PKT <type>"; the hook runs once the spawn status arrived, then the client leaves.
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannelFactory;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption;
import org.cloudburstmc.netty.channel.nethernet.signaling.HttpSignalingSettings;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPClientSignaling;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.PacketDirection;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.auth.PlayerAuthenticationType;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.powernukkitx.network.nethernet.NetherNetFrameCodec;
import tel.schich.libdatachannel.LibDataChannelArchDetect;

public final class NetherNetJoin {
    static final long T0 = System.currentTimeMillis();
    static volatile String stage = "start";
    static final CountDownLatch DONE = new CountDownLatch(1);
    // The session is made when the peer becomes active, which on NetherNet is after connect() completes (r1: NPE)
    static final CountDownLatch SESSION = new CountDownLatch(1);
    static final AtomicReference<String> RESULT = new AtomicReference<>("TIMEOUT");

    static void log(String s) { System.out.println("[" + (System.currentTimeMillis() - T0) + " ms] " + s); }

    public static void main(String[] a) throws Exception {
        String host = a[0];
        int port = Integer.parseInt(a[1]);
        String name = a[2];
        String xuid = a[3];
        long timeout = Long.parseLong(a[4]);
        String hook = a.length > 5 && !a[5].isEmpty() ? a[5] : null;
        BedrockCodec codec = Bedrock_v2225.CODEC;
        log("codec " + codec.getMinecraftVersion() + " protocol " + codec.getProtocolVersion());

        // Not EncryptionUtils.createKeyPair: its static initialiser fetches Microsoft discovery documents (ADR-0018).
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp384r1"));
        KeyPair kp = gen.generateKeyPair();
        String pub = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());

        stage = "native";
        LibDataChannelArchDetect.initialize();
        log("libdatachannel native loaded");

        stage = "probe";
        HttpSignalingSettings settings = HttpSignalingSettings.DEFAULT;
        String scheme = System.getenv().getOrDefault("PS_SCHEME", "AUTO");
        if (!scheme.equals("AUTO")) settings = settings.withScheme(HttpSignalingSettings.Scheme.valueOf(scheme));
        InetSocketAddress target = new InetSocketAddress(host, port);
        try {
            NetherNetHTTPClientSignaling.Probe probe = NetherNetHTTPClientSignaling.probe(target, settings).get(15, TimeUnit.SECONDS);
            log("PROBE scheme=" + probe.scheme() + " motd=" + probe.motd());
        } catch (Exception e) {
            log("PROBE failed: " + e);
        }

        // The identity assertion of the offer: a fresh, unstored key standing for this player. A server with
        // TokenTrust.ANY (PowerNukkitX 3.0.6) reads it unverified; a retail one wants a Minecraft-issued token.
        OperatorIdentity identity = OperatorIdentity.generate("proofstone-spike").forPlayer(xuid, name);
        HttpSignalingSettings fixed = settings;

        NioEventLoopGroup group = new NioEventLoopGroup(1);
        BedrockClientSession[] holder = new BedrockClientSession[1];
        try {
            Bootstrap b = new Bootstrap()
                    .channelFactory(NetherNetChannelFactory.client(() -> new NetherNetHTTPClientSignaling(fixed)))
                    .group(group)
                    .option(NetherChannelOption.NETHER_CLIENT_IDENTITY, identity)
                    .option(NetherChannelOption.NETHER_CLIENT_MAX_HANDSHAKE_ATTEMPTS, 1)
                    .handler(new BedrockClientInitializer() {
                        @Override protected void preInitChannel(Channel ch) {
                            // The client-side mirror of PowerNukkitX's NetherNetServerInitializer: no RakNet frame id
                            ch.attr(PacketDirection.ATTRIBUTE).set(PacketDirection.SERVER_BOUND);
                            ch.pipeline().addLast("nethernet-frame-codec", NetherNetFrameCodec.INSTANCE);
                            ch.pipeline().addLast("compression-codec", new CompressionCodec(
                                    BedrockChannelInitializer.getCompression(PacketCompressionAlgorithm.ZLIB, 11, true), false));
                        }
                        // A NetherNet channel has no RAK_PROTOCOL_VERSION option, which setCompression reads (r2: NPE);
                        // PowerNukkitX's server initializer pins the peer's version the same way
                        @Override protected org.cloudburstmc.protocol.bedrock.BedrockPeer createPeer(Channel ch) {
                            return new org.cloudburstmc.protocol.bedrock.BedrockPeer(ch, this::createSession) {
                                @Override public int getRakVersion() { return 11; }
                            };
                        }
                        @Override protected void initPacketCodec(Channel ch) {
                            ch.pipeline().addLast("bedrock-packet-codec", new BedrockPacketCodec_v3());
                        }
                        @Override protected void initSession(BedrockClientSession s) {
                            holder[0] = s; SESSION.countDown();
                            s.setCodec(codec);
                            s.setPacketHandler(new H(s, kp, pub, uuid, name, xuid, host + ":" + port, hook));
                        }
                    });
            stage = "nethernet-connect";
            ChannelFuture cf = b.connect(target);
            if (!cf.await(timeout, TimeUnit.SECONDS)) throw new IllegalStateException("NetherNet connect did not finish in " + timeout + " s");
            if (!cf.isSuccess()) throw new IllegalStateException("NetherNet connect failed", cf.cause());
            Channel ch = cf.channel();
            log("NetherNet connected local=" + ch.localAddress() + " remote=" + ch.remoteAddress());
            stage = "network-settings";
            RequestNetworkSettingsPacket req = new RequestNetworkSettingsPacket();
            req.setClientNetworkVersion(codec.getProtocolVersion());
            if (!SESSION.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("no Bedrock session 10 s after the NetherNet connect");
            log("Bedrock session up");
            holder[0].sendPacketImmediately(req);
            boolean finished = DONE.await(timeout, TimeUnit.SECONDS);
            if (!finished) RESULT.set("TIMEOUT stage=" + stage);
            log("RESULT " + RESULT.get());
            if (holder[0] != null && holder[0].isConnected()) holder[0].disconnect();
            ch.close().await(5, TimeUnit.SECONDS);
        } catch (Throwable t) {
            log("RESULT ERROR stage=" + stage + " " + t);
            t.printStackTrace(System.out);
            System.exit(4);
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
        System.exit(RESULT.get().startsWith("SPAWNED") ? 0 : 3);
    }

    static final class H implements BedrockPacketHandler {
        final BedrockClientSession s; final KeyPair kp; final String pub; final UUID uuid; final String name; final String xuid;
        final String addr; final String hook;
        long runtimeId;
        H(BedrockClientSession s, KeyPair kp, String pub, UUID uuid, String name, String xuid, String addr, String hook) {
            this.s = s; this.kp = kp; this.pub = pub; this.uuid = uuid; this.name = name; this.xuid = xuid; this.addr = addr; this.hook = hook;
        }

        @Override public PacketSignal handlePacket(BedrockPacket p) {
            log("PKT " + p.getPacketType());
            return p.handle(this);
        }

        @Override public void onDisconnect(String reason) {
            log("session closed: " + reason);
            if (RESULT.get().equals("TIMEOUT")) RESULT.set("CLOSED stage=" + stage + " reason=" + reason);
            DONE.countDown();
        }

        @Override public PacketSignal handle(NetworkSettingsPacket p) {
            log("NetworkSettings compression=" + p.getCompressionAlgorithm() + " threshold=" + p.getCompressionThreshold());
            s.setCompression(p.getCompressionAlgorithm());
            stage = "login";
            try {
                LoginPacket login = new LoginPacket();
                login.setClientNetworkVersion(s.getCodec().getProtocolVersion());
                login.setAuthenticationType(PlayerAuthenticationType.SELF_SIGNED); login.setChain(List.of(identityJwt())); login.setToken("");
                login.setClientJwt(clientJwt());
                s.sendPacketImmediately(login);
            } catch (Exception e) { throw new RuntimeException(e); }
            stage = "login-sent";
            return PacketSignal.HANDLED;
        }

        @Override public PacketSignal handle(ServerToClientHandshakePacket p) {
            try {
                JsonWebSignature jws = new JsonWebSignature();
                jws.setCompactSerialization(p.getHandshakeWebToken());
                String serverKey = jws.getHeader("x5u");
                String payload = jws.getUnverifiedPayload();
                String salt = payload.replaceAll("(?s).*\"salt\"\\s*:\\s*\"([^\"]+)\".*", "$1");
                javax.crypto.SecretKey key = EncryptionUtils.getSecretKey(kp.getPrivate(), EncryptionUtils.parseKey(serverKey), Base64.getDecoder().decode(salt));
                s.enableEncryption(key);
                s.sendPacketImmediately(new ClientToServerHandshakePacket());
                stage = "encrypted";
            } catch (Exception e) { throw new RuntimeException(e); }
            return PacketSignal.HANDLED;
        }

        @Override public PacketSignal handle(PlayStatusPacket p) {
            log("PlayStatus " + p.getStatus());
            switch (p.getStatus()) {
                case LOGIN_SUCCESS -> stage = "login-success";
                case PLAYER_SPAWN -> {
                    stage = "spawn";
                    SetLocalPlayerAsInitializedPacket init = new SetLocalPlayerAsInitializedPacket();
                    init.setPlayerID(runtimeId);
                    s.sendPacket(init);
                    finish();
                }
                default -> { RESULT.set("PLAYSTATUS " + p.getStatus() + " stage=" + stage); DONE.countDown(); }
            }
            return PacketSignal.HANDLED;
        }

        @Override public PacketSignal handle(ResourcePacksInfoPacket p) {
            stage = "resource-packs";
            ResourcePackClientResponsePacket r = new ResourcePackClientResponsePacket();
            r.setResponse(org.cloudburstmc.protocol.bedrock.data.ResourcePackResponse.DOWNLOADING_FINISHED);
            s.sendPacket(r);
            return PacketSignal.HANDLED;
        }

        @Override public PacketSignal handle(ResourcePackStackPacket p) {
            ResourcePackClientResponsePacket r = new ResourcePackClientResponsePacket();
            r.setResponse(org.cloudburstmc.protocol.bedrock.data.ResourcePackResponse.RESOURCE_PACK_STACK_FINISHED);
            s.sendPacket(r);
            return PacketSignal.HANDLED;
        }

        @Override public PacketSignal handle(StartGamePacket p) {
            stage = "start-game";
            runtimeId = p.getRuntimeID();
            log("StartGame level=" + p.getLevelName() + " runtimeId=" + runtimeId);
            RequestChunkRadiusPacket r = new RequestChunkRadiusPacket();
            // PowerNukkitX sends the spawn status only after chunks.spawnThreshold chunks (r6: radius 4 gave 49, no spawn)
            r.setChunkRadius(8); r.setMaxChunkRadius(8);
            s.sendPacket(r);
            return PacketSignal.HANDLED;
        }

        @Override public PacketSignal handle(DisconnectPacket p) {
            RESULT.set("DISCONNECT stage=" + stage + " reason=" + p.getReason() + " message=" + p.getMessages());
            DONE.countDown();
            return PacketSignal.HANDLED;
        }

        void finish() {
            String out = "SPAWNED";
            if (hook != null) {
                stage = "hook";
                try {
                    Process pr = new ProcessBuilder("sh", "-c", hook).redirectErrorStream(true).start();
                    String o = new String(pr.getInputStream().readAllBytes());
                    int rc = pr.waitFor();
                    log("HOOK rc=" + rc + " " + o.trim().replace("\n", " | "));
                    out = rc == 0 ? "SPAWNED hook-ok" : "SPAWNED hook-failed rc=" + rc;
                } catch (Exception e) { out = "SPAWNED hook-error " + e; }
            }
            RESULT.set(out);
            DONE.countDown();
        }

        String identityJwt() throws Exception {
            long now = System.currentTimeMillis() / 1000;
            String payload = "{\"extraData\":{\"displayName\":\"" + name + "\",\"identity\":\"" + uuid + "\",\"XUID\":\"" + xuid + "\"}"
                    + ",\"identityPublicKey\":\"" + pub + "\",\"nbf\":" + (now - 60) + ",\"exp\":" + (now + 86400) + ",\"iat\":" + now + "}";
            return sign(payload);
        }

        String clientJwt() throws Exception {
            String skin = Base64.getEncoder().encodeToString(new byte[64 * 64 * 4]);
            String patch = Base64.getEncoder().encodeToString("{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}".getBytes());
            String payload = "{\"ClientRandomId\":" + java.util.concurrent.ThreadLocalRandom.current().nextLong(1L << 62)
                    + ",\"CurrentInputMode\":1,\"DefaultInputMode\":1,\"DeviceId\":\"" + uuid + "\",\"DeviceModel\":\"proofstone-spike\",\"DeviceOS\":7"
                    + ",\"GameVersion\":\"" + s.getCodec().getMinecraftVersion() + "\",\"GuiScale\":0,\"LanguageCode\":\"en_US\""
                    + ",\"PlatformOfflineId\":\"\",\"PlatformOnlineId\":\"\",\"PlayFabId\":\"\",\"SelfSignedId\":\"" + uuid + "\""
                    + ",\"ServerAddress\":\"" + addr + "\",\"SkinId\":\"" + uuid + ".Custom\",\"SkinData\":\"" + skin + "\""
                    + ",\"SkinImageHeight\":64,\"SkinImageWidth\":64,\"SkinResourcePatch\":\"" + patch + "\""
                    + ",\"SkinGeometryData\":\"\",\"SkinGeometryDataEngineVersion\":\"\",\"SkinAnimationData\":\"\",\"SkinColor\":\"#0\",\"ArmSize\":\"wide\""
                    + ",\"CapeData\":\"\",\"CapeId\":\"\",\"CapeImageHeight\":0,\"CapeImageWidth\":0,\"CapeOnClassicSkin\":false,\"PersonaSkin\":false"
                    + ",\"PremiumSkin\":false,\"TrustedSkin\":true,\"OverrideSkin\":false,\"ThirdPartyName\":\"" + name + "\",\"ThirdPartyNameOnly\":false"
                    + ",\"UIProfile\":0,\"AnimatedImageData\":[],\"PersonaPieces\":[],\"PieceTintColors\":[]"
                    // PowerNukkitX 3.0.6 ClientChainData.from (compiled) returns null, reported as INVALID_PLATFORM_SKIN (r3),
                    // unless these claims are present with the right JSON type (r3, r4); IsEduMode is left out, as its mere presence
                    // marks an Education client (r5: LOGIN_FAILED_EDITION_MISMATCH_EDU_TO_VANILLA)
                    + ",\"CompatibleWithClientSideChunkGen\":false,\"GraphicsMode\":1,\"ClientIsEditorCapable\":false"
                    + ",\"MaxViewDistance\":16,\"MemoryTier\":0,\"PartyId\":\"\",\"PlatformType\":0,\"ClientEditorConnectionIntent\":0}";
            return sign(payload);
        }

        String sign(String payload) throws Exception {
            JsonWebSignature jws = new JsonWebSignature();
            jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384);
            jws.setHeader("x5u", pub);
            jws.setKey((ECPrivateKey) kp.getPrivate());
            jws.setPayload(payload);
            return jws.getCompactSerialization();
        }
    }
}
