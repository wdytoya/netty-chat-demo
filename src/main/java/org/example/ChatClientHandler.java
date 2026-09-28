package org.example;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.example.demo.protos.ChatMsg;

public class ChatClientHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        System.out.println(msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
       ChatMsg chatMsg = ChatMsgFactory.buildMsg(ChatMsg.MsgType.MSG_TYPE_REQUEST,"this is connect message...");

        ctx.writeAndFlush(chatMsg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                System.out.println("Client read idle");
            } else if (event.state() == IdleState.WRITER_IDLE) {
                ChatMsg chatMsg = ChatMsgFactory.buildMsg(ChatMsg.MsgType.MSG_TYPE_PING,"");
                ctx.writeAndFlush(chatMsg);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
