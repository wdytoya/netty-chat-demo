package org.example.resolver.chat;

import io.netty.channel.ChannelHandlerContext;
import org.example.codec.EnvelopeFactory;
import org.example.demo.protos.ChatMsgType;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;
import org.example.demo.protos.ErrorCode;
import org.example.demo.protos.LoginReq;
import org.example.demo.protos.LoginResp;
import org.example.resolver.Resolver;
import org.example.session.Session;
import org.example.session.SessionManager;

/**
 * Binds a display username to the current clientId session.
 */
public class LoginResolver implements Resolver {
    @Override
    public boolean support(Domain domain, int msgType) {
        return domain == Domain.DOMAIN_CHAT
                && msgType == ChatMsgType.LOGIN_REQ.getNumber();
    }

    @Override
    public void resolve(ChannelHandlerContext ctx, Envelope envelope) throws Exception {
        Session session = SessionManager.getInstance().getByChannel(ctx.channel());
        if (session == null) {
            write(ctx, envelope, false, ErrorCode.NOT_AUTHENTICATED.getNumber(),
                    "Not authenticated", null);
            return;
        }
        if (session.isLoggedIn()) {
            write(ctx, envelope, false, ErrorCode.BAD_REQUEST.getNumber(),
                    "Already logged in as " + session.getUsername(),
                    session.getUsername());
            return;
        }
        LoginReq req = LoginReq.parseFrom(envelope.getPayload());
        SessionManager.LoginResult result = SessionManager.getInstance()
                .login(session, req.hasUsername() ? req.getUsername() : null);
        write(ctx, envelope, result.success, result.code, result.message, result.username);
        if (result.success) {
            System.out.println("[LOGIN] clientId=" + session.getClientId()
                    + " username=" + result.username);
        } else {
            System.out.println("[LOGIN] failed clientId=" + session.getClientId()
                    + " username=" + session.logUsername()
                    + " reason=" + result.message);
        }
    }

    private void write(ChannelHandlerContext ctx, Envelope envelope,
                       boolean success, int code, String message, String username) {
        LoginResp.Builder builder = LoginResp.newBuilder()
                .setSuccess(success)
                .setCode(code)
                .setMessage(message);
        if (username != null) {
            builder.setUsername(username);
        }
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_CHAT,
                ChatMsgType.LOGIN_RESP.getNumber(),
                builder.build(),
                envelope.getRequestId()));
    }
}
