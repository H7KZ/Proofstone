package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.api.Capability;
import io.github.h7kz.proofstone.api.Client;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    /** A Geyser cell's Bedrock join, recording what it was asked. */
    private static final class FakeBedrock implements TestClients.Bedrock {
        final List<String> joined = new ArrayList<>();
        final List<String> gone = new ArrayList<>();
        private final boolean liveSession;

        FakeBedrock(boolean liveSession) {
            this.liveSession = liveSession;
        }

        @Override
        public SessionClient join(String name) {
            joined.add(name);
            return new SessionClient("." + name, liveSession ? noopSession() : null);
        }

        @Override
        public void awaitGone(String serverName) {
            gone.add(serverName);
        }
    }

    private static TestClients geyser(FakeBedrock bedrock) {
        return new TestClients(name -> new SessionClient(name, null), "geyser@2.12.1+paper@26.3",
                Set.of(Capability.BEDROCK_CLIENT), bedrock);
    }

    @Test
    void aBedrockClientReportsTheFloodgateNameAndIsAwaitedOnTheAgentAfterItClosed() {
        FakeBedrock bedrock = new FakeBedrock(true);
        TestClients clients = geyser(bedrock);
        assertThat(clients.bedrock("PsBed1").name()).isEqualTo(".PsBed1");
        assertThat(bedrock.joined).containsExactly("PsBed1");
        assertThat(clients.names()).containsExactly(".PsBed1");
        assertThat(bedrock.gone).as("not before the close").isEmpty();
        assertThat(clients.closeAll()).isNull();
        assertThat(bedrock.gone).containsExactly(".PsBed1");
    }

    @Test
    void aSessionThatCannotCloseIsNotAwaitedButIsReported() {
        FakeBedrock bedrock = new FakeBedrock(false);
        TestClients clients = geyser(bedrock);
        clients.bedrock("PsBed1");
        assertThat(clients.closeAll()).hasMessageContaining(".PsBed1");
        assertThat(bedrock.gone).isEmpty();
    }

    @Test
    void aBedrockNameIsValidatedBeforeAnythingConnects() {
        FakeBedrock bedrock = new FakeBedrock(true);
        TestClients clients = geyser(bedrock);
        for (String bad : List.of("", ".PsBed1", "Sixteen_Chars_16", "Ps Bed", "Ps.Bed", "Na\u00efve")) {
            assertThatThrownBy(() -> clients.bedrock(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> clients.bedrock(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(bedrock.joined).isEmpty();
        assertThat(clients.bedrock("Fifteen_Cha_150").name()).isEqualTo(".Fifteen_Cha_150");
    }

    @Test
    void withoutTheCapabilityBedrockIsAnIllegalStateNamingTheCellAndTheCapability() {
        FakeBedrock bedrock = new FakeBedrock(true);
        // A Geyser cell whose Floodgate failed: the host exists, the capability does not.
        TestClients noFloodgate = new TestClients(name -> new SessionClient(name, null), "geyser@2.12.1+paper@26.3",
                Set.of(Capability.CONSOLE), bedrock);
        assertThatThrownBy(() -> noFloodgate.bedrock("PsBed1")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("geyser@2.12.1+paper@26.3").hasMessageContaining("BEDROCK_CLIENT");
        // A Paper cell: no Bedrock host at all, whatever the capability set says.
        TestClients paper = new TestClients(name -> new SessionClient(name, null), "paper@26.3",
                Set.of(Capability.BEDROCK_CLIENT), null);
        assertThatThrownBy(() -> paper.bedrock("PsBed1")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("paper@26.3").hasMessageContaining("BEDROCK_CLIENT");
        assertThatThrownBy(() -> new TestClients(name -> new SessionClient(name, null)).bedrock("PsBed1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("BEDROCK_CLIENT");
        assertThat(bedrock.joined).isEmpty();
    }

    private static ClientSession noopSession() {
        return (ClientSession) java.lang.reflect.Proxy.newProxyInstance(
                ClientSession.class.getClassLoader(), new Class<?>[] {ClientSession.class}, (proxy, method, args) -> null);
    }
}
