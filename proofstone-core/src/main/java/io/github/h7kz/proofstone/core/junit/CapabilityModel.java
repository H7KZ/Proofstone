package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.api.Capability;
import io.github.h7kz.proofstone.api.Requires;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which capabilities a cell holds, and which ones a test requires (test-api spec section 10). A cell holds a capability
 * only when its platform pack declares it and the agent reports the token that confirms it. Versions never imply one.
 */
final class CapabilityModel {

    /**
     * What each platform pack declares, from its Capabilities section in docs/platforms. A platform without an entry
     * declares nothing, so every requirement on its cells is a skip with its reason, never a pass.
     */
    private static final Map<String, Set<Capability>> DECLARED = Map.of(
            "folia", Set.of(Capability.FOLIA_REGIONS, Capability.BUKKIT_EVENTS, Capability.CONSOLE),
            // Purpur declares only what its conformance run verified (docs/platforms/purpur.md).
            "purpur", Set.of(Capability.BUKKIT_EVENTS, Capability.CONSOLE),
            // Spigot declares only what its conformance run verified (docs/platforms/spigot.md), like Purpur.
            "spigot", Set.of(Capability.BUKKIT_EVENTS, Capability.CONSOLE),
            // Paper's offline login accepts a '.' prefixed name with no setting changed (docs/platforms/paper.md, verified).
            "paper", Set.of(Capability.BUKKIT_EVENTS, Capability.CONSOLE, Capability.SIMULATED_BEDROCK));

    /**
     * The agent's report token that confirms a declared capability (docs/platforms/paper.md and folia.md, Gotchas). A
     * capability with no token is never held, until the agent reports one.
     */
    private static final Map<Capability, String> AGENT_TOKEN = Map.of(
            Capability.BUKKIT_EVENTS, "events",
            Capability.CONSOLE, "command",
            Capability.FOLIA_REGIONS, "regions",
            // Reported only when the server runs offline, where a prefixed name can log in (Agent: bedrock-prefix).
            Capability.SIMULATED_BEDROCK, "bedrock-prefix");

    private CapabilityModel() {
    }

    /**
     * Bridged cells that do not hold SIMULATED_BEDROCK, even when declared and reported. A prefixed login through ViaProxy
     * stalls before the server sees it (observed on paper@1.21.11, T-M5-06, docs/platforms/paper.md), so the capability
     * is held on native cells only until a bridged row shows the login working.
     */
    private static final Set<Capability> NOT_HELD_BRIDGED = Set.of(Capability.SIMULATED_BEDROCK);

    /** The capabilities a native (unbridged) cell holds. */
    static Set<Capability> cellCapabilities(String platform, List<String> agentReported) {
        return cellCapabilities(platform, agentReported, false);
    }

    /**
     * The capabilities a cell holds: the declared ones whose agent token the agent reported, less those a bridged cell
     * cannot hold.
     *
     * @param agentReported the agent's {@code /v1/health} capability tokens
     * @param bridged whether the client reaches the server through ViaProxy
     * @throws IllegalStateException when the agent reported no list at all, which is not the same as an empty one
     */
    static Set<Capability> cellCapabilities(String platform, List<String> agentReported, boolean bridged) {
        if (agentReported == null) {
            throw new IllegalStateException("the agent reported no capability list for " + platform
                    + ": a missing list is an error, not an empty one");
        }
        EnumSet<Capability> held = EnumSet.noneOf(Capability.class);
        for (Capability capability : DECLARED.getOrDefault(platform, Set.of())) {
            String token = AGENT_TOKEN.get(capability);
            if (token != null && agentReported.contains(token) && !(bridged && NOT_HELD_BRIDGED.contains(capability))) {
                held.add(capability);
            }
        }
        return Collections.unmodifiableSet(held);
    }

    /** The capabilities a test needs: its class's {@link Requires} and its method's, added together. */
    static Set<Capability> required(Class<?> testClass, Method test) {
        EnumSet<Capability> required = EnumSet.noneOf(Capability.class);
        Requires onClass = testClass.getAnnotation(Requires.class);
        if (onClass != null) {
            required.addAll(List.of(onClass.value()));
        }
        Requires onMethod = test.getAnnotation(Requires.class);
        if (onMethod != null) {
            required.addAll(List.of(onMethod.value()));
        }
        return Collections.unmodifiableSet(required);
    }

    /**
     * The skip reason for a test on a cell that lacks some of its requirements, naming every missing capability in enum
     * order. Empty when the cell holds all of them.
     */
    static Optional<String> missingReason(Set<Capability> required, Set<Capability> held, String cellId) {
        String missing = required.stream().filter(capability -> !held.contains(capability))
                .sorted().map(Capability::name).collect(Collectors.joining(", "));
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("missing capability " + missing + " on cell " + cellId);
    }
}
