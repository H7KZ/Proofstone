package io.github.h7kz.proofstone.driver.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.h7kz.proofstone.core.driver.bedrock.rpc.BedrockRpc;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;
import org.junit.jupiter.api.Test;

/** The NetherNet frame (ADR-0021): a message is a compressed batch with no RakNet frame id, and the stage names are the contract's. */
class NetherNetFrameCodecTest {

    private static final byte[] BATCH = {0x78, 0x01, 0x4b, 0x04, 0x00}; // not a real batch: the codec never reads it

    @Test
    void aMessageBecomesTheCompressedSideOfABatchWithoutDroppingOrAddingAByte() {
        EmbeddedChannel channel = new EmbeddedChannel(new NetherNetFrameCodec());
        assertThat(channel.writeInbound(Unpooled.wrappedBuffer(BATCH))).isTrue();
        BedrockBatchWrapper batch = channel.readInbound();
        byte[] seen = new byte[batch.getCompressed().readableBytes()];
        batch.getCompressed().getBytes(batch.getCompressed().readerIndex(), seen);
        assertThat(seen).containsExactly(BATCH);
        batch.release();
        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void aBatchGoesOutAsExactlyItsCompressedBytesAndNoFrameId() {
        EmbeddedChannel channel = new EmbeddedChannel(new NetherNetFrameCodec());
        BedrockBatchWrapper batch = BedrockBatchWrapper.newInstance(Unpooled.wrappedBuffer(BATCH), null);
        assertThat(channel.writeOutbound(batch)).isTrue();
        ByteBuf out = channel.readOutbound();
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        assertThat(bytes).containsExactly(BATCH);
        assertThat(bytes[0]).isNotEqualTo((byte) 0xFE);
        out.release();
    }

    @Test
    void anEmptyMessageIsAFramingFaultNeverASilentDrop() {
        EmbeddedChannel channel = new EmbeddedChannel(new NetherNetFrameCodec());
        assertThatThrownBy(() -> channel.writeInbound(Unpooled.EMPTY_BUFFER)).hasMessageContaining("empty NetherNet message");
    }

    @Test
    void aBatchThatWasNeverCompressedCannotBeSent() {
        EmbeddedChannel channel = new EmbeddedChannel(new NetherNetFrameCodec());
        BedrockBatchWrapper batch = BedrockBatchWrapper.newInstance(null, Unpooled.wrappedBuffer(BATCH));
        assertThatThrownBy(() -> channel.writeOutbound(batch)).hasMessageContaining("before it was compressed");
        // the pipeline released the batch when the encode failed (releasing it again is an IllegalReferenceCountException)
    }

    @Test
    void theNativeLibraryIsChosenPerArchitectureAndAnythingElseFailsNamed() {
        assertThat(NetherNetTransport.nativeResource("Linux", "amd64")).isEqualTo("/native/linux-x86_64/libdatachannel-java.so");
        assertThat(NetherNetTransport.nativeResource("Linux", "x86_64")).isEqualTo("/native/linux-x86_64/libdatachannel-java.so");
        assertThat(NetherNetTransport.nativeResource("Linux", "aarch64")).isEqualTo("/native/linux-aarch64/libdatachannel-java.so");
        assertThatThrownBy(() -> NetherNetTransport.nativeResource("Linux", "riscv64")).hasMessageContaining("riscv64");
        assertThatThrownBy(() -> NetherNetTransport.nativeResource("Windows 11", "amd64")).hasMessageContaining("Windows 11");
    }

    @Test
    void theNetherNetStagesAreNamedAsTheAdrSaysAndRakNetStaysTheDefault() {
        assertThat(BedrockRpc.JoinStage.valueOf("SIGNALLING")).isNotNull();
        assertThat(BedrockRpc.JoinStage.valueOf("NETHERNET_CONNECT")).isNotNull();
        assertThat(BedrockRpc.requireTransport(null)).isEqualTo("raknet");
        assertThat(BedrockRpc.requireTransport("nethernet")).isEqualTo("nethernet");
        assertThatThrownBy(() -> BedrockRpc.requireTransport("quic")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quic");
    }
}
