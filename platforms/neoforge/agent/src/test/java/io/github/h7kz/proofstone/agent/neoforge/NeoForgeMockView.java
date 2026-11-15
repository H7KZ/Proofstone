package io.github.h7kz.proofstone.agent.neoforge;

import io.github.h7kz.proofstone.agent.vanilla.MockView;

/** The shared in-memory server plus the NeoForge row's mod list. */
final class NeoForgeMockView extends MockView {

    NeoForgeMockView() {
        // The neoforge@26.3 row has no mod of its own in /mods: the agent is the only one.
        mods.add(mod(ProofstoneNeoForgeAgent.MOD_ID, "0.1.0"));
    }
}
