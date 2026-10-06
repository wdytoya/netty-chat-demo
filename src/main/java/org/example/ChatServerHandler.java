package org.example;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.example.codec.EnvelopeFactory;
import org.example.demo.protos.ChatMsgType;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;
import org.example.demo.protos.RoomMemberOfflineNotify;
import org.example.demo.protos.RoomMsgType;
import org.example.reconnect.ReconnectStateStore;
import org.example.resolver.MessageResolverFactory;
import org.example.resolver.Resolver;
import org.example.resolver.chat.ClientHelloResolver;
import org.example.resolver.chat.LoginResolver;
import org.example.resolver.chat.LogoutResolver;
import org.example.resolver.chat.PingMessageResolver;
import org.example.resolver.room.CreateRoomResolver;
import org.example.resolver.room.JoinRoomResolver;
import org.example.resolver.room.LeaveRoomResolver;
import org.example.resolver.room.ListRoomsResolver;
import org.example.resolver.room.ReconnectRoomConfirmResolver;
import org.example.resolver.room.RoomChatResolver;
import org.example.room.Room;
import org.example.room.RoomAuditLogger;
import org.example.room.RoomManager;
import org.example.session.Session;
import org.example.session.SessionManager;

/**
 * Server business handler: routes Envelope by (domain, msgType) and handles heartbeat kick.
 */
public class ChatServerHandler extends ChannelInboundHandlerAdapter {
    private static final io.netty.util.AttributeKey<Boolean> HEARTBEAT_KICKED =
            io.netty.util.AttributeKey.valueOf("heartbeatKicked");

    private final MessageResolverFactory resolverFactory = MessageResolverFactory.getInstance();

    static {
        MessageResolverFactory factory = MessageResolverFactory.getInstance();
        factory.registerResolver(new ClientHelloResolver());
        factory.registerResolver(new LoginResolver());
        factory.registerResolver(new LogoutResolver());
        factory.registerResolver(new PingMessageResolver());
        factory.registerResolver(new CreateRoomResolver());
        factory.registerResolver(new ListRoomsResolver());
        factory.registerResolver(new JoinRoomResolver());
        factory.registerResolver(new LeaveRoomResolver());
        factory.registerResolver(new RoomChatResolver());
        factory.registerResolver(new ReconnectRoomConfirmResolver());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Envelope envelope = (Envelope) msg;
        boolean hello = envelope.getDomain() == Domain.DOMAIN_CHAT
                && envelope.getMsgType() == ChatMsgType.CLIENT_HELLO_REQ.getNumber();
        if (!hello) {
            Session session = SessionManager.getInstance().getByChannel(ctx.channel());
            if (session == null) {
                System.out.println("[REJECT] unauthenticated domain="
                        + envelope.getDomain() + " msgType=" + envelope.getMsgType()
                        + " remote=" + ctx.channel().remoteAddress());
                return;
            }
        }
        Resolver resolver = resolverFactory.getMessageResolver(envelope);
        resolver.resolve(ctx, envelope);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                handleHeartbeatKick(ctx);
            }
        }
    }

    private void handleHeartbeatKick(ChannelHandlerContext ctx) {
        Session session = SessionManager.getInstance().getByChannel(ctx.channel());
        String clientId = session == null ? "-" : session.getClientId();
        String username = session == null ? "-" : session.logUsername();
        System.out.println("[KICK] clientId=" + clientId
                + " username=" + username
                + " remote=" + ctx.channel().remoteAddress()
                + " reason=reader idle 60s");

        ctx.channel().attr(HEARTBEAT_KICKED).set(Boolean.TRUE);
        if (session != null) {
            // Start 1-minute grace for reconnectToken + username; then server releases them.
            SessionManager.getInstance().beginDisconnectedGrace(session.getClientId());
        }
        if (session != null && session.isInRoom()) {
            String roomName = session.getCurrentRoomName();
            String broadcastName = session.displayName();
            Room room = RoomManager.getInstance().forceLeave(session);
            boolean roomStillExists = RoomManager.getInstance().getRoom(roomName) != null;

            if (roomStillExists) {
                ReconnectStateStore.getInstance().save(clientId, roomName);
                RoomAuditLogger.event("OFFLINE", roomName, username, "heartbeat kick");
                RoomMemberOfflineNotify notify = RoomMemberOfflineNotify.newBuilder()
                        .setRoomName(roomName)
                        .setUsername(broadcastName)
                        .build();
                RoomManager.getInstance().broadcast(room,
                        EnvelopeFactory.wrap(Domain.DOMAIN_ROOM,
                                RoomMsgType.ROOM_MEMBER_OFFLINE_NOTIFY.getNumber(), notify),
                        clientId);
            } else {
                RoomAuditLogger.event("OFFLINE", roomName, username,
                        "heartbeat kick, room removed (empty)");
            }
        }
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Boolean kicked = ctx.channel().attr(HEARTBEAT_KICKED).get();
        Session session = SessionManager.getInstance().getByChannel(ctx.channel());
        if (!Boolean.TRUE.equals(kicked) && session != null && session.isInRoom()) {
            String roomName = session.getCurrentRoomName();
            String clientId = session.getClientId();
            String username = session.logUsername();
            String broadcastName = session.displayName();
            Room room = RoomManager.getInstance().forceLeave(session);
            RoomAuditLogger.event("DISCONNECT", roomName, username, "channel inactive");
            if (room != null && RoomManager.getInstance().getRoom(roomName) != null) {
                RoomMemberOfflineNotify notify = RoomMemberOfflineNotify.newBuilder()
                        .setRoomName(roomName)
                        .setUsername(broadcastName)
                        .build();
                RoomManager.getInstance().broadcast(room,
                        EnvelopeFactory.wrap(Domain.DOMAIN_ROOM,
                                RoomMsgType.ROOM_MEMBER_OFFLINE_NOTIFY.getNumber(), notify),
                        clientId);
            }
        }
        SessionManager.getInstance().onChannelInactive(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
