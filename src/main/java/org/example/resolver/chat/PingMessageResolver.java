package org.example.resolver.chat;

import io.netty.channel.ChannelHandlerContext;
import org.example.codec.EnvelopeFactory;
import org.example.demo.protos.ChatMsgType;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;
import org.example.demo.protos.Pong;
import org.example.resolver.Resolver;
import org.example.session.Session;
import org.example.session.SessionManager;

/**
 * Responds to client PING with PONG and renews credential TTL while active.
 */
public class PingMessageResolver implements Resolver {
    @Override
    public boolean support(Domain domain, int msgType) {
        return domain == Domain.DOMAIN_CHAT
                && msgType == ChatMsgType.PING.getNumber();
    }

    @Override
    public void resolve(ChannelHandlerContext ctx, Envelope envelope) {
        Session session = SessionManager.getInstance().getByChannel(ctx.channel());
        if (session == null) {
            return;
        }
        SessionManager.getInstance().renewCredential(session.getClientId());
        Pong pong = Pong.newBuilder().setServerTime(System.currentTimeMillis()).build();
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_CHAT,
                ChatMsgType.PONG.getNumber(),
                pong,
                envelope.getRequestId()));
    }
}
