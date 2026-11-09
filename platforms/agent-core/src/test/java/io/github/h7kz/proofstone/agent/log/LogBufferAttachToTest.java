package io.github.h7kz.proofstone.agent.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * {@link LogBuffer#attachTo(Logger)} is for a server whose own logger is not under the JUL root (BungeeCord's
 * {@code BungeeLogger} has no parent, and every plugin logger parents to it), so a root handler sees none of its records
 * (T-M7-06). The buffer reads that one logger and must leave the root logger alone.
 */
class LogBufferAttachToTest {

    @Test
    void readsTheGivenLoggerAndItsChildrenAndNotTheRoot() {
        Logger parentless = new Logger("proxy-like", null) { };
        parentless.setLevel(Level.ALL);
        Logger child = new Logger("plugin-like", null) { };
        child.setParent(parentless);
        Logger root = Logger.getLogger("");
        int rootHandlers = root.getHandlers().length;

        LogBuffer buffer = LogBuffer.attachTo(parentless);
        try {
            assertThat(root.getHandlers()).hasSize(rootHandlers);
            assertThat(parentless.getHandlers()).hasSize(1);
            parentless.log(Level.INFO, "from the proxy logger");
            child.log(Level.WARNING, "from a plugin", new IllegalStateException("boom"));
            Logger.getLogger("proofstone.attach-to-test.elsewhere").log(Level.WARNING, "not ours");

            List<?> records = (List<?>) buffer.read(0, "INFO").get("records");
            assertThat(records).hasSize(2);
            assertThat(records.get(0).toString()).contains("from the proxy logger");
            assertThat(records.get(1).toString()).contains("from a plugin").contains("IllegalStateException: boom");
        } finally {
            buffer.detach();
        }
        assertThat(parentless.getHandlers()).isEmpty();
        assertThat(root.getHandlers()).hasSize(rootHandlers);
    }

    @Test
    void aRecordIsKeptOnceEvenWhenTheGivenLoggerPassesItToItsParent() {
        Logger parent = new Logger("parent-like", null) { };
        parent.setLevel(Level.ALL);
        Logger child = new Logger("child-like", null) { };
        child.setParent(parent);
        LogBuffer buffer = LogBuffer.attachTo(parent);
        try {
            child.log(Level.INFO, "once");
            assertThat((List<?>) buffer.read(0, "INFO").get("records")).hasSize(1);
        } finally {
            buffer.detach();
        }
    }
}
