package org.example.session;

import io.netty.channel.Channel;

/**
 * Per-connection authenticated session state.
 */
public class Session {
    private final String clientId;
    private volatile Channel channel;
    private volatile String currentRoomName;
    private volatile String username;

    public Session(String clientId, Channel channel) {
        this.clientId = clientId;
        this.channel = channel;
    }

    public String getClientId() {
        return clientId;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getCurrentRoomName() {
        return currentRoomName;
    }

    public void setCurrentRoomName(String currentRoomName) {
        this.currentRoomName = currentRoomName;
    }

    public boolean isInRoom() {
        return currentRoomName != null && !currentRoomName.isEmpty();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isLoggedIn() {
        return username != null && !username.isEmpty();
    }

    /** Display name for room broadcasts; never expose clientId to peers. */
    public String displayName() {
        return isLoggedIn() ? username : "anonymous";
    }

    /** Username for server logs; "-" when not logged in. */
    public String logUsername() {
        return isLoggedIn() ? username : "-";
    }
}
