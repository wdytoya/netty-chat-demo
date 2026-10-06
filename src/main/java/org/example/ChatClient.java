package org.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.example.codec.EnvelopeDecoder;
import org.example.codec.EnvelopeEncoder;
import org.example.codec.EnvelopeFactory;
import org.example.config.ServerConfig;
import org.example.demo.protos.ChatMsgType;
import org.example.demo.protos.CreateRoomReq;
import org.example.demo.protos.Domain;
import org.example.demo.protos.JoinRoomReq;
import org.example.demo.protos.LeaveRoomReq;
import org.example.demo.protos.ListRoomsReq;
import org.example.demo.protos.LoginReq;
import org.example.demo.protos.LogoutReq;
import org.example.demo.protos.ReconnectRoomConfirmReq;
import org.example.demo.protos.RoomChatReq;
import org.example.demo.protos.RoomMsgType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Console chat client with manual reconnect and room commands.
 */
public class ChatClient {
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();
    private final Bootstrap bootstrap = new Bootstrap();
    private final AtomicReference<Channel> channelRef = new AtomicReference<>();
    private final AtomicBoolean quitting = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private volatile String cachedClientId;
    private volatile String cachedReconnectToken;
    private volatile String cachedUsername;
    private volatile String pendingRejoinRoom;
    private volatile String currentRoomName;
    /** When false, client stops auto PING so server reader-idle kick can be tested. */
    private volatile boolean heartbeatEnabled = true;

    public static void main(String[] args) throws Exception {
        new ChatClient().start();
    }

