package org.example.session;

/**
 * Server-issued identity credential used for reconnect HELLO.
 */
public class ClientCredential {
    private final String clientId;
    private final String reconnectToken;
    private volatile long expireAtMs;

    public ClientCredential(String clientId, String reconnectToken, long expireAtMs) {
        this.clientId = clientId;
        this.reconnectToken = reconnectToken;
        this.expireAtMs = expireAtMs;
    }

    public String getClientId() {
        return clientId;
    }

    public String getReconnectToken() {
        return reconnectToken;
    }

    public long getExpireAtMs() {
        return expireAtMs;
    }

    public void renew(long expireAtMs) {
        this.expireAtMs = expireAtMs;
    }

    public boolean isExpired(long nowMs) {
        return nowMs > expireAtMs;
    }
}
