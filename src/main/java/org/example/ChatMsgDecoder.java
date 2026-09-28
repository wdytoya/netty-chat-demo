package org.example;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.example.demo.protos.ChatMsg;

import java.util.List;

public class ChatMsgDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        System.out.println("Decoder, time=" + System.currentTimeMillis());

        byte[] bytes = new byte[in.readableBytes()];
        in.readBytes(bytes);
        out.add(ChatMsg.parseFrom(bytes));
    }
}
