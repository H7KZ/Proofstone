package io.github.h7kz.proofstone.driver.mcpl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.h7kz.proofstone.api.ResourcePackAnswer.Answer;
import io.github.h7kz.proofstone.api.ResourcePackPush;
import io.github.h7kz.proofstone.core.driver.ResourcePackPolicy;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPopPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPushPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundResourcePackPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.junit.jupiter.api.Test;

/**
 * T-M8-10: the driver's resource-pack contract (client-driver spec section 2b), driven by scripted packets with no server.
 * The download runs on the calling thread here, so every status is sent before {@code received} returns.
 */
class McplResourcePackTest {

    private static final String SOURCE = "http://127.0.0.1:40000/";
    private static final String URL = SOURCE + "proofstone-pack/pack.zip";
    private static final byte[] PACK = "a resource pack".getBytes(StandardCharsets.UTF_8);
    private static final String SHA1 = sha1(PACK);

    private final List<Packet> sent = new ArrayList<>();
    private final List<String> disconnects = new ArrayList<>();
    private final List<String> fetched = new ArrayList<>();
    private final Session network = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(),
            new Class<?>[] {Session.class}, (proxy, method, args) -> {
                if (method.getName().equals("send")) {
                    sent.add((Packet) args[0]);
                } else if (method.getName().equals("disconnect")) {
                    disconnects.add(String.valueOf(args[0]));
                }
                return null;
            });
    private byte[] served = PACK;

    private McplSession session(Answer answer) {
        return new McplSession(network, "Bot", new ResourcePackPolicy(answer, SOURCE), url -> {
            fetched.add(url);
            if (served == null) {
                throw new IOException("HTTP 404");
            }
            return served;
        }, Runnable::run);
    }

    private static ClientboundResourcePackPushPacket push(UUID id, String url, String hash, Component prompt) {
        return new ClientboundResourcePackPushPacket(id, url, hash, true, prompt);
    }

    private List<ResourcePackStatus> statusesSent() {
        return sent.stream().filter(p -> p instanceof ServerboundResourcePackPacket)
                .map(p -> ((ServerboundResourcePackPacket) p).getStatus()).toList();
    }

    @Test
    void aConfigurationPhasePushAcceptedFromTheHarnessSourceIsDownloadedCheckedAndLoaded() {
        McplSession bot = session(Answer.ACCEPT);
        UUID id = UUID.randomUUID();
        bot.listener().packetReceived(network, push(id, URL, SHA1, Component.text("Please")));

        assertThat(fetched).containsExactly(URL);
        assertThat(statusesSent()).containsExactly(ResourcePackStatus.ACCEPTED, ResourcePackStatus.DOWNLOADED,
                ResourcePackStatus.SUCCESSFULLY_LOADED);
        assertThat(sent).allMatch(p -> ((ServerboundResourcePackPacket) p).getId().equals(id));
        ResourcePackPush record = bot.resourcePacks().get(0);
        assertThat(record.id()).isEqualTo(id.toString());
        assertThat(record.url()).isEqualTo(URL);
        assertThat(record.hash()).isEqualTo(SHA1);
        assertThat(record.required()).isTrue();
        assertThat(record.prompt()).isEqualTo("Please");
        assertThat(record.answer()).isEqualTo(Answer.ACCEPT);
        assertThat(record.statuses()).containsExactly("ACCEPTED", "DOWNLOADED", "SUCCESSFULLY_LOADED");
        assertThat(record.problem()).isNull();
        assertThat(record.popped()).isFalse();
        assertThat(bot.state()).as("still in configuration until the server finishes it").isEqualTo(McplSession.State.JOINING);
        assertThat(disconnects).isEmpty();
        assertThat(bot.transcript()).anyMatch(line -> line.contains("ClientboundResourcePackPushPacket") && line.contains(URL));
    }

    @Test
    void aWrongHashIsReportedAndAnsweredWithAFailedDownload() {
        McplSession bot = session(Answer.ACCEPT);
        String wrong = "0000000000000000000000000000000000000000";
        bot.listener().packetReceived(network, push(UUID.randomUUID(), URL, wrong, null));

        assertThat(statusesSent()).containsExactly(ResourcePackStatus.ACCEPTED, ResourcePackStatus.FAILED_DOWNLOAD);
        ResourcePackPush record = bot.resourcePacks().get(0);
        assertThat(record.problem()).isEqualTo("hash mismatch: expected " + wrong + ", got " + SHA1);
        assertThat(record.prompt()).isNull();
        assertThat(disconnects).as("the test, not the driver, decides whether a mismatch fails").isEmpty();
    }

    @Test
    void aFailedDownloadIsReportedAndAnsweredWithAFailedDownload() {
        served = null;
        McplSession bot = session(Answer.ACCEPT);
        bot.listener().packetReceived(network, push(UUID.randomUUID(), URL, SHA1, null));

        assertThat(statusesSent()).containsExactly(ResourcePackStatus.ACCEPTED, ResourcePackStatus.FAILED_DOWNLOAD);
        assertThat(bot.resourcePacks().get(0).problem()).startsWith("download failed").contains("HTTP 404");
    }

    @Test
    void declineSendsDeclinedAndDownloadsNothing() {
        McplSession bot = session(Answer.DECLINE);
        bot.listener().packetReceived(network, push(UUID.randomUUID(), URL, SHA1, null));

        assertThat(statusesSent()).containsExactly(ResourcePackStatus.DECLINED);
        assertThat(fetched).isEmpty();
        assertThat(bot.resourcePacks().get(0).statuses()).containsExactly("DECLINED");
    }

    @Test
    void failDownloadAcceptsThenFailsWithoutDownloading() {
        McplSession bot = session(Answer.FAIL_DOWNLOAD);
        bot.listener().packetReceived(network, push(UUID.randomUUID(), URL, SHA1, null));

        assertThat(statusesSent()).containsExactly(ResourcePackStatus.ACCEPTED, ResourcePackStatus.FAILED_DOWNLOAD);
        assertThat(fetched).isEmpty();
    }

    @Test
    void aPushWithNoDeclaredAnswerEndsTheJoinAtOnceNamingTheUrlAndNeverAccepts() {
        McplSession bot = session(null);
        bot.listener().packetReceived(network, push(UUID.randomUUID(), URL, SHA1, null));

        assertThat(statusesSent()).as("never a silent accept").isEmpty();
        assertThat(fetched).isEmpty();
        assertThat(disconnects).hasSize(1);
        assertThat(bot.disconnectReason()).contains(URL).contains("no resource-pack answer declared");
        assertThat(bot.resourcePacks().get(0).answer()).isNull();
        assertThat(bot.resourcePacks().get(0).problem()).contains("no resource-pack answer declared");
    }

    @Test
    void anotherUrlIsNeverDownloadedAcceptingItEndsTheSession() {
        McplSession bot = session(Answer.ACCEPT);
        String elsewhere = "https://example.invalid/pack.zip";
        bot.listener().packetReceived(network, push(UUID.randomUUID(), elsewhere, SHA1, null));

        assertThat(fetched).isEmpty();
        assertThat(statusesSent()).isEmpty();
        assertThat(disconnects).hasSize(1);
        assertThat(bot.disconnectReason()).contains(elsewhere).contains("harness source");
        assertThat(bot.resourcePacks().get(0).url()).isEqualTo(elsewhere);
    }

    @Test
    void anotherUrlIsStillDeclinedAsDeclared() {
        McplSession bot = session(Answer.DECLINE);
        bot.listener().packetReceived(network, push(UUID.randomUUID(), "https://example.invalid/p.zip", SHA1, null));

        assertThat(statusesSent()).containsExactly(ResourcePackStatus.DECLINED);
        assertThat(disconnects).isEmpty();
    }

    @Test
    void aPushInPlayIsRecordedAndAnsweredTooAndSeveralPacksAreAnsweredOneByOne() {
        McplSession bot = session(Answer.ACCEPT);
        UUID first = UUID.randomUUID();
        bot.listener().packetReceived(network, push(first, URL, SHA1, null));
        bot.listener().packetReceived(network,
                new ClientboundLoginPacket(1, false, new Key[0], 20, 8, 8, false, true, false, null, false, false));
        assertThat(bot.state()).isEqualTo(McplSession.State.PLAYING);
        UUID second = UUID.randomUUID();
        bot.listener().packetReceived(network, new ClientboundResourcePackPushPacket(second, URL, SHA1, false, null));

        assertThat(bot.resourcePacks()).extracting(ResourcePackPush::id).containsExactly(first.toString(), second.toString());
        assertThat(bot.resourcePacks().get(1).required()).isFalse();
        assertThat(statusesSent()).hasSize(6);
    }

    @Test
    void aPopOfOneIdOrOfAllIsRecorded() {
        McplSession bot = session(Answer.DECLINE);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        bot.listener().packetReceived(network, push(first, URL, SHA1, null));
        bot.listener().packetReceived(network, push(second, URL, SHA1, null));

        bot.listener().packetReceived(network, new ClientboundResourcePackPopPacket(first));
        assertThat(bot.resourcePacks()).extracting(ResourcePackPush::popped).containsExactly(true, false);

        bot.listener().packetReceived(network, new ClientboundResourcePackPopPacket((UUID) null));
        assertThat(bot.resourcePacks()).extracting(ResourcePackPush::popped).containsExactly(true, true);
        assertThat(bot.transcript()).anyMatch(line -> line.contains("ClientboundResourcePackPopPacket") && line.contains("all"));
    }

    @Test
    void theDefaultPolicyHasNoAnswerSoAPushIsNeverAccepted() {
        McplSession bot = new McplSession(network, "Bot");
        bot.listener().packetReceived(network, push(UUID.randomUUID(), URL, SHA1, null));

        assertThat(statusesSent()).isEmpty();
        assertThat(bot.disconnectReason()).contains("no resource-pack answer declared");
    }

    @Test
    void theSourceRuleIsAPrefixOfTheHarnessBaseUrl() {
        ResourcePackPolicy policy = new ResourcePackPolicy(Answer.ACCEPT, SOURCE);
        assertThat(policy.fromSource(URL)).isTrue();
        assertThat(policy.fromSource("http://127.0.0.1:40000.example/x.zip")).isFalse();
        assertThat(policy.fromSource("http://127.0.0.1:40001/proofstone-pack/pack.zip")).isFalse();
        assertThat(ResourcePackPolicy.NONE.fromSource(URL)).isFalse();
    }

    private static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
