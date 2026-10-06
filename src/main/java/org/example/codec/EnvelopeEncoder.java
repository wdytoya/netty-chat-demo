package org.example.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.example.demo.protos.Envelope;

/**
 * Encodes Envelope as: 4-byte big-endian length + protobuf bytes.
 */
public class EnvelopeEncoder extends MessageToByteEncoder<Envelope> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Envelope msg, ByteBuf out) {
        byte[] bytes = msg.toByteArray();
        out.writeInt(bytes.length);
        out.writeBytes(bytes);
    }
}