    private void start() throws Exception {
        final ChatClient self = this;
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                new LengthFieldBasedFrameDecoder(
                                        ServerConfig.MAX_FRAME_LENGTH, 0, 4, 0, 4),
                                new IdleStateHandler(0, ServerConfig.CLIENT_WRITER_IDLE_SECONDS, 0),
                                new EnvelopeEncoder(),
                                new EnvelopeDecoder(),
                                new ChatClientHandler(self));
                    }
                })
                .option(ChannelOption.SO_KEEPALIVE, true);

        connect();

        Thread consoleThread = new Thread(this::consoleLoop, "console-input");
        consoleThread.setDaemon(true);
        consoleThread.start();

        while (!quitting.get()) {
            Thread.sleep(500);
        }
        Channel channel = channelRef.get();
        if (channel != null) {
            channel.close().syncUninterruptibly();
        }
        workerGroup.shutdownGracefully();
    }

    private void connect() {
        if (quitting.get()) {
            return;
        }
        Channel existing = channelRef.get();
        if (existing != null && existing.isActive()) {
            System.out.println("Already connected.");
            return;
        }
        if (!connecting.compareAndSet(false, true)) {
            System.out.println("Connecting...");
            return;
        }
        bootstrap.connect("localhost", ServerConfig.SERVER_PORT).addListener(future -> {
            connecting.set(false);
            if (future.isSuccess()) {
                Channel channel = ((io.netty.channel.ChannelFuture) future).channel();
                channelRef.set(channel);
                System.out.println("Connected.");
            } else {
                System.out.println("Connect failed: " + future.cause().getMessage()
                        + ". Type reconnect to retry.");
            }
        });
    }

    void onDisconnected() {
        channelRef.set(null);
        currentRoomName = null;
        if (!quitting.get()) {
            System.out.println("Disconnected. Type reconnect to reconnect, or quit to exit.");
            printHelp();
        }
    }

    private void consoleLoop() {
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (!handleCommand(line)) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        quitting.set(true);
    }

    private boolean handleCommand(String line) {
        String[] parts = line.split("\\s+", 3);
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "login":
                if (!requireConnected()) {
                    return true;
                }
                if (isLoggedIn()) {
                    System.out.println("Already logged in as " + cachedUsername);
                    return true;
                }
                if (parts.length < 2) {
                    System.out.println("Usage: login <username>");
                    return true;
                }
                sendLogin(parts[1]);
                return true;
            case "create":
                if (!requireLoggedIn()) {
                    return true;
                }
                if (parts.length < 3) {
                    System.out.println("Usage: create <room> <password>");
                    return true;
                }
                sendCreate(parts[1], parts[2]);
                return true;
            case "list":
                if (!requireLoggedIn()) {
                    return true;
                }
                sendList();
                return true;
            case "join":
                if (!requireLoggedIn()) {
                    return true;
                }
                if (parts.length < 3) {
                    System.out.println("Usage: join <room> <password>");
                    return true;
                }
                sendJoin(parts[1], parts[2]);
                return true;
            case "msg":
                if (!requireLoggedIn()) {
                    return true;
                }
                if (parts.length < 2) {
                    System.out.println("Usage: msg <text>");
                    return true;
                }
                sendMsg(line.substring(line.indexOf(' ') + 1));
                return true;
            case "leave":
                if (!requireLoggedIn()) {
                    return true;
                }
                sendLeave();
                return true;
            case "reconnect":
                if (isConnected()) {
                    if (!isLoggedIn()) {
                        System.out.println("Please login first.");
                    } else {
                        System.out.println("Already connected.");
                    }
                    return true;
                }
                connect();
                return true;
            case "rejoin":
                if (!requireLoggedIn()) {
                    return true;
                }
                if (pendingRejoinRoom == null) {
                    System.out.println("No room to rejoin.");
                    return true;
                }
                sendRejoin();
                return true;
            case "noping":
                if (!requireLoggedIn()) {
                    return true;
                }
                heartbeatEnabled = false;
                System.out.println("Auto PING disabled. Stay idle ~60s to trigger kick.");
                return true;
            case "pingon":
                if (!requireLoggedIn()) {
                    return true;
                }
                heartbeatEnabled = true;
                System.out.println("Auto PING enabled.");
                return true;
            case "help":
                printHelp();
                return true;
            case "quit":
                doQuit();
                return false;
            default:
                System.out.println("Unknown command: " + cmd + ". Type help.");
                return true;
        }
    }

    private void doQuit() {
        System.out.println("Bye.");
        quitting.set(true);
        Channel ch = channelRef.get();
        if (ch != null && ch.isActive()) {
            try {
                ch.writeAndFlush(EnvelopeFactory.wrap(
                        Domain.DOMAIN_CHAT,
                        ChatMsgType.LOGOUT_REQ.getNumber(),
                        LogoutReq.getDefaultInstance())).syncUninterruptibly();
            } catch (Exception ignored) {
                // best-effort logout before close
            }
            ch.close();
        }
        channelRef.set(null);
        clearLocalSession();
    }

    void printHelp() {
        System.out.println("Commands:");
        boolean connected = isConnected();
        if (!connected) {
            System.out.println("  reconnect");
            System.out.println("  help");
            System.out.println("  quit");
            return;
        }
        if (!isLoggedIn()) {
            System.out.println("  login <username>");
            System.out.println("  help");
            System.out.println("  quit");
            return;
        }
        System.out.println("  create <room> <password>");
        System.out.println("  list");
        System.out.println("  join <room> <password>");
        System.out.println("  msg <text>");
        System.out.println("  leave");
        if (pendingRejoinRoom != null) {
            System.out.println("  rejoin");
        }
        System.out.println("  noping / pingon");
        System.out.println("  help");
        System.out.println("  quit");
    }

    private boolean isConnected() {
        Channel channel = channelRef.get();
        return channel != null && channel.isActive();
    }

    private boolean isLoggedIn() {
        return cachedUsername != null && !cachedUsername.isEmpty();
    }

    private boolean requireConnected() {
        if (!isConnected()) {
            System.out.println("Not connected. Type reconnect.");
            return false;
        }
        return true;
    }

    private boolean requireLoggedIn() {
        if (!requireConnected()) {
            return false;
        }
        if (!isLoggedIn()) {
            System.out.println("Please login first.");
            return false;
        }
        return true;
    }

    boolean isHeartbeatEnabled() {
        return heartbeatEnabled;
    }

    boolean isQuitting() {
        return quitting.get();
    }

    private Channel requireActiveChannel() {
        Channel channel = channelRef.get();
        if (channel == null || !channel.isActive()) {
            System.out.println("Not connected. Type reconnect.");
            return null;
        }
        return channel;
    }

    private void sendLogin(String username) {
        Channel channel = requireActiveChannel();
        if (channel == null) {
            return;
        }
        LoginReq req = LoginReq.newBuilder().setUsername(username).build();
        channel.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_CHAT, ChatMsgType.LOGIN_REQ.getNumber(), req));
    }

    private void sendCreate(String room, String password) {
        Channel channel = requireActiveChannel();
        if (channel == null) {
            return;
        }
        CreateRoomReq req = CreateRoomReq.newBuilder()
                .setRoomName(room)
                .setPassword(password)
                .build();
        channel.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM, RoomMsgType.CREATE_ROOM_REQ.getNumber(), req));
    }

    private void sendList() {
        Channel channel = requireActiveChannel();
        if (channel == null) {
            return;
        }
        channel.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM,
                RoomMsgType.LIST_ROOMS_REQ.getNumber(),
                ListRoomsReq.getDefaultInstance()));
    }

    private void sendJoin(String room, String password) {
        Channel channel = requireActiveChannel();
        if (channel == null) {
            return;
        }
        JoinRoomReq req = JoinRoomReq.newBuilder()
                .setRoomName(room)
                .setPassword(password)
                .build();
        channel.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM, RoomMsgType.JOIN_ROOM_REQ.getNumber(), req));
    }

    private void sendMsg(String text) {
        Channel channel = requireActiveChannel();
        if (channel == null) {
            return;
        }
        RoomChatReq req = RoomChatReq.newBuilder()
                .setText(text)
                .setMsgId(UUID.randomUUID().toString())
                .build();
        String name = cachedUsername == null ? "me" : cachedUsername;
        if (currentRoomName != null) {
            System.out.println("[" + currentRoomName + "] " + name + ": " + text);
        } else {
            System.out.println(name + ": " + text);
        }
        channel.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM, RoomMsgType.ROOM_CHAT_REQ.getNumber(), req));
    }

    private void sendLeave() {
        Channel channel = requireActiveChannel();
        if (channel == null) {
            return;
        }
        channel.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM,
                RoomMsgType.LEAVE_ROOM_REQ.getNumber(),
                LeaveRoomReq.getDefaultInstance()));
    }

    private void sendRejoin() {
        Channel channel = requireActiveChannel();
        if (channel == null) {
            return;
        }
        channel.writeAndFlush(EnvelopeFactory.wrap(
                Domain.DOMAIN_ROOM,
                RoomMsgType.RECONNECT_ROOM_CONFIRM_REQ.getNumber(),
                ReconnectRoomConfirmReq.getDefaultInstance()));
    }

    void cacheCredentials(String clientId, String reconnectToken) {
        this.cachedClientId = clientId;
        this.cachedReconnectToken = reconnectToken;
    }

    void cacheUsername(String username) {
        this.cachedUsername = username;
    }

    void clearLocalSession() {
        cachedClientId = null;
        cachedReconnectToken = null;
        cachedUsername = null;
        pendingRejoinRoom = null;
        currentRoomName = null;
    }

    String getCachedClientId() {
        return cachedClientId;
    }

    String getCachedReconnectToken() {
        return cachedReconnectToken;
    }

    String getCachedUsername() {
        return cachedUsername;
    }

    void setPendingRejoinRoom(String roomName) {
        this.pendingRejoinRoom = roomName;
    }

    void setCurrentRoomName(String roomName) {
        this.currentRoomName = roomName;
    }

    String getCurrentRoomName() {
        return currentRoomName;
    }
}
