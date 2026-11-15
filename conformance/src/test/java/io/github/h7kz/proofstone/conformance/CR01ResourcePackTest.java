package io.github.h7kz.proofstone.conformance;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CapturedEvent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Clients;
import io.github.h7kz.proofstone.api.EventProbe;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.ResourcePackAnswer;
import io.github.h7kz.proofstone.api.ResourcePackAnswer.Answer;
import io.github.h7kz.proofstone.api.ResourcePackPush;
import io.github.h7kz.proofstone.api.ServerResourcePack;
import io.github.h7kz.proofstone.core.await.Await;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;

/**
 * C-R01 (specs/self-tests.md section 1c, T-M8-10): a required resource pack, served by the harness on loopback and pushed by
 * {@code server.properties} in the configuration phase. Accept reaches play with the pack checked; decline is kicked with
 * the server's message; no declared answer ends the join at once, never a silent accept and never a join timeout.
 *
 * <p>The server's view is read with the event probe (ADR-0017 section 7). Paper 26.3 fires
 * {@code PlayerResourcePackStatusEvent} for a play-phase answer only, not for the configuration-phase push
 * (docs/platforms/paper.md), so the fixture plugin pushes the same pack again in play, as a plugin under test would, and the
 * probe reads that answer. A cell without RESOURCE_PACKS skips with the capability named.
 */
@Proofstone(plugins = "build/fixture-plugins/*.jar")
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@ServerResourcePack(value = "build/resourcepacks/proofstone-e2e.zip", required = true,
        prompt = "{\"text\":\"Proofstone C-R01 pack\"}")
class CR01ResourcePackTest {

    static final String STATUS_EVENT = "org.bukkit.event.player.PlayerResourcePackStatusEvent";
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final List<String> LOADED = List.of("ACCEPTED", "DOWNLOADED", "SUCCESSFULLY_LOADED");

    @CellTest
    @ResourcePackAnswer(Answer.ACCEPT)
    void anAcceptedPackIsDownloadedCheckedAndTheServerSeesItLoaded(Agent agent, Client client) {
        List<ResourcePackPush> pushes = client.resourcePacks();
        Assertions.assertEquals(1, pushes.size(), "one push in configuration, from server.properties: " + pushes);
        ResourcePackPush push = pushes.get(0);
        Assertions.assertTrue(push.url().startsWith("http://127.0.0.1:"), "the harness source on loopback: " + push);
        Assertions.assertTrue(push.hash().matches("[0-9a-f]{40}"), "a sha1 was pushed: " + push);
        Assertions.assertTrue(push.required(), "required: " + push);
        Assertions.assertEquals("Proofstone C-R01 pack", push.prompt());
        Assertions.assertEquals(Answer.ACCEPT, push.answer());
        Assertions.assertEquals(LOADED, push.statuses());
        Assertions.assertNull(push.problem(), "the downloaded bytes match the pushed sha1");

        Assertions.assertEquals(LOADED, pushInPlay(agent, client, push.hash(), "SUCCESSFULLY_LOADED"),
                "the server's view of the answer");
        ResourcePackPush again = client.resourcePacks().get(1);
        Assertions.assertEquals(LOADED, again.statuses(), "the play-phase push is recorded and answered too: " + again);
    }

    @CellTest
    @ResourcePackAnswer(Answer.DECLINE)
    void aDeclinedRequiredPackIsKickedWithTheServersMessage(Clients clients) {
        RuntimeException refused = Assertions.assertThrows(RuntimeException.class, () -> clients.create("RpDecline"));

        Assertions.assertTrue(refused.getMessage().contains("multiplayer.requiredTexturePrompt.disconnect"),
                "the server's kick message: " + refused.getMessage());
        Assertions.assertFalse(refused.getMessage().startsWith("timed out"), "a kick, not a join timeout");
    }

    @CellTest
    void aPushWithNoDeclaredAnswerEndsTheJoinAtOnceNamingTheUrl(Clients clients) {
        long started = System.nanoTime();
        RuntimeException refused = Assertions.assertThrows(RuntimeException.class, () -> clients.create("RpSilent"));
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        Assertions.assertTrue(refused.getMessage().contains("no resource-pack answer declared"), refused.getMessage());
        Assertions.assertTrue(refused.getMessage().contains("http://127.0.0.1:"), "names the URL: " + refused.getMessage());
        Assertions.assertFalse(refused.getMessage().startsWith("timed out"), "never a CONFIGURATION_STALLED timeout");
        Assertions.assertTrue(took.compareTo(Duration.ofSeconds(15)) < 0, "ended at once, not at the 30 s join timeout: "
                + took);
    }

    /**
     * Has the fixture push the harness's pack, with {@code sha1}, to the client in play, and returns the statuses the
     * server's event reported for it once {@code last} arrived.
     */
    static List<String> pushInPlay(Agent agent, Client client, String sha1, String last) {
        String url = client.resourcePacks().get(0).url();
        try (EventProbe probe = agent.events().subscribe(STATUS_EVENT)) {
            agent.command("fixture-resourcepack " + client.name() + " " + url + " " + sha1 + " true");
            Await.until("the client's record of the play-phase push", () -> client.resourcePacks().size(),
                    size -> size == 2, WAIT);
            return Await.until("PlayerResourcePackStatusEvent " + last + " for " + client.name(),
                    () -> probe.events().stream().filter(event -> client.name().equals(name(event)))
                            .map(CR01ResourcePackTest::status).toList(),
                    statuses -> statuses.contains(last), WAIT);
        }
    }

    private static Object name(CapturedEvent event) {
        return ((Map<?, ?>) event.field("player")).get("name");
    }

    private static String status(CapturedEvent event) {
        return String.valueOf(((Map<?, ?>) event.field("status")).get("value"));
    }
}
