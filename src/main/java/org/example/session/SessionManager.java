package org.example.session;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.example.config.ServerConfig;
import org.example.demo.protos.ErrorCode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe mapping between channels, sessions and reconnect credentials.
 */
public class SessionManager {
    public static final AttributeKey<String> CLIENT_ID_KEY = AttributeKey.valueOf("clientId");

    private static final SessionManager INSTANCE = new SessionManager();

    private final Map<String, Session> sessionsByClientId = new ConcurrentHashMap<>();
    private final Map<Channel, String> clientIdByChannel = new ConcurrentHashMap<>();
    private final Map<String, ClientCredential> credentialsByClientId = new ConcurrentHashMap<>();
    /** username -> clientId, unique among logged-in identities. */
    private final Map<String, String> clientIdByUsername = new ConcurrentHashMap<>();

    private SessionManager() {
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    /**
     * First connect or reconnect with optional cached credentials.
     * Returns the bound session (always succeeds; invalid credentials mint a new identity).
     */
    public HelloResult hello(Channel channel, String claimedClientId, String claimedToken) {
        long now = System.currentTimeMillis();
        purgeExpiredCredentials(now);

        if (claimedClientId != null && !claimedClientId.isEmpty()
                && claimedToken != null && !claimedToken.isEmpty()) {
            ClientCredential credential = credentialsByClientId.get(claimedClientId);
            if (credential != null
                    && !credential.isExpired(now)
                    && credential.getReconnectToken().equals(claimedToken)) {
                // Valid reconnect: rebind channel to same clientId.
                detachChannel(channel);
                Session existing = sessionsByClientId.get(claimedClientId);
                if (existing != null) {
                    Channel oldChannel = existing.getChannel();
                    if (oldChannel != null && oldChannel != channel && oldChannel.isActive()) {
                        oldChannel.close();
                    }
                    existing.setChannel(channel);
                } else {
                    existing = new Session(claimedClientId, channel);
                    sessionsByClientId.put(claimedClientId, existing);
                }
                bindChannel(channel, claimedClientId);
                credential.renew(now + ServerConfig.CREDENTIAL_TTL_MS);
                return new HelloResult(existing, credential, true);
            }
        }

        // New identity.
        detachChannel(channel);
        String clientId = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        ClientCredential credential = new ClientCredential(
                clientId, token, now + ServerConfig.CREDENTIAL_TTL_MS);
        credentialsByClientId.put(clientId, credential);

        Session session = new Session(clientId, channel);
        sessionsByClientId.put(clientId, session);
        bindChannel(channel, clientId);
        return new HelloResult(session, credential, false);
    }

    public Session getByChannel(Channel channel) {
        String clientId = clientIdByChannel.get(channel);
        if (clientId == null) {
            return null;
        }
        return sessionsByClientId.get(clientId);
    }

    public Session getByClientId(String clientId) {
        return sessionsByClientId.get(clientId);
    }

    /**
     * Bind a display username to the session. Username must be unique across clients.
     */
    public LoginResult login(Session session, String rawUsername) {
        purgeExpiredCredentials(System.currentTimeMillis());
        if (rawUsername == null || rawUsername.trim().isEmpty()) {
            return LoginResult.fail(ErrorCode.BAD_REQUEST.getNumber(), "Username is empty");
        }
        String username = rawUsername.trim();
        if (username.length() > ServerConfig.MAX_USERNAME_LENGTH) {
            return LoginResult.fail(ErrorCode.BAD_REQUEST.getNumber(), "Username too long");
        }
        if (username.equals(session.getUsername())) {
            return LoginResult.ok(username);
        }
        // Already logged in as another name: reject (client should not re-login).
        if (session.isLoggedIn()) {
            return LoginResult.fail(ErrorCode.BAD_REQUEST.getNumber(),
                    "Already logged in as " + session.getUsername());
        }

        String occupiedBy = clientIdByUsername.get(username);
        if (occupiedBy != null && !occupiedBy.equals(session.getClientId())) {
            return LoginResult.fail(ErrorCode.USERNAME_TAKEN.getNumber(), "Username already taken");
        }

        clientIdByUsername.put(username, session.getClientId());
        session.setUsername(username);
        return LoginResult.ok(username);
    }

    public void onChannelInactive(Channel channel) {
        String clientId = clientIdByChannel.remove(channel);
        if (clientId == null) {
            return;
        }
        channel.attr(CLIENT_ID_KEY).set(null);
        Session session = sessionsByClientId.get(clientId);
        if (session != null && session.getChannel() == channel) {
            session.setChannel(null);
            // Keep session briefly for reconnect; username freed after disconnected TTL.
            beginDisconnectedGrace(clientId);
        }
    }

    public void renewCredential(String clientId) {
        ClientCredential credential = credentialsByClientId.get(clientId);
        if (credential != null) {
            credential.renew(System.currentTimeMillis() + ServerConfig.CREDENTIAL_TTL_MS);
        }
    }

    /**
     * After kick/disconnect: shorten reconnect-token + username hold to DISCONNECTED_SESSION_TTL.
     */
    public void beginDisconnectedGrace(String clientId) {
        ClientCredential credential = credentialsByClientId.get(clientId);
        if (credential != null) {
            long expireAt = System.currentTimeMillis() + ServerConfig.DISCONNECTED_SESSION_TTL_MS;
            credential.renew(expireAt);
            System.out.println("[SESSION] clientId=" + clientId
                    + " disconnected grace until " + expireAt
                    + " (" + (ServerConfig.DISCONNECTED_SESSION_TTL_MS / 1000) + "s)");
        }
    }

    public void removeIdentity(String clientId) {
        Session session = sessionsByClientId.remove(clientId);
        if (session != null) {
            if (session.getUsername() != null) {
                clientIdByUsername.remove(session.getUsername(), clientId);
            }
            if (session.getChannel() != null) {
                clientIdByChannel.remove(session.getChannel());
                session.getChannel().attr(CLIENT_ID_KEY).set(null);
            }
        }
        credentialsByClientId.remove(clientId);
    }

    private void bindChannel(Channel channel, String clientId) {
        clientIdByChannel.put(channel, clientId);
        channel.attr(CLIENT_ID_KEY).set(clientId);
    }

    private void detachChannel(Channel channel) {
        String oldId = clientIdByChannel.remove(channel);
        if (oldId != null) {
            channel.attr(CLIENT_ID_KEY).set(null);
            Session session = sessionsByClientId.get(oldId);
            if (session != null && session.getChannel() == channel) {
                session.setChannel(null);
            }
        }
    }

    private void purgeExpiredCredentials(long now) {
        credentialsByClientId.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired(now)) {
                String clientId = entry.getKey();
                Session session = sessionsByClientId.get(clientId);
                // Only remove identity when no active channel.
                if (session == null || session.getChannel() == null || !session.getChannel().isActive()) {
                    if (session != null && session.getUsername() != null) {
                        clientIdByUsername.remove(session.getUsername(), clientId);
                    }
                    sessionsByClientId.remove(clientId);
                    return true;
                }
            }
            return false;
        });
    }

    public static final class LoginResult {
        public final boolean success;
        public final int code;
        public final String message;
        public final String username;

        private LoginResult(boolean success, int code, String message, String username) {
            this.success = success;
            this.code = code;
            this.message = message;
            this.username = username;
        }

        static LoginResult ok(String username) {
            return new LoginResult(true, ErrorCode.OK.getNumber(), "OK", username);
        }

        static LoginResult fail(int code, String message) {
            return new LoginResult(false, code, message, null);
        }
    }

    public static final class HelloResult {
        private final Session session;
        private final ClientCredential credential;
        private final boolean reconnect;

        public HelloResult(Session session, ClientCredential credential, boolean reconnect) {
            this.session = session;
            this.credential = credential;
            this.reconnect = reconnect;
        }

        public Session getSession() {
            return session;
        }

        public ClientCredential getCredential() {
            return credential;
        }

        public boolean isReconnect() {
            return reconnect;
        }
    }
}
