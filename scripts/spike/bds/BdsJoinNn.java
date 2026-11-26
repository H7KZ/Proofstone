// T-M12-11: BdsJoin over NetherNet. Same client and the same PacketViolationWarningPacket change as BdsJoin.java (T-M10-01); the
// transport is the driver host's own NetherNetTransport (package-private, so this class is declared in its package and run with
// bedrock-host-all.jar on the class path). Offline: the key pair of the login is also the key of the offer identity.
package io.github.h7kz.proofstone.driver.bedrock;

// T-M10-01 copy of ../geyser/BedrockJoin.java (T-M9-01) with the imports relocated to the driver host jar's shaded packages (it
// is compiled against and run with bedrock-host-all.jar) and one change, marked "T-M10-01" below. Original header:
// T-M9-01 spike client, not production code. A minimal offline Bedrock client on CloudburstMC Protocol (the library
// Geyser itself uses): RakNet connect, network settings, a self-signed (not Xbox signed) login chain, encryption
// handshake, resource pack answers, then it waits for the spawn status. It never contacts any Microsoft or Xbox host and
// holds no account, token or secret: the key pair is generated per run.
// Usage: java -cp <classpath> BedrockJoin <host> <port> <name> <timeout s> [hook shell command] [no-skin-fields]
// Every packet type received is printed as "PKT <ms> <type>" so the join log is the "packets up to spawn" evidence. The
// hook runs once the spawn status arrived (the agent players read of the spike script) and the client then disconnects.
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import io.github.h7kz.proofstone.shaded.io.netty.bootstrap.Bootstrap;
import io.github.h7kz.proofstone.shaded.io.netty.channel.Channel;
import io.github.h7kz.proofstone.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.github.h7kz.proofstone.shaded.org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import io.github.h7kz.proofstone.shaded.org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import io.github.h7kz.proofstone.shaded.org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import io.github.h7kz.proofstone.shaded.org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import io.github.h7kz.proofstone.shaded.org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import io.github.h7kz.proofstone.shaded.org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import io.github.h7kz.proofstone.shaded.org.cloudburstmc.protocol.bedrock.packet.*;
import io.github.h7kz.proofstone.shaded.org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import io.github.h7kz.proofstone.shaded.org.cloudburstmc.protocol.common.PacketSignal;
import io.github.h7kz.proofstone.shaded.org.jose4j.jws.AlgorithmIdentifiers;
import io.github.h7kz.proofstone.shaded.org.jose4j.jws.JsonWebSignature;

public final class BdsJoinNn {
    static final long T0 = System.currentTimeMillis();
    static volatile String stage = "start";
    static final CountDownLatch DONE = new CountDownLatch(1);
    static final CountDownLatch SESSION = new CountDownLatch(1);
    static final AtomicReference<String> RESULT = new AtomicReference<>("TIMEOUT");

    // A claimed, unsigned XUID: Floodgate parses it as a number, an empty one fails (spike finding). Overridable.
    static final String XUID = System.getenv().getOrDefault("PS_XUID", "2535400000000001");

    static void log(String s) { System.out.println("[" + (System.currentTimeMillis() - T0) + " ms] " + s); }

    public static void main(String[] a) throws Exception {
        String host = a[0];
        int port = Integer.parseInt(a[1]);
        String name = a[2];
        // T-M10-01: BDS answers the login with a PacketViolationWarningPacket, which the library registers as server-bound only, so
        // the M9 driver host fails the decode. Registered here as BOTH, so its text is printed.
        BedrockCodec codec = Bedrock_v2225.CODEC.toBuilder().deregisterPacket(PacketViolationWarningPacket.class)
                .registerPacket(PacketViolationWarningPacket::new,
                        io.github.h7kz.proofstone.shaded.org.cloudburstmc.protocol.bedrock.codec.v407.serializer.PacketViolationWarningSerializer_v407.INSTANCE,
                        Bedrock_v2225.CODEC.getPacketDefinition(PacketViolationWarningPacket.class).getId(),
                        io.github.h7kz.proofstone.shaded.org.cloudburstmc.protocol.bedrock.data.PacketRecipient.BOTH)
                .build();
        long timeout = Long.parseLong(a[3]);
        String hook = a.length > 4 && !a[4].isEmpty() ? a[4] : null;
        log("codec " + codec.getMinecraftVersion() + " protocol " + codec.getProtocolVersion() + " raknet " + codec.getRaknetProtocolVersion());
        KeyPair kp = EncryptionUtils.createKeyPair();
        String pub = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());

