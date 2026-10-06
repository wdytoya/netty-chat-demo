package org.example;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.example.codec.EnvelopeFactory;
import org.example.demo.protos.ChatMsgType;
import org.example.demo.protos.ClientHelloReq;
import org.example.demo.protos.ClientHelloResp;
import org.example.demo.protos.CreateRoomResp;
import org.example.demo.protos.Domain;
import org.example.demo.protos.Envelope;
import org.example.demo.protos.ErrorCode;
import org.example.demo.protos.JoinRoomResp;
import org.example.demo.protos.LeaveRoomResp;
import org.example.demo.protos.ListRoomsResp;
import org.example.demo.protos.LoginResp;
import org.example.demo.protos.LogoutResp;
import org.example.demo.protos.Ping;
import org.example.demo.protos.ReconnectRoomAvailable;
import org.example.demo.protos.ReconnectRoomConfirmResp;
import org.example.demo.protos.RoomChatNotify;
import org.example.demo.protos.RoomChatResp;
import org.example.demo.protos.RoomInfo;
import org.example.demo.protos.RoomMemberJoinedNotify;
import org.example.demo.protos.RoomMemberLeftNotify;
import org.example.demo.protos.RoomMemberOfflineNotify;
import org.example.demo.protos.RoomMemberReconnectedNotify;
import org.example.demo.protos.RoomMsgType;

/**
 * Client inbound handler: concise console output, HELLO / PING.
 */
public class ChatClientHandler extends ChannelInboundHandlerAdapter {
    private final ChatClient client;

