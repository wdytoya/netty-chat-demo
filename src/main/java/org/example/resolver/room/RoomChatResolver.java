package org.example.resolver.room;

import io.netty.channel.ChannelHandlerContext;
import org.example.codec.EnvelopeFactory;
import org.example.config.ServerConfig;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;
import org.example.demo.protos.ErrorCode;
import org.example.demo.protos.RoomChatNotify;
import org.example.demo.protos.RoomChatReq;
import org.example.demo.protos.RoomChatResp;
import org.example.demo.protos.RoomMsgType;
import org.example.resolver.Resolver;
import org.example.room.Room;
import org.example.room.RoomAuditLogger;
import org.example.room.RoomManager;
import org.example.session.Session;
import org.example.session.SessionManager;

import java.util.UUID;

public class RoomChatResolver implements Resolver {
    @Override
    public boolean support(Domain domain, int msgType) {
        return domain == Domain.DOMAIN_ROOM
                && msgType == RoomMsgType.ROOM_CHAT_REQ.getNumber();
    }

    @Override
    public void resolve(ChannelHandlerContext ctx, Envelope envelope) throws Exception {
        Session session = SessionManager.getInstance().getByChannel(ctx.channel());
        if (session == null) {
            writeResp(ctx, envelope, false, ErrorCode.NOT_AUTHENTICATED.getNumber(),
                    "Not authenticated", null);
            return;
        }
        if (!session.isLoggedIn()) {
            writeResp(ctx, envelope, false, ErrorCode.NOT_LOGGED_IN.getNumber(),
                    "Please login first", null);
            return;
        }
        if (!session.isInRoom()) {
            writeResp(ctx, envelope, false, ErrorCode.NOT_IN_ROOM.getNumber(),
                    "Not in any room", null);
            return;
        }

        RoomChatReq req = RoomChatReq.parseFrom(envelope.getPayload());
        String text = req.hasText() ? req.getText() : "";
        if (text.isEmpty()) {
            writeResp(ctx, envelope, false, ErrorCode.BAD_REQUEST.getNumber(),
                    "Message is empty", null);
            return;
        }
        if (text.length() > ServerConfig.MAX_MESSAGE_LENGTH) {
            writeResp(ctx, envelope, false, ErrorCode.MSG_TOO_LONG.getNumber(),
                    "Message too long", null);
            return;
        }

        String msgId = req.hasMsgId() && !req.getMsgId().isEmpty()
                ? req.getMsgId()
                : UUID.randomUUID().toString();
        String roomName = session.getCurrentRoomName();
        Room room = RoomManager.getInstance().getRoom(roomName);
        if (room == null || !room.contains(session.getClientId())) {
            session.setCurrentRoomName(null);
            writeResp(ctx, envelope, false, ErrorCode.NOT_IN_ROOM.getNumber(),
                    "Not in any room", null);
            return;
        }

        writeResp(ctx, envelope, true, ErrorCode.OK.getNumber(), "OK", msgId);
        RoomAuditLogger.chat(roomName, session.logUsername(), msgId, text);

        RoomChatNotify notify = RoomChatNotify.newBuilder()
                .setRoomName(roomName)
                .setSenderName(session.displayName())
                .setMsgId(msgId)
                .setText(text)
                .setTimestamp(System.currentTimeMillis())
                .build();
        RoomManager.getInstance().broadcast(room,
                EnvelopeFactory.wrap(Domain.DOMAIN_ROOM,
                        RoomMsgType.ROOM_CHAT_NOTIFY.getNumber(), notify),
                session.getClientId());
    }

    private void writeResp(ChannelHandlerContext ctx, Envelope envelope,
                           boolean success, int code, String message, String msgId) {
        RoomChatResp.Builder builder = RoomChatResp.newBuilder()
                .setSuccess(success)
                .setCode(code)
                .setMessage(message);
        if (msgId != null) {
            builder.setMsgId(msgId);
        }
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM,
                RoomMsgType.ROOM_CHAT_RESP.getNumber(),
                builder.build(),
                envelope.getRequestId()));
    }
}
