package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.DisagreeException;
import io.github.h7kz.proofstone.core.await.Await;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * The dual oracle of {@code Client.transferTo} (ADR-0016 sections 5 and 6). The client oracle is the switch the driver
 * sees; the server oracle is split: the proxy agent says which backend the player is on, each backend agent lists its
 * own players. A switch passes when the client saw it, the proxy agent says the target, the target lists the player and
 * the source does not. Everything is awaited (ADR-0007); nothing is re-sent.
 */
final class ServerSwitch {

    /** What the agents say, read fresh on every call. Either may throw when its agent does not answer. */
    interface Agents {
        /** The proxy agent's current server of the player, or {@code null} when the proxy does not list the player. */
        String proxyServer(String player);

        /** Whether the backend's agent lists the player online. */
        boolean lists(String backend, String player);
    }

    /** The client side: type the command, then wait for the switch to complete (or throw with its stage and hop). */
    interface ClientSide {
        void type(String command);

        void awaitSwitch();
    }

    /** One reading of every agent; a value is {@code null} when that agent did not answer, with the reason in errors. */
    record View(String proxy, Map<String, Boolean> lists, Map<String, String> errors) {
        boolean shows(String target, String source) {
            return errors.isEmpty() && target.equals(proxy) && Boolean.TRUE.equals(lists.get(target))
                    && Boolean.FALSE.equals(lists.get(source));
        }

        String describe() {
            StringJoiner text = new StringJoiner("; ");
            text.add("proxy agent says server=" + (errors.containsKey("proxy") ? "no answer (" + errors.get("proxy") + ")"
                    : proxy));
            lists.forEach((backend, listed) -> text.add("backend " + backend + " agent "
                    + (listed == null ? "no answer (" + errors.get(backend) + ")"
                            : listed ? "lists the player" : "does not list the player")));
            return text.toString();
        }
    }

    private ServerSwitch() {
    }

    /**
     * Moves {@code player} to {@code target}.
     *
     * @param backends the topology's backends, in order
     * @throws IllegalArgumentException before anything is sent: an unknown target, or the backend the player is on
     */
    static void transfer(String player, String target, List<String> backends, Agents agents, ClientSide client,
            Duration timeout) {
        if (!backends.contains(target)) {
            throw new IllegalArgumentException("transferTo(\"" + target + "\"): no backend of that name; backends are "
                    + backends);
        }
        String source;
        try {
            source = agents.proxyServer(player);
        } catch (RuntimeException e) {
            throw new AssertionError("ORACLE MISSING transferTo(" + target + ") of " + player
                    + ": the proxy agent did not answer before the switch: " + e.getMessage(), e);
        }
        if (source == null || !backends.contains(source)) {
            throw new IllegalStateException("transferTo(" + target + ") of " + player + ": the proxy agent says the "
                    + "player is on " + source + ", which is not a backend of this topology " + backends);
        }
        if (source.equals(target)) {
            throw new IllegalArgumentException("transferTo(\"" + target + "\"): " + player + " is already on " + target);
        }
        client.type("server " + target);
        // Client oracle first: a timeout here carries the stalled stage and the hop, and is never retried.
        client.awaitSwitch();
        View last;
        try {
            Await.until(player + " on " + target + " by every agent", () -> read(player, agents, source, target),
                    view -> view.shows(target, source), timeout);
            return;
        } catch (AwaitTimeoutException e) {
            last = e.observations().isEmpty() ? null : (View) e.observations().get(0).lastValue();
        }
        throw judge(player, source, target, last);
    }

    /** Reads every agent once; an agent that throws is recorded, so a missing oracle is never read as a value. */
    static View read(String player, Agents agents, String source, String target) {
        Map<String, String> errors = new LinkedHashMap<>();
        String proxy = null;
        try {
            proxy = agents.proxyServer(player);
        } catch (RuntimeException e) {
            errors.put("proxy", String.valueOf(e.getMessage()));
        }
        Map<String, Boolean> lists = new LinkedHashMap<>();
        for (String backend : List.of(source, target)) {
            try {
                lists.put(backend, agents.lists(backend, player));
            } catch (RuntimeException e) {
                lists.put(backend, null);
                errors.put(backend, String.valueOf(e.getMessage()));
            }
        }
        return new View(proxy, lists, errors);
    }

    /**
     * The verdict once the agents did not agree in time, after the client saw the switch. An agent that never answered
     * is ORACLE MISSING. The proxy on the old server, or agents that contradict each other, is DISAGREE. No server oracle
     * showing the target at all (the proxy says a third server and the target does not list the player) is FAIL.
     */
    static AssertionError judge(String player, String source, String target, View last) {
        String head = "transferTo(" + target + ") of " + player + " from " + source + ": the client saw a server switch; ";
        if (last == null || !last.errors().isEmpty()) {
            return new AssertionError("ORACLE MISSING " + head + (last == null ? "no agent reading" : last.describe()));
        }
        boolean proxyOnTarget = target.equals(last.proxy());
        boolean targetLists = Boolean.TRUE.equals(last.lists().get(target));
        if (!proxyOnTarget && !targetLists && !source.equals(last.proxy())) {
            return new AssertionError("FAIL " + head + "no agent shows the player on " + target + ": "
                    + last.describe());
        }
        return new DisagreeException("DISAGREE " + head + last.describe(), last.describe(),
                "server switch seen (new join completed)");
    }
}
