package io.github.h7kz.proofstone.agent.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;

/** A Velocity-shaped event for the probe tests: getters of every kind a probe serialises, and one setter it must never call. */
public final class SampleEvent {

    private final Player player;
    private final RegisteredServer server;
    private RegisteredServer chosen;
    int setterCalls;

    public SampleEvent(Player player, RegisteredServer server, RegisteredServer chosen) {
        this.player = player;
        this.server = server;
        this.chosen = chosen;
    }

    public Player getPlayer() {
        return player;
    }

    public RegisteredServer getServer() {
        return server;
    }

    public Optional<RegisteredServer> getInitialServer() {
        return Optional.ofNullable(chosen);
    }

    public Optional<RegisteredServer> getNothing() {
        return Optional.empty();
    }

    public String getReason() {
        return "because";
    }

    public int getCount() {
        return 3;
    }

    public boolean getFlag() {
        return true;
    }

    public java.util.UUID getId() {
        return java.util.UUID.fromString("00000000-0000-4000-8000-000000000002");
    }

    public String getBroken() {
        throw new IllegalStateException("not today");
    }

    /** A mutator: the probe must never call it, or the event would change (observe-only rule). */
    public void setInitialServer(RegisteredServer other) {
        setterCalls++;
        chosen = other;
    }

    RegisteredServer chosen() {
        return chosen;
    }
}
