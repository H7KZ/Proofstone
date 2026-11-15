package io.github.h7kz.proofstone.conformance;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.ResourcePackAnswer;
import io.github.h7kz.proofstone.api.ResourcePackAnswer.Answer;
import io.github.h7kz.proofstone.api.ResourcePackPush;
import io.github.h7kz.proofstone.api.ServerResourcePack;
import java.util.List;
import org.junit.jupiter.api.Assertions;

/**
 * C-R02 (specs/self-tests.md section 1c, T-M8-10): the same required pack, but the server pushes a sha1 that is not the
 * file's. The client accepts, downloads from the harness source, finds the mismatch and reports it: the record names both
 * hashes and the client sends {@code FAILED_DOWNLOAD}, as a vanilla client does. The server's view is read as in C-R01, from
 * the same wrong pack pushed again in play. A cell without RESOURCE_PACKS skips with the capability named.
 */
@Proofstone(plugins = "build/fixture-plugins/*.jar")
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@ServerResourcePack(value = "build/resourcepacks/proofstone-e2e.zip", required = true,
        sha1 = CR02ResourcePackWrongHashTest.WRONG)
@ResourcePackAnswer(Answer.ACCEPT)
class CR02ResourcePackWrongHashTest {

    static final String WRONG = "0000000000000000000000000000000000000000";
    private static final List<String> FAILED = List.of("ACCEPTED", "FAILED_DOWNLOAD");

    @CellTest
    void aWrongHashIsReportedAndAnsweredWithAFailedDownload(Agent agent, Client client) {
        ResourcePackPush push = client.resourcePacks().get(0);
        Assertions.assertEquals(WRONG, push.hash());
        Assertions.assertEquals(FAILED, push.statuses());
        Assertions.assertNotNull(push.problem(), "the mismatch is reported: " + push);
        Assertions.assertTrue(push.problem().startsWith("hash mismatch: expected " + WRONG + ", got "), push.problem());

        Assertions.assertEquals(FAILED, CR01ResourcePackTest.pushInPlay(agent, client, WRONG, "FAILED_DOWNLOAD"),
                "the server's view of the answer");
        Assertions.assertTrue(client.resourcePacks().get(1).problem().startsWith("hash mismatch"),
                "the play-phase push is checked too: " + client.resourcePacks().get(1));
    }
}