        NioEventLoopGroup group = new NioEventLoopGroup(1);
        BedrockClientSession[] holder = new BedrockClientSession[1];
        try {
            NetherNetTransport.loadNative();
            stage = "probe";
            log("PROBE " + NetherNetTransport.probe(new InetSocketAddress(host, port), 15000));
            Bootstrap b = new Bootstrap().group(group);
            NetherNetTransport.configure(b, kp, a.length > 5 ? a[5] : XUID, name, new NetherNetTransport.SessionInitializer() {
                @Override public void init(BedrockClientSession s) {
                    holder[0] = s;
                    s.setCodec(codec);
                    s.setPacketHandler(new H(s, kp, pub, uuid, name, host + ":" + port, hook));
                    SESSION.countDown();
                }
                @Override public void postInit(Channel channel) { }
            });
            stage = "nethernet-connect";
            Channel ch = b.connect(new InetSocketAddress(host, port)).syncUninterruptibly().channel();
            log("NetherNet connected");
            stage = "network-settings";
            RequestNetworkSettingsPacket req = new RequestNetworkSettingsPacket();
            req.setProtocolVersion(codec.getProtocolVersion());
            if (!SESSION.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("no Bedrock session 10 s after the NetherNet connect");
            holder[0].sendPacketImmediately(req);
            boolean finished = DONE.await(timeout, TimeUnit.SECONDS);
            if (!finished) RESULT.set("TIMEOUT stage=" + stage);
            log("RESULT " + RESULT.get());
            if (holder[0] != null && holder[0].isConnected()) holder[0].disconnect();
            ch.close();
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
        final BedrockClientSession s; final KeyPair kp; final String pub; final UUID uuid; final String name; final String addr; final String hook;
        long runtimeId;
        H(BedrockClientSession s, KeyPair kp, String pub, UUID uuid, String name, String addr, String hook) {
            this.s = s; this.kp = kp; this.pub = pub; this.uuid = uuid; this.name = name; this.addr = addr; this.hook = hook;
        }

        @Override public PacketSignal handlePacket(BedrockPacket p) {
            log("PKT " + p.getPacketType());
            return p.handle(this);
        }

        @Override public void onDisconnect(CharSequence reason) {
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
                login.setProtocolVersion(s.getCodec().getProtocolVersion());
                login.setAuthPayload(new CertificateChainPayload(List.of(identityJwt()), AuthType.SELF_SIGNED));
                login.setClientJwt(clientJwt());
                s.sendPacketImmediately(login);
            } catch (Exception e) { throw new RuntimeException(e); }
            stage = "login-sent";
            return PacketSignal.HANDLED;
        }

        @Override public PacketSignal handle(ServerToClientHandshakePacket p) {
            try {
                JsonWebSignature jws = new JsonWebSignature();
                jws.setCompactSerialization(p.getJwt());
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
                    init.setRuntimeEntityId(runtimeId);
                    s.sendPacket(init);
                    finish();
                }
                default -> { RESULT.set("PLAYSTATUS " + p.getStatus() + " stage=" + stage); DONE.countDown(); }
            }
            return PacketSignal.HANDLED;
        }

        @Override public PacketSignal handle(ResourcePacksInfoPacket p) {
            stage = "resource-packs";
            log("ResourcePacksInfo resourcePacks=" + p.getResourcePackInfos().size() + " behaviorPacks=" + p.getBehaviorPackInfos().size());
            ResourcePackClientResponsePacket r = new ResourcePackClientResponsePacket();
            r.setStatus(ResourcePackClientResponsePacket.Status.HAVE_ALL_PACKS);
            s.sendPacket(r);
            return PacketSignal.HANDLED;
        }

        @Override public PacketSignal handle(ResourcePackStackPacket p) {
            log("ResourcePackStack gameVersion=" + p.getGameVersion());
            ResourcePackClientResponsePacket r = new ResourcePackClientResponsePacket();
            r.setStatus(ResourcePackClientResponsePacket.Status.COMPLETED);
            s.sendPacket(r);
            return PacketSignal.HANDLED;
        }

        @Override public PacketSignal handle(StartGamePacket p) {
            stage = "start-game";
            runtimeId = p.getRuntimeEntityId();
            log("StartGame level=" + p.getLevelName() + " runtimeId=" + runtimeId + " uniqueId=" + p.getUniqueEntityId());
            RequestChunkRadiusPacket r = new RequestChunkRadiusPacket();
            r.setRadius(4); r.setMaxRadius(4);
            s.sendPacket(r);
            return PacketSignal.HANDLED;
        }

        @Override public PacketSignal handle(PacketViolationWarningPacket p) {
            log("VIOLATION type=" + p.getType() + " severity=" + p.getSeverity() + " causeId=" + p.getPacketCauseId() + " context=" + p.getContext());
            return PacketSignal.HANDLED;
        }

        @Override public PacketSignal handle(DisconnectPacket p) {
            RESULT.set("DISCONNECT stage=" + stage + " reason=" + p.getReason() + " message=" + p.getKickMessage());
            DONE.countDown();
            return PacketSignal.HANDLED;
        }

        void finish() {
            String out = "SPAWNED";
            if (hook != null) {
                stage = "hook";
                try {
                    Process pr = new ProcessBuilder(System.getenv().getOrDefault("PS_BASH", "bash"), "-c", hook).redirectErrorStream(true).start();
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
            String payload = "{\"extraData\":{\"displayName\":\"" + name + "\",\"identity\":\"" + uuid + "\",\"XUID\":\"" + XUID + "\"}"
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
                    + ",\"UIProfile\":0,\"AnimatedImageData\":[],\"PersonaPieces\":[],\"PieceTintColors\":[]}";
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
