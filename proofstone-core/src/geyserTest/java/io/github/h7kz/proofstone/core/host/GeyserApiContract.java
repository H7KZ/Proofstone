package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.api.Capability;
import io.github.h7kz.proofstone.api.Cell;
import io.github.h7kz.proofstone.api.CellTest;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.api.Clients;
import io.github.h7kz.proofstone.api.DualAssert;
import io.github.h7kz.proofstone.api.Item;
import io.github.h7kz.proofstone.api.Matrix;
import io.github.h7kz.proofstone.api.Platform;
import io.github.h7kz.proofstone.api.Proofstone;
import io.github.h7kz.proofstone.api.Requires;

/**
 * T-M9-07 on a real Geyser cell, through the public API only: {@code Platform.GEYSER}, {@code Capability.BEDROCK_CLIENT}
 * and {@code Clients.bedrock}, run by the JUnit extension as a plugin author would. The cell is named by
 * {@code -Dproofstone.cells=geyser@2.12.1+paper@26.3}: its row is experimental, so no default run boots it. One cell per
 * invocation (M9 flake rule); Bedrock join timeouts are counted apart, with the stalled stage named.
 *
 * <p>Run it alone, because {@code geyserTest} holds {@code GeyserCellIT} too:
 * {@code PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :proofstone-core:geyserTest
 * --tests '*GeyserApiContract' -Dproofstone.cells=geyser@2.12.1+paper@26.3 --no-parallel --max-workers=1}.
 */
@Proofstone
@Matrix(platform = Platform.GEYSER, versions = {"latest"})
@Requires(Capability.BEDROCK_CLIENT)
class GeyserApiContract {

    @CellTest
    void theCellHoldsTheBedrockClientAndIsOneNativeServer(Cell cell, Agent agent) {
        assertThat(cell.capabilities()).contains(Capability.BEDROCK_CLIENT, Capability.CONSOLE)
                .doesNotContain(Capability.PROXY, Capability.TOPOLOGY, Capability.SIMULATED_BEDROCK);
        assertThat(cell.platform()).isEqualTo("paper");
        assertThat(cell.bridged()).isFalse();
        assertThat(agent.capabilities()).contains("floodgate");
    }

    @CellTest
    void aBedrockClientJoinsUnderTheFloodgateNameAndBothOraclesAgreeOnItsInventory(Agent agent, Clients clients) {
        Client bedrock = clients.bedrock("PsBed1");

        assertThat(bedrock.name()).isEqualTo(".PsBed1");
        agent.player(bedrock).give(Item.of("minecraft:stick").amount(3));
        DualAssert.item(agent.player(bedrock), bedrock).inInventory(i -> i.is("stick")).amount(3);

        assertThatThrownBy(() -> bedrock.transferTo("lobby")).isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Bedrock").hasMessageContaining("ADR-0018");
        assertThatThrownBy(() -> clients.bedrock(".PsBed2")).isInstanceOf(IllegalArgumentException.class);
    }
}
