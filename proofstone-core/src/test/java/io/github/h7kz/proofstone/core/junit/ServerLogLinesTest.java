package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.api.Agent;
import io.github.h7kz.proofstone.core.await.AwaitTimeoutException;
import io.github.h7kz.proofstone.core.host.AgentEndpoint;
import io.github.h7kz.proofstone.core.host.LogBuffer;
import io.github.h7kz.proofstone.protocol.Health;
import io.github.h7kz.proofstone.protocol.LogPage;
import io.github.h7kz.proofstone.protocol.LogRecord;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** {@code Agent.logLines} over a fake agent that keeps a dense log like the real buffer (T-M11-01). */
class ServerLogLinesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static LogRecord record(String level, String logger, String message, String thrown) {
        return new LogRecord("2026-01-01T00:00:00Z", level, logger, message, thrown);
    }

    /** Serves {@code GET /v1/log?since=&minLevel=} from a list: record i has sequence number i, {@code first} is evicted below. */
    private static final class FakeAgent implements AgentEndpoint {

        final List<LogRecord> records = new ArrayList<>();
        final List<String> paths = new ArrayList<>();
        /** Records below this sequence number were evicted from the ring buffer. */
        int evictedBelow;

        @Override
        public JsonNode request(String method, String path, Object body) {
            paths.add(method + " " + path);
            long since = Long.parseLong(path.replaceAll(".*since=(\\d+).*", "$1"));
            List<LogRecord> page = new ArrayList<>();
            for (int seq = Math.max(evictedBelow, (int) since); seq < records.size(); seq++) {
                page.add(records.get(seq));
            }
            return JSON.valueToTree(new LogPage(page, records.size()));
        }

        @Override
        public String command(String command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Health health() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String agentToken() {
            return "token";
        }

        @Override
        public List<String> agentTranscript() {
            return List.of();
        }

        @Override
        public LogBuffer logs() {
            return new LogBuffer();
        }
    }

    private static Agent agent(FakeAgent host, AtomicLong cursor) {
        return new HostAgent(host, Set.of(), cursor::get);
    }

    @Test
    void returnsOnlyTheLinesSinceTheTestCursorAsPlainText() {
        FakeAgent host = new FakeAgent();
        host.records.add(record("INFO", "Minecraft", "Done (3.1s)! For help, type \"help\"", null));
        AtomicLong cursor = new AtomicLong(host.records.size());
        host.records.add(record("INFO", "Minecraft", "Steve joined the game", null));
        host.records.add(record("WARN", "Disenchantment", "something odd", null));

        assertThat(agent(host, cursor).logLines()).containsExactly(
                "INFO Minecraft: Steve joined the game", "WARN Disenchantment: something odd");
        assertThat(host.paths).containsExactly("GET /v1/log?since=1&minLevel=TRACE");
    }

    @Test
    void aStackTraceAndAMultiLineMessageAddOneLinePerLine() {
        FakeAgent host = new FakeAgent();
        host.records.add(record("ERROR", "Disenchantment", "failed\nsecond line",
                "java.lang.IllegalStateException: boom\n\tat a.B.c(B.java:1)"));

        assertThat(agent(host, new AtomicLong(0)).logLines()).containsExactly(
                "ERROR Disenchantment: failed", "second line", "java.lang.IllegalStateException: boom",
                "\tat a.B.c(B.java:1)");
    }

    @Test
    void noRecordsIsAnEmptyListNotAnError() {
        assertThat(agent(new FakeAgent(), new AtomicLong(0)).logLines()).isEmpty();
    }

    @Test
    void theCursorIsReadOnEveryCallSoAServerSwitchMovesTheWindow() {
        FakeAgent host = new FakeAgent();
        host.records.add(record("INFO", "Minecraft", "old", null));
        host.records.add(record("INFO", "Minecraft", "new", null));
        AtomicLong cursor = new AtomicLong(0);
        Agent agent = agent(host, cursor);
        assertThat(agent.logLines()).hasSize(2);
        cursor.set(1);
        assertThat(agent.logLines()).containsExactly("INFO Minecraft: new");
    }

    @Test
    void recordsTheRingBufferDroppedFailLoudlyInsteadOfReadingAsAnIncompleteLog() {
        FakeAgent host = new FakeAgent();
        for (int i = 0; i < 5; i++) {
            host.records.add(record("INFO", "Minecraft", "line " + i, null));
        }
        host.evictedBelow = 2;

        assertThatThrownBy(() -> agent(host, new AtomicLong(0)).logLines())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("dropped 2 of 5 records");
    }

    @Test
    void awaitReturnsEveryMatchingLineOnceOneArrives() {
        FakeAgent host = new FakeAgent();
        host.records.add(record("INFO", "Minecraft", "NMS module: NMS_v1_21_R5", null));
        host.records.add(record("INFO", "Minecraft", "unrelated", null));
        host.records.add(record("INFO", "Minecraft", "NMS module: again", null));

        assertThat(agent(host, new AtomicLong(0)).logLines(Pattern.compile("NMS module: \\w+"), Duration.ofSeconds(5)))
                .containsExactly("INFO Minecraft: NMS module: NMS_v1_21_R5", "INFO Minecraft: NMS module: again");
    }

    @Test
    void awaitTimesOutNamingThePatternWhenNoLineMatches() {
        FakeAgent host = new FakeAgent();
        host.records.add(record("INFO", "Minecraft", "something else", null));

        assertThatThrownBy(() -> agent(host, new AtomicLong(0)).logLines(Pattern.compile("never logged"),
                Duration.ofMillis(100)))
                .isInstanceOf(AwaitTimeoutException.class).hasMessageContaining("/never logged/");
    }

    @Test
    void awaitIgnoresAMatchLoggedBeforeTheTestCursor() {
        FakeAgent host = new FakeAgent();
        host.records.add(record("INFO", "Minecraft", "boot marker", null));
        AtomicLong cursor = new AtomicLong(1);

        assertThatThrownBy(() -> agent(host, cursor).logLines(Pattern.compile("boot marker"), Duration.ofMillis(100)))
                .isInstanceOf(AwaitTimeoutException.class);
    }

    @Test
    void awaitRethrowsDroppedRecordsAtOnceInsteadOfPollingToTheTimeout() {
        FakeAgent host = new FakeAgent();
        for (int i = 0; i < 5; i++) {
            host.records.add(record("INFO", "Minecraft", "line " + i, null));
        }
        host.evictedBelow = 2;

        // A timeout of an hour: only an immediate rethrow lets this test finish.
        assertThatThrownBy(() -> agent(host, new AtomicLong(0)).logLines(Pattern.compile("never"), Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("dropped 2 of 5 records");
        assertThat(host.paths).hasSize(1);
    }

    @Test
    void bootLogLinesAreTheRecordsBeforeTheTestCursorAndTheComplementOfLogLines() {
        FakeAgent host = new FakeAgent();
        host.records.add(record("INFO", "Disenchantment", "NMS module: NMS_v1_21_R5", null));
        host.records.add(record("INFO", "Minecraft", "Done (3.1s)!", null));
        AtomicLong cursor = new AtomicLong(host.records.size());
        host.records.add(record("INFO", "Minecraft", "Steve joined the game", null));
        Agent agent = agent(host, cursor);

        assertThat(agent.bootLogLines()).containsExactly(
                "INFO Disenchantment: NMS module: NMS_v1_21_R5", "INFO Minecraft: Done (3.1s)!");
        assertThat(agent.logLines()).containsExactly("INFO Minecraft: Steve joined the game");
        assertThat(host.paths).contains("GET /v1/log?since=0&minLevel=TRACE");
    }

    @Test
    void bootLogLinesRenderStackTracesAndAreEmptyBeforeAnyRecord() {
        FakeAgent host = new FakeAgent();
        assertThat(agent(host, new AtomicLong(0)).bootLogLines()).isEmpty();
        host.records.add(record("ERROR", "P", "failed", "java.lang.IllegalStateException: boom\n\tat a.B.c(B.java:1)"));
        assertThat(agent(host, new AtomicLong(1)).bootLogLines()).containsExactly(
                "ERROR P: failed", "java.lang.IllegalStateException: boom", "\tat a.B.c(B.java:1)");
    }

    @Test
    void bootLogLinesFailLoudlyWhenTheRingBufferDroppedTheBoot() {
        FakeAgent host = new FakeAgent();
        for (int i = 0; i < 5; i++) {
            host.records.add(record("INFO", "Minecraft", "line " + i, null));
        }
        host.evictedBelow = 2;

        assertThatThrownBy(() -> agent(host, new AtomicLong(4)).bootLogLines())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("dropped 2 of 5 records");
    }

    @Test
    void aCursorPastTheLoggedRecordsFailsLoudly() {
        FakeAgent host = new FakeAgent();
        host.records.add(record("INFO", "Minecraft", "only", null));

        assertThatThrownBy(() -> agent(host, new AtomicLong(3)).bootLogLines())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("past the 1 records");
    }
}
