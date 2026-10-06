package org.example.room;

import org.example.config.ServerConfig;
import org.example.demo.protos.Envelope;
import org.example.demo.protos.ErrorCode;
import org.example.demo.protos.RoomInfo;
import org.example.session.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory room registry.
 */
public class RoomManager {
    private static final RoomManager INSTANCE = new RoomManager();

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    private RoomManager() {
    }

    public static RoomManager getInstance() {
        return INSTANCE;
    }

    public CreateResult create(Session session, String roomName, String password) {
        ValidationError validation = validateRoomNameAndPassword(roomName, password);
        if (validation != null) {
            return CreateResult.fail(validation.code, validation.message);
        }
        roomName = roomName.trim();
        if (session.isInRoom()) {
            return CreateResult.fail(ErrorCode.ALREADY_IN_ROOM.getNumber(),
                    "Already in room " + session.getCurrentRoomName() + ", leave first");
        }

        PasswordHasher.SaltedHash salted = PasswordHasher.hash(password);
        Room room = new Room(roomName, salted.getSaltB64(), salted.getHashB64());
        Room prev = rooms.putIfAbsent(roomName, room);
        if (prev != null) {
            return CreateResult.fail(ErrorCode.ROOM_EXISTS.getNumber(), "Room already exists: " + roomName);
        }

        room.addMember(session.getClientId(), session.getChannel());
        session.setCurrentRoomName(roomName);
        RoomAuditLogger.event("CREATE", roomName, session.logUsername(), "created and joined");
        return CreateResult.ok(roomName);
    }

    public JoinResult join(Session session, String roomName, String password) {
        ValidationError validation = validateRoomNameAndPassword(roomName, password);
        if (validation != null) {
            return JoinResult.fail(validation.code, validation.message);
        }
        roomName = roomName.trim();
        if (session.isInRoom()) {
            return JoinResult.fail(ErrorCode.ALREADY_IN_ROOM.getNumber(),
                    "Already in room " + session.getCurrentRoomName() + ", leave first");
        }

        Room room = rooms.get(roomName);
        if (room == null) {
            return JoinResult.fail(ErrorCode.ROOM_NOT_FOUND.getNumber(), "Room not found: " + roomName);
        }
        if (!room.verifyPassword(password)) {
            return JoinResult.fail(ErrorCode.BAD_PASSWORD.getNumber(), "Incorrect password");
        }
        if (room.isFull()) {
            return JoinResult.fail(ErrorCode.ROOM_FULL.getNumber(), "Room is full");
        }

        room.addMember(session.getClientId(), session.getChannel());
        session.setCurrentRoomName(roomName);
        RoomAuditLogger.event("JOIN", roomName, session.logUsername(), "joined");
        return JoinResult.ok(roomName, room);
    }

    public LeaveResult leave(Session session) {
        if (!session.isInRoom()) {
            return LeaveResult.fail(ErrorCode.NOT_IN_ROOM.getNumber(), "Not in any room");
        }
        String roomName = session.getCurrentRoomName();
        Room room = rooms.get(roomName);
        if (room != null) {
            room.removeMember(session.getClientId());
            maybeRemoveEmptyRoom(room);
        }
        session.setCurrentRoomName(null);
        RoomAuditLogger.event("LEAVE", roomName, session.logUsername(), "left");
        return LeaveResult.ok(roomName, room);
    }

    /**
     * Remove member due to disconnect / heartbeat kick. Returns room if member was present.
     */
    public Room forceLeave(Session session) {
        if (session == null || !session.isInRoom()) {
            return null;
        }
        String roomName = session.getCurrentRoomName();
        Room room = rooms.get(roomName);
        if (room != null) {
            room.removeMember(session.getClientId());
            maybeRemoveEmptyRoom(room);
        }
        session.setCurrentRoomName(null);
        return room;
    }

    public RejoinResult rejoin(Session session, String roomName) {
        if (session.isInRoom()) {
            return RejoinResult.fail(ErrorCode.ALREADY_IN_ROOM.getNumber(),
                    "Already in room " + session.getCurrentRoomName());
        }
        Room room = rooms.get(roomName);
        if (room == null) {
            return RejoinResult.fail(ErrorCode.ROOM_NOT_FOUND.getNumber(), "Original room no longer exists");
        }
        if (room.isFull()) {
            return RejoinResult.fail(ErrorCode.ROOM_FULL.getNumber(), "Room is full");
        }
        room.addMember(session.getClientId(), session.getChannel());
        session.setCurrentRoomName(roomName);
        RoomAuditLogger.event("REJOIN", roomName, session.logUsername(), "reconnected into room");
        return RejoinResult.ok(roomName, room);
    }

