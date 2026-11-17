package io.github.h7kz.proofstone.core.driver.bedrock;

import java.time.Duration;

/**
 * How the harness reaches the Bedrock driver host: one HTTP request, answered with the body. The real one runs
 * {@code curl} inside the host's container ({@link ExecCurlTransport}), so no port is published; tests script it.
 */
public interface HostTransport {

    /**
     * @param path starts with {@code /rpc/v1}
     * @param body the JSON body, or {@code null}
     * @param timeout the longest the call may take; the host's own timeouts are shorter
     * @return the response body, whatever the status: the envelope says whether it failed
     * @throws io.github.h7kz.proofstone.core.driver.ClientDriverException when the host cannot be reached
     */
    String call(String method, String path, String body, Duration timeout);
}
