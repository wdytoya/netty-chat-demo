package org.example.resolver.room;

import io.netty.channel.ChannelHandlerContext;
import org.example.codec.EnvelopeFactory;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;
import org.example.demo.protos.ErrorCode;
import org.example.demo.protos.LeaveRoomResp;
import org.example.demo.protos.RoomMemberLeftNotify;
import org.example.demo.protos.RoomMsgType;
import org.example.resolver.Resolver;
import org.example.room.RoomManager;
import org.example.session.Session;
import org.example.session.SessionManager;

public class LeaveRoomResolver implements Resolver {
    @Override
    public boolean support(Domain domain, int msgType) {
        return domain == Domain.DOMAIN_ROOM
                && msgType == RoomMsgType.LEAVE_ROOM_REQ.getNumber();
    }

    @Override
    public void resolve(ChannelHandlerContext ctx, Envelope envelope) {
        Session session = SessionManager.getInstance().getByChannel(ctx.channel());
        if (session == null) {
            writeFail(ctx, envelope, ErrorCode.NOT_AUTHENTICATED.getNumber(), "Not authenticated");
            return;
        }
        if (!session.isLoggedIn()) {
            writeFail(ctx, envelope, ErrorCode.NOT_LOGGED_IN.getNumber(), "Please login first");
            return;
        }
        String displayName = session.displayName();
        RoomManager.LeaveResult result = RoomManager.getInstance().leave(session);
        LeaveRoomResp.Builder builder = LeaveRoomResp.newBuilder()
                .setSuccess(result.success)
                .setCode(result.code)
                .setMessage(result.message);
        if (result.roomName != null) {
            builder.setRoomName(result.roomName);
        }
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM,
                RoomMsgType.LEAVE_ROOM_RESP.getNumber(),
                builder.build(),
                envelope.getRequestId()));

        if (result.success && result.room != null) {
            RoomMemberLeftNotify notify = RoomMemberLeftNotify.newBuilder()
                    .setRoomName(result.roomName)
                    .setUsername(displayName)
                    .build();
            RoomManager.getInstance().broadcast(result.room,
                    EnvelopeFactory.wrap(Domain.DOMAIN_ROOM,
                            RoomMsgType.ROOM_MEMBER_LEFT_NOTIFY.getNumber(), notify),
                    session.getClientId());
        }
    }

    private void writeFail(ChannelHandlerContext ctx, Envelope envelope, int code, String message) {
        LeaveRoomResp resp = LeaveRoomResp.newBuilder()
                .setSuccess(false)
                .setCode(code)
                .setMessage(message)
                .build();
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM,
                RoomMsgType.LEAVE_ROOM_RESP.getNumber(),
                resp,
                envelope.getRequestId()));
    }
}
