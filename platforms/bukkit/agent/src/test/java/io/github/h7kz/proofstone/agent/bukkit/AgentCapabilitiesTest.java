package io.github.h7kz.proofstone.agent.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The tokens the Bukkit agent reports (test-api section 10, ADR-0017 section 8). */
class AgentCapabilitiesTest {

    @Test
    void datapacksIsReportedOnlyWhereTheServerApiListsPacks() {
        assertThat(ProofstoneAgentPlugin.capabilities("paper", true, true)).contains("datapacks");
        assertThat(ProofstoneAgentPlugin.capabilities("paper", true, false)).doesNotContain("datapacks");
        assertThat(ProofstoneAgentPlugin.capabilities("paper", true)).doesNotContain("datapacks");
    }

    @Test
    void resourcePackStatusIsReportedOnlyWhereTheStatusEventExistsAfterDatapacksAndBeforeRegions() {
        assertThat(ProofstoneAgentPlugin.capabilities("paper", false, true, true))
                .endsWith("bedrock-prefix", "datapacks", "resource-pack-status");
        assertThat(ProofstoneAgentPlugin.capabilities("folia", true, true, true)).endsWith("resource-pack-status", "regions");
        assertThat(ProofstoneAgentPlugin.capabilities("paper", true, true, false)).doesNotContain("resource-pack-status");
        assertThat(ProofstoneAgentPlugin.capabilities("paper", true, true)).doesNotContain("resource-pack-status");
    }

    @Test
    void pdcIsReportedOnlyWhereTheServerHasPersistentData() {
        assertThat(ProofstoneAgentPlugin.capabilities("paper", true, true, true, true)).contains("pdc");
        assertThat(ProofstoneAgentPlugin.capabilities("paper", true, true, true, false)).doesNotContain("pdc");
        assertThat(ProofstoneAgentPlugin.capabilities("paper", true, true, true)).doesNotContain("pdc");
        assertThat(ProofstoneAgentPlugin.capabilities("folia", true, true, true, true)).endsWith("pdc", "regions");
    }
}