    public ChatClientHandler(ChatClient client) {
        this.client = client;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ClientHelloReq.Builder builder = ClientHelloReq.newBuilder();
        if (client.getCachedClientId() != null && client.getCachedReconnectToken() != null) {
            builder.setClientId(client.getCachedClientId())
                    .setReconnectToken(client.getCachedReconnectToken());
        }
        ctx.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_CHAT,
                ChatMsgType.CLIENT_HELLO_REQ.getNumber(),
                builder.build()));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Envelope envelope = (Envelope) msg;
        if (envelope.getDomain() == Domain.DOMAIN_CHAT) {
            handleChat(envelope);
        } else if (envelope.getDomain() == Domain.DOMAIN_ROOM) {
            handleRoom(envelope);
        }
    }

    private void handleChat(Envelope envelope) throws Exception {
        int msgType = envelope.getMsgType();
        if (msgType == ChatMsgType.CLIENT_HELLO_RESP.getNumber()) {
            ClientHelloResp resp = ClientHelloResp.parseFrom(envelope.getPayload());
            client.cacheCredentials(resp.getClientId(), resp.getReconnectToken());
            if (resp.hasUsername() && !resp.getUsername().isEmpty()) {
                client.cacheUsername(resp.getUsername());
                System.out.println("Session restored as " + resp.getUsername());
            } else {
                // New or non-restored identity: drop stale login UI state from previous session.
                client.cacheUsername(null);
                client.setPendingRejoinRoom(null);
                System.out.println("Please login <username>");
            }
            client.printHelp();
        } else if (msgType == ChatMsgType.LOGIN_RESP.getNumber()) {
            LoginResp resp = LoginResp.parseFrom(envelope.getPayload());
            if (resp.getSuccess()) {
                client.cacheUsername(resp.getUsername());
                System.out.println("Logged in as " + resp.getUsername());
                client.printHelp();
            } else {
                System.out.println("Login failed: " + resp.getMessage());
            }
        } else if (msgType == ChatMsgType.LOGOUT_RESP.getNumber()) {
            LogoutResp.parseFrom(envelope.getPayload());
        } else if (msgType == ChatMsgType.PONG.getNumber()) {
            // quiet
        }
    }

    private void handleRoom(Envelope envelope) throws Exception {
        int msgType = envelope.getMsgType();
        if (msgType == RoomMsgType.CREATE_ROOM_RESP.getNumber()) {
            CreateRoomResp resp = CreateRoomResp.parseFrom(envelope.getPayload());
            if (resp.getSuccess()) {
                client.setPendingRejoinRoom(null);
                client.setCurrentRoomName(resp.getRoomName());
                System.out.println("Created room " + resp.getRoomName());
            } else {
                System.out.println("Create failed: " + resp.getMessage());
            }
        } else if (msgType == RoomMsgType.LIST_ROOMS_RESP.getNumber()) {
            ListRoomsResp resp = ListRoomsResp.parseFrom(envelope.getPayload());
            if (resp.hasSuccess() && !resp.getSuccess()) {
                System.out.println("List failed: " + resp.getMessage());
                return;
            }
            if (resp.getRoomsCount() == 0) {
                System.out.println("No rooms.");
                return;
            }
            System.out.println("Rooms:");
            for (RoomInfo info : resp.getRoomsList()) {
                System.out.println("  " + info.getRoomName() + " (" + info.getOnlineCount() + ")");
            }
        } else if (msgType == RoomMsgType.JOIN_ROOM_RESP.getNumber()) {
            JoinRoomResp resp = JoinRoomResp.parseFrom(envelope.getPayload());
            if (resp.getSuccess()) {
                client.setPendingRejoinRoom(null);
                client.setCurrentRoomName(resp.getRoomName());
                System.out.println("Joined " + resp.getRoomName());
            } else {
                System.out.println("Join failed: " + resp.getMessage());
            }
        } else if (msgType == RoomMsgType.LEAVE_ROOM_RESP.getNumber()) {
            LeaveRoomResp resp = LeaveRoomResp.parseFrom(envelope.getPayload());
            if (resp.getSuccess()) {
                client.setCurrentRoomName(null);
                System.out.println("Left room");
            } else {
                if (resp.getCode() == ErrorCode.NOT_IN_ROOM.getNumber()) {
                    client.setCurrentRoomName(null);
                }
                System.out.println("Leave failed: " + resp.getMessage());
            }
        } else if (msgType == RoomMsgType.ROOM_CHAT_RESP.getNumber()) {
            RoomChatResp resp = RoomChatResp.parseFrom(envelope.getPayload());
            if (!resp.getSuccess()) {
                if (resp.getCode() == ErrorCode.NOT_IN_ROOM.getNumber()) {
                    client.setCurrentRoomName(null);
                }
                System.out.println("Send failed: " + resp.getMessage());
            }
        } else if (msgType == RoomMsgType.ROOM_CHAT_NOTIFY.getNumber()) {
            RoomChatNotify notify = RoomChatNotify.parseFrom(envelope.getPayload());
            System.out.println("[" + notify.getRoomName() + "] "
                    + notify.getSenderName() + ": " + notify.getText());
        } else if (msgType == RoomMsgType.ROOM_MEMBER_OFFLINE_NOTIFY.getNumber()) {
            RoomMemberOfflineNotify notify = RoomMemberOfflineNotify.parseFrom(envelope.getPayload());
            System.out.println("[" + notify.getRoomName() + "] "
                    + notify.getUsername() + " is offline");
        } else if (msgType == RoomMsgType.RECONNECT_ROOM_AVAILABLE.getNumber()) {
            ReconnectRoomAvailable available = ReconnectRoomAvailable.parseFrom(envelope.getPayload());
            client.setPendingRejoinRoom(available.getRoomName());
            System.out.println("You can rejoin " + available.getRoomName() + ". Type: rejoin");
        } else if (msgType == RoomMsgType.RECONNECT_ROOM_CONFIRM_RESP.getNumber()) {
            ReconnectRoomConfirmResp resp = ReconnectRoomConfirmResp.parseFrom(envelope.getPayload());
            if (resp.getSuccess()) {
                client.setPendingRejoinRoom(null);
                if (resp.hasRoomName()) {
                    client.setCurrentRoomName(resp.getRoomName());
                }
                System.out.println("Rejoined " + resp.getRoomName());
            } else {
                int code = resp.getCode();
                if (code != ErrorCode.ROOM_FULL.getNumber()) {
                    client.setPendingRejoinRoom(null);
                }
                System.out.println("Rejoin failed: " + resp.getMessage());
            }
        } else if (msgType == RoomMsgType.ROOM_MEMBER_RECONNECTED_NOTIFY.getNumber()) {
            RoomMemberReconnectedNotify notify =
                    RoomMemberReconnectedNotify.parseFrom(envelope.getPayload());
            System.out.println("[" + notify.getRoomName() + "] "
                    + notify.getUsername() + " reconnected");
        } else if (msgType == RoomMsgType.ROOM_MEMBER_JOINED_NOTIFY.getNumber()) {
            RoomMemberJoinedNotify notify = RoomMemberJoinedNotify.parseFrom(envelope.getPayload());
            System.out.println("[" + notify.getRoomName() + "] "
                    + notify.getUsername() + " joined");
        } else if (msgType == RoomMsgType.ROOM_MEMBER_LEFT_NOTIFY.getNumber()) {
            RoomMemberLeftNotify notify = RoomMemberLeftNotify.parseFrom(envelope.getPayload());
            System.out.println("[" + notify.getRoomName() + "] "
                    + notify.getUsername() + " left");
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                if (!client.isHeartbeatEnabled()) {
                    return;
                }
                ctx.writeAndFlush(EnvelopeFactory.wrap(
                        Domain.DOMAIN_CHAT,
                        ChatMsgType.PING.getNumber(),
                        Ping.getDefaultInstance()));
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (client.isQuitting()) {
            return;
        }
        client.onDisconnected();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
