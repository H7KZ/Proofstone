package io.github.h7kz.proofstone.core.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.h7kz.proofstone.core.driver.ClientSession;
import io.github.h7kz.proofstone.core.host.LogBuffer;
import io.github.h7kz.proofstone.core.host.ServerHost;
import io.github.h7kz.proofstone.core.junit.ErrorLogGuard.Patterns;
import io.github.h7kz.proofstone.core.verdict.ConformanceResult;
import io.github.h7kz.proofstone.protocol.Health;
import io.github.h7kz.proofstone.protocol.LogPage;
import io.github.h7kz.proofstone.protocol.LogRecord;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * T-M8-22: the conformance client's own leave cannot red a test. A fake host runs the vanilla race of T-M8-16: the
 * server notices a close on one tick and logs "left the game", and handles it a second time on the next tick, which
 * logs the WARN. The fake's ticks run on their own thread, so a harness that does not wait for them loses the race.
 */
class ConformanceLeaveTest {

    private static final String TWICE = "handleDisconnection() called twice";
    private static final Duration SHORT = Duration.ofMillis(400);

    private final FakeHost host = new FakeHost();

    @AfterEach
    void stop() {
        host.stop();
    }

    @Test
    void theSecondHandlingAfterTheLeaveDoesNotFailTheFirstTest() {
        ConformanceResult result = host.runConformance(Patterns.FABRIC, true, client -> { });
        assertThat(result.passed()).as(result.detail()).isTrue();
        // beforeEach takes the first test's cursor after the conformance returns.
        long cursor = ErrorLogGuard.cursor(host);
        host.settle();
        // The race line did reach the log, before the cursor: nothing is hidden and nothing is allowlisted.
        assertThat(host.warnings()).anyMatch(line -> line.contains(TWICE));
        ErrorLogGuard.check(host, cursor, Patterns.FABRIC);
    }

    @Test
    void theSameRecordInsideTheConformanceBeforeTheCloseFailsTheConformanceNamingTheLine() {
        ConformanceResult result = host.runConformance(Patterns.FABRIC, false, client -> host.warn(TWICE));
        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains(TWICE).contains("conformance");
    }

    @Test
    void anyOtherWarnAfterTheNewCursorStillFailsTheTest() {
        ConformanceResult result = host.runConformance(Patterns.FABRIC, true, client -> { });
        assertThat(result.passed()).as(result.detail()).isTrue();
        long cursor = ErrorLogGuard.cursor(host);
        host.warn("Can't keep up! Is the server overloaded?");
        assertThatThrownBy(() -> ErrorLogGuard.check(host, cursor, Patterns.FABRIC))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Can't keep up");
    }

    @Test
    void aPaperCellTakesTheSamePathAndItsTestsAreUnchanged() {
        ConformanceResult result = host.runConformance(Patterns.PAPER, false, client -> { });
        assertThat(result.passed()).as(result.detail()).isTrue();
        long cursor = ErrorLogGuard.cursor(host);
        ErrorLogGuard.check(host, cursor, Patterns.PAPER);
        host.warn("Steve moved too quickly! 0.5,-60.0,-3.5");
        assertThatThrownBy(() -> ErrorLogGuard.check(host, cursor, Patterns.PAPER))
                .isInstanceOf(AssertionError.class).hasMessageContaining("moved too quickly");
    }

    @Test
    void aLeaveLineThatNeverArrivesIsAFailedConformanceNeverAPass() {
        host.printLeaveLine = false;
        ConformanceResult result = host.runConformance(Patterns.FABRIC, false, client -> { });
        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("left the game");
    }

