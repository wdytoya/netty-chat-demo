package org.example.resolver.chat;

import io.netty.channel.ChannelHandlerContext;
import org.example.codec.EnvelopeFactory;
import org.example.demo.protos.ChatMsgType;
import org.example.demo.protos.ClientHelloReq;
import org.example.demo.protos.ClientHelloResp;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;
import org.example.demo.protos.ReconnectRoomAvailable;
import org.example.demo.protos.RoomMsgType;
import org.example.reconnect.PendingRejoin;
import org.example.reconnect.ReconnectStateStore;
import org.example.resolver.Resolver;
import org.example.room.RoomManager;
import org.example.session.Session;
import org.example.session.SessionManager;

/**
 * Issues or restores server-generated clientId + reconnectToken.
 */
public class ClientHelloResolver implements Resolver {
    @Override
    public boolean support(Domain domain, int msgType) {
        return domain == Domain.DOMAIN_CHAT
                && msgType == ChatMsgType.CLIENT_HELLO_REQ.getNumber();
    }

    @Override
    public void resolve(ChannelHandlerContext ctx, Envelope envelope) throws Exception {
        ClientHelloReq req = ClientHelloReq.parseFrom(envelope.getPayload());
        SessionManager.HelloResult result = SessionManager.getInstance().hello(
                ctx.channel(),
                req.hasClientId() ? req.getClientId() : null,
                req.hasReconnectToken() ? req.getReconnectToken() : null);

        Session session = result.getSession();
        ClientHelloResp.Builder respBuilder = ClientHelloResp.newBuilder()
                .setSuccess(true)
                .setClientId(result.getCredential().getClientId())
                .setReconnectToken(result.getCredential().getReconnectToken())
                .setMessage(result.isReconnect() ? "Reconnect OK" : "Hello OK");
        if (session.isLoggedIn()) {
            respBuilder.setUsername(session.getUsername());
        }

        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_CHAT,
                ChatMsgType.CLIENT_HELLO_RESP.getNumber(),
                respBuilder.build(),
                envelope.getRequestId()));

        PendingRejoin pending = ReconnectStateStore.getInstance()
                .getValid(session.getClientId());
        if (pending != null) {
            if (RoomManager.getInstance().getRoom(pending.getRoomName()) == null) {
                ReconnectStateStore.getInstance().remove(session.getClientId());
                System.out.println("[HELLO] clientId=" + session.getClientId()
                        + " username=" + session.logUsername()
                        + " pending room gone: " + pending.getRoomName());
            } else {
                ReconnectRoomAvailable available = ReconnectRoomAvailable.newBuilder()
                        .setRoomName(pending.getRoomName())
                        .setExpireAtMs(pending.getExpireAtMs())
                        .build();
                ctx.writeAndFlush(EnvelopeFactory.wrap(
                        Domain.DOMAIN_ROOM,
                        RoomMsgType.RECONNECT_ROOM_AVAILABLE.getNumber(),
                        available));
                System.out.println("[HELLO] clientId=" + session.getClientId()
                        + " username=" + session.logUsername()
                        + " may rejoin room=" + pending.getRoomName());
                return;
            }
        }
        System.out.println("[HELLO] clientId=" + session.getClientId()
                + " username=" + session.logUsername()
                + " reconnect=" + result.isReconnect());
    }
}
