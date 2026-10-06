package org.example.resolver.room;

import io.netty.channel.ChannelHandlerContext;
import org.example.codec.EnvelopeFactory;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;
import org.example.demo.protos.ErrorCode;
import org.example.demo.protos.ReconnectRoomConfirmResp;
import org.example.demo.protos.RoomMemberReconnectedNotify;
import org.example.demo.protos.RoomMsgType;
import org.example.reconnect.PendingRejoin;
import org.example.reconnect.ReconnectStateStore;
import org.example.resolver.Resolver;
import org.example.room.RoomManager;
import org.example.session.Session;
import org.example.session.SessionManager;

public class ReconnectRoomConfirmResolver implements Resolver {
    @Override
    public boolean support(Domain domain, int msgType) {
        return domain == Domain.DOMAIN_ROOM
                && msgType == RoomMsgType.RECONNECT_ROOM_CONFIRM_REQ.getNumber();
    }

    @Override
    public void resolve(ChannelHandlerContext ctx, Envelope envelope) {
        Session session = SessionManager.getInstance().getByChannel(ctx.channel());
        if (session == null) {
            write(ctx, envelope, false, ErrorCode.NOT_AUTHENTICATED.getNumber(),
                    "Not authenticated", null);
            return;
        }
        if (!session.isLoggedIn()) {
            write(ctx, envelope, false, ErrorCode.NOT_LOGGED_IN.getNumber(),
                    "Please login first", null);
            return;
        }

        PendingRejoin pending = ReconnectStateStore.getInstance().getValid(session.getClientId());
        if (pending == null) {
            write(ctx, envelope, false, ErrorCode.RECONNECT_EXPIRED.getNumber(),
                    "No pending room to rejoin (missing or expired)", null);
            return;
        }
        if (RoomManager.getInstance().getRoom(pending.getRoomName()) == null) {
            ReconnectStateStore.getInstance().remove(session.getClientId());
            write(ctx, envelope, false, ErrorCode.ROOM_NOT_FOUND.getNumber(),
                    "Original room no longer exists", null);
            return;
        }

        RoomManager.RejoinResult result = RoomManager.getInstance()
                .rejoin(session, pending.getRoomName());
        if (!result.success) {
            if (result.code == ErrorCode.ROOM_NOT_FOUND.getNumber()
                    || result.code == ErrorCode.ALREADY_IN_ROOM.getNumber()) {
                // Room gone, or already in a room: pending can never succeed as-is.
                ReconnectStateStore.getInstance().remove(session.getClientId());
            }
            write(ctx, envelope, false, result.code, result.message, null);
            return;
        }

        ReconnectStateStore.getInstance().remove(session.getClientId());
        write(ctx, envelope, true, ErrorCode.OK.getNumber(), "Rejoined", result.roomName);

        RoomMemberReconnectedNotify notify = RoomMemberReconnectedNotify.newBuilder()
                .setRoomName(result.roomName)
                .setUsername(session.displayName())
                .build();
        RoomManager.getInstance().broadcast(result.room,
                EnvelopeFactory.wrap(Domain.DOMAIN_ROOM,
                        RoomMsgType.ROOM_MEMBER_RECONNECTED_NOTIFY.getNumber(), notify),
                session.getClientId());
    }

    private void write(ChannelHandlerContext ctx, Envelope envelope,
                       boolean success, int code, String message, String roomName) {
        ReconnectRoomConfirmResp.Builder builder = ReconnectRoomConfirmResp.newBuilder()
                .setSuccess(success)
                .setCode(code)
                .setMessage(message);
        if (roomName != null) {
            builder.setRoomName(roomName);
        }
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM,
                RoomMsgType.RECONNECT_ROOM_CONFIRM_RESP.getNumber(),
                builder.build(),
                envelope.getRequestId()));
    }
}
