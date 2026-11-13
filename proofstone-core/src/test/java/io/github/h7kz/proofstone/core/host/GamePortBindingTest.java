package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.Test;

/** The test-only exception to ADR-0016 section 8: a networked server's game port is bound to loopback, never to all addresses. */
class GamePortBindingTest {

    @Test
    void theGamePortIsBoundToLoopbackOnARandomHostPort() {
        HostConfig config = PaperHost.loopbackOnly(HostConfig.newHostConfig().withPublishAllPorts(true),
                new ExposedPort[] {ExposedPort.tcp(PaperHost.GAME_PORT)});

        assertThat(config.getPublishAllPorts()).as("publish-all would bind every address").isFalse();
        Ports.Binding[] bound = config.getPortBindings().getBindings().get(ExposedPort.tcp(PaperHost.GAME_PORT));
        assertThat(bound).hasSize(1);
        assertThat(bound[0].getHostIp()).isEqualTo("127.0.0.1");
        assertThat(Integer.parseInt(bound[0].getHostPortSpec())).as("a concrete free loopback port").isPositive();
    }

    @Test
    void otherBindingsAreKeptAndNothingIsBoundToAllAddresses() {
        Ports existing = new Ports();
        existing.bind(ExposedPort.tcp(25580), Ports.Binding.bindIp("127.0.0.1"));

        HostConfig config = PaperHost.loopbackOnly(HostConfig.newHostConfig().withPortBindings(existing),
                new ExposedPort[] {ExposedPort.tcp(PaperHost.GAME_PORT)});

        assertThat(config.getPortBindings().getBindings()).containsKeys(ExposedPort.tcp(25580),
                ExposedPort.tcp(PaperHost.GAME_PORT));
        config.getPortBindings().getBindings().values().forEach(all -> {
            for (Ports.Binding binding : all) {
                assertThat(binding.getHostIp()).isEqualTo("127.0.0.1");
            }
        });
    }
}
