package dev.proofstone.agent.server;

import dev.proofstone.agent.game.Game;
import dev.proofstone.agent.protocol.AgentException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canned game state shaped like the golden cases; records what the server asked for. */
final class FakeGame implements Game {

    String lastCommand;
    long lastTimeoutMs = -1;

    @Override
    public String command(String command, long timeoutMs) {
        lastCommand = command;
        lastTimeoutMs = timeoutMs;
        return "Server version 1.8.8";
    }

    @Override
    public List<String> players(long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        return Collections.singletonList("Alex");
    }

    @Override
    public Map<String, Object> player(String name, long timeoutMs) {
        lastTimeoutMs = timeoutMs;
        if (!"Alex".equals(name)) {
            throw new AgentException(404, "PLAYER_NOT_FOUND", "no player named " + name,
                    Collections.singletonMap("name", name));
        }
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("world", "world");
        location.put("x", 0.5);
        location.put("y", 64.0);
        location.put("z", 0.5);
        location.put("yaw", 0.0);
        location.put("pitch", 0.0);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", "Alex");
        state.put("uuid", "8a2c1f0e-6b1d-4f7e-9c3a-2d5e7b9a1c40");
        state.put("level", 0);
        state.put("exp", 0.0);
        state.put("gamemode", "survival");
        state.put("health", 20.0);
        state.put("location", location);
        state.put("inventory", Collections.emptyMap());
        state.put("cursor", null);
        state.put("openWindow", null);
        return state;
    }

    static List<String> capabilities() {
        return Arrays.asList("command", "players");
    }
}
