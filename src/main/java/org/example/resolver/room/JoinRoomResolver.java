package org.example.resolver.room;

import io.netty.channel.ChannelHandlerContext;
import org.example.codec.EnvelopeFactory;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;
import org.example.demo.protos.ErrorCode;
import org.example.demo.protos.JoinRoomReq;
import org.example.demo.protos.JoinRoomResp;
import org.example.demo.protos.RoomMemberJoinedNotify;
import org.example.demo.protos.RoomMsgType;
import org.example.reconnect.ReconnectStateStore;
import org.example.resolver.Resolver;
import org.example.room.RoomManager;
import org.example.session.Session;
import org.example.session.SessionManager;

public class JoinRoomResolver implements Resolver {
    @Override
    public boolean support(Domain domain, int msgType) {
        return domain == Domain.DOMAIN_ROOM
                && msgType == RoomMsgType.JOIN_ROOM_REQ.getNumber();
    }

    @Override
    public void resolve(ChannelHandlerContext ctx, Envelope envelope) throws Exception {
        Session session = SessionManager.getInstance().getByChannel(ctx.channel());
        if (session == null) {
            writeFail(ctx, envelope, ErrorCode.NOT_AUTHENTICATED.getNumber(), "Not authenticated");
            return;
        }
        if (!session.isLoggedIn()) {
            writeFail(ctx, envelope, ErrorCode.NOT_LOGGED_IN.getNumber(), "Please login first");
            return;
        }
        JoinRoomReq req = JoinRoomReq.parseFrom(envelope.getPayload());
        RoomManager.JoinResult result = RoomManager.getInstance()
                .join(session, req.getRoomName(), req.getPassword());
        if (result.success) {
            // Choosing a room abandons any heartbeat pending rejoin.
            ReconnectStateStore.getInstance().remove(session.getClientId());
        }

        JoinRoomResp.Builder builder = JoinRoomResp.newBuilder()
                .setSuccess(result.success)
                .setCode(result.code)
                .setMessage(result.message);
        if (result.roomName != null) {
            builder.setRoomName(result.roomName);
        }
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM,
                RoomMsgType.JOIN_ROOM_RESP.getNumber(),
                builder.build(),
                envelope.getRequestId()));

        if (result.success && result.room != null) {
            RoomMemberJoinedNotify notify = RoomMemberJoinedNotify.newBuilder()
                    .setRoomName(result.roomName)
                    .setUsername(session.displayName())
                    .build();
            RoomManager.getInstance().broadcast(result.room,
                    EnvelopeFactory.wrap(Domain.DOMAIN_ROOM,
                            RoomMsgType.ROOM_MEMBER_JOINED_NOTIFY.getNumber(), notify),
                    session.getClientId());
        }
    }

    private void writeFail(ChannelHandlerContext ctx, Envelope envelope, int code, String message) {
        JoinRoomResp resp = JoinRoomResp.newBuilder()
                .setSuccess(false)
                .setCode(code)
                .setMessage(message)
                .build();
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM,
                RoomMsgType.JOIN_ROOM_RESP.getNumber(),
                resp,
                envelope.getRequestId()));
    }
}
