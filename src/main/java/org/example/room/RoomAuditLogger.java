package org.example.room;

/**
 * Console audit logger for room events. Prefix is the event type (e.g. [CHAT], [JOIN]).
 * Does not log passwords. Actor field is always username (never clientId).
 */
public final class RoomAuditLogger {

    private RoomAuditLogger() {
    }

    public static void chat(String roomName, String username, String msgId, String content) {
        System.out.println(String.format(
                "[CHAT] time=%d room=%s username=%s msgId=%s content=%s",
                System.currentTimeMillis(), roomName, nullToDash(username), msgId, content));
    }

    /**
     * @param event    short type used as log prefix, e.g. CREATE / JOIN / LEAVE / OFFLINE
     * @param username display name of the actor (not clientId)
     */
    public static void event(String event, String roomName, String username, String detail) {
        System.out.println(String.format(
                "[%s] time=%d room=%s username=%s detail=%s",
                event, System.currentTimeMillis(), roomName, nullToDash(username),
                detail == null ? "" : detail));
    }

    private static String nullToDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }
}
