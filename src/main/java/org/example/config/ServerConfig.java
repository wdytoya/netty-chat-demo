package org.example.config;

/**
 * Server-side defaults for idle timeout, reconnect window and room limits.
 */
public final class ServerConfig {
    public static final int PROTOCOL_VERSION = 1;
    public static final int SERVER_PORT = 6666;

    /** Reader idle seconds before heartbeat kick. */
    public static final int READER_IDLE_SECONDS = 60;

    /** Client writer idle seconds; ping interval should be well below reader idle. */
    public static final int CLIENT_WRITER_IDLE_SECONDS = 20;

    /** While connected, reconnect-token sliding TTL refreshed by PING. */
    public static final long CREDENTIAL_TTL_MS = 5 * 60 * 1000L;

    /**
     * After disconnect / heartbeat kick: how long reconnect token + username reservation remain.
     * Within this window the same process can reconnect; after it, username can be logged in again.
     */
    public static final long DISCONNECTED_SESSION_TTL_MS = 60 * 1000L;

    /** Pending rejoin TTL after heartbeat kick (aligned with disconnected session window). */
    public static final long PENDING_REJOIN_TTL_MS = DISCONNECTED_SESSION_TTL_MS;

    public static final int MAX_ROOM_MEMBERS = 50;
    public static final int MAX_MESSAGE_LENGTH = 2048;
    public static final int MAX_ROOM_NAME_LENGTH = 32;
    public static final int MAX_PASSWORD_LENGTH = 64;
    public static final int MAX_USERNAME_LENGTH = 32;

    public static final int MAX_FRAME_LENGTH = 1024 * 1024;

    private ServerConfig() {
    }
}
