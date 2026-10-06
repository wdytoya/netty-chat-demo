package org.example.reconnect;

/**
 * Temporary state allowing a heartbeat-kicked client to rejoin its previous room.
 */
public class PendingRejoin {
    private final String clientId;
    private final String roomName;
    private final long expireAtMs;

    public PendingRejoin(String clientId, String roomName, long expireAtMs) {
        this.clientId = clientId;
        this.roomName = roomName;
        this.expireAtMs = expireAtMs;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRoomName() {
        return roomName;
    }

    public long getExpireAtMs() {
        return expireAtMs;
    }

    public boolean isExpired(long nowMs) {
        return nowMs > expireAtMs;
    }
}
