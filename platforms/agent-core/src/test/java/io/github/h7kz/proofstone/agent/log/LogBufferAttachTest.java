package io.github.h7kz.proofstone.agent.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * {@link LogBuffer#attachPreferringLog4j()} reads JUL itself only when the server has no Log4j2. This module's test
 * classpath has none, so the fallback is what runs here; the Log4j side (no JUL handler, no duplicate) is checked by the
 * Velocity agent's real-server test.
 */
class LogBufferAttachTest {

    @Test
    void withoutLog4jItFallsBackToTheJulRootLogger() {
        Logger root = Logger.getLogger("");
        int handlersBefore = root.getHandlers().length;
        LogBuffer buffer = LogBuffer.attachPreferringLog4j();
        try {
            assertThat(root.getHandlers()).hasSize(handlersBefore + 1);
            Logger.getLogger("proofstone.attach-test").log(Level.WARNING, "from jul", new IllegalStateException("x"));

            List<?> records = (List<?>) buffer.read(0, "WARN").get("records");
            assertThat(records).hasSize(1);
            assertThat(records.get(0).toString()).contains("from jul").contains("IllegalStateException");
        } finally {
            buffer.detach();
        }
        assertThat(root.getHandlers()).hasSize(handlersBefore);
    }
}