    public List<RoomInfo> listRoomInfos() {
        List<RoomInfo> list = new ArrayList<>();
        for (Room room : rooms.values()) {
            list.add(RoomInfo.newBuilder()
                    .setRoomName(room.getName())
                    .setOnlineCount(room.onlineCount())
                    .build());
        }
        return list;
    }

    public Room getRoom(String roomName) {
        return rooms.get(roomName);
    }

    public void broadcast(Room room, Envelope envelope, String excludeClientId) {
        if (room != null) {
            room.broadcast(envelope, excludeClientId);
        }
    }

    private void maybeRemoveEmptyRoom(Room room) {
        if (room != null && room.onlineCount() == 0) {
            rooms.remove(room.getName(), room);
        }
    }

    private static ValidationError validateRoomNameAndPassword(String roomName, String password) {
        if (roomName == null || roomName.trim().isEmpty()) {
            return new ValidationError(ErrorCode.BAD_REQUEST.getNumber(), "Room name is empty");
        }
        roomName = roomName.trim();
        if (roomName.length() > ServerConfig.MAX_ROOM_NAME_LENGTH) {
            return new ValidationError(ErrorCode.BAD_REQUEST.getNumber(), "Room name too long");
        }
        if (password == null || password.isEmpty()) {
            return new ValidationError(ErrorCode.BAD_REQUEST.getNumber(), "Password is empty");
        }
        if (password.length() > ServerConfig.MAX_PASSWORD_LENGTH) {
            return new ValidationError(ErrorCode.BAD_REQUEST.getNumber(), "Password too long");
        }
        return null;
    }

    private static final class ValidationError {
        final int code;
        final String message;

        ValidationError(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    public static final class CreateResult {
        public final boolean success;
        public final int code;
        public final String message;
        public final String roomName;

        private CreateResult(boolean success, int code, String message, String roomName) {
            this.success = success;
            this.code = code;
            this.message = message;
            this.roomName = roomName;
        }

        static CreateResult ok(String roomName) {
            return new CreateResult(true, ErrorCode.OK.getNumber(), "OK", roomName);
        }

        static CreateResult fail(int code, String message) {
            return new CreateResult(false, code, message, null);
        }
    }

    public static final class JoinResult {
        public final boolean success;
        public final int code;
        public final String message;
        public final String roomName;
        public final Room room;

        private JoinResult(boolean success, int code, String message, String roomName, Room room) {
            this.success = success;
            this.code = code;
            this.message = message;
            this.roomName = roomName;
            this.room = room;
        }

        static JoinResult ok(String roomName, Room room) {
            return new JoinResult(true, ErrorCode.OK.getNumber(), "OK", roomName, room);
        }

        static JoinResult fail(int code, String message) {
            return new JoinResult(false, code, message, null, null);
        }
    }

    public static final class LeaveResult {
        public final boolean success;
        public final int code;
        public final String message;
        public final String roomName;
        public final Room room;

        private LeaveResult(boolean success, int code, String message, String roomName, Room room) {
            this.success = success;
            this.code = code;
            this.message = message;
            this.roomName = roomName;
            this.room = room;
        }

        static LeaveResult ok(String roomName, Room room) {
            return new LeaveResult(true, ErrorCode.OK.getNumber(), "OK", roomName, room);
        }

        static LeaveResult fail(int code, String message) {
            return new LeaveResult(false, code, message, null, null);
        }
    }

    public static final class RejoinResult {
        public final boolean success;
        public final int code;
        public final String message;
        public final String roomName;
        public final Room room;

        private RejoinResult(boolean success, int code, String message, String roomName, Room room) {
            this.success = success;
            this.code = code;
            this.message = message;
            this.roomName = roomName;
            this.room = room;
        }

        static RejoinResult ok(String roomName, Room room) {
            return new RejoinResult(true, ErrorCode.OK.getNumber(), "OK", roomName, room);
        }

        static RejoinResult fail(int code, String message) {
            return new RejoinResult(false, code, message, null, null);
        }
    }
}
