package org.example.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.example.demo.protos.Envelope;

import java.util.List;

/**
 * Decodes a length-stripped frame into an Envelope.
 * LengthFieldBasedFrameDecoder must run ahead of this handler.
 */
public class EnvelopeDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byte[] bytes = new byte[in.readableBytes()];
        in.readBytes(bytes);
        out.add(Envelope.parseFrom(bytes));
    }
}
