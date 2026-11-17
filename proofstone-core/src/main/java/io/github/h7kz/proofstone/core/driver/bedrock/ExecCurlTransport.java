package io.github.h7kz.proofstone.core.driver.bedrock;

import io.github.h7kz.proofstone.core.driver.ClientDriverException;
import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;

/**
 * Calls the driver host by running {@code curl} inside its container, the way the harness calls an agent, so nothing
 * is published and the token never leaves the two containers' exec channel (ADR-0018 section 2).
 */
public final class ExecCurlTransport implements HostTransport {

    private final GenericContainer<?> container;
    private final String token;

    public ExecCurlTransport(GenericContainer<?> container, String token) {
        this.container = container;
        this.token = token;
    }

    @Override
    public String call(String method, String path, String body, Duration timeout) {
        List<String> args = new ArrayList<>(List.of("curl", "-sS", "-m", String.valueOf(Math.max(1, timeout.toSeconds())),
                "-X", method, "-H", "Authorization: Bearer " + token, "-H", "Content-Type: application/json",
                "-H", BedrockRpc.REQUEST_ID_HEADER + ": " + UUID.randomUUID()));
        if (body != null) {
            args.addAll(List.of("-d", body));
        }
        args.add("http://127.0.0.1:" + BedrockRpc.PORT + path);
        try {
            ExecResult result = container.execInContainer(args.toArray(String[]::new));
            if (result.getExitCode() != 0) {
                throw new ClientDriverException("driver host call " + method + " " + path + " failed, curl exit "
                        + result.getExitCode() + ": " + result.getStderr().trim());
            }
            return result.getStdout();
        } catch (IOException e) {
            throw new ClientDriverException("driver host call " + method + " " + path + " could not run", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClientDriverException("driver host call " + method + " " + path + " was interrupted", e);
        }
    }
}
