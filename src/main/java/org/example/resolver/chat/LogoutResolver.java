package org.example.resolver.chat;

import io.netty.channel.ChannelHandlerContext;
import org.example.codec.EnvelopeFactory;
import org.example.demo.protos.ChatMsgType;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;
import org.example.demo.protos.LogoutResp;
import org.example.demo.protos.RoomMemberLeftNotify;
import org.example.demo.protos.RoomMsgType;
import org.example.reconnect.ReconnectStateStore;
import org.example.resolver.Resolver;
import org.example.room.Room;
import org.example.room.RoomAuditLogger;
import org.example.room.RoomManager;
import org.example.session.Session;
import org.example.session.SessionManager;

/**
 * Active logout/quit: clear login state, pending rejoin and credentials.
 * Distinct from heartbeat-kick pending rejoin retention.
 */
public class LogoutResolver implements Resolver {
    @Override
    public boolean support(Domain domain, int msgType) {
        return domain == Domain.DOMAIN_CHAT
                && msgType == ChatMsgType.LOGOUT_REQ.getNumber();
    }

    @Override
    public void resolve(ChannelHandlerContext ctx, Envelope envelope) {
        Session session = SessionManager.getInstance().getByChannel(ctx.channel());
        if (session == null) {
            write(ctx, envelope, true, "OK");
            return;
        }

        String clientId = session.getClientId();
        String username = session.logUsername();

        // Active quit must not keep pending rejoin.
        ReconnectStateStore.getInstance().remove(clientId);

        if (session.isInRoom()) {
            String roomName = session.getCurrentRoomName();
            String broadcastName = session.displayName();
            Room room = RoomManager.getInstance().forceLeave(session);
            RoomAuditLogger.event("LOGOUT", roomName, username, "active quit");
            if (room != null && RoomManager.getInstance().getRoom(roomName) != null) {
                RoomMemberLeftNotify notify = RoomMemberLeftNotify.newBuilder()
                        .setRoomName(roomName)
                        .setUsername(broadcastName)
                        .build();
                RoomManager.getInstance().broadcast(room,
                        EnvelopeFactory.wrap(Domain.DOMAIN_ROOM,
                                RoomMsgType.ROOM_MEMBER_LEFT_NOTIFY.getNumber(), notify),
                        clientId);
            }
        }

        SessionManager.getInstance().removeIdentity(clientId);
        write(ctx, envelope, true, "Logged out");
        System.out.println("[LOGOUT] clientId=" + clientId + " username=" + username);
    }

    private void write(ChannelHandlerContext ctx, Envelope envelope, boolean success, String message) {
        LogoutResp resp = LogoutResp.newBuilder()
                .setSuccess(success)
                .setMessage(message)
                .build();
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_CHAT,
                ChatMsgType.LOGOUT_RESP.getNumber(),
                resp,
                envelope.getRequestId()));
    }
}
