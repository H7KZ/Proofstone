package dev.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.proofstone.api.Capability;
import dev.proofstone.api.Requires;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The capability rules of one cell (test-api spec section 10), without a server or JUnit. */
class CapabilityModelTest {

    @Requires(Capability.BUKKIT_EVENTS)
    static class ClassNeedsEvents {
        void plain() {
        }

        @Requires(Capability.NMS)
        void methodNeedsNms() {
        }
    }

    static class MethodOnly {
        @Requires({Capability.CONSOLE, Capability.DATAPACKS})
        void needsTwo() {
        }
    }

    @Test
    void aCellHoldsOnlyWhatItsPackDeclaresAndTheAgentReports() {
        // Paper declares NMS, DATAPACKS, BUKKIT_EVENTS and CONSOLE; the agent confirms only the tokens events and command.
        assertThat(CapabilityModel.cellCapabilities("paper", List.of("command", "players", "events", "ticks")))
                .containsExactlyInAnyOrder(Capability.BUKKIT_EVENTS, Capability.CONSOLE);
    }

    @Test
    void anAgentTokenWithoutADeclaredCapabilityHoldsNothing() {
        assertThat(CapabilityModel.cellCapabilities("paper", List.of("players", "world"))).isEmpty();
    }

    @Test
    void aPlatformWithNoPackDeclaresNothing() {
        // Every Bukkit-family pack declares its capabilities now, so the platform with no pack entry is velocity here.
        assertThat(CapabilityModel.cellCapabilities("velocity", List.of("command", "events", "regions"))).isEmpty();
    }

    @Test
    void spigotHoldsOnlyWhatItsPackDeclares() {
        assertThat(CapabilityModel.cellCapabilities("spigot", List.of("command", "events", "regions")))
                .containsExactlyInAnyOrder(Capability.BUKKIT_EVENTS, Capability.CONSOLE);
    }

    @Test
    void foliaHoldsFoliaRegionsOnlyWhenTheAgentReportsRegions() {
        assertThat(CapabilityModel.cellCapabilities("folia", List.of("command", "events", "regions")))
                .containsExactlyInAnyOrder(Capability.FOLIA_REGIONS, Capability.BUKKIT_EVENTS, Capability.CONSOLE);
        assertThat(CapabilityModel.cellCapabilities("folia", List.of("command", "events")))
                .containsExactlyInAnyOrder(Capability.BUKKIT_EVENTS, Capability.CONSOLE);
    }

    @Test
    void simulatedBedrockIsHeldOnNativePaperOnly() {
        List<String> tokens = List.of("command", "events", "bedrock-prefix");
        assertThat(CapabilityModel.cellCapabilities("paper", tokens))
                .contains(Capability.SIMULATED_BEDROCK);
        // A prefixed login through ViaProxy stalls (paper@1.21.11, T-M5-06), so a bridged cell does not hold it.
        assertThat(CapabilityModel.cellCapabilities("paper", tokens, true))
                .containsExactlyInAnyOrder(Capability.BUKKIT_EVENTS, Capability.CONSOLE);
    }

    @Test
    void simulatedBedrockNeedsTheAgentTokenAndADeclaringPack() {
        // The offline token is missing (online server): the pack declares it, but no cell holds it.
        assertThat(CapabilityModel.cellCapabilities("paper", List.of("command", "events")))
                .doesNotContain(Capability.SIMULATED_BEDROCK);
        // Purpur and Spigot do not declare it (no conformance run has verified a prefixed login there).
        assertThat(CapabilityModel.cellCapabilities("purpur", List.of("command", "events", "bedrock-prefix")))
                .doesNotContain(Capability.SIMULATED_BEDROCK);
    }

    @Test
    void versionStringsNeverImplyACapability() {
        assertThat(CapabilityModel.cellCapabilities("paper", List.of())).isEmpty();
    }

    @Test
    void anAgentWithoutACapabilityListIsAnError() {
        assertThatThrownBy(() -> CapabilityModel.cellCapabilities("paper", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("capability list");
    }

    @Test
    void theClassAndTheMethodRequirementsAreAddedTogether() throws NoSuchMethodException {
        Method plain = ClassNeedsEvents.class.getDeclaredMethod("plain");
        Method needsNms = ClassNeedsEvents.class.getDeclaredMethod("methodNeedsNms");
        assertThat(CapabilityModel.required(ClassNeedsEvents.class, plain))
                .containsExactly(Capability.BUKKIT_EVENTS);
        assertThat(CapabilityModel.required(ClassNeedsEvents.class, needsNms))
                .containsExactlyInAnyOrder(Capability.BUKKIT_EVENTS, Capability.NMS);
    }

    @Test
    void aTestWithoutRequiresNeedsNothing() throws NoSuchMethodException {
        Method plain = ClassNeedsEvents.class.getDeclaredMethod("plain");
        assertThat(CapabilityModel.required(Object.class, plain)).isEmpty();
    }

    @Test
    void aSkipNamesEveryMissingCapabilityOnTheCell() throws NoSuchMethodException {
        Set<Capability> required = CapabilityModel.required(MethodOnly.class,
                MethodOnly.class.getDeclaredMethod("needsTwo"));
        assertThat(CapabilityModel.missingReason(required, Set.of(Capability.CONSOLE), "paper@26.3"))
                .contains("missing capability DATAPACKS on cell paper@26.3");
        assertThat(CapabilityModel.missingReason(required, Set.of(), "paper@26.3"))
                .contains("missing capability CONSOLE, DATAPACKS on cell paper@26.3");
    }

    @Test
    void aHeldRequirementGivesNoSkip() {
        assertThat(CapabilityModel.missingReason(Set.of(Capability.CONSOLE), Set.of(Capability.CONSOLE),
                "paper@26.3")).isEmpty();
        assertThat(CapabilityModel.missingReason(Set.of(), Set.of(), "paper@26.3")).isEmpty();
    }
}
