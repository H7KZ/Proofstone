package io.github.h7kz.proofstone.core.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The driver edition (ADR-0018 section 3): a driver never joins an endpoint of the other edition. */
class EditionTest {

    private static final ConnectSpec SPEC = new ConnectSpec("server", 25565, "Bot", Duration.ofSeconds(1));

    /** A driver that counts how often it was asked to connect; it never opens a socket. */
    private static final class Fake implements ClientDriver {
        final Edition edition;
        final AtomicInteger connects = new AtomicInteger();

        Fake(Edition edition) {
            this.edition = edition;
        }

        @Override
        public String id() {
            return "fake-" + edition.name().toLowerCase();
        }

        @Override
        public String protocolVersion() {
            return "26.3";
        }

        @Override
        public Edition edition() {
            return edition;
        }

        @Override
        public ClientSession connect(ConnectSpec spec) {
            connects.incrementAndGet();
            return null;
        }
    }

    @Test
    void theEditionsAreJavaAndBedrock() {
        assertThat(Edition.values()).containsExactly(Edition.JAVA, Edition.BEDROCK);
    }

    @Test
    void aDriverJoinsAnEndpointOfItsOwnEdition() {
        Fake java = new Fake(Edition.JAVA);
        Fake bedrock = new Fake(Edition.BEDROCK);

        java.connect(SPEC, Edition.JAVA);
        bedrock.connect(SPEC, Edition.BEDROCK);

        assertThat(java.connects).hasValue(1);
        assertThat(bedrock.connects).hasValue(1);
    }

    @Test
    void aJavaDriverOnABedrockEndpointIsRefusedNamingBothAndNeverConnects() {
        Fake java = new Fake(Edition.JAVA);

        assertThatThrownBy(() -> java.connect(SPEC, Edition.BEDROCK))
                .isInstanceOf(ClientDriverException.class)
                .hasMessageContainingAll("fake-java", "JAVA", "BEDROCK", "server:25565");
        assertThat(java.connects).hasValue(0);
    }

    @Test
    void aBedrockDriverOnAJavaEndpointIsRefusedNamingBoth() {
        Fake bedrock = new Fake(Edition.BEDROCK);

        assertThatThrownBy(() -> bedrock.connect(SPEC, Edition.JAVA))
                .isInstanceOf(ClientDriverException.class)
                .hasMessageContainingAll("fake-bedrock", "BEDROCK", "JAVA", "server:25565");
        assertThat(bedrock.connects).hasValue(0);
    }
}
