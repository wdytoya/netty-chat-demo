package org.example;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.example.demo.protos.ChatMsg;

public class ChatMsgEncoder extends MessageToByteEncoder<ChatMsg> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ChatMsg msg, ByteBuf out) throws Exception {
        System.out.println("Encoder, time=" + System.currentTimeMillis());

        out.writeInt(msg.getSerializedSize());
        out.writeBytes(msg.toByteArray());
    }
}
