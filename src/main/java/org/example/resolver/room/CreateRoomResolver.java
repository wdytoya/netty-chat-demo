package org.example.resolver.room;

import io.netty.channel.ChannelHandlerContext;
import org.example.codec.EnvelopeFactory;
import org.example.demo.protos.CreateRoomReq;
import org.example.demo.protos.CreateRoomResp;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;
import org.example.demo.protos.ErrorCode;
import org.example.demo.protos.RoomMsgType;
import org.example.reconnect.ReconnectStateStore;
import org.example.resolver.Resolver;
import org.example.room.RoomManager;
import org.example.session.Session;
import org.example.session.SessionManager;

public class CreateRoomResolver implements Resolver {
    @Override
    public boolean support(Domain domain, int msgType) {
        return domain == Domain.DOMAIN_ROOM
                && msgType == RoomMsgType.CREATE_ROOM_REQ.getNumber();
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
        CreateRoomReq req = CreateRoomReq.parseFrom(envelope.getPayload());
        RoomManager.CreateResult result = RoomManager.getInstance()
                .create(session, req.getRoomName(), req.getPassword());
        if (result.success) {
            // Choosing a new room abandons any heartbeat pending rejoin.
            ReconnectStateStore.getInstance().remove(session.getClientId());
        }
        CreateRoomResp.Builder builder = CreateRoomResp.newBuilder()
                .setSuccess(result.success)
                .setCode(result.code)
                .setMessage(result.message);
        if (result.roomName != null) {
            builder.setRoomName(result.roomName);
        }
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM,
                RoomMsgType.CREATE_ROOM_RESP.getNumber(),
                builder.build(),
                envelope.getRequestId()));
    }

    private void writeFail(ChannelHandlerContext ctx, Envelope envelope, int code, String message) {
        CreateRoomResp resp = CreateRoomResp.newBuilder()
                .setSuccess(false)
                .setCode(code)
                .setMessage(message)
                .build();
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM,
                RoomMsgType.CREATE_ROOM_RESP.getNumber(),
                resp,
                envelope.getRequestId()));
    }
}
