package io.github.h7kz.proofstone.agent.fabric;

import io.github.h7kz.proofstone.agent.log.LogBuffer;
import io.github.h7kz.proofstone.agent.server.AgentConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * The server entrypoint (fabric.mod.json). At mod init it reads the config (a missing token fails the boot loudly),
 * starts the log capture and registers three Fabric API lifecycle listeners, each last and each returning nothing: the
 * tick counter, the start of the HTTP server once the server has started, and its stop. Nothing else: no command, no
 * mixin, no listener that can change a result (AGENTS.md section 3, ADR-0017 section 5).
 */
public final class ProofstoneFabricAgent implements DedicatedServerModInitializer {

    static final String MOD_ID = "proofstone-agent";

    private FabricAgent agent;

    @Override
    public void onInitializeServer() {
        AgentConfig config = AgentConfig.fromEnvironment(System.getenv());
        LogBuffer logs = LogBuffer.attachPreferringLog4j();
        TickCounter ticks = new TickCounter();
        FabricEventProbes probes = new FabricEventProbes(ticks::current);
        FabricEventProbes.registerLast(ServerTickEvents.END_SERVER_TICK, server -> ticks.tick());
        FabricEventProbes.registerLast(ServerLifecycleEvents.SERVER_STARTED,
                server -> start(server, config, probes, logs, ticks));
        FabricEventProbes.registerLast(ServerLifecycleEvents.SERVER_STOPPING, server -> stop(logs));
    }

    private void start(MinecraftServer server, AgentConfig config, FabricEventProbes probes, LogBuffer logs,
            TickCounter ticks) {
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElseThrow(() -> new IllegalStateException(MOD_ID + " is not in the loader's mod list"));
        try {
            agent = FabricAgent.start(config, server, new MinecraftView(server), probes, logs, ticks, version,
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
