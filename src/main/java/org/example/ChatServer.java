package org.example;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.example.codec.EnvelopeDecoder;
import org.example.codec.EnvelopeEncoder;
import org.example.config.ServerConfig;

/**
 * Chat room server entrypoint.
 */
public class ChatServer {
    public static void main(String[] args) throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap sbs = new ServerBootstrap();
            sbs.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(
                                    new LengthFieldBasedFrameDecoder(
                                            ServerConfig.MAX_FRAME_LENGTH, 0, 4, 0, 4),
                                    new IdleStateHandler(ServerConfig.READER_IDLE_SECONDS, 0, 0),
                                    new EnvelopeEncoder(),
                                    new EnvelopeDecoder(),
                                    new ChatServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = sbs.bind(ServerConfig.SERVER_PORT).sync();
            System.out.println("ChatServer started on " + ServerConfig.SERVER_PORT
                    + ", reader idle=" + ServerConfig.READER_IDLE_SECONDS + "s");
            f.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
