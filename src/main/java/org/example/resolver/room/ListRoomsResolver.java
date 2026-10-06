package org.example.resolver.room;

import io.netty.channel.ChannelHandlerContext;
import org.example.codec.EnvelopeFactory;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;
import org.example.demo.protos.ErrorCode;
import org.example.demo.protos.ListRoomsResp;
import org.example.demo.protos.RoomMsgType;
import org.example.resolver.Resolver;
import org.example.room.RoomManager;
import org.example.session.Session;
import org.example.session.SessionManager;

public class ListRoomsResolver implements Resolver {
    @Override
    public boolean support(Domain domain, int msgType) {
        return domain == Domain.DOMAIN_ROOM
                && msgType == RoomMsgType.LIST_ROOMS_REQ.getNumber();
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
        ListRoomsResp resp = ListRoomsResp.newBuilder()
                .setSuccess(true)
                .setCode(ErrorCode.OK.getNumber())
                .setMessage("OK")
                .addAllRooms(RoomManager.getInstance().listRoomInfos())
                .build();
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM,
                RoomMsgType.LIST_ROOMS_RESP.getNumber(),
                resp,
                envelope.getRequestId()));
    }

    private void writeFail(ChannelHandlerContext ctx, Envelope envelope, int code, String message) {
        ListRoomsResp resp = ListRoomsResp.newBuilder()
                .setSuccess(false)
                .setCode(code)
                .setMessage(message)
                .build();
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM,
                RoomMsgType.LIST_ROOMS_RESP.getNumber(),
                resp,
                envelope.getRequestId()));
    }
}
