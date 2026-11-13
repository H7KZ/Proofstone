package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** What T00 says when the proxy cannot reach a backend (T-M7-13): the container and the hop, never just a timeout. */
class TopologyConformanceT00Test {

    @Test
    void anOfflineBackendIsNamedWithItsContainerAndTheProxyToBackendHop() {
        String message = TopologyConformance.unreachable(List.of("lobby online", "survival offline"),
                List.of("lobby", "survival"));

        assertThat(message).contains("backend survival is offline at the proxy")
                .contains("container server-survival")
                .contains("hop: proxy to backend survival")
                .doesNotContain("lobby is offline");
    }

    @Test
    void aBackendTheProxyDoesNotListAtAllIsNamedToo() {
        String message = TopologyConformance.unreachable(List.of("lobby online"), List.of("lobby", "survival"));

        assertThat(message).contains("backend survival is not listed by the proxy")
                .contains("hop: proxy to backend survival");
    }

    @Test
    void withNoReadingThereIsNothingToNameAndTheMessageSaysSo() {
        assertThat(TopologyConformance.unreachable(null, List.of("lobby")))
                .contains("no reading of the proxy's server list");
    }
}