    @Test
    void ticksThatNeverArriveAreAFailedConformanceNeverAPass() {
        host.ticksFail = true;
        ConformanceResult result = host.runConformance(Patterns.FABRIC, false, client -> { });
        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).contains("tick");
    }

    /** A server whose ticks run on a thread, 20 ms each: a close is handled on the next tick, and again on the one after. */
    private static final class FakeHost implements ServerHost {

        private static final ObjectMapper JSON = new ObjectMapper();
        private final List<LogRecord> agentLog = new ArrayList<>();
        private final LogBuffer console = new LogBuffer();
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile long ticks;
        /** 0 when no close is pending, else the tick on which the first handling happens. */
        private volatile long firstHandling;
        private volatile boolean raceOnClose;
        private volatile boolean running = true;
        volatile boolean printLeaveLine = true;
        volatile boolean ticksFail;

        FakeHost() {
            Thread ticker = new Thread(this::tickLoop, "fake-ticks");
            ticker.setDaemon(true);
            ticker.start();
        }

        void stop() {
            running = false;
        }

        private void tickLoop() {
            while (running) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    return;
                }
                long now = ticks + 1;
                long first = firstHandling;
                if (first != 0 && now == first && printLeaveLine) {
                    console.append("[12:00:00] [Server thread/INFO]: " + CellConformance.CLIENT + " left the game");
                }
                if (first != 0 && now == first + 1) {
                    if (raceOnClose) {
                        warn("Failed to handle packet or " + TWICE);
                    }
                    firstHandling = 0;
                }
                ticks = now;
            }
        }

        ConformanceResult runConformance(Patterns patterns, boolean race, Consumer<SessionClient> body) {
            raceOnClose = race;
            return CellConformance.session("fake@1", this, patterns, SHORT,
                    name -> new SessionClient(name, session()), body, () -> "C03");
        }

        /** Lets the second handling land, so a test can see it reached the log. */
        void settle() {
            long until = ticks + 5;
            while (ticks < until) {
                Thread.onSpinWait();
            }
        }

        private ClientSession session() {
            return (ClientSession) java.lang.reflect.Proxy.newProxyInstance(ClientSession.class.getClassLoader(),
                    new Class<?>[] {ClientSession.class}, (proxy, method, args) -> {
                        if (method.getName().equals("close") && closed.compareAndSet(false, true)) {
                            firstHandling = ticks + 1;
                        }
                        return null;
                    });
        }

        synchronized void warn(String message) {
            agentLog.add(new LogRecord("2026-01-01T00:00:00Z", "WARN", "Minecraft", message, null));
        }

        synchronized List<String> warnings() {
            return agentLog.stream().map(LogRecord::message).toList();
        }

        @Override
        public JsonNode request(String method, String path, Object body) {
            if (path.startsWith("/v1/log?")) {
                return log(Long.parseLong(path.substring(path.indexOf("since=") + 6, path.indexOf('&'))));
            }
            if (method.equals("POST") && path.equals("/v1/ticks/await")) {
                if (ticksFail) {
                    throw new IllegalStateException("agent POST /v1/ticks/await failed: TIMEOUT");
                }
                long until = ticks + JSON.valueToTree(body).get("n").asLong();
                while (ticks < until) {
                    Thread.onSpinWait();
                }
                return JSON.valueToTree(Map.of("global", ticks));
            }
            throw new IllegalStateException("unexpected " + method + " " + path);
        }

        private synchronized JsonNode log(long since) {
            List<LogRecord> page = new ArrayList<>();
            for (int seq = 0; seq < agentLog.size(); seq++) {
                if (seq >= since) {
                    page.add(agentLog.get(seq));
                }
            }
            return JSON.valueToTree(new LogPage(page, agentLog.size()));
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
            return "t";
        }

        @Override
        public List<String> agentTranscript() {
            return List.of();
        }

        @Override
        public LogBuffer logs() {
            return console;
        }

        @Override
        public String mc() {
            return "26.3";
        }

        @Override
        public String gameHost() {
            return "localhost";
        }

        @Override
        public int gamePort() {
            return 25565;
        }

        @Override
        public String logTail() {
            return console.tail(50);
        }

        @Override
        public void close() {
        }
    }
}
