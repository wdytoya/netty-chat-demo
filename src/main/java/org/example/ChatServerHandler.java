package org.example;

import com.google.protobuf.Message;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.example.demo.protos.ChatMsg;

public class ChatServerHandler extends ChannelInboundHandlerAdapter {
    // 获取一个消息处理器工厂类实例
    final private MessageResolverFactory resolverFactory = MessageResolverFactory.getInstance();

    static {
        MessageResolverFactory.getInstance().registerResolver(new RequestMessageResolver());
        MessageResolverFactory.getInstance().registerResolver(new PingMessageResolver());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ChatMsg message = (ChatMsg)msg;
        Resolver resolver = resolverFactory.getMessageResolver(message); // 获取消息处理器
        Message result = resolver.resolve(message); // 对消息进行处理并获取响应数据
        ctx.writeAndFlush(result); // 将响应数据写入到处理器中
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                System.out.println("Client " + ctx.channel().remoteAddress() + " not active, close connection...");
                ctx.close();
            } else if (event.state() == IdleState.WRITER_IDLE) {
                System.out.println("Server write idle");
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 当出现异常就关闭连接
        cause.printStackTrace();
        ctx.close();
    }
}
