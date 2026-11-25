package io.github.h7kz.proofstone.driver.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import java.util.List;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;

/**
 * The frame of a Bedrock batch on NetherNet (ADR-0021): one NetherNet message is one compressed batch, with none of the
 * {@code 0xFE} frame id that RakNet puts in front. Inbound the message's bytes become the wrapper's compressed side; outbound the
 * wrapper's compressed bytes are the message. Nothing else is added or removed, so the compression and encryption codecs above it
 * are the RakNet ones unchanged. One instance per channel.
 */
final class NetherNetFrameCodec extends MessageToMessageCodec<ByteBuf, BedrockBatchWrapper> {

    @Override
    protected void encode(ChannelHandlerContext ctx, BedrockBatchWrapper batch, List<Object> out) {
        if (batch.getCompressed() == null) {
            throw new IllegalStateException("a batch reached the NetherNet frame codec before it was compressed");
        }
        out.add(batch.getCompressed().retainedSlice());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf message, List<Object> out) {
        // An empty message carries no batch; the library never sends one, and a silent drop here would hide a framing fault.
        if (!message.isReadable()) {
            throw new IllegalStateException("an empty NetherNet message is not a Bedrock batch");
        }
        out.add(BedrockBatchWrapper.newInstance(message.readRetainedSlice(message.readableBytes()), null));
    }
}
