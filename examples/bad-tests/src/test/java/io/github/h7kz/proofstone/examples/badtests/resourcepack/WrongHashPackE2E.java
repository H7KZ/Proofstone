package io.github.h7kz.proofstone.examples.badtests.resourcepack;

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
 * A test that expects its resource pack to load, on a server that pushes a sha1 that is not the pack's (T-M8-11). The
 * client accepts, downloads the pack, finds the mismatch and sends {@code FAILED_DOWNLOAD}; the driver records both hashes.
 * On Paper the client still reaches play (T-M8-10 finding), so the join succeeds and nothing kicks: the fault shows only in
 * the pack's record, which is where this test looks. It must be a plain {@code FAIL} whose message carries the
 * {@code FAILED_DOWNLOAD} status and the hash mismatch, never a join timeout. Excluded from every suite except
 * {@code metaModTest}.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"26.3"})
@ServerResourcePack(value = "build/resourcepacks/wrong-hash.zip", required = true, sha1 = WrongHashPackE2E.WRONG)
@ResourcePackAnswer(Answer.ACCEPT)
class WrongHashPackE2E {

    static final String WRONG = "0000000000000000000000000000000000000000";

    @CellTest
    void aPackThatFailsItsHashCheckIsNotLoaded(Client client) {
        ResourcePackPush push = client.resourcePacks().get(0);
        Assertions.assertEquals(List.of("ACCEPTED", "DOWNLOADED", "SUCCESSFULLY_LOADED"), push.statuses(),
                "the pack must load, and the driver reported: " + push);
    }
}
