package dev.proofstone.examples.badtests.differential;

import dev.proofstone.api.Agent;
import dev.proofstone.api.CellTest;
import dev.proofstone.api.Client;
import dev.proofstone.api.DualAssert;
import dev.proofstone.api.Item;
import dev.proofstone.api.Matrix;
import dev.proofstone.api.Platform;
import dev.proofstone.api.Proofstone;

/**
 * A deliberately disagreeing test (T-M4-06, self-tests section 3). The reference driver is told to report one extra
 * point of damage on the player inventory (driver-fault injection, {@code proofstone.injectDriverFault}). The test
 * asserts only type and amount, which both drivers report correctly, so both dual-oracle assertions pass. Only the
 * differential comparator sees the difference, so the verdict is {@code DISAGREE}, with both transcripts attached.
 *
 * <p>It runs only in a differential run on the reference driver's native row 26.1.2: the bad-tests build excludes this
 * package otherwise, and sets the fault flag for the differential run. It stays out of normal suites.
 */
@Proofstone
@Matrix(platform = Platform.PAPER, versions = {"26.1.2"})
class DriverFaultDisagreeE2E {

    @CellTest
    void theDriversDisagreeOnTheDamageOfAGivenSword(Agent agent, Client client) {
        agent.player(client).give(Item.of("diamond_sword").name("Blade"));
        DualAssert.item(agent.player(client), client).inInventory(item -> item.is("diamond_sword"))
                .amount(1).name("Blade");
    }
}
