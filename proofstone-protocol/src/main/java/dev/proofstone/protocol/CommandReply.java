package dev.proofstone.protocol;

/** Reply of {@code POST /v1/command}. {@code raw} is best-effort console output (ADR-0002). */
public record CommandReply(String raw) {
}
