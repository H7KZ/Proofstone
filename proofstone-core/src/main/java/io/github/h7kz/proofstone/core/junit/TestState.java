package io.github.h7kz.proofstone.core.junit;

import io.github.h7kz.proofstone.core.host.ServerHost;
import io.github.h7kz.proofstone.core.host.ViaProxyHost;
import io.github.h7kz.proofstone.core.verdict.ConformanceResult;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the lifecycle keeps for one test: the log cursor from before it, the plugins whose config it patched and
 * its client. Closing it disconnects a client the lifecycle did not get to, for example when setup failed half way.
 */
final class TestState implements AutoCloseable {

    final ServerHost host;
    /**
     * Where this test's log window starts. {@code beforeEach} sets it before the join; the invocation that runs the cell's
     * conformance moves it past the conformance's own leave (T-M8-22), so the conformance client's close is not this test's.
     */
    long logCursor;
    /** {@code null} for an unbridged cell; its log goes into the repro bundle when set. */
    ViaProxyHost proxy;
    final Set<String> patchedPlugins = ConcurrentHashMap.newKeySet();
    /** {@code null} when the test method takes no {@code Client}. */
    SessionClient client;
    /** The cell's conformance result, read before the test; a failed one makes the test INVALID. */
    ConformanceResult conformance;
    /**
     * The conformance client's driver transcript, set only when the cell's conformance failed (T-M7-21). The INVALID test's
     * own client never ran a scenario, so this is the only packet record of the failure; it goes into the bundle.
     */
    List<String> conformanceTranscript = List.of();
    /** Set when the cell's conformance failed: the INVALID failure that {@code beforeEach} threw. */
    AssertionError invalid;
    /** The leak detector's baseline, from before the test's client joins; {@code null} when the test never got that far. */
    List<String> baseline;
    /** What the cell holds, read from the agent before the test (test-api spec section 10). */
    Set<io.github.h7kz.proofstone.api.Capability> capabilities = Set.of();
    /** Set when the cell lacks a capability the test requires: the skip reason. The body never runs then. */
    String skipped;
    /** The Geyser cell's host, for its bundle; {@code null} on any other cell. */
    io.github.h7kz.proofstone.core.host.GeyserHost geyser;
    /** The {@link io.github.h7kz.proofstone.api.Clients} the test may use; it closes the clients they created. */
    TestClients clients;

    TestState(ServerHost host, long logCursor) {
        this.host = host;
        this.logCursor = logCursor;
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        if (client != null) {
            client.close();
        }
        if (clients != null) {
            failure = clients.closeAll();
        }
        if (failure != null) {
            throw failure;
        }
    }
}
