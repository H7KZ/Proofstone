package dev.proofstone.protocol;

/** Request body of {@code POST /v1/command}. {@code timeoutMs} is optional; the agent defaults it to 10 000. */
public record Command(String command, Integer timeoutMs) {
}
