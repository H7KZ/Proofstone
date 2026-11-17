package io.github.h7kz.proofstone.driver.bedrock;

import java.security.PrivateKey;
import javax.crypto.SecretKey;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

/**
 * What the packet handler needs of the connection. The real one wraps a Cloudburst session; the unit tests script it,
 * so the join runs on scripted packets without a network, and without loading the library class that fetches
 * Microsoft's discovery documents (ADR-0018 section 4).
 */
interface Wire {

    void send(BedrockPacket packet);

    void setCompression(PacketCompressionAlgorithm algorithm);

    void enableEncryption(SecretKey key);

    /** Installs the item definitions of the start game packet, so later packets can name their items. */
    void useItemDefinitions(java.util.List<ItemDefinition> definitions);

    /** The shared secret of the encryption handshake from the server's JWT. */
    SecretKey secret(PrivateKey key, String serverJwt);
}
