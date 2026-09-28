package org.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.example.demo.protos.ChatMsg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ChatClient {
    public static void main(String[] args) throws Exception{
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        try{
            Bootstrap bs = new Bootstrap();
            bs.group(workerGroup).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4),new IdleStateHandler(0, 0, 0), new ChatMsgEncoder(), new ChatMsgDecoder(), new ChatClientHandler());
                }
            }).option(ChannelOption.SO_KEEPALIVE, true);

            Channel channel = bs.connect("localhost", 6666).sync().channel();

            Thread consoleThread = new Thread(() -> {
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        ChatMsg msg = ChatMsgFactory.buildMsg(ChatMsg.MsgType.MSG_TYPE_REQUEST, line);
                        System.out.println("Build new message " + msg.getSessionId() + " to send...");
                        channel.writeAndFlush(msg);   // 线程安全
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, "console-input");
            consoleThread.setDaemon(true);
            consoleThread.start();

            channel.closeFuture().sync();
        }finally {
            workerGroup.shutdownGracefully();
        }

    }
}
