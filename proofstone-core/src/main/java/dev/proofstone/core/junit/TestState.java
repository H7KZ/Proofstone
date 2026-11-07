package dev.proofstone.core.junit;

import dev.proofstone.core.host.PaperHost;
import dev.proofstone.core.host.ViaProxyHost;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the lifecycle keeps for one test: the log cursor from before it, the plugins whose config it patched and
 * its client. Closing it disconnects a client the lifecycle did not get to, for example when setup failed half way.
 */
final class TestState implements AutoCloseable {

    final PaperHost host;
    final long logCursor;
    /** {@code null} for an unbridged cell; its log goes into the repro bundle when set. */
    ViaProxyHost proxy;
    final Set<String> patchedPlugins = ConcurrentHashMap.newKeySet();
    /** {@code null} when the test method takes no {@code Client}. */
    SessionClient client;

    TestState(PaperHost host, long logCursor) {
        this.host = host;
        this.logCursor = logCursor;
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
