package dev.proofstone.core.junit;

import dev.proofstone.api.Capability;
import dev.proofstone.api.Requires;
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
            "paper", Set.of(Capability.NMS, Capability.DATAPACKS, Capability.BUKKIT_EVENTS, Capability.CONSOLE),
            "folia", Set.of(Capability.FOLIA_REGIONS, Capability.BUKKIT_EVENTS, Capability.CONSOLE),
            // Purpur declares only what its conformance run verified (docs/platforms/purpur.md), not Paper's NMS.
            "purpur", Set.of(Capability.BUKKIT_EVENTS, Capability.CONSOLE));

    /**
     * The agent's report token that confirms a declared capability (docs/platforms/paper.md and folia.md, Gotchas). A
     * capability with no token is never held, until the agent reports one.
     */
    private static final Map<Capability, String> AGENT_TOKEN = Map.of(
            Capability.BUKKIT_EVENTS, "events",
            Capability.CONSOLE, "command",
            Capability.FOLIA_REGIONS, "regions");

    private CapabilityModel() {
    }

    /**
     * The capabilities a cell holds: the declared ones whose agent token the agent reported.
     *
     * @param agentReported the agent's {@code /v1/health} capability tokens
     * @throws IllegalStateException when the agent reported no list at all, which is not the same as an empty one
     */
    static Set<Capability> cellCapabilities(String platform, List<String> agentReported) {
        if (agentReported == null) {
            throw new IllegalStateException("the agent reported no capability list for " + platform
                    + ": a missing list is an error, not an empty one");
        }
        EnumSet<Capability> held = EnumSet.noneOf(Capability.class);
        for (Capability capability : DECLARED.getOrDefault(platform, Set.of())) {
            String token = AGENT_TOKEN.get(capability);
            if (token != null && agentReported.contains(token)) {
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
