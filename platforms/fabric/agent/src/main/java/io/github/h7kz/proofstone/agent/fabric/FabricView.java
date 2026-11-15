package io.github.h7kz.proofstone.agent.fabric;

import io.github.h7kz.proofstone.agent.vanilla.GameView;
import java.util.Map;

/**
 * The shared vanilla {@link GameView} plus what only this loader's agent answers: the data packs, read from the server's
 * pack repository. {@link FabricMinecraftView} is the real server; the contract test uses a mock.
 */
interface FabricView extends GameView {

    /**
     * The server's data packs, {@code {enabled, available}} (datapacks.json): the pack repository's selected and available
     * ids in its order ({@code vanilla}, {@code file/x.zip}). Read-only; never reloads or selects a pack.
     */
    Map<String, Object> dataPacks();
}
