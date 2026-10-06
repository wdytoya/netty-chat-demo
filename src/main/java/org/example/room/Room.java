package org.example.room;

import io.netty.channel.Channel;
import org.example.config.ServerConfig;
import org.example.demo.protos.Envelope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single chat room with salted password hash and concurrent member map.
 */
public class Room {
    private final String name;
    private final String passwordSaltB64;
    private final String passwordHashB64;
    private final Map<String, Channel> members = new ConcurrentHashMap<>();

    public Room(String name, String passwordSaltB64, String passwordHashB64) {
        this.name = name;
        this.passwordSaltB64 = passwordSaltB64;
        this.passwordHashB64 = passwordHashB64;
    }

    public String getName() {
        return name;
    }

    public boolean verifyPassword(String password) {
        return PasswordHasher.matches(password, passwordSaltB64, passwordHashB64);
    }

    public int onlineCount() {
        return members.size();
    }

    public boolean isFull() {
        return members.size() >= ServerConfig.MAX_ROOM_MEMBERS;
    }

    public boolean contains(String clientId) {
        return members.containsKey(clientId);
    }

    public void addMember(String clientId, Channel channel) {
        members.put(clientId, channel);
    }

    public Channel removeMember(String clientId) {
        return members.remove(clientId);
    }

    /**
     * Broadcast to all members except optional excluded clientId.
     * Iterates the live ConcurrentHashMap so concurrent leave is less likely to still receive.
     */
    public void broadcast(Envelope envelope, String excludeClientId) {
        for (Map.Entry<String, Channel> entry : members.entrySet()) {
            if (excludeClientId != null && excludeClientId.equals(entry.getKey())) {
                continue;
            }
            Channel channel = entry.getValue();
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(envelope);
            }
        }
    }
}
