package io.github.h7kz.proofstone.agent.neoforge;

import io.github.h7kz.proofstone.agent.log.LogBuffer;
import io.github.h7kz.proofstone.agent.server.AgentConfig;
import io.github.h7kz.proofstone.agent.vanilla.TickCounter;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The mod entrypoint ({@code META-INF/neoforge.mods.toml}), loaded on the dedicated server only. At construction it reads
 * the config (a missing token fails the boot loudly), starts the log capture and registers three listeners on the game
 * bus, each at {@code LOWEST} and each reading only: the tick counter, the start of the HTTP server once the server has
 * started ({@link ServerStartedEvent}, ADR-0017 section 5) and its stop. Nothing else: no command, no network payload
 * (so the vanilla client needs no mod in the configuration phase), no mixin, no listener on the mod bus, nothing that
 * cancels or decides an event (AGENTS.md section 3).
 */
@Mod(value = ProofstoneNeoForgeAgent.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class ProofstoneNeoForgeAgent {

    /** A NeoForge mod id allows no '-', so this is the Fabric agent's id with '_'. */
    static final String MOD_ID = "proofstone_agent";

    private NeoForgeAgent agent;

    /** FML passes the mod's own bus; the agent listens on nothing there. */
    public ProofstoneNeoForgeAgent(IEventBus modBus) {
        AgentConfig config = AgentConfig.fromEnvironment(System.getenv());
        LogBuffer logs = LogBuffer.attachPreferringLog4j();
        TickCounter ticks = new TickCounter();
        IEventBus game = NeoForge.EVENT_BUS;
        NeoForgeEventProbes probes = new NeoForgeEventProbes(game, ticks::current);
        NeoForgeEventProbes.registerLast(game, ServerTickEvent.Post.class, event -> ticks.tick());
        NeoForgeEventProbes.registerLast(game, ServerStartedEvent.class,
                event -> start(event.getServer(), config, probes, logs, ticks));
        NeoForgeEventProbes.registerLast(game, ServerStoppingEvent.class, event -> stop(logs));
    }

    private void start(MinecraftServer server, AgentConfig config, NeoForgeEventProbes probes, LogBuffer logs,
            TickCounter ticks) {
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(mod -> mod.getModInfo().getVersion().toString())
                .orElseThrow(() -> new IllegalStateException(MOD_ID + " is not in the loader's mod list"));
        try {
            agent = NeoForgeAgent.start(config, server, new NeoForgeMinecraftView(server), probes, logs, ticks, version,
                    server.getServerVersion());
        } catch (IOException e) {
            throw new UncheckedIOException("Proofstone agent could not open port " + config.port(), e);
        }
    }

    private void stop(LogBuffer logs) {
        if (agent != null) {
            agent.close();
            agent = null;
        }
        logs.detach();
    }
}
