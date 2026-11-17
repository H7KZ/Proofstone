package io.github.h7kz.proofstone.driver.bedrock;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

/**
 * The offline identity of one join: a key pair made per join, a claimed name and a claimed numeric XUID, signed by
 * itself. It holds no account, token or secret and never contacts Microsoft or Xbox (ADR-0018 section 4). The chain is
 * accepted only by a Geyser with {@code validate-bedrock-login: false}.
 */
final class Identity {

    private static final int SKIN_SIZE = 64;

    private final String name;
    private final String xuid;
    private final UUID uuid;
    private final KeyPair keys;
    private final String publicKey;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    Identity(String name, String xuid, Clock clock) {
        this.name = BedrockRpc.requireName(name);
        this.xuid = BedrockRpc.requireXuid(xuid);
        this.clock = clock;
        // Not EncryptionUtils.createKeyPair: that class's static initialiser fetches Microsoft discovery documents.
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp384r1"));
            this.keys = generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("no P-384 key pair", e);
        }
        this.publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        this.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    String name() {
        return name;
    }

    String xuid() {
        return xuid;
    }

    UUID uuid() {
        return uuid;
    }

    KeyPair keys() {
        return keys;
    }

    /** The one self-signed link of the chain: the claimed name, UUID and XUID. */
    String identityJwt() {
        long now = clock.millis() / 1000;
        ObjectNode payload = BedrockRpc.JSON.createObjectNode();
        ObjectNode extra = payload.putObject("extraData");
        extra.put("displayName", name);
        extra.put("identity", uuid.toString());
        extra.put("XUID", xuid);
        payload.put("identityPublicKey", publicKey);
        payload.put("nbf", now - 60);
        payload.put("exp", now + 86400);
        payload.put("iat", now);
        return sign(payload);
    }

    /** The client data JWT: a blank 64x64 skin and the device a plain client reports. */
    String clientJwt(String serverAddress, String gameVersion) {
        String skin = Base64.getEncoder().encodeToString(new byte[SKIN_SIZE * SKIN_SIZE * 4]);
        String patch = Base64.getEncoder()
                .encodeToString("{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ObjectNode p = BedrockRpc.JSON.createObjectNode();
        p.put("ClientRandomId", random.nextLong(1L << 62));
        p.put("CurrentInputMode", 1).put("DefaultInputMode", 1);
        p.put("DeviceId", uuid.toString()).put("DeviceModel", "proofstone").put("DeviceOS", 7);
        p.put("GameVersion", gameVersion).put("GuiScale", 0).put("LanguageCode", "en_US");
        p.put("PlatformOfflineId", "").put("PlatformOnlineId", "").put("PlayFabId", "").put("SelfSignedId", uuid.toString());
        p.put("ServerAddress", serverAddress);
        p.put("SkinId", uuid + ".Custom").put("SkinData", skin).put("SkinImageHeight", SKIN_SIZE).put("SkinImageWidth", SKIN_SIZE);
        p.put("SkinResourcePatch", patch).put("SkinGeometryData", "").put("SkinGeometryDataEngineVersion", "");
        p.put("SkinAnimationData", "").put("SkinColor", "#0").put("ArmSize", "wide");
        p.put("CapeData", "").put("CapeId", "").put("CapeImageHeight", 0).put("CapeImageWidth", 0);
        p.put("CapeOnClassicSkin", false).put("PersonaSkin", false).put("PremiumSkin", false).put("TrustedSkin", true);
        p.put("OverrideSkin", false).put("ThirdPartyName", name).put("ThirdPartyNameOnly", false).put("UIProfile", 0);
        for (String empty : new String[] {"AnimatedImageData", "PersonaPieces", "PieceTintColors"}) {
            p.putArray(empty);
        }
        return sign(p);
    }

    private String sign(ObjectNode payload) {
        try {
            JsonWebSignature jws = new JsonWebSignature();
            jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384);
            jws.setHeader("x5u", publicKey);
            jws.setKey((ECPrivateKey) keys.getPrivate());
            jws.setPayload(payload.toString());
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new IllegalStateException("cannot sign the offline login", e);
        }
    }
}
