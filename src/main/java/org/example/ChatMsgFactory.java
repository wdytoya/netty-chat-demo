package org.example;

import org.example.demo.protos.ChatMsg;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ChatMsgFactory {
    public static ChatMsg buildMsg(ChatMsg.MsgType type, String body) {
        return ChatMsg.newBuilder()
                .setServerMainVer(1).setServerSubVer(0).setServerModVer(0)
                .setSessionId(UUID.randomUUID().toString())
                .setMsgType(type)
                .setMsgLen(body.getBytes(StandardCharsets.UTF_8).length)
                .setMsgBody(body)
                .build();
    }
}
