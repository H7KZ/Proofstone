package io.github.h7kz.proofstone.driver.bedrock;

import java.util.List;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginType;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;

/** The two packets a v1 client sends after the join: a chat line and a command request. */
final class TextPackets {

    private TextPackets() {
    }

    static TextPacket chat(Identity identity, String message) {
        TextPacket packet = new TextPacket();
        packet.setType(TextPacket.Type.CHAT);
        packet.setNeedsTranslation(false);
        packet.setSourceName(identity.name());
        packet.setMessage(message);
        packet.setXuid(identity.xuid());
        packet.setPlatformChatId("");
        packet.setParameters(List.of());
        return packet;
    }

    /** @param command the command without its leading slash */
    static CommandRequestPacket command(Identity identity, String command) {
        CommandRequestPacket packet = new CommandRequestPacket();
        packet.setCommand("/" + command);
        packet.setCommandOriginData(new CommandOriginData(CommandOriginType.PLAYER, identity.uuid(), "", 0));
        packet.setInternal(false);
        return packet;
    }
}
