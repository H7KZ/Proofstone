package io.github.h7kz.proofstone.core.versions;

/**
 * The {@code drivers.bedrock} pin of schema 5 (ADR-0018 section 8).
 *
 * @param library Maven coordinates of the client library
 * @param version the exact pinned version; a timestamped snapshot until a release carries the codec
 * @param protocol the Bedrock protocol number the driver speaks
 * @param gameVersion the Bedrock game version of that protocol
 */
public record BedrockDriver(String library, String version, int protocol, String gameVersion) {
}
