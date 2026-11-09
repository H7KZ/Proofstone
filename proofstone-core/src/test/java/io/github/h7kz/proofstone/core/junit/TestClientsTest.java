package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestClientsTest {

    @Test
    void acceptsVanillaNamesAndOnePrefixDotButNothingElse() {
        TestClients clients = new TestClients(name -> new SessionClient(name, null));
        for (String ok : List.of(".BedrockBot", "Steve", "a_1", ".x", "T1234abcd")) {
            assertThat(clients.create(ok).name()).isEqualTo(ok);
        }
        for (String bad : List.of("", "..Bot", "Bot.Name", "Steve Smith", "Sixteen_Characters_1", ".Sixteen_Characters1",
                " Steve")) {
            assertThatThrownBy(() -> clients.create(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> clients.create(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theNameIsRejectedBeforeAnythingConnects() {
        List<String> connected = new ArrayList<>();
        TestClients clients = new TestClients(name -> {
            connected.add(name);
            return new SessionClient(name, null);
        });
        assertThatThrownBy(() -> clients.create("bad name")).isInstanceOf(IllegalArgumentException.class);
        assertThat(connected).isEmpty();
    }

    @Test
    void closeAllRunsEveryCloseAndReportsTheFirstFailure() {
        TestClients clients = new TestClients(name -> new SessionClient(name, failingSession(name)));
        Client one = clients.create(".One");
        clients.create(".Two");
        RuntimeException failure = clients.closeAll();
        assertThat(failure).hasMessageContaining(".One");
        assertThat(failure.getSuppressed()).hasSize(1);
        assertThat(one.name()).isEqualTo(".One");
    }

    /** A session whose close throws, to prove every close still runs. */
    private static ClientSession failingSession(String name) {
        return (ClientSession) java.lang.reflect.Proxy.newProxyInstance(
                ClientSession.class.getClassLoader(), new Class<?>[] {ClientSession.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("close")) {
                        throw new IllegalStateException("close failed for " + name);
                    }
                    return null;
                });
    }
}
