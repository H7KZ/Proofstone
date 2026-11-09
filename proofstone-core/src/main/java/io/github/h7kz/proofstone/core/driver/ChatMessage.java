package io.github.h7kz.proofstone.core.driver;

/**
 * A chat message as plain text.
 *
 * @param text the component flattened to text, with no formatting
 * @param system true for system messages (command output, join messages), false for player chat
 */
public record ChatMessage(String text, boolean system) {
}
