package org.example.reconnect;

import org.example.config.ServerConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores pending room-rejoin state after heartbeat kick. Entries expire to avoid leaks.
 */
public class ReconnectStateStore {
    private static final ReconnectStateStore INSTANCE = new ReconnectStateStore();

    private final Map<String, PendingRejoin> pendingByClientId = new ConcurrentHashMap<>();

    private ReconnectStateStore() {
    }

    public static ReconnectStateStore getInstance() {
        return INSTANCE;
    }

    public void save(String clientId, String roomName) {
        long expireAt = System.currentTimeMillis() + ServerConfig.PENDING_REJOIN_TTL_MS;
        pendingByClientId.put(clientId, new PendingRejoin(clientId, roomName, expireAt));
    }

    public PendingRejoin getValid(String clientId) {
        purgeExpired();
        PendingRejoin pending = pendingByClientId.get(clientId);
        if (pending == null) {
            return null;
        }
        if (pending.isExpired(System.currentTimeMillis())) {
            pendingByClientId.remove(clientId, pending);
            return null;
        }
        return pending;
    }

    public PendingRejoin remove(String clientId) {
        return pendingByClientId.remove(clientId);
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        pendingByClientId.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }
}
